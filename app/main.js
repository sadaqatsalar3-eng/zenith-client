const { app, BrowserWindow, shell, ipcMain } = require('electron');
const path = require('path'), engine = require('./engine'), { Presence } = require('./discord');
let cfg = {}; try { cfg = require('./config.json'); } catch (e) {}
const rpc = new Presence(process.env.ZENITH_DISCORD_ID || cfg.discordClientId);
const t0 = Date.now();
const idle = () => rpc.set({ details: 'In the launcher', state: 'Picking a profile', timestamps: { start: t0 }, assets: { large_image: 'zenith', large_text: 'Zenith Client' } });
if (process.platform === 'linux') app.commandLine.appendSwitch('no-sandbox');
if (!app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
let win;
function create() {
  win = new BrowserWindow({
    width: 1280, height: 860, minWidth: 380, minHeight: 600, backgroundColor: '#06060b', title: 'Zenith Client', autoHideMenuBar: true,
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: { contextIsolation: true, preload: path.join(__dirname, 'preload.js') }
  });
  win.loadFile(path.join(__dirname, 'launcher', 'index.html'));
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
}
let running = false;
ipcMain.handle('zenith:launch', async (e, opts) => {
  if (running) return { ok: false, error: 'Minecraft is already starting.' };
  running = true; const prof = opts.profile || {}; const send = ev => {
    if (ev.type === 'progress' && ev.step < 5) rpc.set({ details: 'Preparing ' + (prof.n || 'Minecraft'), state: 'Downloading game files', timestamps: { start: t0 }, assets: { large_image: 'zenith', large_text: 'Zenith Client' } });
    if (ev.type === 'started') rpc.set({ details: 'Playing Minecraft ' + prof.v, state: (opts.server ? 'On ' + opts.server : prof.n + ' · ' + prof.l), timestamps: { start: Date.now() }, assets: { large_image: 'zenith', large_text: 'Zenith Client' } });
    if (ev.type === 'exit') idle();
 if (!e.sender.isDestroyed()) e.sender.send('zenith:event', ev); if (ev.type === 'exit') running = false; };
  try { const r = await engine.launch(Object.assign({}, opts, { onEvent: send })); return { ok: true, pid: r.pid, gameDir: r.gameDir }; }
  catch (err) { running = false; return { ok: false, error: String(err && err.message || err) }; }
});
ipcMain.handle('zenith:folder', (e, sub) => shell.openPath(path.join(engine.dataRoot(), sub || '')));
app.whenReady().then(() => { create(); rpc.start(); idle(); });
app.on('before-quit', () => rpc.stop());
app.on('window-all-closed', () => app.quit());
