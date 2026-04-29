#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import gzip
import hashlib
import html
import json
import re
import shutil
import sqlite3
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from import_builder_catalog import (
    build_ancestries as build_builder_ancestries,
    build_backgrounds as build_builder_backgrounds,
    build_classes as build_builder_classes,
    build_features as build_builder_features,
    build_feats as build_builder_feats,
    build_heritages as build_builder_heritages,
    feat_index_record as builder_feat_index_record,
)


CATALOG_SCHEMA_VERSION = 1
CATALOG_ROOM_DATABASE_VERSION = 2
DEFAULT_WARN_SIZE_BYTES = 40 * 1024 * 1024
DEFAULT_MAX_SIZE_BYTES = 80 * 1024 * 1024

DEPRECATED_DATASET_SCRIPTS = [
    "scripts/update_spell_dataset.ps1",
    "scripts/update_class_dataset.ps1",
    "scripts/update_class_spellcasting_dataset.ps1",
    "scripts/update_builder_catalog.ps1",
    "scripts/update_rules_catalog.ps1",
]

SUPPORT_FILES = [
    "system.pf2e.json",
    "static/template.json",
    "static/lang/en.json",
    "static/lang/action-en.json",
    "static/lang/re-en.json",
    "static/lang/kingmaker-en.json",
]

# PF2e player-facing Item packs. Actor, JournalEntry, RollTable, Macro, and SF2e
# packs are intentionally excluded from the Stage 1 catalog.
INCLUDED_PACKS: dict[str, dict[str, str]] = {
    "actionspf2e": {"reason": "sheet-critical", "defaultAutomation": "reference_only"},
    "adventure-specific-actions": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "ancestries": {"reason": "sheet-critical", "defaultAutomation": "partially_automated"},
    "ancestryfeatures": {"reason": "sheet-critical", "defaultAutomation": "partially_automated"},
    "backgrounds": {"reason": "sheet-critical", "defaultAutomation": "partially_automated"},
    "bestiary-ability-glossary-srd": {"reason": "reference-only", "defaultAutomation": "reference_only"},
    "bestiary-effects": {"reason": "reference-only", "defaultAutomation": "reference_only"},
    "bestiary-family-ability-glossary": {"reason": "reference-only", "defaultAutomation": "reference_only"},
    "boons-and-curses": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "campaign-effects": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "classes": {"reason": "sheet-critical", "defaultAutomation": "partially_automated"},
    "classfeatures": {"reason": "sheet-critical", "defaultAutomation": "partially_automated"},
    "conditionitems": {"reason": "sheet-critical", "defaultAutomation": "reference_only"},
    "deities": {"reason": "sheet-critical", "defaultAutomation": "reference_only"},
    "equipment-effects": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "equipment-srd": {"reason": "sheet-critical", "defaultAutomation": "reference_only"},
    "familiar-abilities": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "feat-effects": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "feats-srd": {"reason": "sheet-critical", "defaultAutomation": "partially_automated"},
    "heritages": {"reason": "sheet-critical", "defaultAutomation": "partially_automated"},
    "kingmaker-features": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "other-effects": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "pathfinder-society-boons": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "spell-effects": {"reason": "player-reference", "defaultAutomation": "reference_only"},
    "spells-srd": {"reason": "sheet-critical", "defaultAutomation": "partially_automated"},
}

UUID_PATTERN = re.compile(r"@UUID\[([^\]]+)\](?:\{([^}]+)\})?")
LOCALIZE_PATTERN = re.compile(r"@Localize\[([^\]]+)\]")
INLINE_ROLL_WITH_LABEL_PATTERN = re.compile(r"\[\[[^\]]+\]\]\{([^}]+)\}")
INLINE_ROLL_PATTERN = re.compile(r"\[\[[^\]]+\]\]")


@dataclass(frozen=True)
class PackSpec:
    name: str
    label: str
    document_type: str
    path: str
    full_path: Path
    reason: str
    default_automation: str


def utc_now() -> str:
    return dt.datetime.now(dt.UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def slugify(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", (value or "").strip().lower())
    return normalized.strip("-") or "unknown"


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def pretty_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2)


def get_path(obj: Any, *path: str, default: Any = None) -> Any:
    current = obj
    for segment in path:
        if not isinstance(current, dict) or segment not in current:
            return default
        current = current[segment]
    return current


def as_list(value: Any) -> list[Any]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]


def string_list(value: Any) -> list[str]:
    return sorted({str(item).strip() for item in as_list(value) if str(item).strip()})


def int_or_none(value: Any) -> int | None:
    try:
        if value is None or value == "":
            return None
        return int(value)
    except (TypeError, ValueError):
        return None


def load_json_file(path: Path) -> Any:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def support_file_info(root: Path, issues: list[dict[str, Any]]) -> list[dict[str, Any]]:
    files: list[dict[str, Any]] = []
    for relative in SUPPORT_FILES:
        path = root / relative
        if not path.exists():
            issues.append(issue("WARNING", "MISSING_SUPPORT_FILE", f"Support file is missing: {relative}", path=str(path)))
            continue
        try:
            load_json_file(path)
        except Exception as exc:  # noqa: BLE001
            issues.append(issue("ERROR", "SUPPORT_FILE_PARSE_ERROR", f"Could not parse support file {relative}: {exc}", path=str(path)))
            continue
        files.append(
            {
                "relativePath": relative,
                "sizeBytes": path.stat().st_size,
                "sha256": sha256(path),
            }
        )
    return files


def flatten_localization(value: Any, prefix: str = "") -> dict[str, str]:
    entries: dict[str, str] = {}
    if isinstance(value, dict):
        for key, child in value.items():
            next_prefix = f"{prefix}.{key}" if prefix else str(key)
            entries.update(flatten_localization(child, next_prefix))
    elif isinstance(value, str):
        entries[prefix] = value
    return entries


