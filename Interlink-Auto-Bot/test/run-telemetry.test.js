'use strict';
const {test}=require('node:test'); const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process');
const {createTelemetry,safe}=require('../lib/run-telemetry');
test('durable report correlates both windows and drops credential fields',t=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'interlink-report-')); t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
 const telemetry=createTelemetry({executionId:'schedule-123',attemptId:'attempt-1',scheduledAt:123,directory});
 telemetry.emit('NODE_PROCESS_STARTED',{token:'secret',headers:{Authorization:'secret'}});
 telemetry.emit('WINDOW_1_SUCCESS',{reason:'accepted_and_last_claim_advanced',submitted:true});
 telemetry.emit('WINDOW_2_NOT_ELIGIBLE',{reason:'server_not_eligible',eligible:false});
 const raw=fs.readFileSync(path.join(directory,'attempt-1.json'),'utf8'); const data=JSON.parse(raw);
 assert.equal(data.executionId,'schedule-123'); assert.equal(data.windows['1'].result,'WINDOW_1_SUCCESS');
 assert.equal(data.windows['2'].result,'WINDOW_2_NOT_ELIGIBLE'); assert.ok(!raw.includes('secret'));
 assert.ok(data.events.some(e=>e.event==='CLAIM_WINDOW_2_COMPLETED'));
 assert.equal(fs.statSync(path.join(directory,'attempt-1.json')).mode&0o777,0o600);
 assert.throws(()=>createTelemetry({executionId:'../escape',attemptId:'bad',scheduledAt:123,directory}));
});
test('report callback handles partial process output without claiming success',t=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'interlink-protocol-')); t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
 fs.mkdirSync(path.join(directory,'lib')); fs.mkdirSync(path.join(directory,'.runtime','reports'),{recursive:true});
 for(const f of ['run-report.js','run-telemetry.js','storage.js']) fs.copyFileSync(path.join(__dirname,'..','lib',f),path.join(directory,'lib',f));
 const telemetry=createTelemetry({executionId:'schedule-123',attemptId:'attempt-2',scheduledAt:123,directory:path.join(directory,'.runtime','reports')});
 telemetry.emit('NODE_PROCESS_STARTED');telemetry.emit('WINDOW_1_SUCCESS',{reason:'accepted_and_last_claim_advanced'});
 const stdout=cp.execFileSync(process.execPath,[path.join(directory,'lib/run-report.js'),'--execution-id','schedule-123','--attempt-id','attempt-2','--scheduled-at','123','--wrapper-exit','137'],{encoding:'utf8'});
 const data=JSON.parse(stdout.trim().slice('INTERLINK_RESULT '.length));
 assert.equal(data.exitCode,137);assert.equal(data.nodeStarted,true);assert.equal(data.windows['2'],undefined);
 const missing=cp.execFileSync(process.execPath,[path.join(directory,'lib/run-report.js'),'--execution-id','schedule-123','--attempt-id','missing','--scheduled-at','123','--wrapper-exit','127'],{encoding:'utf8'});
 assert.equal(JSON.parse(missing.trim().slice('INTERLINK_RESULT '.length)).nodeStarted,false);
});
test('safe metadata excludes URLs, response bodies, and arbitrary nested objects',()=>{
 assert.deepEqual(safe({url:'https://secret',data:{token:'secret'},stage:'eligibility',httpStatus:429}),{stage:'eligibility',httpStatus:429});
});
test('real Node entrypoint exits expired original slot and persists exit evidence without loading auth',t=>{
 const directory=fs.mkdtempSync(path.join(os.tmpdir(),'interlink-entry-'));t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
 fs.copyFileSync(path.join(__dirname,'..','scheduled.js'),path.join(directory,'scheduled.js'));
 fs.cpSync(path.join(__dirname,'..','lib'),path.join(directory,'lib'),{recursive:true});
 // No index.js or credentials exist in this fixture: any auth/network access would fail.
 const run=cp.spawnSync(process.execPath,[path.join(directory,'scheduled.js'),'--scheduled-at','1','--execution-id','schedule-1','--attempt-id','expired'],{encoding:'utf8'});
 assert.equal(run.status,20);
 const report=JSON.parse(fs.readFileSync(path.join(directory,'.runtime/reports/expired.json'),'utf8'));
 assert.ok(report.events.some(e=>e.event==='NODE_PROCESS_STARTED'));
 assert.ok(report.events.some(e=>e.event==='NODE_PROCESS_EXITED'&&e.exitCode===20));
 assert.deepEqual(report.windows,{});
});
