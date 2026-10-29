'use strict';
// Zenith launch engine: real Minecraft download + launch. Node only, no dependencies.
const fs = require('fs'), fsp = fs.promises, path = require('path'), os = require('os');
const crypto = require('crypto'), cp = require('child_process'), https = require('https'), http = require('http');

const URLS = {
  manifest: 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',
  fabric: 'https://meta.fabricmc.net/v2/versions/loader',
  modrinth: 'https://api.modrinth.com/v2',
  resources: 'https://resources.download.minecraft.net/',
  adoptium: 'https://api.adoptium.net/v3/binary/latest/%V%/ga/%OS%/%ARCH%/jre/hotspot/normal/eclipse'
};
if (process.env.ZENITH_URLS) Object.assign(URLS, JSON.parse(process.env.ZENITH_URLS));
const UA = 'ZenithClient/1.0 (minecraft launcher)';
const OSN = process.platform === 'win32' ? 'windows' : process.platform === 'darwin' ? 'osx' : 'linux';
const ARCH = process.arch === 'arm64' ? 'arm64' : process.arch === 'ia32' ? 'x86' : 'x86_64';

function dataRoot() { return process.env.ZENITH_HOME || path.join(os.homedir(), '.zenith-client'); }

function get(url, { json = false, redirects = 8, headers = {} } = {}) {
  return new Promise((res, rej) => {
    const lib = url.startsWith('https') ? https : http;
    const rq = lib.get(url, { headers: Object.assign({ 'User-Agent': UA }, headers) }, r => {
      if (r.statusCode >= 300 && r.statusCode < 400 && r.headers.location && redirects > 0) {
        r.resume(); return res(get(new URL(r.headers.location, url).href, { json, redirects: redirects - 1, headers }));
      }
      if (r.statusCode !== 200) { r.resume(); return rej(new Error('HTTP ' + r.statusCode + ' for ' + url)); }
      const b = []; r.on('data', c => b.push(c)); r.on('end', () => {
        const buf = Buffer.concat(b);
        if (!json) return res(buf);
        try { res(JSON.parse(buf.toString('utf8'))); } catch (e) { rej(new Error('Bad JSON from ' + url)); }
      });
    });
    rq.on('error', rej); rq.setTimeout(30000, () => rq.destroy(new Error('Timeout ' + url)));
  });
}
const retry = async (fn, n = 3) => { let e; for (let i = 0; i < n; i++) { try { return await fn(); } catch (x) { e = x; await new Promise(r => setTimeout(r, 400 * (i + 1))); } } throw e; };
const sha1 = f => new Promise((res, rej) => { const h = crypto.createHash('sha1'); fs.createReadStream(f).on('data', d => h.update(d)).on('end', () => res(h.digest('hex'))).on('error', rej); });
const exists = f => fsp.access(f).then(() => true, () => false);

async function download(url, dest, hash) {
  if (await exists(dest)) { if (!hash || (await sha1(dest)) === hash) return false; }
  await fsp.mkdir(path.dirname(dest), { recursive: true });
  await retry(async () => {
    const buf = await get(url);
    if (hash) { const h = crypto.createHash('sha1').update(buf).digest('hex'); if (h !== hash) throw new Error('Checksum mismatch ' + url); }
    const tmp = dest + '.part'; await fsp.writeFile(tmp, buf); await fsp.rename(tmp, dest);
  });
  return true;
}
async function pool(items, n, fn) {
  let i = 0, done = 0; const w = async () => { while (i < items.length) { const k = i++; await fn(items[k], k); done++; } };
  await Promise.all(Array.from({ length: Math.min(n, items.length) }, w));
}

// ---------- rules ----------
function ruleOk(rules, features = {}) {
  if (!rules) return true; let ok = false;
  for (const r of rules) {
    let m = true;
    if (r.os) { if (r.os.name && r.os.name !== OSN) m = false; if (r.os.arch && r.os.arch !== (ARCH === 'x86_64' ? 'x86_64' : ARCH) && !(r.os.arch === 'x86' && ARCH === 'x86')) m = false; }
    if (r.features) for (const k in r.features) if (!!features[k] !== r.features[k]) m = false;
    if (m) ok = r.action === 'allow';
  }
  return ok;
}
function mavenPath(name) {
  const [g, a, v, c] = name.split(':'); const [ver, ext] = v.split('@'); return g.replace(/\./g, '/') + '/' + a + '/' + ver + '/' + a + '-' + ver + (c ? '-' + c : '') + '.' + (ext || 'jar');
}

