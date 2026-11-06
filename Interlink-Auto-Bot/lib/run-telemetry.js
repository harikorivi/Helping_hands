'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { atomicWrite } = require('./storage');
const ID = /^[A-Za-z0-9_-]{1,100}$/;
const fields = ['reason','stage','code','httpStatus','applicationStatus','attempt','eligible','submitted','result','start','end','delayMs','exitCode'];
function safe(data = {}) {
  return Object.fromEntries(fields.filter(k => Object.hasOwn(data,k)).map(k => [k,
    typeof data[k] === 'string' ? (/^[A-Za-z0-9_:.+ -]{0,120}$/.test(data[k]) ? data[k] : 'omitted') :
    typeof data[k] === 'boolean' || typeof data[k] === 'number' || data[k] === null ? data[k] : null]));
}
function createTelemetry({ executionId, attemptId, scheduledAt, directory }) {
  if (!ID.test(executionId) || !ID.test(attemptId) || !Number.isFinite(scheduledAt)) throw new Error('invalid_execution_context');
  fs.mkdirSync(directory,{recursive:true,mode:0o700});
  // Diagnostic reports only, never the claim ledger. Keep today and yesterday.
  const date=new Date(); const cutoff=new Date(date.getFullYear(),date.getMonth(),date.getDate()-1).getTime();
  for(const entry of fs.readdirSync(directory,{withFileTypes:true})) {
    if(!entry.isFile() || !/^[A-Za-z0-9_-]{1,100}\.json$/.test(entry.name)) continue;
    try { const target=path.join(directory,entry.name); if(fs.lstatSync(target).mtimeMs<cutoff) fs.unlinkSync(target); } catch {}
  }
  const file = path.join(directory,`${attemptId}.json`);
  const report = { version:1, executionId, attemptId, scheduledAt, nodeStarted:true, events:[], windows:{} };
  function emit(event,data={}) {
    if (!/^[A-Z0-9_]{1,60}$/.test(event)) return;
    const entry = { event, actualAt:Date.now(), pid:process.pid, ...safe(data) };
    report.events.push(entry); report.events=report.events.slice(-100);
    const match = /^WINDOW_([12])_(SUCCESS|NOT_ELIGIBLE|FAILED)$/.exec(event);
    if (match) {
      report.windows[match[1]] = { result:event, ...safe(data) };
      report.events.push({...entry,event:`CLAIM_WINDOW_${match[1]}_COMPLETED`,result:event});
    }
    atomicWrite(file,JSON.stringify(report));
  }
  return { emit, report };
}
function argument(args, name, fallback) { const i=args.indexOf(name); return i<0 ? fallback : args[i+1]; }
module.exports={createTelemetry,safe,ID,argument};
