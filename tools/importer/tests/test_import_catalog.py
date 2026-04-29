import gzip
import json
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from import_catalog import CATALOG_ROOM_DATABASE_VERSION, INCLUDED_PACKS, build_catalog  # noqa: E402


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def make_pf2e_root(root: Path) -> None:
    packs = []
    for pack_name in sorted(INCLUDED_PACKS):
        packs.append(
            {
                "name": pack_name,
                "label": pack_name,
                "type": "Item",
                "path": f"packs/{pack_name}",
                "system": "pf2e",
            }
        )
        (root / "packs" / "pf2e" / pack_name).mkdir(parents=True, exist_ok=True)

    write_json(
        root / "system.pf2e.json",
        {
            "id": "pf2e",
            "title": "Pathfinder Second Edition",
            "version": "test-system",
            "packs": packs,
        },
    )
    for name in ["en.json", "action-en.json", "re-en.json", "kingmaker-en.json"]:
        write_json(root / "static" / "lang" / name, {"PF2E": {"Known": "Known localized text"}})
    write_json(root / "static" / "template.json", {})


def minimal_record(name: str, record_type: str, *, description: str = "", image: str | None = None) -> dict:
    return {
        "_id": f"{name.lower().replace(' ', '-')}-id",
        "img": image,
        "name": name,
        "system": {
            "description": {"value": description},
            "publication": {"license": "ORC", "title": "Test Source"},
            "traits": {"rarity": "common", "value": []},
        },
        "type": record_type,
    }


