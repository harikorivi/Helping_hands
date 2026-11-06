'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { createClock, boundedSleep } = require('./lib/execution-clock');
const { atomicWrite, readJson } = require('./lib/storage');
const { slotsFor, confirmed } = require('./lib/windows');
const { failureDetails } = require('./lib/diagnostics');
const { parseEligibility, responseShape } = require('./lib/eligibility');
const root = path.join(__dirname, '.runtime');
const log = (event, extra = {}) => console.log(JSON.stringify({ time: new Date().toISOString(), event, ...extra }));

async function executeWindow(slot, state, deps) {
  const { now, sleep, client, persist, ensureFresh } = deps;
  const finish = (suffix, reason) => {
    const result = `WINDOW_${slot.number}_${suffix}`;
    Object.assign(state, { result, reason }); persist(); log(result, { key: slot.key, reason });
  };
  if (state.result || state.submitted) {
    if (!state.result) finish('FAILED', 'previous_submission_outcome_unknown');
    return;
  }
  log('WINDOW_STARTED', { key: slot.key, start: new Date(slot.start).toISOString(), end: new Date(slot.end).toISOString() });
  if (now() < slot.start) await sleep(slot.start - now());
  if (now() >= slot.end) { finish('FAILED', 'missed_window'); return; }
  let notEligible = false;
  for (let attempt = 0; attempt < (slot.manual ? 1 : 6) && now() + 25000 < slot.end; attempt++) {
    if (!await ensureFresh()) { finish('FAILED', 'authentication_required'); return; }
    let stage = 'eligibility';
    try {
      const api = client();
      const check = await api.get('/token/check-is-claimable');
      let eligible;
      try { eligible = parseEligibility(check); }
      catch (error) { log('ELIGIBILITY_INVALID_RESPONSE', responseShape(check)); throw error; }
      if (!eligible) {
        notEligible = true;
        // nextFrame units are undocumented: do not interpret or override them.
        if (slot.manual) break;
        await sleep(Math.min(30000, Math.max(0, slot.end - now()))); continue;
      }
      notEligible = false;
      stage = 'account_baseline';
      const before = await api.get('/auth/current-user-full?include=userInfo,token,isClaimable');
      if (before.data?.statusCode !== 200 || before.data?.data?.token?.lastClaimTime == null) {
        throw new Error('missing_verification_baseline');
      }
      const last = before.data.data.token.lastClaimTime;
      stage = 'pre_claim_sequence';
      await api.get('/token/get-random-ads-mining-new', { params: { totalHhp: 1, lastTimeClaim: last } });
      await sleep(15000);
      if (now() + 10000 >= slot.end) break;
      // Durable intent BEFORE the non-idempotent POST. Never retry an ambiguous submission.
      state.submitted = true; persist();
      stage = 'claim_submission';
      const response = await api.post('/token/claim-airdrop', '');
      stage = 'claim_verification';
      const after = await api.get('/auth/current-user-full?include=userInfo,token,isClaimable');
      if (after.data?.statusCode !== 200 || !confirmed(response.data, last, after.data?.data?.token?.lastClaimTime)) {
        finish('FAILED', 'claim_unconfirmed'); return;
      }
      finish('SUCCESS', 'accepted_and_last_claim_advanced');
      try {
        await api.get('/token/get-random-ads-mining-new', { params: { totalHhp: 1, lastTimeClaim: after.data.data.token.lastClaimTime } });
      } catch { log('POST_CLAIM_FOLLOWUP_FAILED', { key: slot.key }); }
      return;
    } catch (e) {
      if (e.code === 'CLOCK_CHANGED') throw e;
      log('REQUEST_FAILED', { key: slot.key, ...failureDetails(e, stage) });
      if (state.submitted) { finish('FAILED', 'submission_outcome_unknown'); return; }
      const status = e.response?.status;
      if (status === 401 || status === 403 || status === 429) {
        finish('FAILED', status === 429 ? 'rate_limited' : 'authentication_rejected'); return;
      }
      log('READ_ATTEMPT_FAILED', { key: slot.key, attempt: attempt + 1 });
      notEligible = false;
      if (slot.manual) break;
      await sleep(Math.min(30000, Math.max(0, slot.end - now())));
    }
  }
  finish(notEligible ? 'NOT_ELIGIBLE' : 'FAILED', notEligible ? 'server_not_eligible' : 'deadline_or_read_failure');
}

