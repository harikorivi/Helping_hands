'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { slotsFor, confirmed } = require('../lib/windows');
const { atomicWrite, readJson } = require('../lib/storage');
const { executeWindow } = require('../scheduled');
function fake(options = {}) {
  let time = 0, posts = 0, reads = 0;
  const state = {}, slot = { key: 'test', number: 1, start: 0, end: 180000 };
  const deps = { now: () => time, sleep: async ms => { time += ms; }, persist: () => {}, ensureFresh: async () => !options.authFailure,
    client: () => ({
      get: async url => {
        reads++;
        if (options.offline) throw new Error('offline');
        if (url.includes('check-is-claimable')) return { status: 200, data: { statusCode: 200, data: { isClaimable: !options.notEligible } } };
        return { status: 200, data: { statusCode: 200, data: { token: { lastClaimTime: posts ? 200 : 100 } } } };
      },
      post: async () => { posts++; if (options.ambiguous) throw new Error('timeout'); return { status: 200, data: { statusCode: options.rejected ? 400 : 200 } }; }
    }) };
  return { state, slot, deps, posts: () => posts, reads: () => reads };
}
test('six local schedule hours and exclusive endpoints', () => {
  for (const h of [1,5,9,13,17,21]) {
    const slots = slotsFor(new Date(2026,10,5,h,26)); assert.equal(slots.length,2);
    assert.equal(new Date(slots[0].end).getMinutes(),30);
    assert.equal(new Date(slots[1].end).getMinutes(),34);
  }
  for (const d of [new Date(2026,10,5,2,26),new Date(2026,10,5,1,25),new Date(2026,10,5,1,34)]) assert.deepEqual(slotsFor(d),[]);
});
test('each window succeeds independently; duplicate delivery does not submit again', async () => {
  const f = fake(); await executeWindow(f.slot,f.state,f.deps); assert.equal(f.state.result,'WINDOW_1_SUCCESS');
  await executeWindow(f.slot,f.state,f.deps); assert.equal(f.posts(),1);
  const second = {}; await executeWindow({...f.slot,number:2},second,f.deps);
  assert.equal(f.posts(),2); // Same baseline now unchanged -> fail conservative verification.
  assert.equal(second.result,'WINDOW_2_FAILED');
  const g = fake(); await executeWindow({...g.slot,number:2},g.state,g.deps); assert.equal(g.state.result,'WINDOW_2_SUCCESS');
});
test('ambiguous submission is never retried, including after restart', async () => {
  const f = fake({ambiguous:true}); await executeWindow(f.slot,f.state,f.deps);
  assert.equal(f.posts(),1); assert.equal(f.state.result,'WINDOW_1_FAILED');
  delete f.state.result; await executeWindow(f.slot,f.state,f.deps); assert.equal(f.posts(),1);
});
test('not eligible, network outage, authentication failure, rejection are distinct from success', async () => {
  for (const options of [{notEligible:true},{offline:true},{authFailure:true},{rejected:true}]) {
    const f=fake(options); await executeWindow(f.slot,f.state,f.deps);
    assert.equal(f.state.result, options.notEligible ? 'WINDOW_1_NOT_ELIGIBLE' : 'WINDOW_1_FAILED');
    assert.ok(f.reads()<=10); assert.ok(f.posts()<=1);
  }
});
test('missed slot makes no API request', async () => {
  const f=fake(); f.deps.now=()=>180001; await executeWindow(f.slot,f.state,f.deps);
  assert.equal(f.state.reason,'missed_window'); assert.equal(f.reads(),0);
});
test('HTTP/application acceptance without advancement cannot count as success', () => {
  assert.equal(confirmed({statusCode:200},100,100),false);
  assert.equal(confirmed({statusCode:400},100,200),false);
  assert.equal(confirmed({statusCode:200},undefined,200),false);
  assert.equal(confirmed({statusCode:200},100,200),true);
});
test('atomic private file replacement and corrupt ledger fail closed', () => {
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'interlink-test-'));
  try {
    const file=path.join(dir,'tokens.json'); atomicWrite(file,JSON.stringify({a:'first',r:'first'}));
    atomicWrite(file,JSON.stringify({a:'second',r:'second'})); assert.deepEqual(readJson(file),{a:'second',r:'second'});
    assert.equal(fs.statSync(file).mode & 0o777,0o600);
    fs.writeFileSync(file,'broken'); assert.throws(()=>readJson(file,{}));
  } finally { fs.rmSync(dir,{recursive:true}); }
});
