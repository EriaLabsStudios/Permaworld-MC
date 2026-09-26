"""Build one reproducible Minecraft-version bundle from tagged mod repos."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


# Keep permaworld-main before permaworld-web, which compiles against its JAR.
REPOSITORIES = (
    "EriaLabsStudios/permaworld-main",
    "AdanJoGoHe/permaworld-utilities",
    "EriaLabsStudios/permaworld-chat",
    "EriaLabsStudios/permaworld-server-changelogs",
    "AdanJoGoHe/permaworld-multiworld",
    "EriaLabsStudios/permaworld-web",
)
MINECRAFT_VERSION = re.compile(r"[0-9]+\.[0-9]+(?:\.[0-9]+)?\Z")


def run(*args, cwd=None):
    return subprocess.run(args, cwd=cwd, check=True, text=True, capture_output=True).stdout.strip()


def select_tag(lines, minecraft_version):
    """Select the highest stable three-part mod version for an exact MC version."""
    pattern = re.compile(rf"v([0-9]+)\.([0-9]+)\.([0-9]+)\+mc{re.escape(minecraft_version)}\Z")
    matches = []
    for line in lines:
        name, sha = line.split("\t", 1)
        match = pattern.fullmatch(name)
        if match:
            matches.append((tuple(map(int, match.groups())), name, sha))
    if not matches:
        raise ValueError(f"No hay tag estable vX.Y.Z+mc{minecraft_version}")
    _, name, sha = max(matches)
    return name, sha


def properties(path):
    return dict(
        line.split("=", 1)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line and not line.lstrip().startswith("#") and "=" in line
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("minecraft_version")
    parser.add_argument("--workdir", type=Path, default=Path(".bundle-work"))
    parser.add_argument("--output", type=Path, default=Path("dist"))
    args = parser.parse_args()
    mc = args.minecraft_version
    if not MINECRAFT_VERSION.fullmatch(mc):
        parser.error("La version de Minecraft debe ser X.Y o X.Y.Z")

    selected = []
    for repo in REPOSITORIES:
        try:
            tags = run("gh", "api", "--paginate", f"repos/{repo}/tags?per_page=100",
                       "--jq", ".[] | [.name, .commit.sha] | @tsv")
            tag, sha = select_tag(tags.splitlines(), mc)
        except subprocess.CalledProcessError as error:
            raise SystemExit(f"No se pueden leer los tags de {repo}: {error.stderr.strip()}") from error
        except ValueError as error:
            raise SystemExit(f"No se puede seleccionar {repo}: {error}") from error
        selected.append((repo, tag, sha))
        print(f"{repo}: {tag} ({sha})", flush=True)

    workdir = args.workdir.resolve()
    output = args.output.resolve()
    if output.exists() and any(output.iterdir()):
        raise SystemExit(f"El directorio de salida no esta vacio: {output}")
    workdir.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)
    manifest = {"minecraft_version": mc, "mods": []}
    main_jar = None
    for repo, tag, sha in selected:
        name = repo.rsplit("/", 1)[1]
        checkout = workdir / name
        if checkout.exists():
            raise SystemExit(f"El directorio de trabajo ya existe: {checkout}")
        subprocess.run(["gh", "repo", "clone", repo, str(checkout), "--",
                        "--branch", tag, "--depth", "1"], check=True)
        actual_sha = run("git", "rev-parse", "HEAD", cwd=checkout)
        if actual_sha != sha:
            raise SystemExit(f"El tag {tag} de {repo} cambio durante la compilacion")
        props = properties(checkout / "gradle.properties")
        mod_version = tag.split("+mc", 1)[0][1:]
        if props.get("minecraft_version") != mc or props.get("mod_version") != mod_version:
            raise SystemExit(f"gradle.properties no coincide con {tag} en {repo}")
        wrapper = checkout / "gradlew"
        if not wrapper.is_file():
            raise SystemExit(f"Falta gradlew en {repo}")
        wrapper.chmod(wrapper.stat().st_mode | 0o111)
        command = [str(wrapper), "build", "--no-daemon"]
        if name == "permaworld-web":
            if main_jar is None:
                raise SystemExit("permaworld-web requiere permaworld-main")
            command.append(f"-PpermaworldJar={main_jar}")
        subprocess.run(command, cwd=checkout, check=True)
        jars = [jar for jar in (checkout / "build" / "libs").glob("*.jar")
                if not jar.stem.endswith(("-sources", "-javadoc", "-dev"))]
        if len(jars) != 1:
            raise SystemExit(f"Se esperaba un JAR instalable en {repo}; encontrados: {jars}")
        jar = jars[0]
        with zipfile.ZipFile(jar) as archive:
            if archive.testzip() is not None or "fabric.mod.json" not in archive.namelist():
                raise SystemExit(f"JAR invalido: {jar}")
        destination = output / jar.name
        if destination.exists():
            raise SystemExit(f"Nombre de JAR duplicado: {jar.name}")
        shutil.copy2(jar, destination)
        digest = hashlib.sha256(destination.read_bytes()).hexdigest()
        manifest["mods"].append({"repository": repo, "tag": tag, "commit": sha,
                                 "jar": jar.name, "sha256": digest})
        if name == "permaworld-main":
            main_jar = jar.resolve()

    (output / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text(
        "".join(f"{mod['sha256']}  {mod['jar']}\n" for mod in manifest["mods"]),
        encoding="utf-8")
    (output / "RELEASE_NOTES.md").write_text(
        f"Mods para Minecraft {mc}. Cada JAR procede del tag indicado; "
        "el manifiesto incluye los commits y hashes SHA-256.\n\n"
        + "".join(f"- [{mod['repository']}](https://github.com/{mod['repository']}/tree/{mod['tag']}): "
                  f"`{mod['tag']}` — `{mod['jar']}`\n" for mod in manifest["mods"]),
        encoding="utf-8")


if __name__ == "__main__":
    main()
