import unittest
from unittest.mock import patch

from build_bundle import repo_env, same_selection, select_tag


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

    def test_uses_token_for_each_owner(self):
        with patch.dict("os.environ", {"GH_TOKEN": "default", "BUNDLE_ERIA_TOKEN": "eria",
                                       "BUNDLE_ADAN_TOKEN": "adan"}):
            self.assertEqual("eria", repo_env("EriaLabsStudios/permaworld-main")["GH_TOKEN"])
            self.assertEqual("adan", repo_env("AdanJoGoHe/permaworld-multiworld")["GH_TOKEN"])

    def test_unchanged_manifest(self):
        selected = [("owner/mod", "v1.0.0+mc26.3", "abc")]
        manifest = {"mods": [{"repository": "owner/mod", "tag": "v1.0.0+mc26.3", "commit": "abc"}]}
        self.assertTrue(same_selection(manifest, selected))
        self.assertFalse(same_selection(None, selected))


if __name__ == "__main__":
    unittest.main()
