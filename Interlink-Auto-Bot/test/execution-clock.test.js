const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createClock, boundedSleep } = require('../lib/execution-clock');
const { executeWindow } = require('../scheduled');
function fixture() {
  let wall = new Date(2026,10,6,1,27).getTime(), elapsed = 100000, zone = 'Asia/Kolkata';
  const clock = createClock({ wall: () => wall, elapsed: () => elapsed, zone: () => zone });
  return { clock, advance: ms => { wall += ms; elapsed += ms; }, shift: ms => { wall += ms; }, zone: () => {zone='UTC';} };
}
test('suspension advances BOOTTIME with wall time and does not expire a future window', async () => {
  const f=fixture(); f.advance(213000); // 01:30:33, reproducing the reported symptom.
  const start=new Date(2026,10,6,1,31).getTime(); const state={}; let checked=false;
  await executeWindow({key:'second',number:2,start,end:start+180000,manual:true},state,{
    now:f.clock.now, sleep:async ms=>f.advance(ms), persist:()=>{}, ensureFresh:async()=>true,
    client:()=>({get:async()=>{checked=true;return {status:200,data:{data:{isClaimable:false}}};}})
  });
  assert.equal(checked,true); assert.equal(state.result,'WINDOW_2_NOT_ELIGIBLE');
});
test('suspend past actual deadline makes no network request', async () => {
  const f=fixture();const start=f.clock.now();f.advance(4*60*60*1000);const state={};
  await executeWindow({key:'late',number:2,start,end:start+420000},state,{
    now:f.clock.now,persist:()=>{},sleep:async()=>{},ensureFresh:()=>{throw Error('must not authenticate');},client:()=>{throw Error('must not request');}
  });
  assert.equal(state.reason,'missed_window');
});
test('real wall-clock jumps and timezone changes report an explicit clock error', () => {
  for (const change of [f=>f.shift(60000),f=>f.shift(-60000),f=>f.zone()]) {
    const f=fixture();change(f);assert.throws(()=>f.clock.now(),{code:'CLOCK_CHANGED'});
  }
});
test('bounded sleep finishes on first callback after a long suspension', async () => {
  const f=fixture();let waits=0;
  await boundedSleep(30000,f.clock.now,undefined,async()=>{waits++;f.advance(3600000);});
  assert.equal(waits,1);
});