class CatalogImporterTest(unittest.TestCase):
    def test_builds_manifest_db_and_resolves_uuid_links(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "pf2e"
            output = Path(temp) / "out"
            make_pf2e_root(root)
            write_json(
                root / "packs" / "pf2e" / "actionspf2e" / "subsist.json",
                minimal_record("Subsist", "action"),
            )
            write_json(
                root / "packs" / "pf2e" / "heritages" / "irongut-goblin.json",
                minimal_record(
                    "Irongut Goblin",
                    "heritage",
                    description="<p>Use @UUID[Compendium.pf2e.actionspf2e.Item.Subsist] safely.</p>",
                ),
            )
            spell = minimal_record("Force Barrage", "spell")
            spell["system"]["level"] = {"value": 1}
            spell["system"]["traits"] = {
                "rarity": "common",
                "traditions": ["arcane", "occult"],
                "value": ["concentrate", "force"],
            }
            write_json(
                root / "packs" / "pf2e" / "spells-srd" / "force-barrage.json",
                spell,
            )

            manifest = build_catalog(root, output, "abc123")

            self.assertEqual(manifest["catalog_schema_version"], 1)
            self.assertEqual(manifest["source_commit"], "abc123")
            self.assertEqual(manifest["pf2e_system_version"], "test-system")
            self.assertEqual(manifest["counts"]["records"], 3)
            self.assertEqual(manifest["counts"]["spellIndexRecords"], 1)
            self.assertEqual(manifest["counts"]["builderIndexRecords"], 1)
            self.assertEqual(manifest["counts"]["links"]["resolved"], 1)
            self.assertEqual("android_compact", manifest["runtimeDatabase"]["profile"])
            self.assertTrue((output / "catalog.runtime.db").is_file())

            connection = sqlite3.connect(output / "catalog.db")
            try:
                self.assertEqual(CATALOG_ROOM_DATABASE_VERSION, connection.execute("PRAGMA user_version").fetchone()[0])
                row = connection.execute(
                    "SELECT detail_text FROM catalog_records WHERE name = ?",
                    ("Irongut Goblin",),
                ).fetchone()
                self.assertIsNotNone(row)
                self.assertIn("Subsist", row[0])
                self.assertNotIn("@UUID", row[0])

                spell_row = connection.execute(
                    "SELECT spell_id, rank, traditions_csv FROM catalog_spell_index WHERE spell_id = ?",
                    ("force-barrage",),
                ).fetchone()
                self.assertIsNotNone(spell_row)
                self.assertEqual(("force-barrage", 1, "arcane,occult"), spell_row)

                builder_row = connection.execute(
                    "SELECT record_count, payload_json_gzip FROM catalog_builder_assets WHERE name = ?",
                    ("heritages",),
                ).fetchone()
                self.assertIsNotNone(builder_row)
                self.assertEqual(1, builder_row[0])
                builder_payload = json.loads(gzip.decompress(builder_row[1]).decode("utf-8"))
                self.assertEqual("catalog_records.detail_text", builder_payload["descriptionSource"])
                self.assertEqual("heritages:irongut-goblin", builder_payload["heritages"][0]["catalogRecordId"])
                self.assertNotIn("description", builder_payload["heritages"][0])
            finally:
                connection.close()

            runtime_connection = sqlite3.connect(output / "catalog.runtime.db")
            try:
                self.assertEqual(CATALOG_ROOM_DATABASE_VERSION, runtime_connection.execute("PRAGMA user_version").fetchone()[0])
                runtime_row = runtime_connection.execute(
                    "SELECT detail_text, raw_json_gzip, normalized_json FROM catalog_records WHERE name = ?",
                    ("Irongut Goblin",),
                ).fetchone()
                self.assertIsNotNone(runtime_row)
                self.assertIn("Subsist", runtime_row[0])
                self.assertEqual(b"", runtime_row[1])
                self.assertEqual("", runtime_row[2])
                self.assertEqual(
                    [],
                    runtime_connection.execute(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('catalog_issues', 'catalog_pack_stats', 'catalog_traits')",
                    ).fetchall(),
                )
            finally:
                runtime_connection.close()

    def test_strict_references_fail_unresolved_included_uuid(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "pf2e"
            output = Path(temp) / "out"
            make_pf2e_root(root)
            write_json(
                root / "packs" / "pf2e" / "heritages" / "bad-link.json",
                minimal_record(
                    "Bad Link",
                    "heritage",
                    description="<p>@UUID[Compendium.pf2e.actionspf2e.Item.Does Not Exist]</p>",
                ),
            )

            with self.assertRaises(SystemExit):
                build_catalog(root, output, "abc123", strict_references=True)

            audit = json.loads((output / "catalog.audit.json").read_text(encoding="utf-8"))
            self.assertEqual(audit["status"], "failed")
            self.assertIn("UNRESOLVED_UUID", audit["issueSummary"]["byCode"])

    def test_malformed_json_is_reported_as_failed_audit(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "pf2e"
            output = Path(temp) / "out"
            make_pf2e_root(root)
            bad_path = root / "packs" / "pf2e" / "spells-srd" / "broken.json"
            bad_path.write_text("{not-json", encoding="utf-8")

            with self.assertRaises(SystemExit):
                build_catalog(root, output, "abc123")

            audit = json.loads((output / "catalog.audit.json").read_text(encoding="utf-8"))
            self.assertEqual(audit["status"], "failed")
            self.assertIn("MALFORMED_JSON", audit["issueSummary"]["byCode"])

    def test_missing_pf2e_image_is_audit_warning(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "pf2e"
            output = Path(temp) / "out"
            make_pf2e_root(root)
            write_json(
                root / "packs" / "pf2e" / "spells-srd" / "image-missing.json",
                minimal_record(
                    "Image Missing",
                    "spell",
                    image="systems/pf2e/icons/spells/nope.webp",
                ),
            )

            build_catalog(root, output, "abc123")

            audit = json.loads((output / "catalog.audit.json").read_text(encoding="utf-8"))
            self.assertEqual(audit["status"], "ok")
            self.assertIn("MISSING_IMAGE", audit["issueSummary"]["byCode"])

    def test_size_limit_fails_without_override(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "pf2e"
            output = Path(temp) / "out"
            make_pf2e_root(root)
            write_json(
                root / "packs" / "pf2e" / "spells-srd" / "tiny.json",
                minimal_record("Tiny", "spell"),
            )

            with self.assertRaises(SystemExit):
                build_catalog(root, output, "abc123", max_size_bytes=1)

            audit = json.loads((output / "catalog.audit.json").read_text(encoding="utf-8"))
            self.assertEqual(audit["status"], "failed")
            self.assertIn("CATALOG_DB_SIZE_LIMIT", audit["issueSummary"]["byCode"])


if __name__ == "__main__":
    unittest.main()