def load_localization(root: Path, issues: list[dict[str, Any]]) -> dict[str, str]:
    result: dict[str, str] = {}
    for relative in [
        "static/lang/en.json",
        "static/lang/action-en.json",
        "static/lang/re-en.json",
        "static/lang/kingmaker-en.json",
    ]:
        path = root / relative
        if not path.exists():
            issues.append(issue("WARNING", "MISSING_LOCALIZATION_FILE", f"Localization file is missing: {relative}", path=str(path)))
            continue
        try:
            result.update(flatten_localization(load_json_file(path)))
        except Exception as exc:  # noqa: BLE001 - importer should report source file problems.
            issues.append(issue("ERROR", "LOCALIZATION_PARSE_ERROR", f"Could not parse {relative}: {exc}", path=str(path)))
    return result


def issue(
    severity: str,
    code: str,
    message: str,
    *,
    path: str | None = None,
    record_id: str | None = None,
    detail: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "severity": severity,
        "code": code,
        "message": message,
    }
    if path:
        payload["path"] = path
    if record_id:
        payload["recordId"] = record_id
    if detail:
        payload["detail"] = detail
    return payload


def publication(record: dict[str, Any]) -> dict[str, Any]:
    raw = get_path(record, "system", "publication", default={}) or {}
    return {
        "license": raw.get("license"),
        "page": raw.get("page"),
        "remaster": bool(raw.get("remaster", False)),
        "title": raw.get("title"),
    }


def trait_values(record: dict[str, Any]) -> list[str]:
    values = string_list(get_path(record, "system", "traits", "value", default=[]))
    traditions = string_list(get_path(record, "system", "traits", "traditions", default=[]))
    return sorted(set(values + traditions))


def record_category(record: dict[str, Any]) -> str | None:
    record_type = str(record.get("type") or "").strip()
    category = get_path(record, "system", "category")
    if category:
        return str(category)
    if record_type in {"weapon", "armor", "shield", "consumable", "equipment", "treasure", "ammo", "backpack", "kit"}:
        return record_type
    if record_type:
        return record_type
    return None


def record_level(record: dict[str, Any]) -> int | None:
    if record.get("type") == "spell" and "cantrip" in trait_values(record):
        return 0
    return int_or_none(get_path(record, "system", "level", "value"))


def record_rarity(record: dict[str, Any]) -> str:
    return str(get_path(record, "system", "traits", "rarity", default="common") or "common")


def raw_description(record: dict[str, Any]) -> str:
    value = get_path(record, "system", "description", "value", default="")
    return "" if value is None else str(value)


def format_area(record: dict[str, Any]) -> str | None:
    area = get_path(record, "system", "area")
    if not isinstance(area, dict):
        return None
    value = int_or_none(area.get("value"))
    if value is None:
        return None
    area_type = str(area.get("type") or "").strip()
    if area_type:
        return f"{value}-foot {area_type}"
    return f"{value}-foot area"


def format_defense(record: dict[str, Any]) -> str | None:
    save = get_path(record, "system", "defense", "save")
    if not isinstance(save, dict):
        return None
    statistic = str(save.get("statistic") or "").strip()
    if not statistic:
        return None
    capitalized = statistic[:1].upper() + statistic[1:]
    if save.get("basic") is True:
        return f"basic {capitalized}"
    return capitalized


def spell_index(record_id: str, path: Path, record: dict[str, Any]) -> dict[str, Any] | None:
    if record.get("type") != "spell":
        return None
    traits = string_list(get_path(record, "system", "traits", "value", default=[]))
    traditions = string_list(get_path(record, "system", "traits", "traditions", default=[]))
    return {
        "recordId": record_id,
        "spellId": slugify(get_path(record, "system", "slug") or path.stem),
        "rank": record_level(record) or 0,
        "traditionsCsv": ",".join(traditions),
        "traitsCsv": ",".join(traits),
        "castTime": str(get_path(record, "system", "time", "value", default="") or ""),
        "rangeText": str(get_path(record, "system", "range", "value", default="") or ""),
        "targetText": str(get_path(record, "system", "target", "value", default="") or ""),
        "durationText": str(get_path(record, "system", "duration", "value", default="") or ""),
        "areaText": format_area(record),
        "defenseText": format_defense(record),
    }


