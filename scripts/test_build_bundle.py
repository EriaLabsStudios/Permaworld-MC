import unittest

from build_bundle import select_tag


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


if __name__ == "__main__":
    unittest.main()
