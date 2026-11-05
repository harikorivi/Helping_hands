const axios = require('axios');
const { atomicWrite, readJson } = require('./lib/storage');
process.umask(0o077);
const CREDENTIALS = require('path').join(__dirname, 'credentials.json');
const fs = require('fs');
const path = require('path');
const readline = require('readline');
const { HttpsProxyAgent } = require('https-proxy-agent');
const { SocksProxyAgent } = require('socks-proxy-agent');
const https = require('https');
const crypto = require('crypto');

const API_BASE_URL        = 'https://prod.interlinklabs.ai/api/v1';
const TOKEN_FILE_PATH     = path.join(__dirname, 'token.txt');
const REFRESH_TOKEN_FILE  = path.join(__dirname, 'refresh_token.txt');
const DEVICE_FILE_PATH    = path.join(__dirname, 'device.txt');
const PROXIES_FILE_PATH   = path.join(__dirname, 'proxies.txt');
const APP_VERSION         = '5.0.0';
const REFRESH_BUFFER_MS   = 5 * 60 * 1000;

const logger = Object.fromEntries(['info','warn','error','success','loading','step'].map(level => [level,
  message => console.log(JSON.stringify({ time: new Date().toISOString(), level, event: 'AUTH', message }))]));

let hiddenInput = false;
const output = new (require('node:stream').Writable)({ write(chunk, encoding, callback) {
  if (!hiddenInput) process.stdout.write(chunk, encoding);
  callback();
}});
const rl = require.main === module ? readline.createInterface({ input: process.stdin, output, terminal: process.stdin.isTTY }) : null;
const promptInput = (q, hidden = false) => new Promise(resolve => {
  process.stdout.write(q); hiddenInput = hidden;
  rl.question('', answer => { hiddenInput = false; if (hidden) process.stdout.write('\n'); resolve(answer.trim()); });
});

const save = (file, data) => atomicWrite(file, data);
function saveTokens(accessToken, refreshToken) {
  if (typeof accessToken !== 'string' || !accessToken) throw new Error('Missing access token');
  atomicWrite(CREDENTIALS, JSON.stringify({ accessToken, refreshToken }));
}
const read = (file, fallback = null) => { try { return fs.readFileSync(file, 'utf8').trim(); } catch { return fallback; } };

const saveDevice = d => { save(DEVICE_FILE_PATH, d); logger.info('Device ID saved.'); };
function readCredentials() {
  const stored = readJson(CREDENTIALS, null);
  // Once migrated, never resurrect legacy tokens from an earlier session.
  return stored === null ? { accessToken: read(TOKEN_FILE_PATH), refreshToken: read(REFRESH_TOKEN_FILE) } : stored;
}
const readToken = () => readCredentials().accessToken;
const readRefreshToken = () => readCredentials().refreshToken;
const readDevice       = ()  => read(DEVICE_FILE_PATH);

function readProxies() {
  try {
    if (!fs.existsSync(PROXIES_FILE_PATH)) return [];
    return fs.readFileSync(PROXIES_FILE_PATH, 'utf8')
      .split('\n').map(l => l.trim()).filter(l => l && !l.startsWith('#'));
  } catch { return []; }
}
const getRandomProxy  = (list) => list.length ? list[Math.floor(Math.random() * list.length)] : null;
const makeProxyAgent  = (url)  => url.startsWith('socks') ? new SocksProxyAgent(url) : new HttpsProxyAgent(url);

function jwtPayload(token) {
  try { return JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString('utf8')); }
  catch { return null; }
}
function tokenExpiringSoon(token) {
  const p = jwtPayload(token);
  if (!Number.isFinite(p?.exp)) return true;
  return Date.now() >= p.exp * 1000 - REFRESH_BUFFER_MS;
}

function makeClient(token = null, proxy = null, deviceId = null) {
  const headers = {
    'User-Agent':      'okhttp/4.12.0',
    'Accept-Encoding': 'gzip',
    'Content-Type':    'application/json',
    'version':         APP_VERSION,
    'x-platform':      'android',
  };
  if (token)    headers['authorization'] = `Bearer ${token}`;
  if (deviceId) Object.assign(headers, {
    'x-unique-id':   deviceId,
    'x-model':       'Redmi Note 8 Pro',
    'x-brand':       'XiaoMi',
    'x-system-name': 'Android',
    'x-device-id':   deviceId,
    'x-bundle-id':   'org.ai.interlinklabs.interlinkId',
  });

  const agent = (() => {
    if (proxy) {
      try { return makeProxyAgent(proxy); } catch(e) { logger.error('Invalid proxy configuration'); }
    }
    return new https.Agent({ rejectUnauthorized: true });
  })();

  const inst = axios.create({ baseURL: API_BASE_URL, headers, timeout: 10000, httpsAgent: agent, proxy: false });

  inst.interceptors.request.use(conf => {
    conf.headers['x-date'] = Date.now().toString();
    if (conf.method === 'post' && conf.data) {
      const body = typeof conf.data === 'object' ? JSON.stringify(conf.data) : String(conf.data);
      conf.headers['x-content-hash'] = crypto.createHash('sha256').update(body, 'utf8').digest('base64');
    }
    return conf;
  });

  return inst;
}