def stripped_builder_records(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    stripped: list[dict[str, Any]] = []
    for record in records:
        item = dict(record)
        item.pop("description", None)
        stripped.append(item)
    return stripped


def builder_payload_gzip(payload: dict[str, Any]) -> bytes:
    return gzip.compress(canonical_json(payload).encode("utf-8"), compresslevel=9, mtime=0)


def builder_asset(
    *,
    name: str,
    builder_type: str,
    records: list[dict[str, Any]],
    payload_key: str,
    category: str | None = None,
    extra_payload: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload = {
        "descriptionSource": "catalog_records.detail_text",
        payload_key: stripped_builder_records(records),
    }
    if extra_payload:
        payload.update(extra_payload)
    return {
        "name": name,
        "builderType": builder_type,
        "category": category,
        "recordCount": len(records),
        "payloadJsonGzip": builder_payload_gzip(payload),
    }


def build_builder_assets(packs_dir: Path) -> list[dict[str, Any]]:
    classes = build_builder_classes(packs_dir)
    ancestries = build_builder_ancestries(packs_dir)
    heritages = build_builder_heritages(packs_dir)
    backgrounds = build_builder_backgrounds(packs_dir)
    class_features = build_builder_features(packs_dir, "class-features", "class-feature")
    ancestry_features = build_builder_features(packs_dir, "ancestry-features", "ancestry-feature")
    feats_by_category = build_builder_feats(packs_dir)

    assets = [
        builder_asset(name="classes", builder_type="class", records=classes, payload_key="classes"),
        builder_asset(name="ancestries", builder_type="ancestry", records=ancestries, payload_key="ancestries"),
        builder_asset(name="heritages", builder_type="heritage", records=heritages, payload_key="heritages"),
        builder_asset(name="backgrounds", builder_type="background", records=backgrounds, payload_key="backgrounds"),
        builder_asset(name="class-features", builder_type="class-feature", records=class_features, payload_key="features"),
        builder_asset(name="ancestry-features", builder_type="ancestry-feature", records=ancestry_features, payload_key="features"),
    ]

    feat_index_entries: list[dict[str, Any]] = []
    feat_shards: list[dict[str, Any]] = []
    for category, records in sorted(feats_by_category.items()):
        if not records:
            continue
        asset_name = f"feats.{category}"
        assets.append(
            builder_asset(
                name=asset_name,
                builder_type="feat",
                category=category,
                records=records,
                payload_key="feats",
                extra_payload={"category": category},
            )
        )
        feat_shards.append({"category": category, "name": asset_name, "count": len(records)})
        feat_index_entries.extend(builder_feat_index_record(record, asset_name) for record in records)

    feat_index_payload = {
        "categories": sorted(feats_by_category.keys()),
        "shards": sorted(feat_shards, key=lambda item: item["name"]),
        "feats": sorted(feat_index_entries, key=lambda item: (item["level"], item["name"], item["id"])),
    }
    assets.append(
        {
            "name": "feats.index",
            "builderType": "feat-index",
            "category": None,
            "recordCount": len(feat_index_entries),
            "payloadJsonGzip": builder_payload_gzip(feat_index_payload),
        }
    )
    return assets


def build_builder_assets_for_audit(packs_dir: Path, issues: list[dict[str, Any]]) -> list[dict[str, Any]]:
    try:
        return build_builder_assets(packs_dir)
    except SystemExit as exc:
        issues.append(issue("ERROR", "BUILDER_INDEX_ERROR", f"Could not build builder catalog index: {exc}"))
    except Exception as exc:  # noqa: BLE001
        issues.append(issue("ERROR", "BUILDER_INDEX_ERROR", f"Could not build builder catalog index: {exc}"))
    return []


def html_to_text(markup: str) -> str:
    text = markup or ""
    text = re.sub(r"<\s*br\s*/?\s*>", "\n", text, flags=re.I)
    text = re.sub(r"<\s*hr\s*/?\s*>", "\n---\n", text, flags=re.I)
    text = re.sub(r"<\s*/p\s*>", "\n", text, flags=re.I)
    text = re.sub(r"<\s*p[^>]*\s*>", "", text, flags=re.I)
    text = re.sub(r"<\s*li[^>]*\s*>", "- ", text, flags=re.I)
    text = re.sub(r"<\s*/li\s*>", "\n", text, flags=re.I)
    text = re.sub(r"<\s*/?(ul|ol|h1|h2|h3|h4|h5|h6|section|div|table|thead|tbody|tr|td|th)[^>]*\s*>", "\n", text, flags=re.I)
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text)
    text = text.replace("\r", "")
    text = re.sub(r"[\t ]+", " ", text)
    text = re.sub(r" *\n *", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def display_label_from_uuid(uuid: str) -> str:
    tail = uuid.rsplit(".", 1)[-1]
    return tail.replace("-", " ").strip() or uuid


def resolve_text(text: str, uuid_names: dict[str, str], localization: dict[str, str], unresolved_localizations: set[str]) -> str:
    def replace_uuid(match: re.Match[str]) -> str:
        uuid = match.group(1)
        explicit_label = match.group(2)
        return explicit_label or uuid_names.get(uuid) or display_label_from_uuid(uuid)

    def replace_localize(match: re.Match[str]) -> str:
        key = match.group(1)
        value = localization.get(key)
        if value is None:
            unresolved_localizations.add(key)
            return key
        return value

    text = UUID_PATTERN.sub(replace_uuid, text)
    text = LOCALIZE_PATTERN.sub(replace_localize, text)
    text = INLINE_ROLL_WITH_LABEL_PATTERN.sub(r"\1", text)
    text = INLINE_ROLL_PATTERN.sub("", text)
    text = re.sub(r"@(Damage|Check|Template)\[(.*?)\]", r"\2", text)
    return html_to_text(text)


def extract_uuids(raw_text: str) -> list[dict[str, str | None]]:
    refs = []
    seen: set[tuple[str, str | None]] = set()
    for match in UUID_PATTERN.finditer(raw_text):
        uuid = match.group(1).strip()
        label = match.group(2).strip() if match.group(2) else None
        key = (uuid, label)
        if uuid and key not in seen:
            refs.append({"uuid": uuid, "label": label})
            seen.add(key)
    return refs


def extract_localization_keys(raw_text: str) -> list[str]:
    return sorted({match.group(1).strip() for match in LOCALIZE_PATTERN.finditer(raw_text) if match.group(1).strip()})


def image_audit(root: Path, image_path: str | None) -> tuple[int, str | None]:
    if not image_path:
        return 0, None
    normalized = image_path.replace("\\", "/")
    if normalized.startswith("systems/pf2e/"):
        relative = normalized.removeprefix("systems/pf2e/")
        expected = root / "static" / relative
        return (0 if expected.exists() else 1), str(expected)
    if normalized.startswith("static/"):
        expected = root / normalized
        return (0 if expected.exists() else 1), str(expected)
    # Foundry core icons and other module paths are not bundled from the PF2e repo.
    return 0, None


def load_pack_specs(root: Path, system_manifest: dict[str, Any], issues: list[dict[str, Any]]) -> list[PackSpec]:
    pack_specs: list[PackSpec] = []
    packs_by_name = {str(pack.get("name")): pack for pack in system_manifest.get("packs", []) if isinstance(pack, dict)}
    for pack_name, policy in sorted(INCLUDED_PACKS.items()):
        pack = packs_by_name.get(pack_name)
        if not pack:
            issues.append(issue("ERROR", "MISSING_PACK_REGISTRY_ENTRY", f"system.pf2e.json has no pack named {pack_name}"))
            continue
        document_type = str(pack.get("type") or "")
        if document_type != "Item":
            issues.append(
                issue(
                    "ERROR",
                    "UNEXPECTED_PACK_TYPE",
                    f"Included pack {pack_name} is {document_type}, expected Item",
                    detail={"packName": pack_name, "type": document_type},
                )
            )
            continue
        raw_path = str(pack.get("path") or "")
        if not raw_path.startswith("packs/"):
            issues.append(issue("ERROR", "UNEXPECTED_PACK_PATH", f"Included pack {pack_name} has unexpected path {raw_path}"))
            continue
        full_path = root / "packs" / "pf2e" / raw_path.removeprefix("packs/")
        if not full_path.exists():
            issues.append(issue("ERROR", "MISSING_PACK_PATH", f"Included pack path is missing: {raw_path}", path=str(full_path)))
            continue
        pack_specs.append(
            PackSpec(
                name=pack_name,
                label=str(pack.get("label") or pack_name),
                document_type=document_type,
                path=raw_path,
                full_path=full_path,
                reason=policy["reason"],
                default_automation=policy["defaultAutomation"],
            )
        )
    return pack_specs


def required_field_issues(record: dict[str, Any], record_id: str, path: Path) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    if not str(record.get("name") or "").strip():
        result.append(issue("ERROR", "MISSING_REQUIRED_FIELD", "Record is missing name", path=str(path), record_id=record_id))
    if not str(record.get("type") or "").strip():
        result.append(issue("ERROR", "MISSING_REQUIRED_FIELD", "Record is missing type", path=str(path), record_id=record_id))
    pub = publication(record)
    if not pub.get("title") or not pub.get("license"):
        result.append(
            issue(
                "WARNING",
                "MISSING_SOURCE_METADATA",
                "Record is missing publication title or license",
                path=str(path),
                record_id=record_id,
                detail={"source": pub},
            )
        )
    return result


def uuid_aliases(pack_name: str, record: dict[str, Any]) -> list[str]:
    aliases: list[str] = []
    for document_type in ["Item"]:
        record_id = str(record.get("_id") or "").strip()
        if record_id:
            aliases.append(f"Compendium.pf2e.{pack_name}.{document_type}.{record_id}")
        name = str(record.get("name") or "").strip()
        if name:
            aliases.append(f"Compendium.pf2e.{pack_name}.{document_type}.{name}")
    return aliases


def is_blank_or_suspicious_trait(trait: str) -> bool:
    if not trait.strip():
        return True
    return bool(re.search(r"\s", trait))


def collect_records(
    root: Path,
    pack_specs: list[PackSpec],
    issues: list[dict[str, Any]],
) -> tuple[list[dict[str, Any]], list[dict[str, Any]], dict[str, dict[str, Any]]]:
    records: list[dict[str, Any]] = []
    links: list[dict[str, Any]] = []
    pack_stats: dict[str, dict[str, Any]] = {}
    seen_record_ids: set[str] = set()

    for spec in pack_specs:
        source_files = sorted(spec.full_path.rglob("*.json"), key=lambda value: str(value).lower())
        stats = {
            "packName": spec.name,
            "label": spec.label,
            "path": spec.path,
            "documentType": spec.document_type,
            "includedReason": spec.reason,
            "sourceFileCount": len([path for path in source_files if path.name != "_folders.json"]),
            "importedCount": 0,
            "skippedCount": 0,
            "parseErrorCount": 0,
        }

        for path in source_files:
            if path.name == "_folders.json":
                continue
            relative_to_pack = path.relative_to(spec.full_path).with_suffix("").as_posix()
            record_id = f"{spec.name}:{slugify(relative_to_pack)}"
            if record_id in seen_record_ids:
                issues.append(issue("ERROR", "DUPLICATE_RECORD_ID", f"Duplicate catalog record id: {record_id}", path=str(path), record_id=record_id))
                stats["skippedCount"] += 1
                continue
            seen_record_ids.add(record_id)

            try:
                raw_text = path.read_text(encoding="utf-8-sig")
                record = json.loads(raw_text)
            except Exception as exc:  # noqa: BLE001
                issues.append(issue("ERROR", "MALFORMED_JSON", f"Could not parse JSON: {exc}", path=str(path), record_id=record_id))
                stats["parseErrorCount"] += 1
                stats["skippedCount"] += 1
                continue
            if not isinstance(record, dict):
                issues.append(issue("ERROR", "UNEXPECTED_JSON_SHAPE", "Pack record root must be a JSON object", path=str(path), record_id=record_id))
                stats["skippedCount"] += 1
                continue

            issues.extend(required_field_issues(record, record_id, path))
            pub = publication(record)
            traits = trait_values(record)
            for trait in traits:
                if is_blank_or_suspicious_trait(trait):
                    issues.append(issue("WARNING", "UNKNOWN_TRAIT", f"Suspicious trait value: {trait}", path=str(path), record_id=record_id))

            image_path = str(record.get("img") or "") or None
            image_missing, expected_image_path = image_audit(root, image_path)
            if image_missing:
                issues.append(
                    issue(
                        "WARNING",
                        "MISSING_IMAGE",
                        f"Referenced PF2e image path does not exist: {image_path}",
                        path=str(path),
                        record_id=record_id,
                        detail={"expectedPath": expected_image_path},
                    )
                )

            raw_description_text = raw_description(record)
            raw_payload = raw_text
            refs = extract_uuids(raw_payload)
            for ref in refs:
                links.append(
                    {
                        "fromRecordId": record_id,
                        "toUuid": ref["uuid"],
                        "toRecordId": None,
                        "linkType": "uuid",
                        "sourcePath": "raw_json",
                        "label": ref["label"],
                        "resolved": 0,
                    }
                )

            records.append(
                {
                    "id": record_id,
                    "packName": spec.name,
                    "packLabel": spec.label,
                    "packPath": spec.path,
                    "recordType": str(record.get("type") or ""),
                    "category": record_category(record),
                    "name": str(record.get("name") or record_id),
                    "level": record_level(record),
                    "rarity": record_rarity(record),
                    "sourceTitle": pub.get("title"),
                    "sourceLicense": pub.get("license"),
                    "sourcePage": pub.get("page"),
                    "imagePath": image_path,
                    "imageMissing": image_missing,
                    "automationStatus": spec.default_automation,
                    "detailText": "",
                    "rawDescription": raw_description_text,
                    "rawJson": raw_payload,
                    "normalizedJson": canonical_json(
                        {
                            "id": record_id,
                            "uuidAliases": uuid_aliases(spec.name, record),
                            "name": record.get("name"),
                            "type": record.get("type"),
                            "category": record_category(record),
                            "level": record_level(record),
                            "rarity": record_rarity(record),
                            "traits": traits,
                            "source": pub,
                        }
                    ),
                    "relativePath": str(path.relative_to(root)).replace("\\", "/"),
                    "traits": traits,
                    "uuidAliases": uuid_aliases(spec.name, record),
                    "localizationKeys": extract_localization_keys(raw_payload),
                    "spellIndex": spell_index(record_id, path, record),
                }
            )
            stats["importedCount"] += 1

        pack_stats[spec.name] = stats
    return records, links, pack_stats


def build_uuid_index(records: list[dict[str, Any]], issues: list[dict[str, Any]]) -> dict[str, str]:
    uuid_to_record_id: dict[str, str] = {}
    duplicate_aliases: set[str] = set()
    for record in records:
        for alias in record["uuidAliases"]:
            existing = uuid_to_record_id.get(alias)
            if existing and existing != record["id"]:
                duplicate_aliases.add(alias)
                issues.append(
                    issue(
                        "WARNING",
                        "DUPLICATE_UUID_ALIAS",
                        f"UUID alias maps to more than one record: {alias}",
                        record_id=record["id"],
                        detail={"existingRecordId": existing},
                    )
                )
                continue
            uuid_to_record_id[alias] = record["id"]
    for alias in duplicate_aliases:
        uuid_to_record_id.pop(alias, None)
    return uuid_to_record_id


def included_pack_from_uuid(uuid: str) -> str | None:
    match = re.match(r"^Compendium\.pf2e\.([^.]+)\.", uuid)
    if not match:
        return None
    return match.group(1)


def resolve_links(
    records: list[dict[str, Any]],
    links: list[dict[str, Any]],
    uuid_to_record_id: dict[str, str],
    localization: dict[str, str],
    issues: list[dict[str, Any]],
    strict_references: bool,
) -> None:
    uuid_names = {alias: record["name"] for record in records for alias in record["uuidAliases"] if alias in uuid_to_record_id}
    record_by_id = {record["id"]: record for record in records}

    for link in links:
        target_id = uuid_to_record_id.get(str(link["toUuid"]))
        if target_id:
            link["toRecordId"] = target_id
            link["resolved"] = 1
            continue

        pack_name = included_pack_from_uuid(str(link["toUuid"]))
        severity = "ERROR" if strict_references and pack_name in INCLUDED_PACKS else "WARNING"
        issues.append(
            issue(
                severity,
                "UNRESOLVED_UUID",
                f"Could not resolve UUID reference: {link['toUuid']}",
                record_id=str(link["fromRecordId"]),
                detail={"targetPack": pack_name, "strictReferences": strict_references},
            )
        )

    unresolved_localizations: set[str] = set()
    for record in records:
        resolved_description = resolve_text(record["rawDescription"], uuid_names, localization, unresolved_localizations)
        record["detailText"] = resolved_description
        for key in record["localizationKeys"]:
            if key not in localization:
                unresolved_localizations.add(key)

    for key in sorted(unresolved_localizations):
        issues.append(issue("WARNING", "UNRESOLVED_LOCALIZATION", f"Could not resolve localization key: {key}"))

    # Add record names to links after resolution for easier downstream auditing.
    for link in links:
        target_id = link.get("toRecordId")
        if target_id and target_id in record_by_id and not link.get("label"):
            link["label"] = record_by_id[target_id]["name"]


def create_catalog_db(
    db_path: Path,
    records: list[dict[str, Any]],
    links: list[dict[str, Any]],
    builder_assets: list[dict[str, Any]],
    pack_stats: dict[str, dict[str, Any]],
    issues: list[dict[str, Any]],
    metadata: dict[str, Any],
    uuid_to_record_id: dict[str, str],
) -> None:
    if db_path.exists():
        db_path.unlink()
    db_path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(str(db_path))
    try:
        connection.execute("PRAGMA journal_mode=OFF")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute(f"PRAGMA user_version={CATALOG_ROOM_DATABASE_VERSION}")
        connection.executescript(
            """
            CREATE TABLE catalog_metadata (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT NOT NULL
            );

            CREATE TABLE catalog_records (
                id TEXT PRIMARY KEY NOT NULL,
                uuid TEXT,
                pack_name TEXT NOT NULL,
                pack_label TEXT NOT NULL,
                pack_path TEXT NOT NULL,
                record_type TEXT NOT NULL,
                category TEXT,
                name TEXT NOT NULL,
                level INTEGER,
                rarity TEXT,
                source_title TEXT,
                source_license TEXT,
                source_page TEXT,
                image_path TEXT,
                image_missing INTEGER NOT NULL,
                automation_status TEXT NOT NULL,
                detail_text TEXT NOT NULL,
                raw_json_gzip BLOB NOT NULL,
                normalized_json TEXT NOT NULL,
                relative_path TEXT NOT NULL
            );

            CREATE TABLE catalog_spell_index (
                record_id TEXT PRIMARY KEY NOT NULL,
                spell_id TEXT NOT NULL,
                rank INTEGER NOT NULL,
                traditions_csv TEXT NOT NULL,
                traits_csv TEXT NOT NULL,
                cast_time TEXT NOT NULL,
                range_text TEXT NOT NULL,
                target_text TEXT NOT NULL,
                duration_text TEXT NOT NULL,
                area_text TEXT,
                defense_text TEXT
            );

            CREATE TABLE catalog_builder_assets (
                name TEXT PRIMARY KEY NOT NULL,
                builder_type TEXT NOT NULL,
                category TEXT,
                record_count INTEGER NOT NULL,
                payload_json_gzip BLOB NOT NULL
            );

            CREATE TABLE uuid_index (
                uuid TEXT PRIMARY KEY NOT NULL,
                record_id TEXT NOT NULL,
                name TEXT NOT NULL,
                record_type TEXT NOT NULL,
                pack_name TEXT NOT NULL
            );

            CREATE TABLE catalog_links (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                from_record_id TEXT NOT NULL,
                to_uuid TEXT NOT NULL,
                to_record_id TEXT,
                link_type TEXT NOT NULL,
                source_path TEXT NOT NULL,
                label TEXT,
                resolved INTEGER NOT NULL
            );

            CREATE TABLE catalog_traits (
                record_id TEXT NOT NULL,
                trait TEXT NOT NULL
            );

            CREATE TABLE catalog_pack_stats (
                pack_name TEXT PRIMARY KEY NOT NULL,
                label TEXT NOT NULL,
                path TEXT NOT NULL,
                document_type TEXT NOT NULL,
                included_reason TEXT NOT NULL,
                source_file_count INTEGER NOT NULL,
                imported_count INTEGER NOT NULL,
                skipped_count INTEGER NOT NULL,
                parse_error_count INTEGER NOT NULL
            );

            CREATE TABLE catalog_issues (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                severity TEXT NOT NULL,
                code TEXT NOT NULL,
                message TEXT NOT NULL,
                path TEXT,
                record_id TEXT,
                detail_json TEXT NOT NULL
            );

            CREATE INDEX index_catalog_records_name ON catalog_records(name);
            CREATE INDEX index_catalog_records_type_name ON catalog_records(record_type, name);
            CREATE INDEX index_catalog_records_pack_name ON catalog_records(pack_name, name);
            CREATE INDEX index_catalog_records_category_name ON catalog_records(category, name);
            CREATE INDEX index_catalog_records_level ON catalog_records(level);
            CREATE INDEX index_catalog_records_automation ON catalog_records(automation_status);
            CREATE INDEX index_catalog_spell_index_spell_id ON catalog_spell_index(spell_id);
            CREATE INDEX index_catalog_spell_index_rank ON catalog_spell_index(rank);
            CREATE INDEX index_catalog_spell_index_traditions ON catalog_spell_index(traditions_csv);
            CREATE INDEX index_catalog_spell_index_traits ON catalog_spell_index(traits_csv);
            CREATE INDEX index_catalog_builder_assets_type_category ON catalog_builder_assets(builder_type, category);
            CREATE INDEX index_catalog_links_from_record_id ON catalog_links(from_record_id);
            CREATE INDEX index_catalog_links_to_record_id ON catalog_links(to_record_id);
            CREATE INDEX index_catalog_traits_trait ON catalog_traits(trait);
            """
        )

        connection.executemany(
            "INSERT INTO catalog_metadata(key, value) VALUES (?, ?)",
            [(key, canonical_json(value) if isinstance(value, (dict, list)) else str(value)) for key, value in sorted(metadata.items())],
        )
        connection.executemany(
            """
            INSERT INTO catalog_records(
                id, uuid, pack_name, pack_label, pack_path, record_type, category, name, level,
                rarity, source_title, source_license, source_page, image_path, image_missing,
                automation_status, detail_text, raw_json_gzip, normalized_json, relative_path
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    record["id"],
                    record["uuidAliases"][0] if record["uuidAliases"] else None,
                    record["packName"],
                    record["packLabel"],
                    record["packPath"],
                    record["recordType"],
                    record["category"],
                    record["name"],
                    record["level"],
                    record["rarity"],
                    record["sourceTitle"],
                    record["sourceLicense"],
                    record["sourcePage"],
                    record["imagePath"],
                    record["imageMissing"],
                    record["automationStatus"],
                    record["detailText"],
                    gzip.compress(record["rawJson"].encode("utf-8")),
                    record["normalizedJson"],
                    record["relativePath"],
                )
                for record in records
            ],
        )
        connection.executemany(
            "INSERT OR IGNORE INTO uuid_index(uuid, record_id, name, record_type, pack_name) VALUES (?, ?, ?, ?, ?)",
            [
                (uuid, record_id, record_by_id[record_id]["name"], record_by_id[record_id]["recordType"], record_by_id[record_id]["packName"])
                for record_by_id in [{record["id"]: record for record in records}]
                for uuid, record_id in sorted(uuid_to_record_id.items())
                if record_id in record_by_id
            ],
        )
        connection.executemany(
            """
            INSERT INTO catalog_links(from_record_id, to_uuid, to_record_id, link_type, source_path, label, resolved)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    link["fromRecordId"],
                    link["toUuid"],
                    link.get("toRecordId"),
                    link["linkType"],
                    link["sourcePath"],
                    link.get("label"),
                    int(link["resolved"]),
                )
                for link in links
            ],
        )
        connection.executemany(
            "INSERT INTO catalog_traits(record_id, trait) VALUES (?, ?)",
            [(record["id"], trait) for record in records for trait in record["traits"]],
        )
        connection.executemany(
            """
            INSERT INTO catalog_spell_index(
                record_id, spell_id, rank, traditions_csv, traits_csv, cast_time,
                range_text, target_text, duration_text, area_text, defense_text
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    spell["recordId"],
                    spell["spellId"],
                    spell["rank"],
                    spell["traditionsCsv"],
                    spell["traitsCsv"],
                    spell["castTime"],
                    spell["rangeText"],
                    spell["targetText"],
                    spell["durationText"],
                    spell["areaText"],
                    spell["defenseText"],
                )
                for record in records
                for spell in [record.get("spellIndex")]
                if spell is not None
            ],
        )
        connection.executemany(
            """
            INSERT INTO catalog_builder_assets(name, builder_type, category, record_count, payload_json_gzip)
            VALUES (?, ?, ?, ?, ?)
            """,
            [
                (
                    asset["name"],
                    asset["builderType"],
                    asset.get("category"),
                    asset["recordCount"],
                    asset["payloadJsonGzip"],
                )
                for asset in builder_assets
            ],
        )
        connection.executemany(
            """
            INSERT INTO catalog_pack_stats(
                pack_name, label, path, document_type, included_reason, source_file_count,
                imported_count, skipped_count, parse_error_count
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    stats["packName"],
                    stats["label"],
                    stats["path"],
                    stats["documentType"],
                    stats["includedReason"],
                    stats["sourceFileCount"],
                    stats["importedCount"],
                    stats["skippedCount"],
                    stats["parseErrorCount"],
                )
                for stats in sorted(pack_stats.values(), key=lambda item: item["packName"])
            ],
        )
        connection.executemany(
            "INSERT INTO catalog_issues(severity, code, message, path, record_id, detail_json) VALUES (?, ?, ?, ?, ?, ?)",
            [
                (
                    item["severity"],
                    item["code"],
                    item["message"],
                    item.get("path"),
                    item.get("recordId"),
                    canonical_json(item.get("detail", {})),
                )
                for item in issues
            ],
        )
        connection.commit()
    finally:
        connection.close()


