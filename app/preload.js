const { contextBridge, ipcRenderer } = require('electron');
let cb = null; ipcRenderer.on('zenith:event', (e, ev) => { if (cb) cb(ev); });
contextBridge.exposeInMainWorld('zenithNative', {
  isApp: true, platform: process.platform,
  launch: (opts, onEvent) => { cb = onEvent; return ipcRenderer.invoke('zenith:launch', opts); },
  openFolder: sub => ipcRenderer.invoke('zenith:folder', sub || '')
});