// ---------- versions ----------
async function resolveVersion(root, mc, emit) {
  const vdir = path.join(root, 'versions', mc), vf = path.join(vdir, mc + '.json');
  if (await exists(vf)) return JSON.parse(await fsp.readFile(vf, 'utf8'));
  emit('Reading version list');
  const man = await retry(() => get(URLS.manifest, { json: true }));
  const e = man.versions.find(v => v.id === mc);
  if (!e) throw new Error('Minecraft ' + mc + ' is not in the official version list.');
  const vj = await retry(() => get(e.url, { json: true }));
  await fsp.mkdir(vdir, { recursive: true }); await fsp.writeFile(vf, JSON.stringify(vj)); return vj;
}
async function resolveFabric(root, mc, emit) {
  const f = path.join(root, 'versions', 'fabric-' + mc + '.json');
  if (await exists(f)) return JSON.parse(await fsp.readFile(f, 'utf8'));
  emit('Finding Fabric loader');
  const list = await retry(() => get(URLS.fabric + '/' + mc, { json: true }));
  if (!list.length) throw new Error('Fabric does not support Minecraft ' + mc + ' yet.');
  const l = (list.find(x => x.loader.stable) || list[0]).loader.version;
  const prof = await retry(() => get(URLS.fabric + '/' + mc + '/' + l + '/profile/json', { json: true }));
  await fsp.mkdir(path.dirname(f), { recursive: true }); await fsp.writeFile(f, JSON.stringify(prof)); return prof;
}
function merge(base, over) {
  const o = Object.assign({}, base, over);
  o.libraries = (over.libraries || []).concat(base.libraries || []);
  const a = base.arguments || {}, b = over.arguments || {};
  if (base.arguments || over.arguments) o.arguments = { game: (a.game || []).concat(b.game || []), jvm: (a.jvm || []).concat(b.jvm || []) };
  o.mainClass = over.mainClass || base.mainClass; return o;
}

