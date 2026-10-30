#!/bin/bash
# Copy the tracked files that are safe to publish into a fresh repo with one commit.
# Usage: tools/export-public.sh <target-dir>
set -euo pipefail
src=$(git rev-parse --show-toplevel); dst=${1:?target dir}
[ -e "$dst" ] && { echo "$dst exists"; exit 1; }
cd "$src"
# Never published: research notes about other apps, raw dictionary sources, AI tooling, local config.
git ls-files | grep -vE "$(cat tools/private-paths.txt 2>/dev/null || echo '^research/')" |
  while read -r f; do mkdir -p "$dst/$(dirname "$f")"; cp -p "$f" "$dst/$f"; done
cd "$dst"
git init -q -b main
git add -A
git -c user.name=Altayeb -c user.email=altybanony@gmail.com commit -q -m "Hudhud Keyboard"
gitleaks git --no-banner --redact . && echo "gitleaks: clean"
echo "exported $(git ls-files | wc -l) files to $dst"
