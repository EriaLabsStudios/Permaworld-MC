"""Keep the two newest published bundles for one Minecraft version."""

import json
import os
import re
import subprocess
import sys
from collections import defaultdict


TAG = re.compile(r"bundle-mc([0-9]+\.[0-9]+(?:\.[0-9]+)?)-r([0-9]+)\Z")


def obsolete_tags(releases):
    by_version = defaultdict(list)
    for release in releases:
        match = TAG.fullmatch(release["tag_name"])
        if match and not release["draft"]:
            by_version[match.group(1)].append((int(match.group(2)), release["tag_name"]))
    return [tag for matching in by_version.values()
            for _, tag in sorted(matching, reverse=True)[2:]]


def main():
    published_tag = sys.argv[1]
    if not TAG.fullmatch(published_tag):
        raise SystemExit("Tag de lote invalido")
    repo = os.environ["GITHUB_REPOSITORY"]
    pages = json.loads(subprocess.check_output(
        ["gh", "api", "--paginate", "--slurp", f"repos/{repo}/releases?per_page=100"], text=True))
    releases = [release for page in pages for release in page]
    if not any(release["tag_name"] == published_tag and not release["draft"] for release in releases):
        raise SystemExit(f"No se encontro la release publicada: {published_tag}")
    for tag in obsolete_tags(releases):
        subprocess.run(["gh", "release", "delete", tag, "--yes", "--cleanup-tag", "--repo", repo], check=True)


if __name__ == "__main__":
    main()
