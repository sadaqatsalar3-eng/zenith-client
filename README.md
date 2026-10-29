# Zenith Client

A Minecraft launcher with profiles, a mod browser and a built-in HUD.

## What is in this zip

| Folder | What it is |
|---|---|
| `launcher/index.html` | The launcher interface. One file, no install. Open it in any browser. |
| `videos/` | The two TikTok teasers (1080x1920, 15 seconds). The synced one is cut to the song in your video. |
| `zenith.sh`, `install-app.sh`, `icon.png`, `app/` | Run it like an app on Linux. |
| `legacy-java/Main.java` | The first Java Swing version. It has the old design and is not updated. |

## One-click downloads (like Fast Client)

Run one command: `./publish.sh` (it asks you to log in to GitHub the first time).
It creates the repo, uploads Zenith and starts the build. About 10 minutes later the release page has a Linux `.AppImage`, a
Windows setup `.exe` and a macOS `.dmg`. People open the link, click the file for their system and run it. No Node, no Java, no terminal.
On Linux they just make the AppImage executable (right click, Properties, Allow executing) and double click it.
To ship an update later: change the code, then run `./publish.sh v1.0.1`.

## Install as a real app (Linux, from source)

```bash
sudo pacman -S nodejs npm jdk21-openjdk   # once. Java is optional, Zenith downloads one if missing
cd Zenith-Client
./install-app.sh                          # installs Electron + adds "Zenith Client" to your app menu
```
Or run it once without a menu entry: `./zenith.sh`. For a single file, `cd app && npm run dist` makes an AppImage.

## Discord status ("Playing Zenith Client")

1. Go to https://discord.com/developers/applications, click New Application and name it **Zenith Client** (Discord shows this name as "Playing Zenith Client").
2. Copy the **Application ID** and paste it into `app/config.json` as `discordClientId`.
3. Optional: in Rich Presence > Art Assets upload your logo with the key `zenith`.
4. Rebuild with `./publish.sh v1.0.x`. Discord must be open on the player's computer. It shows "In the launcher", "Preparing <profile>" and "Playing Minecraft <version>".

## What Play does in the app

1. Downloads the Minecraft version, Fabric loader, libraries and assets into `~/.zenith-client`.
2. Finds Java (or downloads the right one).
3. Downloads the mods, resource packs and shaders you installed in that profile from Modrinth (plus required dependencies) into `~/.zenith-client/instances/<profile>/`.
4. Starts Minecraft with your Offline username (and joins the server if you pressed Join).

Not yet real: Microsoft sign-in (Offline mode only), Forge/NeoForge auto-install (Fabric and Vanilla work),
CurseForge downloads, modpacks, and the Zenith HUD (there is no HUD mod yet, so nothing is injected).
The browser file `launcher/index.html` is still a preview: Play there only simulates.

## The launcher

- Intro cutscene with an animated asset loader.
- Login: Offline mode or Microsoft account (the Microsoft sign-in is a demo).
- Profiles: create, rename, delete. Game versions 1.20.1 to 26.2, loader and memory per profile.
- Browser for mods, modpacks, resource packs and shaders. Modrinth or CurseForge, search, filter dropdown, category tabs, pages 1 to 100.
- Play opens the launch checklist (files, loader, Zenith HUD, mods).
- The HUD is edited in game with Right Shift, not in the launcher.
- Quick jump: press Ctrl+K (Cmd+K on Mac) or tap JUMP to run any action by typing.
- Press `/` on the Profiles page to focus the search.
- Smooth mouse glow behind the cards, magnetic Play and Install buttons.
- Settings: accent colour, memory, toggles. "Smooth animations" off removes all motion.

Profiles and settings are saved in the browser (localStorage).

### Swap the mod logos

Open `launcher/index.html` and search for `LOGOS`. It is a list of `id -> image URL`.
Replace a URL with your own image, or a `data:` URI to embed it. Any item without an image
(or one that fails to load) shows a generated placeholder.

Fonts load from Google Fonts. Offline, the launcher uses system fonts.

### What is a demo

- The catalogue is about 115 real Modrinth projects. After them the lists continue with placeholder entries so paging works.
- Install, launch and Microsoft sign-in are simulated. A real launcher plugs the Modrinth API, Microsoft device-code login and the game launch in at those points.
