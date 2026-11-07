"""Release gates G10–G12 (docs/TEST_PLAN.md §7): APK size, credits for every bundled asset, nothing forbidden in git.

Usage: python -I tools/scripts/check_release.py <repo-root> [path/to/app-release.apk]
Exit code 0 only if every check passes.
"""
import json
import subprocess
import sys
from pathlib import Path

ALLOWED = {"CC0", "AI-GENERATED", "PUBLIC-DOMAIN", "OFL-1.1", "ORIGINAL"}
MAX_APK_MB = 60


def main(repo: Path, apk: Path | None) -> int:
    failures = []
    # G10: size budget
    if apk is not None:
        mb = apk.stat().st_size / (1024 * 1024)
        print(f"G10 APK size: {mb:.1f} MB (budget {MAX_APK_MB} MB)")
        if mb > MAX_APK_MB:
            failures.append(f"APK is {mb:.1f} MB")
    # G12: every bundled image is credited with an allowed license
    assets = repo / "app/src/main/assets/img"
    for d in sorted(p for p in assets.iterdir() if p.is_dir()):
        credits_path = d / "credits.json"
        files = sorted(f.stem for f in d.glob("*.webp"))
        if not credits_path.exists():
            failures.append(f"{d.name}: no credits.json")
            continue
        data = json.loads(credits_path.read_text(encoding="utf-8"))
        items = data["items"] if isinstance(data, dict) else data
        credited = {i["id"]: i.get("license", "") for i in items}
        missing = [f for f in files if f not in credited]
        bad = [k for k, v in credited.items() if v.upper() not in ALLOWED and not v.upper().startswith("CC0")]
        orphans = [k for k in credited if k not in files]
        print(f"G12 {d.name}: {len(files)} images, {len(missing)} uncredited, {len(bad)} bad licenses, {len(orphans)} orphan credits")
        if missing or bad:
            failures.append(f"{d.name}: uncredited {missing[:5]} bad {bad[:5]}")
    # G12: audio is original (synthesized by tools/audio/gen_sfx.py and gen_music.py)
    raw = sorted((repo / "app/src/main/res/raw").glob("*"))
    gen = (repo / "tools/audio/gen_sfx.py").read_text(encoding="utf-8") + (repo / "tools/audio/gen_music.py").read_text(encoding="utf-8")
    unknown = [p.name for p in raw if p.stem not in gen]
    print(f"G12 audio: {len(raw)} files, {len(unknown)} not produced by gen_sfx.py / gen_music.py")
    if unknown:
        failures.append(f"audio of unknown origin: {unknown}")
    # G11: nothing forbidden is tracked
    tracked = subprocess.run(["git", "ls-files"], cwd=repo, capture_output=True, text=True, check=True).stdout.splitlines()
    forbidden = [f for f in tracked if f.endswith((".apk", ".aab", ".jks", ".keystore")) or f.endswith("google-services.json")]
    print(f"G11 forbidden files tracked: {len(forbidden)}")
    if forbidden:
        failures.append(f"forbidden files tracked: {forbidden}")
    print("RESULT:", "PASS" if not failures else "FAIL " + "; ".join(failures))
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1]), Path(sys.argv[2]) if len(sys.argv) > 2 else None))
