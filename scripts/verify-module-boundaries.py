#!/usr/bin/env python3
"""Check the launcher module graph and prevent application coupling in libraries.

Run from anywhere; accepts an optional project path for isolated negative checks.
Only the standard library is required. This deliberately checks source/build files,
so it is useful before invoking Gradle or downloading dependencies.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

LIBRARIES = {
    "theme-config": "app.swtv.theme",
    "app-discovery": "app.swtv.discovery",
    "launcher-ui": "app.swtv.launcher.ui",
    "device-diagnostics": "app.swtv.diagnostics",
    "update-manager": "app.swtv.updates",
    "tv-hub-contract": "app.swtv.tvhub.contract",
}
EXPECTED = {"app": set(LIBRARIES), **{name: set() for name in LIBRARIES}}
EXPECTED["launcher-ui"] = {"theme-config", "app-discovery"}


def verify(root: Path) -> list[str]:
    errors = []
    screen = root / "launcher-ui/src/main/java/app/swtv/launcher/ui/HomeScreen.kt"
    if not screen.is_file():
        errors.append("launcher-ui: missing reusable HomeScreen renderer")
    activity = root / "app/src/main/java/app/swtv/home/MainActivity.kt"
    if activity.is_file() and re.search(r"fun\s+(?:buildDock|buildApps|buildToolbar|buildModules|installSurface)\b", activity.read_text(encoding="utf-8-sig")):
        errors.append("app: home section/layout rendering belongs to launcher-ui")
    settings = (root / "settings.gradle.kts").read_text(encoding="utf-8-sig")
    included = set(re.findall(r'["\']:([\w-]+)["\']', settings))
    missing = set(EXPECTED) - included
    if missing:
        errors.append("Missing modules: " + ", ".join(sorted(missing)))
    unexpected = included - set(EXPECTED)
    if unexpected:
        errors.append("Unexpected modules: " + ", ".join(sorted(unexpected)))
    app_entries = []
    for name in sorted(included):
        for source in sorted((root / name / "src/main").rglob("*.kt")):
            text = source.read_text(encoding="utf-8-sig")
            package = re.search(r"^\s*package\s+([\w.]+)", text, re.M)
            for _ in re.finditer(r"\b(?:class|interface|object|typealias)\s+AppEntry\b", text):
                app_entries.append((name, package[1] if package else "", source.relative_to(root)))
    if [(module, package) for module, package, _ in app_entries] != [("app-discovery", "app.swtv.discovery")]:
        declarations = ", ".join(str(path) for _, _, path in app_entries) or "none"
        errors.append("AppEntry must be declared exactly once, in app-discovery/package app.swtv.discovery; found: " + declarations)
    graph = {}
    for name in sorted(included):
        module = root / name
        build = module / "build.gradle.kts"
        if not build.is_file():
            errors.append(f"{name}: missing build.gradle.kts")
            continue
        code = build.read_text(encoding="utf-8-sig")
        dependencies = set(re.findall(r'project\s*\(\s*["\']:([\w-]+)["\']\s*\)', code))
        graph[name] = dependencies
        if name in EXPECTED and dependencies != EXPECTED[name]:
            errors.append(f"{name}: dependencies {sorted(dependencies)}; expected {sorted(EXPECTED[name])}")
        if name not in LIBRARIES:
            continue
        if 'id("com.android.library")' not in code or 'com.android.application' in code:
            errors.append(f"{name}: must use the Android library plugin")
        namespace = re.search(r'namespace\s*=\s*"([\w.]+)"', code)
        if not namespace or namespace[1] != LIBRARIES[name]:
            errors.append(f"{name}: incorrect namespace")
        for setting, pattern in {
            "compile SDK 35": r"compileSdk\s*=\s*35\b",
            "minimum SDK 29": r"minSdk\s*=\s*29\b",
            "JVM 17": r'jvmTarget\s*=\s*"17"',
        }.items():
            if not re.search(pattern, code):
                errors.append(f"{name}: requires {setting}")
        for source in sorted((module / "src").rglob("*.kt")):
            text = source.read_text(encoding="utf-8-sig")
            # Application types/resources must never be dependencies of a library.
            if re.search(r"^\s*import\s+app\.swtv\.home(?:\.|\s|$)", text, re.M):
                errors.append(f"{source.relative_to(root)}: imports application code")
            if re.search(r"\b(?:app\.swtv\.home\.)?(?:MainActivity)\b|app\.swtv\.home\.(?:BuildConfig|R)\b", text):
                errors.append(f"{source.relative_to(root)}: references application implementation")
        manifests = list((module / "src").rglob("AndroidManifest.xml"))
        if not manifests:
            errors.append(f"{name}: missing library manifest")
        for manifest in manifests:
            try:
                tree = ET.parse(manifest)
                if tree.find("application") is not None or tree.findall(".//activity"):
                    errors.append(f"{manifest.relative_to(root)}: libraries cannot own the application/activity manifest")
            except ET.ParseError as error:
                errors.append(f"{manifest.relative_to(root)}: malformed manifest: {error}")
    visited, active = set(), set()

    def visit(name: str) -> None:
        if name in active:
            errors.append(f"Dependency cycle includes {name}")
            return
        if name in visited:
            return
        active.add(name)
        for dependency in graph.get(name, set()):
            if dependency not in included:
                errors.append(f"{name}: dependency {dependency} is not included")
            visit(dependency)
        active.remove(name)
        visited.add(name)

    for name in graph:
        visit(name)
    return errors


if __name__ == "__main__":
    project = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]
    failures = verify(project)
    for failure in failures:
        print("ERROR: " + failure)
    if failures:
        sys.exit(1)
    print("PASS: six Android libraries; expected acyclic dependencies; no library/application coupling")