def create_runtime_catalog_db(source_db_path: Path, runtime_db_path: Path) -> None:
    if runtime_db_path.exists():
        runtime_db_path.unlink()
    shutil.copy2(source_db_path, runtime_db_path)
    connection = sqlite3.connect(str(runtime_db_path))
    try:
        connection.execute("PRAGMA journal_mode=OFF")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute(f"PRAGMA user_version={CATALOG_ROOM_DATABASE_VERSION}")
        connection.executescript(
            """
            DROP TABLE IF EXISTS catalog_issues;
            DROP TABLE IF EXISTS catalog_pack_stats;
            DROP TABLE IF EXISTS catalog_traits;
            UPDATE catalog_records
            SET raw_json_gzip = x'',
                normalized_json = '';
            """
        )
        connection.executemany(
            "INSERT OR REPLACE INTO catalog_metadata(key, value) VALUES (?, ?)",
            [
                ("catalog_runtime_profile", "android_compact"),
                ("catalog_room_database_version", str(CATALOG_ROOM_DATABASE_VERSION)),
                ("raw_json_gzip", "omitted_from_runtime_db"),
                ("normalized_json", "omitted_from_runtime_db"),
            ],
        )
        connection.commit()
        connection.execute("VACUUM")
    finally:
        connection.close()


