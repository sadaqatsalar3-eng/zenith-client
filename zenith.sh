#!/usr/bin/env bash
# Run the real app once without installing a menu entry.
DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
cd "$DIR/app" && { [ -d node_modules ] || npm install --no-audit --no-fund; } && exec npx electron . --no-sandbox
