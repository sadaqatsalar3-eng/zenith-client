const { app, BrowserWindow, shell, ipcMain } = require('electron');
const path = require('path'), engine = require('./engine');
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
  running = true; const send = ev => { if (!e.sender.isDestroyed()) e.sender.send('zenith:event', ev); if (ev.type === 'exit') running = false; };
  try { const r = await engine.launch(Object.assign({}, opts, { onEvent: send })); return { ok: true, pid: r.pid, gameDir: r.gameDir }; }
  catch (err) { running = false; return { ok: false, error: String(err && err.message || err) }; }
});
ipcMain.handle('zenith:folder', (e, sub) => shell.openPath(path.join(engine.dataRoot(), sub || '')));
app.whenReady().then(create);
app.on('window-all-closed', () => app.quit());