def summarize_issues(issues: list[dict[str, Any]]) -> dict[str, Any]:
    by_severity = Counter(item["severity"] for item in issues)
    by_code = Counter(item["code"] for item in issues)
    samples: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in issues:
        bucket = samples[item["code"]]
        if len(bucket) < 10:
            bucket.append(item)
    return {
        "bySeverity": dict(sorted(by_severity.items())),
        "byCode": dict(sorted(by_code.items())),
        "samplesByCode": dict(sorted(samples.items())),
    }


def write_outputs(
    output_dir: Path,
    db_path: Path,
    runtime_db_path: Path,
    records: list[dict[str, Any]],
    links: list[dict[str, Any]],
    builder_assets: list[dict[str, Any]],
    pack_stats: dict[str, dict[str, Any]],
    issues: list[dict[str, Any]],
    metadata: dict[str, Any],
    warn_size_bytes: int,
    max_size_bytes: int,
    allow_oversize: bool,
) -> tuple[dict[str, Any], dict[str, Any]]:
    db_size = db_path.stat().st_size if db_path.exists() else 0
    runtime_db_size = runtime_db_path.stat().st_size if runtime_db_path.exists() else 0
    if db_size > warn_size_bytes:
        issues.append(
            issue(
                "WARNING",
                "CATALOG_DB_SIZE_WARNING",
                f"catalog.db is above warning budget: {db_size} bytes > {warn_size_bytes} bytes",
                detail={"dbSizeBytes": db_size, "warnSizeBytes": warn_size_bytes},
            )
        )
    if db_size > max_size_bytes and not allow_oversize:
        issues.append(
            issue(
                "ERROR",
                "CATALOG_DB_SIZE_LIMIT",
                f"catalog.db is above max budget: {db_size} bytes > {max_size_bytes} bytes",
                detail={"dbSizeBytes": db_size, "maxSizeBytes": max_size_bytes},
            )
        )

    record_counts = Counter(record["recordType"] for record in records)
    automation_counts = Counter(record["automationStatus"] for record in records)
    spell_index_count = sum(1 for record in records if record.get("spellIndex") is not None)
    builder_record_count = sum(asset["recordCount"] for asset in builder_assets if asset["builderType"] != "feat-index")
    link_counts = {
        "total": len(links),
        "resolved": sum(1 for link in links if link.get("resolved")),
        "unresolved": sum(1 for link in links if not link.get("resolved")),
    }
    error_count = sum(1 for item in issues if item["severity"] == "ERROR")
    warning_count = sum(1 for item in issues if item["severity"] == "WARNING")

    manifest = {
        **metadata,
        "database": {
            "fileName": db_path.name,
            "relativePath": str(db_path.relative_to(output_dir)).replace("\\", "/"),
            "sizeBytes": db_size,
            "warnSizeBytes": warn_size_bytes,
            "maxSizeBytes": max_size_bytes,
            "rawPayloadEncoding": "gzip",
        },
        "runtimeDatabase": {
            "fileName": runtime_db_path.name,
            "relativePath": str(runtime_db_path.relative_to(output_dir)).replace("\\", "/"),
            "sizeBytes": runtime_db_size,
            "profile": "android_compact",
            "catalogRoomDatabaseVersion": CATALOG_ROOM_DATABASE_VERSION,
            "omittedPayloads": ["raw_json_gzip", "normalized_json", "catalog_issues", "catalog_pack_stats", "catalog_traits"],
        },
        "counts": {
            "records": len(records),
            "spellIndexRecords": spell_index_count,
            "builderIndexRecords": builder_record_count,
            "builderIndexAssets": {
                asset["name"]: asset["recordCount"]
                for asset in sorted(builder_assets, key=lambda item: item["name"])
            },
            "links": link_counts,
            "recordTypes": dict(sorted(record_counts.items())),
            "automationStatus": dict(sorted(automation_counts.items())),
            "packs": {name: stats["importedCount"] for name, stats in sorted(pack_stats.items())},
            "issues": {
                "errors": error_count,
                "warnings": warning_count,
            },
        },
        "includedPacks": [pack_stats[name] for name in sorted(pack_stats)],
        "deprecatedDatasetScripts": DEPRECATED_DATASET_SCRIPTS,
    }
    audit = {
        **metadata,
        "status": "failed" if error_count else "ok",
        "database": manifest["database"],
        "runtimeDatabase": manifest["runtimeDatabase"],
        "counts": manifest["counts"],
        "issueSummary": summarize_issues(issues),
        "skippedRecords": [item for item in issues if item["code"] in {"MALFORMED_JSON", "UNEXPECTED_JSON_SHAPE", "DUPLICATE_RECORD_ID"}],
        "allIssuesCount": len(issues),
        "allIssues": issues[:1000],
        "allIssuesTruncated": len(issues) > 1000,
    }
    (output_dir / "catalog.manifest.json").write_text(pretty_json(manifest) + "\n", encoding="utf-8")
    (output_dir / "catalog.audit.json").write_text(pretty_json(audit) + "\n", encoding="utf-8")
    return manifest, audit


