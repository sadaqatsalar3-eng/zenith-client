const { app, BrowserWindow, shell, ipcMain } = require('electron');
const path = require('path'), engine = require('./engine'), { Presence } = require('./discord');
let cfg = {}; try { cfg = require('./config.json'); } catch (e) {}
const rpc = new Presence(process.env.ZENITH_DISCORD_ID || cfg.discordClientId);
const t0 = Date.now();
const idle = () => rpc.set({ details: 'In the launcher', state: 'Picking a profile', timestamps: { start: t0 }, assets: { large_image: 'zenith', large_text: 'Zenith Client' } });
const fs = require('fs'), os = require('os');
const LOG = path.join(engine.dataRoot(), 'app.log');
function log(...a) { try { fs.mkdirSync(path.dirname(LOG), { recursive: true }); fs.appendFileSync(LOG, new Date().toISOString() + ' ' + a.join(' ') + '\n'); } catch (e) {} console.log(...a); }
// ---- Self-healing renderer: if the window does not draw, try the next rendering mode and remember the one that works ----
const MODE_FILE = path.join(engine.dataRoot(), 'render-mode.json');
const MODES = process.platform === 'linux' ? [
  { name: 'x11-software', sw: ['disable-gpu', ['ozone-platform', 'x11']], hw: false },
  { name: 'default', sw: [], hw: true },
  { name: 'x11-swiftshader', sw: [['ozone-platform', 'x11'], ['use-angle', 'swiftshader'], 'enable-unsafe-swiftshader'], hw: true },
  { name: 'wayland-software', sw: ['disable-gpu', ['ozone-platform', 'wayland']], hw: false },
  { name: 'x11-no-gpu-compositing', sw: [['ozone-platform', 'x11'], 'disable-gpu-compositing'], hw: true },
  { name: 'in-process-gpu', sw: [['ozone-platform', 'x11'], 'in-process-gpu', 'disable-gpu-sandbox'], hw: true },
  { name: 'x11-software-noshm', sw: [['ozone-platform', 'x11'], 'disable-gpu', 'disable-dev-shm-usage', 'disable-site-isolation-trials'], hw: false },
  { name: 'single-process', sw: [['ozone-platform', 'x11'], 'disable-gpu', 'single-process', 'disable-dev-shm-usage'], hw: false }
] : [{ name: 'default', sw: [], hw: true }];
function pickMode() {
  const a = process.argv.find(x => x.startsWith('--zenith-mode='));
  if (a) return Math.max(0, Math.min(MODES.length - 1, +a.split('=')[1] || 0));
  if (process.env.ZENITH_MODE) return Math.max(0, Math.min(MODES.length - 1, +process.env.ZENITH_MODE || 0));
  try { const j = JSON.parse(fs.readFileSync(MODE_FILE, 'utf8')); if (j && j.mode >= 0 && j.mode < MODES.length) return j.mode; } catch (e) {}
  return 0;
}
const MODE = pickMode();
if (process.platform === 'linux') {
  app.commandLine.appendSwitch('no-sandbox');
  const m = MODES[MODE]; if (!m.hw) app.disableHardwareAcceleration();
  for (const s of m.sw) Array.isArray(s) ? app.commandLine.appendSwitch(s[0], s[1]) : app.commandLine.appendSwitch(s);
}
log('start mode', MODE, MODES[MODE].name, 'session', process.env.XDG_SESSION_TYPE || '?', 'electron', process.versions.electron);
const relaunching = process.argv.some(x => x.startsWith('--zenith-mode='));
if (!app.requestSingleInstanceLock()) {
  if (!relaunching) { log('another Zenith is already running, focusing it'); app.quit(); }
  else setTimeout(() => { if (!app.requestSingleInstanceLock()) { log('still locked, quitting'); app.quit(); } }, 1500);
}
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
let alive = false;
ipcMain.on('zenith:alive', () => {
  if (alive || process.env.ZENITH_TEST_FAIL === String(MODE)) return;
  alive = true; log('window is drawing in mode', MODE, MODES[MODE].name);
  try { fs.mkdirSync(path.dirname(MODE_FILE), { recursive: true }); fs.writeFileSync(MODE_FILE, JSON.stringify({ mode: MODE, name: MODES[MODE].name })); } catch (e) {}
});
function watchdog() {
  const ms = +process.env.ZENITH_WATCHDOG_MS || 10000;
  setTimeout(() => {
    if (alive) return;
    log('window did not draw within', ms, 'ms in mode', MODE, MODES[MODE].name);
    if (MODE + 1 < MODES.length) {
      log('trying next mode', MODE + 1);
      app.releaseSingleInstanceLock(); app.relaunch({ execPath: process.env.APPIMAGE || process.execPath, args: process.argv.slice(1).filter(x => !x.startsWith('--zenith-mode=')).concat(['--zenith-mode=' + (MODE + 1)]) }); app.exit(0);
    } else {
      try { require('electron').dialog.showErrorBox('Zenith could not draw its window', 'None of the rendering modes worked on this computer.\nPlease send this file to support: ' + LOG); } catch (e) {}
    }
  }, ms);
}
app.on('child-process-gone', (e, d) => log('child-process-gone', d.type, d.reason, 'exitCode', d.exitCode));
let win;
function create() {
  win = new BrowserWindow({
    width: 1280, height: 860, minWidth: 380, minHeight: 600, backgroundColor: '#06060b', title: 'Zenith Client', autoHideMenuBar: true,
    icon: path.join(__dirname, 'icon.png'),
    webPreferences: { contextIsolation: true, preload: path.join(__dirname, 'preload.js') }
  });
  const page = path.join(__dirname, 'launcher', 'index.html');
  log('loading', page, 'exists=' + fs.existsSync(page));
  watchdog();
  win.webContents.on('console-message', (e, lvl, msg, line, src) => { if (lvl >= 2) log('[page error]', msg, src + ':' + line); });
  win.webContents.on('did-finish-load', () => log('page loaded'));
  win.webContents.on('did-fail-load', (e, code, desc) => { log('did-fail-load', code, desc); win.loadURL('data:text/html,<body style="background:%2306060b;color:%23fff;font:16px sans-serif;padding:40px">Zenith could not load its interface (' + encodeURIComponent(desc) + '). See ' + encodeURIComponent(LOG) + '</body>'); });
  win.webContents.on('render-process-gone', (e, d) => log('render-process-gone', d.reason, 'exitCode', d.exitCode));
  win.webContents.on('unresponsive', () => log('page unresponsive'));
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
