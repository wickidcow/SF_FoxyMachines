#!/usr/bin/env python3
"""Disposable real-Paper Foxy Chunk Loader ticket/restart probe.

No AlbionMC world or user data is used. Four JVMs run on the same disposable
world: ALWAYS -> ONLINE (0 players) -> OFF -> ALWAYS. Each phase requires the
actual FoxyMachines plugin, a durable block/owner registry, no vanilla force
load flags and exact Paper plugin ticket/Slimefun guide results.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.request
import zipfile

PHASES = ("ALWAYS_FIRST", "ONLINE", "OFF", "ALWAYS_RESTART")
MARKER = "FOXY_NATIVE_CHUNK_POLICY_PASS"
PLUGIN_YML = """name: FoxyTicketProbe
version: '1.0'
main: audit.FoxyTicketProbe
api-version: '1.21.11'
depend: [Slimefun, FoxyMachines]
"""


def fetch_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "FoxyMachines-Native-CI/1.0"})
    with urllib.request.urlopen(req, timeout=45) as response:
        return json.load(response)


def fetch_paper(mc_version: str, destination: Path) -> str:
    builds = fetch_json(f"https://fill.papermc.io/v3/projects/paper/versions/{mc_version}/builds")
    if not isinstance(builds, list):
        raise RuntimeError(f"Paper build list unavailable: {str(builds)[:350]}")
    stable = next((build for build in builds
                   if build.get("channel") == "STABLE"
                   and build.get("downloads", {}).get("server:default", {}).get("url")), None)
    if stable is None:
        raise RuntimeError(f"No STABLE Paper build found for {mc_version}")
    download = stable["downloads"]["server:default"]["url"]
    req = urllib.request.Request(download, headers={"User-Agent": "FoxyMachines-Native-CI/1.0"})
    with urllib.request.urlopen(req, timeout=120) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)
    if destination.stat().st_size < 20_000_000:
        raise RuntimeError("Downloaded Paper runtime is implausibly small")
    return str(stable["id"])


def build_probe(root: Path, foxy: Path, legacy: Path, paper_api: Path,
                resolved_classpath: str, output: Path):
    classes = root / "probe-classes"
    classes.mkdir(parents=True)
    source = Path("scripts/runtime/FoxyTicketProbe.java")
    subprocess.run(["javac", "--release", "21", "-cp",
                    os.pathsep.join(str(path) for path in (foxy, legacy, paper_api))
                    + os.pathsep + resolved_classpath,
                    "-d", str(classes), str(source)], check=True, timeout=120)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for clazz in classes.rglob("*.class"):
            archive.write(clazz, clazz.relative_to(classes).as_posix())
        archive.writestr("plugin.yml", PLUGIN_YML)


def set_mode(config: Path, mode: str):
    text = config.read_text(encoding="utf-8")
    # Bukkit YAML may quote OFF when saving config (YAML 1.1 boolean token).\n    # Accept only a single exact scalar, optionally enclosed in matching quotes.\n    match = list(re.finditer(\n        r"(?m)^chunk-loading-mode:[ \\t]*(?:ONLINE|ALWAYS|OFF|\\\"(?:ONLINE|ALWAYS|OFF)\\\"|\u0027(?:ONLINE|ALWAYS|OFF)\u0027)[ \\t]*$",\n        text,\n    ))
    if len(match) != 1:
        raise RuntimeError("FoxyMachines config must contain exactly one independent policy field")
    text = text[:match[0].start()] + f"chunk-loading-mode: {mode}" + text[match[0].end():]
    config.write_text(text, encoding="utf-8")


def verify_server_cycle(root: Path, phase: str, timeout: int):
    log_path = root / f"paper-{phase.lower()}.log"
    env = {**os.environ, "JAVA_TOOL_OPTIONS": "-Dfile.encoding=UTF-8"}
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            ["java", "-Xms512M", "-Xmx2G", "-jar", "paper.jar", "--nogui"],
            cwd=root, stdin=subprocess.PIPE, stdout=log,
            stderr=subprocess.STDOUT, text=True, env=env,
        )
        started = time.monotonic()
        found_marker = False
        failed = False
        try:
            while time.monotonic() - started < timeout:
                if process.poll() is not None:
                    break
                time.sleep(2)
                data = log_path.read_text(encoding="utf-8", errors="replace")
                if "FOXY_NATIVE_CHUNK_POLICY_FAIL" in data:
                    failed = True
                    break
                if f"{MARKER} {phase} " in data:
                    if "Done (" not in data:
                        raise RuntimeError("Probe reported PASS before Paper reached Done")
                    found_marker = True
                    break
        finally:
            if process.poll() is None:
                try:
                    assert process.stdin is not None
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                    process.wait(timeout=75)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    process.kill()
                    process.wait(timeout=20)
            if process.stdin is not None:
                process.stdin.close()
        log.flush()
    data = log_path.read_text(encoding="utf-8", errors="replace")
    if failed or not found_marker or process.returncode != 0:
        tail = "\n".join(data.splitlines()[-90:])
        raise RuntimeError(f"Native Foxy probe phase {phase} did not pass cleanly. "
                           f"status={process.returncode}, marker={found_marker}\n{tail}")
    print(f"{phase}: PASS (real Paper, clean shutdown, no players)", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--foxy-jar", type=Path, required=True)
    parser.add_argument("--slimefun-jar", type=Path, required=True)
    parser.add_argument("--paper-api-jar", type=Path, required=True)
    parser.add_argument("--compile-classpath-file", type=Path, required=True)
    parser.add_argument("--minecraft", choices=["1.21.11", "26.2", "26.3"], required=True)
    args = parser.parse_args()

    foxy, legacy, api = (v.resolve(strict=True) for v in
                          (args.foxy_jar, args.slimefun_jar, args.paper_api_jar))
    extra_classpath = args.compile_classpath_file.resolve(strict=True).read_text(
        encoding="utf-8").strip()
    if not extra_classpath:
        raise RuntimeError("Maven's resolved native-probe classpath is empty")
    repo = Path.cwd().resolve()
    base = repo / "build" / "foxy-native-ticket-probe"
    if base.is_symlink() or base.parent != repo / "build":
        raise RuntimeError("Refusing unsafe test directory")
    base.mkdir(parents=True, exist_ok=True)
    root = base / args.minecraft
    if root.exists() or root.is_symlink():
        raise RuntimeError(f"Probe must use a fresh disposable server directory: {root}")
    root.mkdir()
    paper = root / "paper.jar"
    build_id = fetch_paper(args.minecraft, paper)
    print(f"Paper {args.minecraft} stable build {build_id}", flush=True)

    (root / "plugins").mkdir()
    shutil.copyfile(foxy, root / "plugins" / "FoxyMachines.jar")
    shutil.copyfile(legacy, root / "plugins" / "Slimefun-Legacy.jar")
    build_probe(root, foxy, legacy, api, extra_classpath,
                root / "plugins" / "FoxyTicketProbe.jar")

    (root / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (root / "server.properties").write_text(
        "online-mode=false\nmax-players=2\n"
        "level-name=foxy-test-world\nview-distance=2\nsimulation-distance=2\n"
        "spawn-protection=0\npause-when-empty-seconds=-1\n"
        "enable-rcon=false\nenable-query=false\n", encoding="utf-8")
    (root / "plugins" / "bStats").mkdir()
    (root / "plugins" / "bStats" / "config.yml").write_text(
        "enabled: false\n", encoding="utf-8")

    config_dir = root / "plugins" / "FoxyMachines"
    config_dir.mkdir()
    shutil.copyfile("src/main/resources/config.yml", config_dir / "config.yml")
    for phase in PHASES:
        set_mode(config_dir / "config.yml", "ALWAYS" if phase.startswith("ALWAYS") else phase)
        (root / "foxy-policy-phase.txt").write_text(phase, encoding="utf-8")
        verify_server_cycle(root, phase, timeout=330)
    print(f"PASS: 4 native Paper cycles {args.minecraft} / {build_id}. "
          "No external world files modified.", flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"FOXY_NATIVE_CHUNK_POLICY_FAIL: {error}", file=sys.stderr)
        sys.exit(1)
