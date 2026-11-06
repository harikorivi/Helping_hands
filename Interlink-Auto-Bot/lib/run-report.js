'use strict';
// Only this bounded, allowlisted protocol reaches the Android callback, never raw bot stdout.
const fs=require('node:fs'); const path=require('node:path');
const { ID, argument, safe }=require('./run-telemetry');
const args=process.argv.slice(2), attemptId=argument(args,'--attempt-id',''), executionId=argument(args,'--execution-id','');
if (ID.test(attemptId) && ID.test(executionId)) {
  const scheduledAt=Number(argument(args,'--scheduled-at','0'));
  const exitCode=Number(argument(args,'--wrapper-exit','1'));
  let data={};
  try { data=JSON.parse(fs.readFileSync(path.join(__dirname,'..','.runtime','reports',`${attemptId}.json`),'utf8')); } catch {}
  const valid=data.attemptId===attemptId && data.executionId===executionId && data.scheduledAt===scheduledAt;
  const report={version:1,attemptId,executionId,scheduledAt,exitCode,nodeStarted:valid && data.nodeStarted===true,
    events:valid && Array.isArray(data.events) ? data.events.slice(-100).map(e=>({event:/^[A-Z0-9_]{1,60}$/.test(e.event)?e.event:'OMITTED',actualAt:Number(e.actualAt),pid:Number(e.pid),...safe(e)})) : [],
    windows:valid ? Object.fromEntries(['1','2'].filter(n=>data.windows?.[n]).map(n=>[n,safe(data.windows[n])])) : {}};
  console.log('INTERLINK_RESULT '+JSON.stringify(report));
}