// ---------- java ----------
function hasGui(bin) {
  if (process.platform !== 'linux') return true;
  try { const home = path.dirname(path.dirname(fs.realpathSync(bin))); return fs.existsSync(path.join(home, 'lib', 'libawt_xawt.so')); } catch (e) { return true; }
}
function javaMajor(bin) {
  try { const r = cp.spawnSync(bin, ['-version'], { encoding: 'utf8' }); const m = /version "(\d+)(?:\.(\d+))?/.exec((r.stderr || '') + (r.stdout || '')); if (!m) return 0; return m[1] === '1' ? +m[2] : +m[1]; } catch (e) { return 0; }
}
async function findJava(root, need, emit, progress) {
  const exe = process.platform === 'win32' ? 'java.exe' : 'java';
  const local = path.join(root, 'runtime', 'java' + need);
  const lb = await findBin(local, exe); if (lb && javaMajor(lb) >= need && hasGui(lb)) return lb;
  const cands = [process.env.JAVA_HOME && path.join(process.env.JAVA_HOME, 'bin', exe), 'java'];
  if (process.platform === 'linux') for (const d of ['/usr/lib/jvm']) { try { for (const n of fs.readdirSync(d)) cands.push(path.join(d, n, 'bin', exe)); } catch (e) {} }
  for (const c of cands.filter(Boolean)) { const m = javaMajor(c); if (m >= need && hasGui(c)) return c; }
  emit('Downloading Java ' + need);
  const url = URLS.adoptium.replace('%V%', need).replace('%OS%', OSN === 'osx' ? 'mac' : OSN).replace('%ARCH%', ARCH === 'x86_64' ? 'x64' : ARCH === 'arm64' ? 'aarch64' : 'x32');
  await fsp.mkdir(local, { recursive: true });
  const arch = path.join(local, process.platform === 'win32' ? 'jre.zip' : 'jre.tar.gz');
  const buf = await retry(() => get(url)); await fsp.writeFile(arch, buf); progress(.8);
  const r = process.platform === 'win32' ? cp.spawnSync('tar', ['-xf', arch, '-C', local]) : cp.spawnSync('tar', ['-xzf', arch, '-C', local]);
  if (r.status !== 0) throw new Error('Could not unpack Java runtime');
  await fsp.unlink(arch); const b = await findBin(local, exe); if (!b) throw new Error('Java runtime download failed');
  if (process.platform !== 'win32') await fsp.chmod(b, 0o755); return b;
}
async function findBin(dir, exe) {
  try { for (const e of await fsp.readdir(dir, { withFileTypes: true })) { const p = path.join(dir, e.name); if (e.isDirectory()) { const b = path.join(p, 'bin', exe); if (await exists(b)) return b; const s = await findBin(p, exe); if (s) return s; } } } catch (e) {} return null;
}

// ---------- mods ----------
const TYPE_DIR = { mods: 'mods', res: 'resourcepacks', shd: 'shaderpacks', packs: 'mods' };
async function installContent(root, gameDir, mc, loader, items, emit, progress) {
  const state = path.join(gameDir, 'zenith-installed.json');
  let prev = []; try { prev = JSON.parse(await fsp.readFile(state, 'utf8')); } catch (e) {}
  const want = new Map(), seenProj = new Set(), report = { ok: [], missing: [] };
  const ld = loader.toLowerCase();
  async function one(slugOrId, type, isDep) {
    if (seenProj.has(slugOrId)) return; seenProj.add(slugOrId);
    const q = new URLSearchParams({ game_versions: JSON.stringify([mc]) });
    if (type === 'mods') q.set('loaders', JSON.stringify([ld === 'vanilla' ? 'fabric' : ld]));
    let vers;
    try { vers = await retry(() => get(URLS.modrinth + '/project/' + encodeURIComponent(slugOrId) + '/version?' + q, { json: true })); } catch (e) { report.missing.push(slugOrId + ' (lookup failed)'); return; }
    const v = vers.find(x => x.version_type === 'release') || vers[0];
    if (!v) { report.missing.push(slugOrId + ' (no build for ' + mc + ')'); return; }
    const f = v.files.find(x => x.primary) || v.files[0];
    const sub = TYPE_DIR[type] || 'mods', dest = path.join(gameDir, sub, f.filename);
    await download(f.url, dest, f.hashes && f.hashes.sha1);
    want.set(path.join(sub, f.filename), 1); report.ok.push(slugOrId);
    if (type === 'mods') for (const d of v.dependencies || []) if (d.dependency_type === 'required' && d.project_id) await one(d.project_id, 'mods', true);
  }
  let n = 0;
  for (const it of items) { await one(it.slug, it.type, false); progress(++n / Math.max(items.length, 1)); }
  for (const old of prev) if (!want.has(old)) { try { await fsp.unlink(path.join(gameDir, old)); } catch (e) {} }
  await fsp.writeFile(state, JSON.stringify([...want.keys()])); return report;
}

// ---------- offline account ----------
function offlineUuid(name) {
  const h = crypto.createHash('md5').update('OfflinePlayer:' + name).digest(); h[6] = (h[6] & 0x0f) | 0x30; h[8] = (h[8] & 0x3f) | 0x80;
  const x = h.toString('hex'); return x.slice(0, 8) + '-' + x.slice(8, 12) + '-' + x.slice(12, 16) + '-' + x.slice(16, 20) + '-' + x.slice(20);
}

function expand(s, vars) { return s.replace(/\$\{(\w+)\}/g, (m, k) => (k in vars ? vars[k] : m)); }

// ---------- main entry ----------
// opts: {profile:{n,v,l,ram,jvm}, jvmArgs:'', account:{n}, content:[{slug,type}], server:'ip', onEvent(evt)}
async function launch(opts) {
  const emit = (step, frac, log) => opts.onEvent && opts.onEvent({ type: 'progress', step, frac, log });
  const root = dataRoot(), p = opts.profile, mc = p.v, loader = p.l;
  const safeName = String(p.n || 'Profile').replace(/[^\w.-]+/g, '_').replace(/^\.+/, '') || 'Profile';
  const gameDir = path.join(root, 'instances', safeName);
  await fsp.mkdir(gameDir, { recursive: true });
  const S = (i, name) => ({ log: m => emit(i, undefined, m), prog: f => emit(i, f), name });

  // 0 version data
  emit(0, 0, 'Checking Minecraft ' + mc);
  let vj = await resolveVersion(root, mc, m => emit(0, .3, m));
  if (loader === 'Fabric') { vj = merge(vj, await resolveFabric(root, mc, m => emit(0, .6, m))); delete vj.inheritsFrom; }
  else if (loader !== 'Vanilla') throw new Error(loader + ' is not installed automatically yet. Use a Fabric or Vanilla profile.');
  const base = vj.inheritsFrom ? await resolveVersion(root, vj.inheritsFrom, () => {}) : null;
  if (base) vj = merge(base, vj);
  emit(0, 1, 'Version data ready');

  // 1 java
  emit(1, 0, 'Looking for Java');
  const need = (vj.javaVersion && vj.javaVersion.majorVersion) || 17;
  const javaBin = await findJava(root, need, m => emit(1, .3, m), f => emit(1, f));
  emit(1, 1, 'Java ' + need + ' at ' + javaBin);

  // 2 client + libraries
  const clientJar = path.join(root, 'versions', mc, mc + '.jar');
  const dl = vj.downloads && vj.downloads.client;
  if (!dl) throw new Error('No client download for ' + mc);
  emit(2, 0, 'Game jar');
  await download(dl.url, clientJar, dl.sha1);
  const libs = [], natives = [], cpath = [], seenKey = new Set();
  for (const l of vj.libraries || []) {
    if (!ruleOk(l.rules)) continue;
    if (l.name) { const pr = l.name.split(':'), key = pr[0] + ':' + pr[1] + ':' + (pr[3] || '') + (l.natives ? ':n' : ''); if (seenKey.has(key)) continue; seenKey.add(key); }
    if (l.downloads && l.downloads.artifact) {
      const a = l.downloads.artifact; libs.push({ url: a.url, dest: path.join(root, 'libraries', a.path), sha1: a.sha1 }); cpath.push(path.join(root, 'libraries', a.path));
    } else if (!l.downloads && l.name) {
      const rel = mavenPath(l.name), base2 = (l.url || 'https://maven.fabricmc.net/'); libs.push({ url: base2.replace(/\/?$/, '/') + rel, dest: path.join(root, 'libraries', rel) }); cpath.push(path.join(root, 'libraries', rel));
    }
    if (l.natives && l.natives[OSN]) {
      const cls = l.natives[OSN].replace('${arch}', ARCH === 'x86' ? '32' : '64'), a = l.downloads && l.downloads.classifiers && l.downloads.classifiers[cls];
      if (a) { libs.push({ url: a.url, dest: path.join(root, 'libraries', a.path), sha1: a.sha1 }); natives.push({ dest: path.join(root, 'libraries', a.path), exclude: (l.extract && l.extract.exclude) || [] }); }
    }
  }
  const seenDest = new Set(); for (let i = libs.length - 1; i >= 0; i--) { if (seenDest.has(libs[i].dest)) libs.splice(i, 1); else seenDest.add(libs[i].dest); }
  let n = 0; await pool(libs, 8, async x => { await download(x.url, x.dest, x.sha1); emit(2, ++n / libs.length, path.basename(x.dest)); });
  const nativesDir = path.join(root, 'natives', mc + '-' + process.pid); await fsp.rm(nativesDir, { recursive: true, force: true }); await fsp.mkdir(nativesDir, { recursive: true });
  for (const nt of natives) cp.spawnSync('unzip', ['-qo', nt.dest, '-d', nativesDir, '-x'].concat(['META-INF/*']));
  cpath.push(clientJar);

  // 3 assets
  emit(3, 0, 'Asset index');
  const ai = vj.assetIndex, idxFile = path.join(root, 'assets', 'indexes', ai.id + '.json');
  await download(ai.url, idxFile, ai.sha1);
  const idx = JSON.parse(await fsp.readFile(idxFile, 'utf8')), objs = Object.values(idx.objects);
  const uniq = [...new Map(objs.map(o => [o.hash, o])).values()]; n = 0;
  await pool(uniq, 24, async o => { await download(URLS.resources + o.hash.slice(0, 2) + '/' + o.hash, path.join(root, 'assets', 'objects', o.hash.slice(0, 2), o.hash), o.hash); if (++n % 40 === 0 || n === uniq.length) emit(3, n / uniq.length, uniq.length + ' sounds and textures'); });
  let assetsDir = path.join(root, 'assets');
  if (idx.map_to_resources || idx.virtual) { // legacy versions
    const vdir = idx.map_to_resources ? path.join(gameDir, 'resources') : path.join(root, 'assets', 'virtual', ai.id);
    for (const k in idx.objects) { const h = idx.objects[k].hash, d = path.join(vdir, k); if (!(await exists(d))) { await fsp.mkdir(path.dirname(d), { recursive: true }); await fsp.copyFile(path.join(root, 'assets', 'objects', h.slice(0, 2), h), d); } }
    assetsDir = idx.map_to_resources ? path.join(gameDir, 'resources') : vdir;
  }

  // 4 content
  const items = opts.content || [];
  emit(4, 0, items.length ? 'Fetching ' + items.length + ' from Modrinth' : 'Nothing extra to install');
  const rep = items.length ? await installContent(root, gameDir, mc, loader, items, m => emit(4, undefined, m), f => emit(4, f)) : { ok: [], missing: [] };
  emit(4, 1, rep.ok.length + ' installed' + (rep.missing.length ? ', skipped: ' + rep.missing.join(', ') : ''));

  // 5 start
  emit(5, 0, 'Building launch command');
  const acc = opts.account || { n: 'Player' }, name = (acc.n || 'Player').replace(/[^\w]/g, '').slice(0, 16) || 'Player';
  const vars = {
    auth_player_name: name, version_name: vj.id || mc, game_directory: gameDir, assets_root: assetsDir, assets_index_name: ai.id,
    auth_uuid: acc.uuid || offlineUuid(name), auth_access_token: acc.token || '0', clientid: '', auth_xuid: '', user_type: acc.token ? 'msa' : 'legacy', user_properties: '{}',
    version_type: 'Zenith', natives_directory: nativesDir, launcher_name: 'Zenith', launcher_version: '1.0', classpath: cpath.join(path.delimiter),
    classpath_separator: path.delimiter, library_directory: path.join(root, 'libraries'), game_assets: assetsDir, resolution_width: '1280', resolution_height: '720'
  };
  const feat = { has_custom_resolution: false, is_demo_user: false, has_quick_plays_support: false, is_quick_play_multiplayer: false };
  const flat = (arr) => { const out = []; for (const a of arr || []) { if (typeof a === 'string') out.push(a); else if (ruleOk(a.rules, feat)) out.push(...[].concat(a.value)); } return out; };
  let jvm, game;
  if (vj.arguments) { jvm = flat(vj.arguments.jvm); game = flat(vj.arguments.game); }
  else { jvm = ['-Djava.library.path=${natives_directory}', '-cp', '${classpath}']; game = (vj.minecraftArguments || '').split(' ').filter(Boolean); }
  jvm = jvm.map(a => expand(a, vars)); game = game.map(a => expand(a, vars));
  const user = (opts.jvmArgs || '').split(/\s+/).filter(Boolean);
  const args = ['-Xms512M', '-Xmx' + (p.ram || 4) + 'G'].concat(process.platform === 'darwin' ? ['-XstartOnFirstThread'] : [], user, jvm, [vj.mainClass], game);
  if (opts.server) { const [h, pt] = opts.server.split(':'); const mm = /^1\.(\d+)/.exec(mc), minor = mm ? +mm[1] : 99; if (minor >= 20) args.push('--quickPlayMultiplayer', opts.server); else args.push('--server', h, '--port', pt || '25565'); }

  emit(5, .5, 'Starting Java');
  await fsp.mkdir(path.join(gameDir, 'logs'), { recursive: true });
  const logFile = path.join(gameDir, 'logs', 'zenith-launch.log'), fd = fs.openSync(logFile, 'w');
  const child = cp.spawn(javaBin, args, { cwd: gameDir, stdio: ['ignore', fd, fd], detached: true });
  child.unref(); fs.closeSync(fd);
  let started = false, finished = false;
  const readTail = () => { try { const st = fs.statSync(logFile), len = Math.min(st.size, 20000), b2 = Buffer.alloc(len), h = fs.openSync(logFile, 'r'); fs.readSync(h, b2, 0, len, st.size - len); fs.closeSync(h); return b2.toString('utf8').split('\n').filter(x => x.trim()); } catch (e) { return []; } };
  const poll = setInterval(() => {
    if (started || finished) return;
    if (readTail().some(l => /Setting user|Backend library|OpenAL initialized|Sound engine started|LWJGL version/.test(l))) { started = true; emit(5, 1, 'Minecraft window is opening'); opts.onEvent && opts.onEvent({ type: 'started' }); }
  }, 500);
  const t = setTimeout(() => { if (!started && !finished) { started = true; opts.onEvent && opts.onEvent({ type: 'started' }); } }, 40000);
  const done = (code, extra) => { if (finished) return; finished = true; clearInterval(poll); clearTimeout(t); opts.onEvent && opts.onEvent({ type: 'exit', code, started, tail: (extra || []).concat(readTail().slice(-15)) }); fsp.rm(nativesDir, { recursive: true, force: true }).catch(() => {}); };
  child.on('error', e => done(-1, [String(e)]));
  child.on('exit', code => done(code));
  return { pid: child.pid, gameDir, java: javaBin, report: rep, args, logFile };
}
module.exports = { launch, offlineUuid, ruleOk, mavenPath, dataRoot, URLS };
