#!/usr/bin/env bash
# Installs Zenith Client as a real desktop app (Electron). Run once: ./install-app.sh
set -e
DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
command -v node >/dev/null && command -v npm >/dev/null || { echo "Install Node.js first: sudo pacman -S nodejs npm"; exit 1; }
echo "Installing app files (downloads Electron, about 100 MB)..."
(cd "$DIR/app" && npm install --no-audit --no-fund)
mkdir -p "$HOME/.local/share/applications" "$HOME/.local/share/icons"
cp "$DIR/icon.png" "$HOME/.local/share/icons/zenith-client.png"
cat > "$HOME/.local/share/applications/zenith-client.desktop" <<DESK
[Desktop Entry]
Type=Application
Name=Zenith Client
Comment=Minecraft launcher
Exec=sh -c 'cd "$DIR/app" && exec npx electron . --no-sandbox'
Icon=$HOME/.local/share/icons/zenith-client.png
Terminal=false
Categories=Game;
StartupWMClass=Zenith Client
DESK
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
echo "Done. Open 'Zenith Client' from your app menu. Keep this folder where it is."
echo "Optional single-file build: cd app && npm run dist  (gives a .AppImage in app/dist)"