def validate_root(root: Path) -> None:
    if not root.exists():
        raise SystemExit(f"PF2e root not found: {root}")
    if not (root / "system.pf2e.json").exists():
        raise SystemExit(f"system.pf2e.json not found under PF2e root: {root}")
    if not (root / "packs" / "pf2e").exists():
        raise SystemExit(f"packs/pf2e not found under PF2e root: {root}")


def build_catalog(
    root: Path,
    output_dir: Path,
    source_commit: str,
    warn_size_bytes: int = DEFAULT_WARN_SIZE_BYTES,
    max_size_bytes: int = DEFAULT_MAX_SIZE_BYTES,
    allow_oversize: bool = False,
    strict_references: bool = False,
) -> dict[str, Any]:
    def publish_staging() -> None:
        if final_output_dir.exists():
            shutil.rmtree(final_output_dir)
        staging_dir.replace(final_output_dir)

    validate_root(root)
    final_output_dir = output_dir
    staging_dir = output_dir.with_name(f"{output_dir.name}.staging")
    if staging_dir.exists():
        shutil.rmtree(staging_dir)
    staging_dir.mkdir(parents=True, exist_ok=True)
    output_dir = staging_dir

    issues: list[dict[str, Any]] = []
    system_manifest = load_json_file(root / "system.pf2e.json")
    if not isinstance(system_manifest, dict):
        raise SystemExit("system.pf2e.json must be a JSON object")

    generated_at = utc_now()
    support_files = support_file_info(root, issues)
    localization = load_localization(root, issues)
    pack_specs = load_pack_specs(root, system_manifest, issues)
    records, links, pack_stats = collect_records(root, pack_specs, issues)
    builder_assets = build_builder_assets_for_audit(root / "packs" / "pf2e", issues)
    uuid_to_record_id = build_uuid_index(records, issues)
    resolve_links(records, links, uuid_to_record_id, localization, issues, strict_references)

    metadata = {
        "catalog_schema_version": CATALOG_SCHEMA_VERSION,
        "source_commit": source_commit,
        "pf2e_system_version": str(system_manifest.get("version") or ""),
        "pf2e_system_id": str(system_manifest.get("id") or "pf2e"),
        "generated_at": generated_at,
        "strict_references": strict_references,
        "support_files": support_files,
    }

    db_path = output_dir / "catalog.db"
    runtime_db_path = output_dir / "catalog.runtime.db"
    create_catalog_db(db_path, records, links, builder_assets, pack_stats, issues, metadata, uuid_to_record_id)
    create_runtime_catalog_db(db_path, runtime_db_path)
    manifest, audit = write_outputs(
        output_dir,
        db_path,
        runtime_db_path,
        records,
        links,
        builder_assets,
        pack_stats,
        issues,
        metadata,
        warn_size_bytes,
        max_size_bytes,
        allow_oversize,
    )
    if audit["status"] != "ok":
        publish_staging()
        raise SystemExit(f"Catalog import failed with {audit['counts']['issues']['errors']} error(s). See {final_output_dir / 'catalog.audit.json'}")
    publish_staging()
    return manifest


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build the Spellapp PF2e catalog database and audit artifacts.")
    parser.add_argument("--foundry-pf2e-root", required=True, help="Path to the Foundry PF2e repository root, e.g. D:/pf2e")
    parser.add_argument("--output-dir", required=True, help="Directory to receive catalog.db, catalog.manifest.json, and catalog.audit.json")
    parser.add_argument("--source-commit", required=True, help="Pinned Foundry PF2e source commit or tag")
    parser.add_argument("--warn-size-bytes", type=int, default=DEFAULT_WARN_SIZE_BYTES)
    parser.add_argument("--max-size-bytes", type=int, default=DEFAULT_MAX_SIZE_BYTES)
    parser.add_argument("--allow-oversize", action="store_true", help="Allow catalog.db to exceed --max-size-bytes")
    parser.add_argument("--strict-references", action="store_true", help="Fail unresolved UUID references to included packs")
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    manifest = build_catalog(
        root=Path(args.foundry_pf2e_root),
        output_dir=Path(args.output_dir),
        source_commit=args.source_commit,
        warn_size_bytes=args.warn_size_bytes,
        max_size_bytes=args.max_size_bytes,
        allow_oversize=args.allow_oversize,
        strict_references=args.strict_references,
    )
    counts = manifest["counts"]
    print("Catalog import complete.")
    print(f"  Records: {counts['records']}")
    print(f"  Links: {counts['links']['total']} ({counts['links']['resolved']} resolved)")
    print(f"  DB size: {manifest['database']['sizeBytes']} bytes")
    print(f"  Runtime DB size: {manifest['runtimeDatabase']['sizeBytes']} bytes")
    print(f"  Output: {Path(args.output_dir)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
