#!/usr/bin/env python3
import argparse
import gzip
import hashlib
import html
import json
import re
import shutil
from pathlib import Path


FEAT_CATEGORIES = {"ancestry", "class", "general", "skill", "archetype"}
WARNING_RULE_KEYS = {"ChoiceSet", "GrantItem", "ActiveEffectLike", "FlatModifier"}
SAVE_PROFICIENCIES = {"fortitude", "reflex", "will"}
ATTACK_PROFICIENCIES = {"advanced", "martial", "simple", "unarmed"}
DEFENSE_PROFICIENCIES = {"heavy", "light", "medium", "unarmored"}


def slugify(value: str) -> str:
    normalized = re.sub(r"[^a-z0-9]+", "-", (value or "").strip().lower())
    return normalized.strip("-") or "unknown"


def get_path(obj, *path, default=None):
    current = obj
    for segment in path:
        if not isinstance(current, dict) or segment not in current:
            return default
        current = current[segment]
    return current


def as_list(value):
    if value is None:
        return []
    if isinstance(value, list):
        return value
    return [value]


def string_list(value):
    return sorted({str(item).strip() for item in as_list(value) if str(item).strip()})


def int_list(value):
    values = []
    for item in as_list(value):
        try:
            values.append(int(item))
        except (TypeError, ValueError):
            continue
    return sorted(set(values))


