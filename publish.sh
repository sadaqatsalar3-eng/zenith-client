#!/usr/bin/env bash
# One command for you (the creator): puts Zenith on GitHub and builds downloads for Linux, Windows and Mac.
set -e
cd "$(dirname "$(readlink -f "$0")")"
command -v git >/dev/null || { echo "Install git: sudo pacman -S git"; exit 1; }
command -v gh >/dev/null  || { echo "Install the GitHub CLI: sudo pacman -S github-cli"; exit 1; }
gh auth status >/dev/null 2>&1 || gh auth login
VER="${1:-v1.0.0}"
[ -d .git ] || { git init -q -b main; git add .; git commit -qm "Zenith Client"; }
gh repo view zenith-client >/dev/null 2>&1 || gh repo create zenith-client --public --source=. --push
git push -q origin main 2>/dev/null || true
git tag "$VER" 2>/dev/null || true
git push -q origin "$VER"
USER_NAME="$(gh api user -q .login)"
echo
echo "Building now (about 10 minutes): https://github.com/$USER_NAME/zenith-client/actions"
echo "Share this link, it always has the newest download:"
echo "https://github.com/$USER_NAME/zenith-client/releases/latest"
