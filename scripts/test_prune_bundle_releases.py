import unittest

from prune_bundle_releases import obsolete_tags


class BundleRetentionTest(unittest.TestCase):
    def test_keeps_two_newest_for_exact_minecraft_version(self):
        releases = [
            {"tag_name": "bundle-mc26.3-r9", "draft": False},
            {"tag_name": "bundle-mc26.3-r28", "draft": False},
            {"tag_name": "bundle-mc26.3-r27", "draft": False},
            {"tag_name": "bundle-mc26.3.1-r50", "draft": False},
            {"tag_name": "bundle-mc26.3.1-r20", "draft": False},
            {"tag_name": "bundle-mc26.3.1-r10", "draft": False},
            {"tag_name": "bundle-mc26.3-r40", "draft": True},
            {"tag_name": "other-release", "draft": False},
        ]
        self.assertEqual(["bundle-mc26.3-r9", "bundle-mc26.3.1-r10"], obsolete_tags(releases))


if __name__ == "__main__":
    unittest.main()
