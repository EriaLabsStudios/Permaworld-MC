"""Build one reproducible Minecraft-version bundle from tagged mod repos."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
from urllib.parse import urlparse
import zipfile


# Keep permaworld-main before permaworld-web, which compiles against its JAR.
REPOSITORIES = (
    "EriaLabsStudios/permaworld-main",
    "EriaLabsStudios/permaworld-texture-pack",
    "EriaLabsStudios/permaworld-utilities",
    "EriaLabsStudios/permaworld-chat",
    "EriaLabsStudios/permaworld-server-changelogs",
    "EriaLabsStudios/permaworld-multiworld",
    "EriaLabsStudios/permaworld-web",
)
TEXTURE_PACKS_REPOSITORY = "EriaLabsStudios/permaworld-texture-packs"
MINECRAFT_VERSION = re.compile(r"[0-9]+\.[0-9]+(?:\.[0-9]+)?\Z")
PACK_VERSION = re.compile(r" v([0-9]+\.[0-9]+\.[0-9]+)\.zip\Z")
DESCRIPTION_VERSION = re.compile(r" v([0-9]+\.[0-9]+\.[0-9]+)\b")
PACK_ID = re.compile(r"-v[0-9]+\.[0-9]+\.[0-9]+\Z")
RELEASES_URL = "https://api.github.com/repos/EriaLabsStudios/Permaworld-MC/releases?per_page=100"


def run(*args, cwd=None, env=None):
    return subprocess.run(args, cwd=cwd, env=env, check=True, text=True, capture_output=True).stdout.strip()


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


def expected_jar_name(repo, mod_version, minecraft_version):
    return f"permaworld-{repo.rsplit('/', 1)[1].removeprefix('permaworld-')}-{mod_version}-{minecraft_version}.jar"


def latest_bundle_manifest(mc):
    request = urllib.request.Request(RELEASES_URL, headers={"User-Agent": "Permaworld-bundle"})
    with urllib.request.urlopen(request, timeout=20) as response:
        releases = json.load(response)
    pattern = re.compile(rf"bundle-mc{re.escape(mc)}-r([0-9]+)\Z")
    matching = [(int(match.group(1)), release) for release in releases
                if (match := pattern.fullmatch(release["tag_name"]))]
    if not matching:
        return None
    release = max(matching)[1]
    asset = next(asset for asset in release["assets"] if asset["name"] == "manifest.json")
    with urllib.request.urlopen(asset["browser_download_url"], timeout=20) as response:
        return json.load(response)


def same_selection(manifest, selected, texture_packs_commit):
    return manifest is not None and {
        (mod["repository"], mod["tag"], mod["commit"]) for mod in manifest["mods"]
    } == set(selected) and manifest.get("resource_packs_commit") == texture_packs_commit


def texture_pack_entries(checkout):
    entries = json.loads((checkout / "packs.json").read_text(encoding="utf-8"))
    packs = []
    for entry in entries:
        if not entry.get("release"):
            continue
        match = PACK_VERSION.search(entry["output"])
        if match is None:
            continue
        source = Path(entry["source"])
        if source.is_absolute() or source.parts[0] != "packs":
            raise ValueError(f"Fuente de pack invalida: {entry['source']}")
        packs.append({"id": PACK_ID.sub("", source.name),
                      "name": entry["output"][:match.start()],
                      "version": match.group(1), "source": entry["source"],
                      "source_zip": entry["output"],
                      "zip": f"{PACK_ID.sub('', source.name)}-{match.group(1)}.zip"})
    return packs


def external_pack_entries(checkout, mc):
    catalog = json.loads((checkout / "external-packs.json").read_text(encoding="utf-8"))
    if catalog.get("minecraftVersion") != mc:
        return []
    packs = []
    seen_ids = set()
    seen_files = set()
    for entry in catalog["packs"]:
        pack = {key: entry[key] for key in ("id", "group", "name", "version", "file",
                                              "project", "url", "sha256", "sha512", "supports26_3")}
        if not re.fullmatch(r"[a-z0-9][a-z0-9-]*", pack["id"]):
            raise ValueError(f"ID de pack externo invalido: {pack['id']}")
        if not re.fullmatch(r"[A-Za-z0-9._+\- ]+\.zip", pack["file"]):
            raise ValueError(f"Nombre de pack externo invalido: {pack['file']}")
        if pack["id"] in seen_ids or pack["file"] in seen_files:
            raise ValueError(f"Pack externo duplicado: {pack['id']}")
        seen_ids.add(pack["id"])
        seen_files.add(pack["file"])
        if not re.fullmatch(r"[0-9a-f]{64}", pack["sha256"]):
            raise ValueError(f"SHA-256 de pack externo invalido: {pack['id']}")
        if not re.fullmatch(r"[0-9a-f]{128}", pack["sha512"]):
            raise ValueError(f"SHA-512 de pack externo invalido: {pack['id']}")
        url = urlparse(pack["url"])
        if url.scheme != "https" or url.hostname not in {"cdn.modrinth.com", "edge.forgecdn.net"} \
                or url.username or url.password:
            raise ValueError(f"URL oficial de pack externo invalida: {pack['id']}")
        if not pack["project"].startswith(("https://modrinth.com/resourcepack/",
                                           "https://www.curseforge.com/minecraft/texture-packs/")):
            raise ValueError(f"Proyecto de pack externo invalido: {pack['id']}")
        if "archive" in entry:
            archive = Path(entry["archive"])
            if archive.parts != ("external-packs", "archives", pack["file"]):
                raise ValueError(f"Ruta de ZIP externo invalida: {pack['id']}")
            pack["archive"] = archive
        packs.append(pack)
    return packs


def component_text(component):
    if isinstance(component, str):
        return component
    if isinstance(component, list):
        return "".join(component_text(item) for item in component)
    if isinstance(component, dict):
        return component.get("text", "")
    return ""


def bundle_resource_packs(packs, packs_output, output, mc):
    name = f"permaworld-resource-packs-{mc}.zip"
    destination = output / name
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
        bundle.writestr("README.txt", "Extrae los ZIP de resourcepacks/ y copia los que quieras instalar "
                        "a la carpeta resourcepacks de Minecraft. No instales este ZIP contenedor.\n")
        for pack in packs:
            bundle.write(packs_output / pack["source_zip"], f"resourcepacks/{pack['zip']}")
    with zipfile.ZipFile(destination) as bundle:
        if bundle.testzip() is not None:
            raise SystemExit(f"ZIP de resource packs invalido: {destination}")
    return {"zip": name, "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
            "packs": [{key: value for key, value in pack.items() if key != "source_zip"} for pack in packs]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("minecraft_version")
    parser.add_argument("--workdir", type=Path, default=Path(".bundle-work"))
    parser.add_argument("--output", type=Path, default=Path("dist"))
    parser.add_argument("--skip-unchanged", action="store_true")
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

    texture_packs_commit = run("gh", "api", f"repos/{TEXTURE_PACKS_REPOSITORY}/commits/main", "--jq", ".sha")
    if args.skip_unchanged and same_selection(latest_bundle_manifest(mc), selected, texture_packs_commit):
        print("Sin cambios respecto a la ultima release conjunta", flush=True)
        return

    workdir = args.workdir.resolve()
    output = args.output.resolve()
    if output.exists() and any(output.iterdir()):
        raise SystemExit(f"El directorio de salida no esta vacio: {output}")
    workdir.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)
    manifest = {"minecraft_version": mc, "mods": [], "resource_packs": [], "external_resource_packs": [],
                "resource_packs_commit": texture_packs_commit}
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
        expected_name = expected_jar_name(repo, mod_version, mc)
        if jar.name != expected_name:
            raise SystemExit(f"El JAR de {repo} debe llamarse {expected_name}; se obtuvo {jar.name}")
        with zipfile.ZipFile(jar) as archive:
            if archive.testzip() is not None or "fabric.mod.json" not in archive.namelist():
                raise SystemExit(f"JAR invalido: {jar}")
            metadata = json.loads(archive.read("fabric.mod.json"))
            if metadata.get("version") not in {mod_version, f"{mod_version}+mc{mc}"}:
                raise SystemExit(f"Version del JAR no coincide con {tag}: {jar}")
            mod_id = metadata.get("id")
            if not isinstance(mod_id, str) or not mod_id:
                raise SystemExit(f"Falta ID de mod en {jar}")
        destination = output / jar.name
        if destination.exists():
            raise SystemExit(f"Nombre de JAR duplicado: {jar.name}")
        shutil.copy2(jar, destination)
        digest = hashlib.sha256(destination.read_bytes()).hexdigest()
        manifest["mods"].append({"repository": repo, "mod_id": mod_id,
                                 "version": metadata["version"], "tag": tag, "commit": sha,
                                 "jar": jar.name, "sha256": digest})
        if name == "permaworld-main":
            main_jar = jar.resolve()

    packs_checkout = workdir / "permaworld-texture-packs"
    subprocess.run(["gh", "repo", "clone", TEXTURE_PACKS_REPOSITORY, str(packs_checkout), "--",
                    "--depth", "1"], check=True)
    if run("git", "rev-parse", "HEAD", cwd=packs_checkout) != texture_packs_commit:
        raise SystemExit("El repositorio de paquetes cambio durante la compilacion")
    try:
        packs = texture_pack_entries(packs_checkout)
        external_packs = external_pack_entries(packs_checkout, mc)
    except (KeyError, ValueError, json.JSONDecodeError) as error:
        raise SystemExit(f"Catalogo de paquetes invalido: {error}") from error
    packs_output = packs_checkout / "dist"
    subprocess.run(["pwsh", "-File", "scripts/build-packs.ps1", "-OutputDirectory", str(packs_output)],
                   cwd=packs_checkout, check=True)
    for pack in packs:
        archive = packs_output / pack["source_zip"]
        if not archive.is_file():
            raise SystemExit(f"Falta el ZIP del pack: {archive}")
        with zipfile.ZipFile(archive) as zip_file:
            if zip_file.testzip() is not None or not {"pack.mcmeta", "pack.png"}.issubset(zip_file.namelist()):
                raise SystemExit(f"Pack invalido: {archive}")
            metadata = json.loads(zip_file.read("pack.mcmeta"))
            description = component_text(metadata.get("pack", {}).get("description", ""))
            match = DESCRIPTION_VERSION.search(description)
            if match is None or match.group(1) != pack["version"]:
                raise SystemExit(f"La versión del pack no coincide con pack.mcmeta: {archive}")
            pack["marker"] = description[:match.start()]
        pack["sha256"] = hashlib.sha256(archive.read_bytes()).hexdigest()
        pack["commit"] = texture_packs_commit
    manifest["resource_pack_archive"] = bundle_resource_packs(packs, packs_output, output, mc)

    for pack in external_packs:
        archive_path = pack.pop("archive", None)
        if archive_path is not None:
            archive = packs_checkout / archive_path
            if not archive.is_file() or hashlib.sha256(archive.read_bytes()).hexdigest() != pack["sha256"] \
                    or hashlib.sha512(archive.read_bytes()).hexdigest() != pack["sha512"]:
                raise SystemExit(f"ZIP externo ausente o hash incorrecto: {archive}")
            with zipfile.ZipFile(archive) as zip_file:
                if zip_file.testzip() is not None or not {"pack.mcmeta", "pack.png"}.issubset(zip_file.namelist()):
                    raise SystemExit(f"ZIP externo invalido: {archive}")
                json.loads(zip_file.read("pack.mcmeta"))
        manifest["external_resource_packs"].append(pack)

    (output / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text(
        "".join(f"{mod['sha256']}  {mod['jar']}\n" for mod in manifest["mods"])
        + f"{manifest['resource_pack_archive']['sha256']}  {manifest['resource_pack_archive']['zip']}\n",
        encoding="utf-8")
    (output / "RELEASE_NOTES.md").write_text(
        f"Mods para Minecraft {mc}. Cada JAR procede del tag indicado; "
        "el manifiesto incluye los commits y hashes SHA-256.\n\n## Mods (JAR)\n\n"
        + "".join(f"- [{mod['repository']}](https://github.com/{mod['repository']}/tree/{mod['tag']}): "
                  f"`{mod['tag']}` — `{mod['jar']}`\n" for mod in manifest["mods"])
        + "\n## Resource packs (ZIP)\n\nDescarga `" + manifest["resource_pack_archive"]["zip"]
        + "`, extráelo y copia los ZIP de `resourcepacks/` a la carpeta `resourcepacks` de Minecraft. "
          "El ZIP contenedor no se instala directamente.\n\nPacks incluidos:\n\n"
        + "".join(f"- `{pack['name']}` {pack['version']} — `{pack['zip']}`\n"
                  for pack in manifest["resource_pack_archive"]["packs"])
        + "\n## Packs originales separados (descarga oficial)\n\n"
        + "".join(f"- [{pack['name']}]({pack['project']}) {pack['version']}\n"
                  for pack in manifest["external_resource_packs"]),
        encoding="utf-8")


if __name__ == "__main__":
    main()
