const { app, BrowserWindow, shell, ipcMain } = require('electron');
const path = require('path'), engine = require('./engine'), { Presence } = require('./discord');
let cfg = {}; try { cfg = require('./config.json'); } catch (e) {}
const rpc = new Presence(process.env.ZENITH_DISCORD_ID || cfg.discordClientId);
const t0 = Date.now();
const idle = () => rpc.set({ details: 'In the launcher', state: 'Picking a profile', timestamps: { start: t0 }, assets: { large_image: 'zenith', large_text: 'Zenith Client' } });
const fs = require('fs'), os = require('os');
const LOG = path.join(engine.dataRoot(), 'app.log');
function log(...a) { try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.appendFileSync(LOG, new Date().toISOString() + ' ' + a.join(' ') + '\n'); } catch (e) {} console.log(...a); }
if (process.platform === 'linux') {
  app.commandLine.appendSwitch('no-sandbox');
  // Black-window fix: many Linux GPU/Wayland setups paint nothing. Set ZENITH_GPU=1 to turn the GPU back on.
  if (!process.env.ZENITH_GPU) { app.disableHardwareAcceleration(); app.commandLine.appendSwitch('disable-gpu'); app.commandLine.appendSwitch('ozone-platform', 'x11'); }
}
if (!app.requestSingleInstanceLock()) { log('another Zenith is already running, focusing it'); app.quit(); }
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
let win;
function create() {
  win = new BrowserWindow({
    width: 1280, height: 860, minWidth: 380, minHeight: 600, backgroundColor: '#06060b', title: 'Zenith Client', autoHideMenuBar: true,
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: { contextIsolation: true, preload: path.join(__dirname, 'preload.js') }
  });
  const page = path.join(__dirname, 'launcher', 'index.html');
  log('loading', page, 'exists=' + fs.existsSync(page));
  win.webContents.on('console-message', (e, lvl, msg, line, src) => { if (lvl >= 2) log('[page error]', msg, src + ':' + line); });
  win.webContents.on('did-fail-load', (e, code, desc) => { log('did-fail-load', code, desc); win.loadURL('data:text/html,<body style="background:%2306060b;color:%23fff;font:16px sans-serif;padding:40px">Zenith could not load its interface (' + encodeURIComponent(desc) + '). See ' + encodeURIComponent(LOG) + '</body>'); });
  win.webContents.on('render-process-gone', (e, d) => log('render-process-gone', d.reason));
  win.webContents.on('before-input-event', (e, i) => { if (i.type === 'keyDown' && i.control && i.shift && i.key.toLowerCase() === 'i') win.webContents.toggleDevTools(); });
  win.loadFile(page);
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
