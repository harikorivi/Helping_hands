const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createSession, tokenExpiringSoon } = require('../index');
const jwt = exp => `header.${Buffer.from(JSON.stringify({exp})).toString('base64url')}.signature`;
test('valid access token avoids refresh; malformed or expired token requires it', async () => {
  assert.equal(tokenExpiringSoon('broken'),true);
  assert.equal(tokenExpiringSoon(jwt('invalid')),true);
  const s=createSession(jwt(Date.now()/1000+3600),'r','d',[],{makeClient:()=>{throw Error('must not call');}});
  assert.equal(await s.ensureFresh(),true);
});
test('startup refresh persists access and rotated refresh together', async () => {
  let saved, calls=0; const next=jwt(Date.now()/1000+3600);
  const s=createSession(jwt(1),'old','d',[],{ persist:(a,r)=>{saved={a,r};}, makeClient:()=>({post:async(url,body)=>{
    calls++; assert.equal(url,'/auth/token'); assert.equal(body.refreshToken,'old');
    return {data:{statusCode:200,data:{accessToken:next,refreshToken:'new'}}};
  }}) });
  assert.equal(await s.ensureFresh(),true); assert.deepEqual(saved,{a:next,r:'new'});
  assert.equal(await s.ensureFresh(),true); assert.equal(calls,1);
});
test('refresh rejection and persistence failure fail closed without prompts', async () => {
  for (const failSave of [false,true]) {
    const s=createSession(jwt(1),'r','d',[],{persist:()=>{throw Error('disk full');},makeClient:()=>({post:async()=>({data:failSave ? {statusCode:200,data:{accessToken:jwt(Date.now()/1000+3600)}} : {statusCode:401}})})});
    assert.equal(await s.ensureFresh(),false); assert.equal(await s.ensureFresh(),false);
  }
});
test('missing access can refresh, missing refresh fails, concurrent callers share one refresh', async () => {
  let calls=0;
  const s=createSession(null,'r','d',[],{persist:()=>{},makeClient:()=>({post:async()=>{
    calls++; return {data:{statusCode:200,data:{accessToken:jwt(Date.now()/1000+3600)}}};
  }})});
  assert.deepEqual(await Promise.all([s.ensureFresh(),s.ensureFresh()]),[true,true]); assert.equal(calls,1);
  const missing=createSession(null,null,'d',[]); assert.equal(await missing.ensureFresh(),false);
});
test('refresh lifecycle emits safe success and rejection outcomes', async () => {
  for(const success of [true,false]) {
    const events=[];
    const s=createSession(jwt(1),'secret-refresh','device',[],{persist:()=>{},onEvent:(event,data)=>events.push({event,...data}),makeClient:()=>({post:async()=>({data:success?{statusCode:200,data:{accessToken:jwt(Date.now()/1000+3600)}}:{statusCode:401}})})});
    assert.equal(await s.ensureFresh(),success);
    assert.equal(events[0].event,'TOKEN_REFRESH_STARTED');
    assert.equal(events.at(-1).event,'TOKEN_REFRESH_COMPLETED');
    assert.equal(events.at(-1).result,success?'SUCCESS':'FAILED');
    assert.ok(!JSON.stringify(events).includes('secret-refresh'));
  }
});