async function main() {
  process.umask(0o077);
  const actual = Date.now();
  const argument = process.argv.indexOf('--scheduled-at');
  const expected = argument < 0 ? actual : Number(process.argv[argument + 1]);
  log('SCHEDULED_RUN_STARTED', { expected: Number.isFinite(expected) ? new Date(expected).toISOString() : null,
    actual: new Date(actual).toISOString(), delayMs: actual - expected });
  const slots = Number.isFinite(expected) ? slotsFor(expected) : [];
  if (!slots.length || actual < slots[0].start || actual >= slots[1].end) {
    log('SCHEDULED_RUN_SKIPPED', { reason: 'outside_original_window' }); process.exitCode = 20; return;
  }
  fs.mkdirSync(root, { recursive: true, mode: 0o700 });
  const file = path.join(root, 'state.json');
  const states = readJson(file, {}); // Corruption is fatal; never reset the claim ledger silently.
  const persist = () => atomicWrite(file, JSON.stringify(states));
  const activeKeys = new Set(slots.map(slot => slot.key));
  for (const [key, state] of Object.entries(states)) {
    if (!activeKeys.has(key) && !state.result) {
      const number = key.endsWith('-2') ? 2 : 1;
      Object.assign(state, { result: `WINDOW_${number}_FAILED`, reason: state.submitted ? 'previous_submission_outcome_unknown' : 'previous_execution_interrupted' });
      log(state.result, { key, reason: state.reason });
    }
  }
  for (const slot of slots) states[slot.key] ||= { started: new Date().toISOString() };
  persist();
  const controller = new AbortController();
  const clock = createClock();
  const now = clock.now;
  for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => controller.abort());
  const watchdog = setInterval(() => {
    let reason = 'execution_deadline';
    try { if (now() < slots[1].end) return; }
    catch { reason = 'clock_or_timezone_changed'; }
    controller.abort();
    for (const slot of slots) {
      const state = states[slot.key] ||= {};
      if (!state.result) {
        Object.assign(state, { result: `WINDOW_${slot.number}_FAILED`, reason });
        log(state.result, { key: slot.key, reason: state.reason });
      }
    }
    try { persist(); } finally { process.exit(2); }
  }, 1000);
  const sleep = ms => boundedSleep(ms, now, controller.signal);
  try {
    const auth = require('./index');
    for (const name of ['token.txt', 'refresh_token.txt', 'device.txt', 'credentials.json']) {
      const p = path.join(__dirname, name); if (fs.existsSync(p)) fs.chmodSync(p, 0o600);
    }
    const session = auth.createSession(auth.readToken(), auth.readRefreshToken(), auth.readDevice(), [], {
      makeClient: (...args) => {
        const client = auth.makeClient(...args);
        client.defaults.signal = controller.signal;
        client.interceptors.request.use(config => {
          const remaining = slots[1].end - now();
          if (remaining <= 0) throw Object.assign(new Error('deadline'), { code: 'EXECUTION_DEADLINE' });
          config.timeout = Math.max(1, Math.min(10000, remaining)); return config;
        });
        return client;
      }
    });
    if (!auth.readDevice() || !await session.ensureFresh()) {
      for (const slot of slots) {
        const state = states[slot.key] ||= {};
        if (!state.result) {
          Object.assign(state, { result: `WINDOW_${slot.number}_FAILED`, reason: 'startup_authentication_required' });
          log(state.result, { key: slot.key, reason: state.reason });
        }
      }
      persist(); process.exitCode = 2; return;
    }
    for (const slot of slots) {
      const state = states[slot.key] ||= {};
      const deps = { now, sleep, persist,
        ensureFresh: async () => !!auth.readDevice() && now() < slot.end && await session.ensureFresh(),
        client: () => {
          const c = session.client(); c.defaults.signal = controller.signal;
          c.interceptors.request.use(config => {
            if (now() >= slot.end) throw new Error('window_closed');
            config.timeout = Math.max(1, Math.min(10000, slot.end - now()));
            return config;
          });
          return c;
        }
      };
      try { await executeWindow(slot, state, deps); }
      catch (error) { Object.assign(state, { result: `WINDOW_${slot.number}_FAILED`, reason: error.code === 'CLOCK_CHANGED' ? 'clock_or_timezone_changed' : 'interrupted' }); persist(); log(state.result, { key: slot.key, reason: state.reason }); }
    }
    if (slots.some(s => states[s.key].result !== `WINDOW_${s.number}_SUCCESS`)) process.exitCode = 2;
  } finally { clearInterval(watchdog); controller.abort(); }
}
module.exports = { executeWindow };
if (require.main === module) main().catch(() => { log('FATAL', { reason: 'local_state_or_runtime_failure' }); process.exitCode = 1; });
