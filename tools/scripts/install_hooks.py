"""Installs the git pre-push hook that runs the secret scanner. Usage: python -I tools/scripts/install_hooks.py"""
import stat
import subprocess
from pathlib import Path

repo = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip())
hook = repo / ".git" / "hooks" / "pre-push"
hook.write_text(
    "#!/bin/sh\n"
    "# Installed by tools/scripts/install_hooks.py — blocks pushes that contain secrets.\n"
    'python -I tools/scripts/scan_secrets.py "$(git rev-parse --show-toplevel)" || {\n'
    '  echo "Push blocked by secret scanner."; exit 1; }\n',
    encoding="utf-8",
    newline="\n",
)
hook.chmod(hook.stat().st_mode | stat.S_IEXEC)
print(f"Installed {hook}")
