const { test } = require('node:test');
const assert = require('node:assert/strict');
const { diagnose, chooseManualSlot, claimOnce } = require('../on-demand');
const { failureDetails } = require('../lib/diagnostics');
test('connection test authenticates and reads without making a claim', async () => {
  const calls=[]; const events=[];
  const session={ensureFresh:async()=>{calls.push('auth');return true;},client:()=>({get:async url=>{
    calls.push(url); return {status:200,data:{statusCode:200,data:{isClaimable:true}}};
  },post:()=>{throw Error('must never submit');}})};
  assert.equal(await diagnose(session,(...args)=>events.push(args)),0);
  assert.equal(calls[0],'auth'); assert.equal(calls.length,3); assert.equal(events[0][1].claimed,false);
});
test('test errors distinguish authentication, response shape and HTTP failures', async () => {
  assert.equal(await diagnose({ensureFresh:async()=>false},()=>{}),11);
  const session=response=>({ensureFresh:async()=>true,client:()=>({get:async()=>response})});
  assert.equal(await diagnose(session({status:200,data:{statusCode:200,data:{}}}),()=>{}),13);
  assert.equal(await diagnose({ensureFresh:async()=>true,client:()=>({get:async()=>{throw {response:{status:401,data:{token:'secret'}}};}})},()=>{}),12);
  const metadata=failureDetails({message:'secret',response:{status:401,data:{token:'secret'}}},'eligibility');
  assert.equal(metadata.httpStatus,401); assert.ok(!JSON.stringify(metadata).includes('secret'));
});
test('manual claim uses existing scheduled window and rejects pre-alarm/gap/deadline', () => {
  const at=m=>new Date(2026,10,5,17,m).getTime();
  assert.equal(chooseManualSlot(at(25)),null);
  assert.equal(chooseManualSlot(at(30)),null);
  assert.equal(chooseManualSlot(at(26)),null);
  assert.equal(chooseManualSlot(at(31)),null);
  assert.equal(chooseManualSlot(at(40)).key,'manual');
  assert.equal(chooseManualSlot(at(34)-10000),null);
});
test('manual claim persists intent before one POST and blocks uncertain replay', async () => {
  const fs=require('node:fs'), os=require('node:os'), path=require('node:path');
  const runtime=fs.mkdtempSync(path.join(os.tmpdir(),'manual-claim-'));
  let posts=0, time=new Date(2026,10,5,18,0).getTime();
  const api={interceptors:{request:{use:()=>{}}},get:async url=>({status:200,data:{statusCode:200,data:url.includes('check-is-claimable') ? {isClaimable:true} : {token:{lastClaimTime:100}}}}),
    post:async()=>{assert.equal(JSON.parse(fs.readFileSync(path.join(runtime,'manual.json'))).manual.submitted,true);posts++;throw Error('ambiguous');}};
  const session={ensureFresh:async()=>true,client:()=>api};
  const options={runtime,sleep:async ms=>{time+=ms;}};
  try {
    assert.equal(await claimOnce(session,()=>time,options),21); assert.equal(posts,1);
    time+=120000;
    assert.equal(await claimOnce(session,()=>time,options),21); assert.equal(posts,1);
  } finally {fs.rmSync(runtime,{recursive:true});}
});
test('manual action submits once only when eligible and confirms timestamp advancement', async () => {
  const fs=require('node:fs'), os=require('node:os'), path=require('node:path');
  for (const eligible of [false,true]) {
    const runtime=fs.mkdtempSync(path.join(os.tmpdir(),'manual-result-'));
    let posts=0, time=new Date(2026,10,5,18,0).getTime();
    const api={interceptors:{request:{use:()=>{}}},get:async url=>({status:200,data:{statusCode:200,data:url.includes('check-is-claimable') ? {isClaimable:eligible} : {token:{lastClaimTime:posts ? 200 : 100}}}}),post:async()=>{posts++;return {status:200,data:{statusCode:200}};}};
    try {
      const result=await claimOnce({ensureFresh:async()=>true,client:()=>api},()=>time,{runtime,sleep:async ms=>{time+=ms;}});
      assert.equal(result,eligible ? 0 : 10); assert.equal(posts,eligible ? 1 : 0);
    } finally {fs.rmSync(runtime,{recursive:true});}
  }
});