function createSession(initAccess, initRefresh, deviceId, proxies, deps = {}) {
  const persist = deps.persist || saveTokens;
  const buildClient = deps.makeClient || makeClient;
  let access   = initAccess;
  let refresh  = initRefresh;
  let refreshing = null;
  let failed   = false;

  async function doRefresh() {
    if (!refresh) { logger.error('No refresh token.'); failed = true; return false; }
    const proxy  = getRandomProxy(proxies);
    try {
      const client = buildClient(access, proxy, deviceId);
      logger.loading('Refreshing access token via POST /auth/token ...');
      const res = await client.post('/auth/token', { refreshToken: refresh });
      if (res.data.statusCode === 200) {
        const newAccess  = res.data.data?.accessToken  || res.data.data?.jwtToken;
        const newRefresh = res.data.data?.refreshToken;
        if (typeof newAccess === 'string' && Number.isFinite(jwtPayload(newAccess)?.exp) && jwtPayload(newAccess).exp * 1000 > Date.now()) {
          const nextRefresh = typeof newRefresh === 'string' && newRefresh ? newRefresh : refresh;
          persist(newAccess, nextRefresh);
          access = newAccess;
          refresh = nextRefresh;
          logger.success('Token refreshed successfully.');
          failed = false;
          return true;
        }
      }
      logger.error(`Refresh failed: [response omitted]`);
    } catch(e) {
      logger.error(`Refresh error: ${e.response?.status || e.code || "request_failed"}`);
    }
    failed = true;
    return false;
  }

  async function ensureFresh() {
    if (failed) return false;
    if (!tokenExpiringSoon(access)) return true;
    if (!refreshing) refreshing = doRefresh().finally(() => { refreshing = null; });
    return refreshing;
  }

  function client(proxy = null) { return buildClient(access, proxy, deviceId); }
  function getAccess()  { return access; }
  function getRefresh() { return refresh; }
  function isOk()       { return !failed; }
  function update(newAccess, newRefresh) {
    access = newAccess; if (newRefresh) refresh = newRefresh;
    failed = false; persist(access, refresh);
  }

  return { ensureFresh, client, getAccess, getRefresh, isOk, update };
}

async function checkLoginId(client, loginId, deviceId) {
  try {
    const r = await client.get(`/auth/loginId-exist-check/${encodeURIComponent(loginId)}`, { params: { deviceId } });
    if (r.data.statusCode === 200) { logger.success('Login ID exists.'); return true; }
    logger.error(`Login ID check failed: [response omitted]`); return false;
  } catch(e) { logger.error(`Login ID error: ${e.response?.status || e.code || "request_failed"}`); return false; }
}

async function checkPasscode(client, loginId, passcode, deviceId) {
  try {
    const r = await client.post('/auth/check-passcode?v=2', { loginId, passcode, deviceId });
    if (r.data.statusCode === 200) {
      logger.success('Passcode verified.');
      const email = r.data.data?.email || r.data.data?.verificationInfo?.[0]?.gmail || null;
      return { ok: true, email };
    }
    logger.error(`Passcode failed: [response omitted]`); return { ok: false, email: null };
  } catch(e) { logger.error(`Passcode error: ${e.response?.status || e.code || "request_failed"}`); return { ok: false, email: null }; }
}

async function sendOtp(client, loginId, passcode, email, deviceId) {
  try {
    const r = await client.post('/auth/send-otp-email-verify-login', { loginId, passcode, email, deviceId });
    if (r.data.statusCode === 200) logger.success('OTP sent.');
    else logger.error(`OTP send failed: [response omitted]`);
  } catch(e) { logger.error(`OTP send error: ${e.response?.status || e.code || "request_failed"}`); }
}

async function verifyOtp(client, loginId, otp, deviceId) {
  try {
    const r = await client.post('/auth/check-otp-email-verify-login?v=2', { loginId, otp, deviceId });
    if (r.data.statusCode === 200) {
      logger.success('OTP verified.');
      return { accessToken: r.data.data.accessToken, refreshToken: r.data.data.refreshToken };
    }
    logger.error(`OTP verify failed: [response omitted]`); return null;
  } catch(e) { logger.error(`OTP verify error: ${e.response?.status || e.code || "request_failed"}`); return null; }
}

async function login(proxies, deviceId) {
  const loginId  = await promptInput('Enter your login ID: ');
  const passcode = await promptInput('Enter your passcode: ', true);
  const proxy    = getRandomProxy(proxies);
  const client   = makeClient(null, proxy, deviceId);

  if (!await checkLoginId(client, loginId, deviceId)) return null;
  const { ok, email: detectedEmail } = await checkPasscode(client, loginId, passcode, deviceId);
  if (!ok) return null;

  const email = detectedEmail || await promptInput('Enter your email for OTP: ');


  await sendOtp(client, loginId, passcode, email, deviceId);
  const otp    = await promptInput('Enter OTP: ', true);
  const tokens = await verifyOtp(client, loginId, otp, deviceId);
  if (!tokens) return null;

  saveTokens(tokens.accessToken, tokens.refreshToken);
  return tokens;
}

module.exports = { createSession, readToken, readRefreshToken, readDevice, tokenExpiringSoon, makeClient, saveTokens };
if (require.main === module) {
  if (!process.argv.includes('--login') || !process.stdin.isTTY) {
    rl.close();
    console.error('Manual authentication: node index.js --login. Scheduled execution: bash run-scheduled.sh.');
    process.exitCode = 1;
  } else {
    (async () => {
      let device = readDevice();
      if (!device) { device = crypto.randomBytes(8).toString('hex'); saveDevice(device); }
      const tokens = await login(process.env.INTERLINK_USE_PROXY === '1' ? readProxies() : [], device);
      if (!tokens) process.exitCode = 1;
    })().catch(() => { logger.error('Authentication or secure storage failed.'); process.exitCode = 1; })
      .finally(() => rl.close());
  }
}
