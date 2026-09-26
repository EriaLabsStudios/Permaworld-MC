import unittest
from build_bundle import expected_jar_name, same_selection, select_tag


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
        manifest = {"mods": [{"repository": "owner/mod", "tag": "v1.0.0+mc26.3", "commit": "abc"}]}
        self.assertTrue(same_selection(manifest, selected))
        self.assertFalse(same_selection(None, selected))


if __name__ == "__main__":
    unittest.main()
