"""Secret scanner. Run before every push (installed as a git pre-push hook by tools/scripts/install_hooks.py).

Scans every file git would publish (tracked + staged + untracked-but-not-ignored) for:
  - forbidden files: google-services.json, keystores, APK/AAB bundles, private keys, .env files
  - secret patterns: Google API keys, private key blocks, OAuth/GitHub tokens, Firebase service accounts
  - local-only patterns from `.secret-patterns.local` (git-ignored), e.g. personal email addresses,
    so those addresses never have to appear in the repo themselves.

Exit code 1 if anything is found. Usage: python -I tools/scripts/scan_secrets.py [repo-root]
"""
import re
import subprocess
import sys
from pathlib import Path

FORBIDDEN_NAMES = re.compile(
    r"(^|/)(google-services\.json|.*\.jks|.*\.keystore|.*\.p12|.*\.pem|.*\.apk|.*\.aab|.*\.apks|"
    r"keystore\.properties|signing\.properties|\.env(\..*)?|service-account.*\.json|local\.properties)$",
    re.IGNORECASE,
)
PATTERNS = {
    "Google API key": re.compile(r"AIza[0-9A-Za-z_\-]{35}"),
    "Private key block": re.compile(r"-----BEGIN (RSA |EC |OPENSSH |PGP )?PRIVATE KEY-----"),
    "GitHub token": re.compile(r"\b(gh[pousr]_[0-9A-Za-z]{30,}|github_pat_[0-9A-Za-z_]{40,})\b"),
    "Google OAuth client secret": re.compile(r"GOCSPX-[0-9A-Za-z_\-]{20,}"),
    "Service account": re.compile(r'"type"\s*:\s*"service_account"'),
    "Firebase CI token": re.compile(r"\b1//0[0-9A-Za-z_\-]{30,}"),
    "Pexels key assignment": re.compile(r"PEXELS_API_KEY\s*[=:]\s*['\"]?[0-9A-Za-z]{30,}"),
    "Keystore password assignment": re.compile(r"ABT_(STORE|KEY)_PASSWORD\s*=\s*\S+"),
}
BINARY_EXT = {".png", ".webp", ".jpg", ".jpeg", ".ttf", ".otf", ".ogg", ".jar", ".zip", ".gz"}
ALLOWLIST = {"tools/scripts/scan_secrets.py"}  # this file contains the patterns themselves


def git_files(repo: Path) -> list[str]:
    out = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard"],
        cwd=repo, capture_output=True, text=True, check=True,
    ).stdout
    return [line for line in out.splitlines() if line]


def local_patterns(repo: Path) -> list[re.Pattern]:
    f = repo / ".secret-patterns.local"
    if not f.exists():
        return []
    lines = [l.strip() for l in f.read_text(encoding="utf-8").splitlines()]
    return [re.compile(re.escape(l), re.IGNORECASE) for l in lines if l and not l.startswith("#")]


def main(repo: Path) -> int:
    problems = []
    extra = local_patterns(repo)
    files = git_files(repo)
    for rel in files:
        if FORBIDDEN_NAMES.search(rel):
            problems.append(f"FORBIDDEN FILE  {rel}")
            continue
        if rel in ALLOWLIST:
            continue
        path = repo / rel
        if not path.is_file() or path.suffix.lower() in BINARY_EXT or path.stat().st_size > 5_000_000:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for name, rx in PATTERNS.items():
            if rx.search(text):
                problems.append(f"{name:28s}  {rel}")
        for rx in extra:
            if rx.search(text):
                problems.append(f"{'Local private pattern':28s}  {rel}")
    print(f"Scanned {len(files)} files; local private patterns loaded: {len(extra)}")
    for p in problems:
        print("  " + p)
    print("RESULT:", "CLEAN" if not problems else f"{len(problems)} PROBLEM(S) — do not push")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()))
