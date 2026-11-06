'use strict';
// Accept copied mixed human text + JSONL; never imports or executes report content.
const fs=require('node:fs');
function analyze(text,now=Date.now()) {
 const runs=new Map();
 for(const line of text.split('\n')) {
  let e;try{e=JSON.parse(line);}catch{continue;}
  if(typeof e.executionId!=='string'||!e.executionId.startsWith('schedule-')||!Number.isFinite(e.scheduledAt))continue;
  const run=runs.get(e.executionId)||{at:e.scheduledAt};
  if(e.event==='ALARM_SCHEDULED')run.registered=true;
  if(e.event==='ALARM_CANCELLED')run.cancelled=true;
  if(e.event==='ALARM_RECEIVED'&&Number.isFinite(e.actualAt))run.received=run.received==null?e.actualAt:Math.min(run.received,e.actualAt);
  runs.set(e.executionId,run);
 }
 const due=[...runs.values()].filter(r=>r.registered&&!r.cancelled&&r.at<=now);
 const delays=due.filter(r=>r.received!=null).map(r=>r.received-r.at).sort((a,b)=>a-b);
 return {scope:'Only retained exported events; not lifetime or production reliability',dueRegistered:due.length,delivered:delays.length,
  withoutDeliveryEvidence:due.length-delays.length,deliveryPercent:due.length?100*delays.length/due.length:null,
  latencyMs:{min:delays[0]??null,mean:delays.length?delays.reduce((a,b)=>a+b,0)/delays.length:null,p95:delays.length?delays[Math.ceil(delays.length*.95)-1]:null,max:delays.at(-1)??null}};
}
module.exports={analyze};
if(require.main===module){if(!process.argv[2]){console.error('Usage: node tools/analyze-diagnostics.js copied-report.txt');process.exitCode=2;}else console.log(JSON.stringify(analyze(fs.readFileSync(process.argv[2],'utf8')),null,2));}
