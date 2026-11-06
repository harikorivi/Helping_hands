'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { setTimeout: sleep } = require('node:timers/promises');
const { createClock, boundedSleep } = require('./lib/execution-clock');
const { atomicWrite, readJson } = require('./lib/storage');
const { failureDetails } = require('./lib/diagnostics');
const { parseEligibility, responseShape } = require('./lib/eligibility');
const { executeWindow } = require('./scheduled');
const log = (event, extra = {}) => console.log(JSON.stringify({ time: new Date().toISOString(), event, ...extra }));

async function diagnose(session, emit = log) {
  if (!await session.ensureFresh()) { emit('TEST_AUTH_FAILED'); return 11; }
  let stage = 'account';
  try {
    const api = session.client();
    const account = await api.get('/auth/current-user-full?include=userInfo,token,isClaimable');
    if (account.data?.statusCode !== 200 || !account.data?.data) { emit('TEST_INVALID_RESPONSE', { stage }); return 13; }
    stage = 'eligibility';
    const response = await api.get('/token/check-is-claimable');
    let eligible;
    try { eligible = parseEligibility(response); }
    catch { emit('TEST_INVALID_RESPONSE', { stage, ...responseShape(response) }); return 13; }
    emit('TEST_SUCCESS', { eligible, claimed: false }); return 0;
  } catch (error) { emit('TEST_REQUEST_FAILED', failureDetails(error, stage)); return 12; }
}

function chooseManualSlot(now) {
  const date = new Date(now), minute = date.getMinutes();
  const scheduledHour = [1,5,9,13,17,21].includes(date.getHours());
  // Reserve the two minutes before each alarm and both automatic windows.
  if (scheduledHour && minute >= 24 && minute < 34) return null;
  return { key: 'manual', number: 1, start: now, end: now + 90000 };
}
async function claimOnce(session, now = Date.now, options = {}) {
  const slot = chooseManualSlot(now());
  if (!slot) { log('MANUAL_DEFER_TO_SCHEDULE'); return 20; }
  if (!await session.ensureFresh()) return 11;
  const runtime = options.runtime || path.join(__dirname,'.runtime');
  const scheduled = slot.key !== 'manual';
  const file = path.join(runtime, scheduled ? 'state.json' : 'manual.json');
  const ledger = readJson(file, {});
  if (!scheduled) {
    if (ledger.manual?.submitted && ledger.manual?.result !== 'WINDOW_1_SUCCESS') {
      log('MANUAL_PREVIOUS_OUTCOME_UNKNOWN'); return 21;
    }
    if (now() - (ledger.manual?.started || 0) < 60000) return 22;
    ledger.manual = { started: now() };
  }
  const state = ledger[slot.key] ||= { started: now() };
  if (scheduled && (state.result || state.submitted)) return 23;
  const persist = () => atomicWrite(file, JSON.stringify(ledger));
  persist();
  await executeWindow({ ...slot, manual: true }, state, {
    now, sleep: options.sleep || sleep, persist, ensureFresh: () => session.ensureFresh(),
    client: () => {
      const api = session.client();
      api.interceptors.request.use(config => {
        if (now() >= slot.end) throw new Error('deadline');
        config.timeout = Math.max(1, Math.min(10000, slot.end - now())); return config;
      }); return api;
    }
  });
  if (state.result === `WINDOW_${slot.number}_SUCCESS`) return 0;
  if (state.result === `WINDOW_${slot.number}_NOT_ELIGIBLE`) return 10;
  return state.submitted ? 21 : 12;
}
async function main(mode) {
  process.umask(0o077);
  if (!['test','claim'].includes(mode)) return 2;
  const start = new Date();
  if ([1,5,9,13,17,21].includes(start.getHours()) && start.getMinutes() >= 24 && start.getMinutes() < 34) return 20;
  fs.mkdirSync(path.join(__dirname,'.runtime'), { recursive: true, mode: 0o700 });
  const auth = require('./index');
  if (!auth.readDevice()) return 11;
  for (const name of ['token.txt','refresh_token.txt','device.txt','credentials.json']) {
    const file = path.join(__dirname,name); if (fs.existsSync(file)) fs.chmodSync(file,0o600);
  }
  const controller = new AbortController();
  const clock = createClock();
  const end = clock.started + (mode === 'test' ? 45000 : 95000);
  const deadline = setInterval(() => {
    try { if (clock.now() < end) return; } catch { log('CLOCK_OR_TIMEZONE_CHANGED'); }
    controller.abort(); log('ON_DEMAND_TIMEOUT'); process.exit(124);
  }, 1000);
  for (const signal of ['SIGTERM','SIGINT']) process.once(signal, () => controller.abort());
  try {
    const session = auth.createSession(auth.readToken(),auth.readRefreshToken(),auth.readDevice(),[], {
      makeClient: (...args) => {
        const api = auth.makeClient(...args); api.defaults.signal = controller.signal;
        api.interceptors.request.use(config => {
          const remaining = end - clock.now();
          if (remaining <= 0) throw new Error('deadline');
          config.timeout = Math.max(1, Math.min(10000, remaining)); return config;
        }); return api;
      }
    });
    return mode === 'test' ? await diagnose(session) : await claimOnce(session, clock.now, { sleep: ms => boundedSleep(ms, clock.now, controller.signal) });
  } finally { clearInterval(deadline); controller.abort(); }
}
module.exports = { diagnose, chooseManualSlot, claimOnce };
if (require.main === module) main(process.argv[2]).then(code => { process.exitCode = code; }).catch(() => { log('ON_DEMAND_FAILED'); process.exitCode = 12; });
