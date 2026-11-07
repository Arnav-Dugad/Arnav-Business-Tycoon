#!/bin/sh
# Rebuilds the business ladder, chapter goals and conglomerate fees from the generators and the tuning knobs, in order.
# Usage: sh tools/content/regen_economy.sh <repo-root>
set -e
R="${1:-.}"
python -I "$R/tools/content/gen_businesses.py" "$R"
for k in "$R"/tools/content/knobs/t*.json; do python -I "$R/tools/content/tune.py" "$R" "$k"; done
python -I "$R/tools/content/gen_chapters.py" "$R"
# City property prices are pegged to the ladder.
python -I "$R/tools/content/gen_estates.py" "$R"
