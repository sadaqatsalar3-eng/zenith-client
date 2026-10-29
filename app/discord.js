'use strict';
// Minimal Discord Rich Presence over local IPC (no dependencies).
const net = require('net'), os = require('os'), path = require('path'), fs = require('fs');
function sockPaths() {
  if (process.platform === 'win32') return Array.from({ length: 10 }, (_, i) => '\\\\?\\pipe\\discord-ipc-' + i);
  const base = [process.env.XDG_RUNTIME_DIR, process.env.TMPDIR, process.env.TMP, process.env.TEMP, '/tmp'].filter(Boolean);
  const out = [];
  for (const b of base) for (let i = 0; i < 10; i++) {
    out.push(path.join(b, 'discord-ipc-' + i), path.join(b, 'app/com.discordapp.Discord', 'discord-ipc-' + i), path.join(b, 'snap.discord', 'discord-ipc-' + i));
  }
  return out;
}
function frame(op, obj) { const d = Buffer.from(JSON.stringify(obj)); const h = Buffer.alloc(8); h.writeInt32LE(op, 0); h.writeInt32LE(d.length, 4); return Buffer.concat([h, d]); }
class Presence {
  constructor(clientId) { this.id = clientId; this.sock = null; this.ready = false; this.pending = null; this.n = 0; this.stopped = false; }
  start() { if (!this.id || /^0+$/.test(this.id) || this.stopped) return; this._try(sockPaths(), 0); }
  _try(list, i) {
    if (this.stopped) return;
    if (i >= list.length) { this.timer = setTimeout(() => this._try(sockPaths(), 0), 15000); return; } // Discord not open yet, retry later
    const p = list[i]; if (process.platform !== 'win32' && !fs.existsSync(p)) return this._try(list, i + 1);
    const s = net.createConnection(p); let buf = Buffer.alloc(0), ok = false;
    s.on('connect', () => s.write(frame(0, { v: 1, client_id: this.id })));
    s.on('data', d => {
      buf = Buffer.concat([buf, d]);
      while (buf.length >= 8) {
        const len = buf.readInt32LE(4); if (buf.length < 8 + len) break;
        let m = {}; try { m = JSON.parse(buf.slice(8, 8 + len).toString()); } catch (e) {} buf = buf.slice(8 + len);
        if (m.evt === 'READY') { ok = true; this.sock = s; this.ready = true; if (this.pending) this._send(this.pending); }
      }
    });
    s.on('error', () => { if (!ok) this._try(list, i + 1); });
    s.on('close', () => { if (this.sock === s) { this.sock = null; this.ready = false; if (!this.stopped) this.timer = setTimeout(() => this._try(sockPaths(), 0), 10000); } });
  }
  _send(a) { if (!this.ready || !this.sock) return; try { this.sock.write(frame(1, { cmd: 'SET_ACTIVITY', args: { pid: process.pid, activity: a }, nonce: String(++this.n) })); } catch (e) {} }
  set(a) { this.pending = a; this._send(a); }
  stop() { this.stopped = true; clearTimeout(this.timer); try { this.sock && this.sock.destroy(); } catch (e) {} }
}
module.exports = { Presence, frame };