def plain_text(markup):
    if not markup:
        return ""
    text = str(markup)
    text = re.sub(r"@UUID\[[^\]]+\]\{([^}]+)\}", r"\1", text)
    text = re.sub(r"@UUID\[[^\]]*\.([^.\]]+)\]", r"\1", text)
    text = re.sub(r"@(Damage|Check|Template|Localize)\[(.*?)\]", r"\2", text)
    text = re.sub(r"<\s*br\s*/?\s*>", "\n", text, flags=re.I)
    text = re.sub(r"<\s*hr\s*/?\s*>", "\n---\n", text, flags=re.I)
    text = re.sub(r"<\s*/p\s*>", "\n", text, flags=re.I)
    text = re.sub(r"<\s*p[^>]*\s*>", "", text, flags=re.I)
    text = re.sub(r"<\s*li[^>]*\s*>", "- ", text, flags=re.I)
    text = re.sub(r"<\s*/li\s*>", "\n", text, flags=re.I)
    text = re.sub(r"<\s*/?(ul|ol|h1|h2|h3|h4|h5|h6)[^>]*\s*>", "\n", text, flags=re.I)
    text = re.sub(r"<[^>]+>", "", text)
    text = html.unescape(text)
    text = text.replace("\r", "")
    text = re.sub(r"[\t ]+", " ", text)
    text = re.sub(r" *\n *", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def publication(record):
    raw = get_path(record, "system", "publication", default={}) or {}
    return {
        "license": raw.get("license"),
        "page": raw.get("page"),
        "remaster": bool(raw.get("remaster", False)),
        "title": raw.get("title"),
    }


def source_titles_from_normalized(*record_lists):
    titles = set()
    for records in record_lists:
        for record in records:
            source = record.get("source") or {}
            title = str(source.get("title") or "").strip()
            if title:
                titles.add(title)
    return sorted(titles)


def traits(record):
    return {
        "rarity": get_path(record, "system", "traits", "rarity", default="common") or "common",
        "value": string_list(get_path(record, "system", "traits", "value", default=[])),
    }


def description(record):
    return plain_text(get_path(record, "system", "description", "value", default=""))


def record_id(path: Path, record):
    slug = get_path(record, "system", "slug")
    if slug:
        return slugify(slug)
    return slugify(path.stem)


def record_uuid(pack_name, record):
    name = record.get("name") or record.get("_id") or "Unknown"
    return f"Compendium.pf2e.{pack_name}.Item.{name}"


def prerequisites(record):
    raw = get_path(record, "system", "prerequisites", "value", default=[])
    values = []
    for entry in as_list(raw):
        if isinstance(entry, dict):
            value = entry.get("value")
        else:
            value = entry
        if value:
            values.append(str(value).strip())
    return sorted(set(filter(None, values)))


def item_grants(record):
    items = get_path(record, "system", "items", default={}) or {}
    grants = []
    if isinstance(items, dict):
        for key, item in sorted(items.items()):
            if not isinstance(item, dict):
                continue
            uuid = item.get("uuid")
            name = item.get("name")
            if not uuid and not name:
                continue
            grants.append({
                "grantId": slugify(f"{key}-{name or uuid}"),
                "name": name,
                "uuid": uuid,
                "level": item.get("level"),
                "source": "system.items",
            })
    return grants


def rules(record):
    return as_list(get_path(record, "system", "rules", default=[]))


def rule_grants(record):
    grants = []
    for index, rule in enumerate(rules(record)):
        if not isinstance(rule, dict) or rule.get("key") != "GrantItem":
            continue
        uuid = rule.get("uuid") or get_path(rule, "inMemoryOnly", "uuid")
        if isinstance(uuid, str) and "{" not in uuid and "[" not in uuid:
            grants.append({
                "grantId": slugify(f"rule-{index}-{uuid}"),
                "name": rule.get("name"),
                "uuid": uuid,
                "level": rule.get("level"),
                "source": f"system.rules[{index}]",
            })
    return grants


def choice_prompts(record):
    prompts = []
    for index, rule in enumerate(rules(record)):
        if not isinstance(rule, dict) or rule.get("key") != "ChoiceSet":
            continue
        choices = rule.get("choices")
        if isinstance(choices, dict):
            choice_config = choices.get("config") or choices.get("ownedItems") or choices.get("predicate")
        else:
            choice_config = choices
        prompts.append({
            "promptId": slugify(rule.get("flag") or rule.get("adjustName") or f"choice-{index}"),
            "label": rule.get("prompt") or rule.get("label") or "Choice",
            "sourceRulePath": f"system.rules[{index}]",
            "required": True,
            "choiceConfig": choice_config,
        })
    return prompts


def warnings_for(record_id_value, record_type, record):
    warnings = []
    for index, rule in enumerate(rules(record)):
        if not isinstance(rule, dict):
            continue
        key = str(rule.get("key", "other") or "other")
        if key not in WARNING_RULE_KEYS:
            continue
        if key == "GrantItem":
            uuid = rule.get("uuid") or get_path(rule, "inMemoryOnly", "uuid")
            if isinstance(uuid, str) and "{" not in uuid and "[" not in uuid:
                continue
        original = json.dumps(rule, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        warnings.append({
            "warningId": slugify(f"{record_type}-{record_id_value}-{index}-{key}"),
            "recordId": record_id_value,
            "recordType": record_type,
            "ruleType": key if key in {"ChoiceSet", "GrantItem", "ActiveEffectLike"} else "other",
            "sourceRulePath": f"system.rules[{index}]",
            "originalText": original[:500],
            "severity": "INFO" if key == "ChoiceSet" else "WARNING",
            "message": f"{key} rule is preserved as metadata and is not fully evaluated yet.",
        })
    return warnings


def source_record(path: Path, pack_name: str, record_type: str, record):
    rid = record_id(path, record)
    return {
        "id": rid,
        "uuid": record_uuid(pack_name, record),
        "name": record.get("name") or rid,
        "type": record_type,
        "source": publication(record),
        "traits": traits(record),
        "description": description(record),
        "grants": item_grants(record) + rule_grants(record),
        "choicePrompts": choice_prompts(record),
        "warnings": warnings_for(rid, record_type, record),
    }


def catalog_record_id(path: Path, pack_dir: Path, catalog_pack_name: str) -> str:
    try:
        relative = path.relative_to(pack_dir).with_suffix("").as_posix()
    except ValueError:
        relative = path.with_suffix("").name
    return f"{catalog_pack_name}:{slugify(relative)}"


def read_records(pack_dir: Path, record_type: str):
    records = []
    for path in sorted(pack_dir.rglob("*.json"), key=lambda p: str(p).lower()):
        if path.name == "_folders.json":
            continue
        try:
            record = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise SystemExit(f"Could not parse {path}: {exc}") from exc
        if record.get("type") != record_type:
            continue
        records.append((path, record))
    return records


def normalize_ability(value):
    return str(value or "").strip().lower()


def normalize_boosts(raw):
    if not isinstance(raw, dict):
        return []
    boosts = []
    for key, entry in sorted(raw.items()):
        if not isinstance(entry, dict):
            continue
        boosts.append({
            "id": str(key),
            "abilities": [normalize_ability(item) for item in string_list(entry.get("value"))],
            "selected": normalize_ability(entry.get("selected")) or None,
        })
    return boosts


def normalize_languages(raw):
    if not isinstance(raw, dict):
        return {}
    return {
        "value": string_list(raw.get("value")),
        "custom": str(raw.get("custom") or "").strip(),
    }


def normalize_trained_skills(raw):
    if not isinstance(raw, dict):
        return {}
    return {
        "value": string_list(raw.get("value")),
        "lore": string_list(raw.get("lore")),
        "additional": raw.get("additional"),
    }


def normalize_rank(value):
    try:
        rank = int(value)
    except (TypeError, ValueError):
        return None
    if rank < 0 or rank > 4:
        return None
    return rank


def proficiency_category(target):
    normalized = slugify(target)
    if normalized == "perception":
        return "perception"
    if normalized in SAVE_PROFICIENCIES:
        return "save"
    if normalized in ATTACK_PROFICIENCIES:
        return "attack"
    if normalized in DEFENSE_PROFICIENCIES:
        return "defense"
    if normalized in {"class-dc", "classdc"}:
        return "class-dc"
    if normalized in {"spell-attack", "spell-attacks", "spell-dc", "spell-dcs"}:
        return "spellcasting"
    return "other"


def proficiency_entry(target, rank, source):
    normalized_rank = normalize_rank(rank)
    if normalized_rank is None:
        return None
    normalized_target = slugify(target)
    return {
        "category": proficiency_category(normalized_target),
        "target": normalized_target,
        "rank": normalized_rank,
        "source": source,
    }


def normalize_base_proficiencies(record):
    system = record.get("system") or {}
    entries = []
    perception = proficiency_entry("perception", system.get("perception"), "system.perception")
    if perception:
        entries.append(perception)
    for target, rank in sorted((system.get("savingThrows") or {}).items()):
        entry = proficiency_entry(target, rank, f"system.savingThrows.{target}")
        if entry:
            entries.append(entry)
    for target, rank in sorted((system.get("attacks") or {}).items()):
        if target == "other" and isinstance(rank, dict):
            rank = rank.get("rank")
        entry = proficiency_entry(target, rank, f"system.attacks.{target}")
        if entry:
            entries.append(entry)
    for target, rank in sorted((system.get("defenses") or {}).items()):
        entry = proficiency_entry(target, rank, f"system.defenses.{target}")
        if entry:
            entries.append(entry)
    class_dc = get_path(record, "system", "classDC", default=None)
    if isinstance(class_dc, dict):
        class_dc = class_dc.get("rank")
    entry = proficiency_entry("class-dc", class_dc, "system.classDC")
    if entry:
        entries.append(entry)
    return sorted(entries, key=lambda item: (item["category"], item["target"], item["source"]))


def normalize_subfeature_proficiencies(record):
    proficiencies = get_path(record, "system", "subfeatures", "proficiencies", default={}) or {}
    entries = []
    if isinstance(proficiencies, dict):
        for target, value in sorted(proficiencies.items()):
            rank = value.get("rank") if isinstance(value, dict) else value
            entry = proficiency_entry(target, rank, f"system.subfeatures.proficiencies.{target}")
            if entry:
                entries.append(entry)
    return entries


def normalize_rule_proficiency_grants(record):
    entries = []
    for index, rule in enumerate(rules(record)):
        if not isinstance(rule, dict) or rule.get("key") != "ActiveEffectLike":
            continue
        path = str(rule.get("path") or "")
        value = rule.get("value")
        match = re.match(r"system\.proficiencies\.(attacks|defenses)\.([^.]+)\.rank$", path)
        if match:
            target = match.group(2)
            entry = proficiency_entry(target, value, f"system.rules[{index}]")
            if entry:
                entries.append(entry)
            continue
        match = re.match(r"system\.saves\.([^.]+)\.rank$", path)
        if match:
            entry = proficiency_entry(match.group(1), value, f"system.rules[{index}]")
            if entry:
                entries.append(entry)
    return entries


def proficiency_grants(record):
    seen = set()
    grants = []
    for entry in normalize_subfeature_proficiencies(record) + normalize_rule_proficiency_grants(record):
        key = (entry["category"], entry["target"], entry["rank"], entry["source"])
        if key not in seen:
            seen.add(key)
            grants.append(entry)
    return sorted(grants, key=lambda item: (item["category"], item["target"], item["rank"], item["source"]))


def normalize_feat_category(record, path):
    category = str(get_path(record, "system", "category", default="") or "").strip().lower()
    traits_value = set(string_list(get_path(record, "system", "traits", "value", default=[])))
    if category == "class" and "archetype" in traits_value:
        return "archetype"
    if category in FEAT_CATEGORIES:
        return category
    parts = [part.lower() for part in path.parts]
    for candidate in ["ancestry", "class", "general", "skill", "archetype"]:
        if candidate in parts:
            return candidate
    return "other"


def normalize_level(record):
    try:
        return int(get_path(record, "system", "level", "value", default=0) or 0)
    except (TypeError, ValueError):
        return 0


def build_classes(packs_dir):
    result = []
    pack_dir = packs_dir / "classes"
    for path, record in read_records(pack_dir, "class"):
        base = source_record(path, "classes", "class", record)
        base["catalogRecordId"] = catalog_record_id(path, pack_dir, "classes")
        feat_slots = []
        for kind, path_key in [
            ("ancestry", "ancestryFeatLevels"),
            ("class", "classFeatLevels"),
            ("general", "generalFeatLevels"),
            ("skill", "skillFeatLevels"),
        ]:
            for level in int_list(get_path(record, "system", path_key, "value", default=[])):
                feat_slots.append({
                    "slotId": f"{base['id']}/{kind}/{level}",
                    "kind": kind,
                    "level": level,
                })
        base.update({
            "hp": get_path(record, "system", "hp"),
            "keyAbilityOptions": string_list(get_path(record, "system", "keyAbility", "value", default=[])),
            "spellcastingFlag": get_path(record, "system", "spellcasting", default=0) or 0,
            "trainedSkills": normalize_trained_skills(get_path(record, "system", "trainedSkills", default={})),
            "skillIncreaseLevels": int_list(get_path(record, "system", "skillIncreaseLevels", "value", default=[])),
            "skillFeatLevels": int_list(get_path(record, "system", "skillFeatLevels", "value", default=[])),
            "baseProficiencies": normalize_base_proficiencies(record),
            "featSlots": sorted(feat_slots, key=lambda item: (item["level"], item["kind"])),
            "featureRefs": [grant["uuid"] for grant in item_grants(record) if grant.get("uuid")],
        })
        result.append(base)
    return sorted(result, key=lambda item: item["id"])


def build_ancestries(packs_dir):
    result = []
    pack_dir = packs_dir / "ancestries"
    for path, record in read_records(pack_dir, "ancestry"):
        base = source_record(path, "ancestries", "ancestry", record)
        base["catalogRecordId"] = catalog_record_id(path, pack_dir, "ancestries")
        base.update({
            "hp": get_path(record, "system", "hp"),
            "speed": get_path(record, "system", "speed"),
            "size": get_path(record, "system", "size"),
            "boosts": normalize_boosts(get_path(record, "system", "boosts", default={})),
            "flaws": normalize_boosts(get_path(record, "system", "flaws", default={})),
            "languages": normalize_languages(get_path(record, "system", "languages", default={})),
            "additionalLanguages": get_path(record, "system", "additionalLanguages", default={}) or {},
            "featureRefs": [grant["uuid"] for grant in item_grants(record) if grant.get("uuid")],
        })
        result.append(base)
    return sorted(result, key=lambda item: item["id"])


def build_heritages(packs_dir):
    result = []
    pack_dir = packs_dir / "heritages"
    for path, record in read_records(pack_dir, "heritage"):
        base = source_record(path, "heritages", "heritage", record)
        base["catalogRecordId"] = catalog_record_id(path, pack_dir, "heritages")
        base.update({
            "ancestryId": slugify(get_path(record, "system", "ancestry", "slug", default="")),
            "ancestryName": get_path(record, "system", "ancestry", "name"),
            "ancestryUuid": get_path(record, "system", "ancestry", "uuid"),
        })
        result.append(base)
    return sorted(result, key=lambda item: (item.get("ancestryId") or "", item["name"]))


def build_backgrounds(packs_dir):
    result = []
    pack_dir = packs_dir / "backgrounds"
    for path, record in read_records(pack_dir, "background"):
        base = source_record(path, "backgrounds", "background", record)
        base["catalogRecordId"] = catalog_record_id(path, pack_dir, "backgrounds")
        base.update({
            "boosts": normalize_boosts(get_path(record, "system", "boosts", default={})),
            "trainedSkills": normalize_trained_skills(get_path(record, "system", "trainedSkills", default={})),
            "featRefs": [grant["uuid"] for grant in item_grants(record) if grant.get("uuid")],
        })
        result.append(base)
    return sorted(result, key=lambda item: item["id"])


def build_features(packs_dir, pack_name, record_type):
    result = []
    pack_dir = packs_dir / pack_name
    catalog_pack_name = {
        "ancestry-features": "ancestryfeatures",
        "class-features": "classfeatures",
    }.get(pack_name, pack_name)
    for path, record in read_records(pack_dir, "feat"):
        category = str(get_path(record, "system", "category", default="") or "").lower()
        if record_type == "class-feature" and category != "classfeature":
            continue
        if record_type == "ancestry-feature" and category != "ancestryfeature":
            continue
        base = source_record(path, pack_name, record_type, record)
        base["catalogRecordId"] = catalog_record_id(path, pack_dir, catalog_pack_name)
        base.update({
            "level": normalize_level(record),
            "category": category,
            "prerequisites": prerequisites(record),
            "proficiencyGrants": proficiency_grants(record),
        })
        result.append(base)
    return sorted(result, key=lambda item: (item["level"], item["id"]))


def build_feats(packs_dir):
    by_category = {category: [] for category in [*sorted(FEAT_CATEGORIES), "other"]}
    pack_dir = packs_dir / "feats"
    for path, record in read_records(pack_dir, "feat"):
        base = source_record(path, "feats-srd", "feat", record)
        base["catalogRecordId"] = catalog_record_id(path, pack_dir, "feats-srd")
        category = normalize_feat_category(record, path)
        base.update({
            "category": category,
            "level": normalize_level(record),
            "prerequisites": prerequisites(record),
            "proficiencyGrants": proficiency_grants(record),
            "actionType": get_path(record, "system", "actionType", "value"),
            "actions": get_path(record, "system", "actions", "value"),
        })
        if category == "other":
            base["warnings"] = base["warnings"] + [{
                "warningId": slugify(f"feat-{base['id']}-unknown-category"),
                "recordId": base["id"],
                "recordType": "feat",
                "ruleType": "other",
                "sourceRulePath": "system.category",
                "originalText": str(get_path(record, "system", "category", default="")),
                "severity": "WARNING",
                "message": "Feat category could not be normalized and was placed in the other shard.",
            }]
        by_category[category].append(base)
    for category in by_category:
        by_category[category] = sorted(by_category[category], key=lambda item: (item["level"], item["name"], item["id"]))
    return by_category


def canonical_json_bytes(obj):
    text = json.dumps(obj, ensure_ascii=False, sort_keys=True, indent=2)
    return (text + "\n").encode("utf-8")


def gzip_bytes(data: bytes):
    compressed = gzip.compress(data, compresslevel=6, mtime=0)
    if len(compressed) >= 10:
        compressed = compressed[:9] + b"\xff" + compressed[10:]
    return compressed


def sha256(data: bytes):
    return hashlib.sha256(data).hexdigest()


def write_json(path: Path, obj):
    data = canonical_json_bytes(obj)
    path.write_bytes(data)
    return data


def write_gzip_json(path: Path, obj):
    content = canonical_json_bytes(obj)
    artifact = gzip_bytes(content)
    path.write_bytes(artifact)
    return content, artifact


def asset_record(path: Path, content: bytes, artifact: bytes = None):
    shipped = artifact if artifact is not None else content
    return {
        "name": path.name,
        "bytes": len(shipped),
        "artifactSha256": sha256(shipped),
        "contentSha256": sha256(content),
        "gzipped": path.name.endswith(".gz"),
    }


def chunk_records(records, max_count=350):
    for start in range(0, len(records), max_count):
        yield records[start:start + max_count]


def write_asset_set(packs_dir: Path, output_dir: Path, source_commit: str, generated_at: str):
    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True)

    dataset_header = {
        "datasetVersion": generated_at.replace(":", "").replace("-", "").replace("T", "-").replace("Z", ""),
        "sourceCommit": source_commit,
        "generatedAtUtc": generated_at,
    }
    assets = []

    plain_assets = {
        "classes.normalized.json": {"classes": build_classes(packs_dir)},
        "ancestries.normalized.json": {"ancestries": build_ancestries(packs_dir)},
        "heritages.normalized.json": {"heritages": build_heritages(packs_dir)},
        "backgrounds.normalized.json": {"backgrounds": build_backgrounds(packs_dir)},
    }
    for name, payload in plain_assets.items():
        root = {**dataset_header, **payload}
        path = output_dir / name
        content = write_json(path, root)
        assets.append(asset_record(path, content))

    gz_assets = {
        "class-features.normalized.json.gz": {
            "features": build_features(packs_dir, "class-features", "class-feature"),
        },
        "ancestry-features.normalized.json.gz": {
            "features": build_features(packs_dir, "ancestry-features", "ancestry-feature"),
        },
    }
    for name, payload in gz_assets.items():
        root = {**dataset_header, **payload}
        path = output_dir / name
        content, artifact = write_gzip_json(path, root)
        assets.append(asset_record(path, content, artifact))

    feat_shards = []
    feat_index_entries = []
    feats_by_category = build_feats(packs_dir)
    for category, records in feats_by_category.items():
        if not records:
            continue
        single_name = f"feats.{category}.normalized.json.gz"
        single_content = canonical_json_bytes({**dataset_header, "category": category, "feats": records})
        single_artifact = gzip_bytes(single_content)
        if len(single_artifact) <= 500_000:
            shard_name = single_name
            path = output_dir / shard_name
            path.write_bytes(single_artifact)
            assets.append(asset_record(path, single_content, single_artifact))
            feat_shards.append({"category": category, "name": shard_name, "count": len(records)})
            for record in records:
                feat_index_entries.append(feat_index_record(record, shard_name))
        else:
            for part_index, chunk in enumerate(chunk_records(records), start=1):
                shard_name = f"feats.{category}.part-{part_index}.normalized.json.gz"
                path = output_dir / shard_name
                content, artifact = write_gzip_json(path, {**dataset_header, "category": category, "feats": chunk})
                assets.append(asset_record(path, content, artifact))
                feat_shards.append({"category": category, "name": shard_name, "count": len(chunk)})
                for record in chunk:
                    feat_index_entries.append(feat_index_record(record, shard_name))

    feat_index = {
        **dataset_header,
        "categories": sorted(feats_by_category.keys()),
        "shards": sorted(feat_shards, key=lambda item: item["name"]),
        "feats": sorted(feat_index_entries, key=lambda item: (item["level"], item["name"], item["id"])),
    }
    path = output_dir / "feats.index.normalized.json"
    content = write_json(path, feat_index)
    assets.append(asset_record(path, content))

    manifest = {
        **dataset_header,
        "assetCount": len(assets),
        "assets": sorted(assets, key=lambda item: item["name"]),
        "counts": {
            "classes": len(plain_assets["classes.normalized.json"]["classes"]),
            "ancestries": len(plain_assets["ancestries.normalized.json"]["ancestries"]),
            "heritages": len(plain_assets["heritages.normalized.json"]["heritages"]),
            "backgrounds": len(plain_assets["backgrounds.normalized.json"]["backgrounds"]),
            "classFeatures": len(gz_assets["class-features.normalized.json.gz"]["features"]),
            "ancestryFeatures": len(gz_assets["ancestry-features.normalized.json.gz"]["features"]),
            "feats": len(feat_index_entries),
            "featShards": len(feat_shards),
        },
        "sources": source_titles_from_normalized(
            plain_assets["classes.normalized.json"]["classes"],
            plain_assets["ancestries.normalized.json"]["ancestries"],
            plain_assets["heritages.normalized.json"]["heritages"],
            plain_assets["backgrounds.normalized.json"]["backgrounds"],
            gz_assets["class-features.normalized.json.gz"]["features"],
            gz_assets["ancestry-features.normalized.json.gz"]["features"],
            feat_index_entries,
        ),
    }
    manifest_path = output_dir / "builder.manifest.normalized.json"
    write_json(manifest_path, manifest)


def feat_index_record(record, shard_name):
    return {
        "id": record["id"],
        "name": record["name"],
        "category": record["category"],
        "level": record["level"],
        "rarity": record["traits"]["rarity"],
        "traits": record["traits"]["value"],
        "shard": shard_name,
        "source": record.get("source") or {},
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--packs-dir", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--source-commit", required=True)
    parser.add_argument("--generated-at-utc", default="2026-04-27T00:00:00Z")
    args = parser.parse_args()
    write_asset_set(
        packs_dir=Path(args.packs_dir),
        output_dir=Path(args.output_dir),
        source_commit=args.source_commit,
        generated_at=args.generated_at_utc,
    )
