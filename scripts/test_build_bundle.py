import unittest
from build_bundle import bundle_resource_packs, component_text, expected_jar_name, external_pack_entries, same_selection, select_tag, texture_pack_entries


class SelectTagTest(unittest.TestCase):
    def test_picks_highest_stable_version_for_exact_minecraft_version(self):
        tags = [
            "v1.9.0+mc26.3\told",
            "v1.10.0+mc26.3\tnew",
            "v2.0.0-beta.1+mc26.3\tpreview",
            "v9.0.0+mc26.3.1\tother-minecraft",
        ]
        self.assertEqual(("v1.10.0+mc26.3", "new"), select_tag(tags, "26.3"))

    def test_missing_version_fails(self):
        with self.assertRaises(ValueError):
           select_tag(["v1.0.0+mc26.2\told"], "26.3")

    def test_uses_one_jar_name_convention_for_every_mod(self):
        self.assertEqual("permaworld-main-1.0.4-26.3.jar",
                         expected_jar_name("EriaLabsStudios/permaworld-main", "1.0.4", "26.3"))
        self.assertEqual("permaworld-server-changelogs-0.1.1-26.3.jar",
                         expected_jar_name("EriaLabsStudios/permaworld-server-changelogs", "0.1.1", "26.3"))
        self.assertEqual("permaworld-texture-pack-0.1.0-26.3.jar",
                         expected_jar_name("EriaLabsStudios/permaworld-texture-pack", "0.1.0", "26.3"))

    def test_unchanged_manifest(self):
        selected = [("owner/mod", "v1.0.0+mc26.3", "abc")]
        manifest = {"mods": [{"repository": "owner/mod", "tag": "v1.0.0+mc26.3", "commit": "abc"}],
                    "resource_packs_commit": "packs"}
        self.assertTrue(same_selection(manifest, selected, "packs"))
        self.assertFalse(same_selection(manifest, selected, "new-packs"))
        self.assertFalse(same_selection(None, selected, "packs"))

    def test_selects_versioned_release_packs_with_stable_ids(self):
        import json
        from tempfile import TemporaryDirectory
        from pathlib import Path

        with TemporaryDirectory() as directory:
            checkout = Path(directory)
            (checkout / "packs.json").write_text(json.dumps([
                {"source": "packs/permaworld-gui-v1.1.1", "output": "Permaworld GUI v1.1.1.zip", "release": True},
                {"source": "packs/permaworld-texturepack", "output": "permaworld_texturepack.zip", "release": True},
                {"source": "packs/legacy/old-v1.0.0", "output": "Old v1.0.0.zip", "release": False},
            ]), encoding="utf-8")
            self.assertEqual([{"id": "permaworld-gui", "name": "Permaworld GUI", "version": "1.1.1",
                               "source": "packs/permaworld-gui-v1.1.1", "source_zip": "Permaworld GUI v1.1.1.zip",
                               "zip": "permaworld-gui-1.1.1.zip"}],
                             texture_pack_entries(checkout))

    def test_reads_the_text_of_resource_pack_components(self):
        self.assertEqual("Permaworld GUI v1.1.1", component_text([
            {"text": "Permaworld GUI"}, {"text": " v1.1.1"}
        ]))

    def test_external_packs_use_fixed_local_filenames_and_official_urls(self):
        import json
        from tempfile import TemporaryDirectory
        from pathlib import Path

        entry = {"id": "fresh-food", "group": "Fresh Details", "name": "Fresh Food",
                 "version": "1.3.5", "file": "fresh-food-1.3.5.zip",
                 "project": "https://modrinth.com/resourcepack/fresh-food",
                 "url": "https://cdn.modrinth.com/data/example/versions/one/FreshFood.zip",
                 "sha256": "a" * 64, "sha512": "b" * 128, "supports26_3": False}
        with TemporaryDirectory() as directory:
            checkout = Path(directory)
            catalog = {"minecraftVersion": "26.3", "packs": [entry]}
            (checkout / "external-packs.json").write_text(json.dumps(catalog), encoding="utf-8")
            self.assertEqual([entry], external_pack_entries(checkout, "26.3"))
            self.assertEqual([], external_pack_entries(checkout, "26.2"))
            catalog["packs"].append({**entry, "id": "other"})
            (checkout / "external-packs.json").write_text(json.dumps(catalog), encoding="utf-8")
            with self.assertRaises(ValueError):
                external_pack_entries(checkout, "26.3")

    def test_resource_packs_are_one_release_asset_with_individual_hashes(self):
        import hashlib
        from pathlib import Path
        from tempfile import TemporaryDirectory
        from zipfile import ZipFile

        with TemporaryDirectory() as directory:
            root = Path(directory)
            packs_output = root / "packs"
            packs_output.mkdir()
            inner = packs_output / "Permaworld GUI v1.1.1.zip"
            with ZipFile(inner, "w") as pack:
                pack.writestr("pack.mcmeta", '{"pack":{"description":"Permaworld GUI v1.1.1"}}')
            entries = [{"id": "permaworld-gui", "zip": "permaworld-gui-1.1.1.zip",
                        "source_zip": inner.name, "sha256": hashlib.sha256(inner.read_bytes()).hexdigest()}]
            archive = bundle_resource_packs(entries, packs_output, root, "26.3")
            self.assertEqual("permaworld-resource-packs-26.3.zip", archive["zip"])
            self.assertEqual(entries[0]["sha256"], archive["packs"][0]["sha256"])
            self.assertNotIn("source_zip", archive["packs"][0])
            with ZipFile(root / archive["zip"]) as bundle:
                self.assertIn("README.txt", bundle.namelist())
                self.assertEqual(inner.read_bytes(), bundle.read("resourcepacks/permaworld-gui-1.1.1.zip"))


if __name__ == "__main__":
    unittest.main()
