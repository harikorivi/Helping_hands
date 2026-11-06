'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process');
const script=path.join(__dirname,'../android-companion/app/src/main/assets/termux-bootstrap.sh');
function fixture(t){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'interlink-bootstrap-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const work=path.join(root,'bot');fs.mkdirSync(work);fs.mkdirSync(path.join(work,'lib'));
 const bin=path.join(root,'bin');fs.mkdirSync(bin);
 for(const name of ['node','flock','timeout'])fs.writeFileSync(path.join(bin,name),'#!/bin/bash\nexit 0\n',{mode:0o700});
 fs.writeFileSync(path.join(work,'run-scheduled.sh'),'INTERLINK_WRAPPER_PROTOCOL=2\nexit 2\n');
 for(const name of ['run-report.js','run-telemetry.js'])fs.writeFileSync(path.join(work,'lib',name),'');
 fs.writeFileSync(path.join(work,'scheduled.js'),'');
 const run=(dir=work,extra=[])=>cp.spawnSync('/bin/bash',[script,dir,'123','schedule-123','attempt-123',...extra],{encoding:'utf8',env:{...process.env,PATH:bin+':'+process.env.PATH}});
 return {work,bin,run};
}
function report(run){assert.equal(run.stderr,'');return JSON.parse(run.stdout.trim().slice('INTERLINK_RESULT '.length));}
test('bootstrap explains missing project and outdated bot before running any claim',t=>{
 const f=fixture(t);assert.equal(report(f.run(f.work+'-missing')).startupReason,'project_directory_missing');
 fs.writeFileSync(path.join(f.work,'run-scheduled.sh'),'echo secret-token\n');
 const r=f.run();assert.equal(r.status,42);assert.equal(report(r).startupReason,'bot_update_required');assert.ok(!r.stdout.includes('secret-token'));
});
test('bootstrap preserves exit 2 and returns safe fallback instead of raw wrapper output',t=>{
 const f=fixture(t);fs.writeFileSync(path.join(f.work,'run-scheduled.sh'),'INTERLINK_WRAPPER_PROTOCOL=2\necho secret-token\necho password >&2\nexit 2\n');
 const r=f.run();assert.equal(r.status,2);assert.equal(report(r).startupReason,'wrapper_exit_without_report');assert.ok(!r.stdout.includes('secret-token'));assert.ok(!r.stdout.includes('password'));
});
test('bootstrap reports dependency failure and forwards only correlated protocol output',t=>{
 const f=fixture(t);fs.writeFileSync(path.join(f.bin,'node'),'#!/bin/bash\ncase "$2" in *require.resolve*)exit 1;; esac\nexit 0\n',{mode:0o700});
 assert.equal(report(f.run()).startupReason,'dependencies_missing');
 fs.writeFileSync(path.join(f.bin,'node'),'#!/bin/bash\nexit 0\n',{mode:0o700});
 const data={version:1,executionId:'schedule-123',attemptId:'attempt-123',scheduledAt:123,nodeStarted:true,exitCode:2,events:[],windows:{}};
 fs.writeFileSync(path.join(f.work,'run-scheduled.sh'),`INTERLINK_WRAPPER_PROTOCOL=2\necho hidden-console\nprintf '%s\\n' '${'INTERLINK_RESULT '+JSON.stringify(data)}'\nexit 2\n`);
 const r=f.run();assert.equal(r.status,2);assert.deepEqual(report(r),data);assert.ok(!r.stdout.includes('hidden-console'));
});

test('offline installation check never executes the scheduled wrapper',t=>{
 const f=fixture(t);fs.writeFileSync(path.join(f.work,'run-scheduled.sh'),'INTERLINK_WRAPPER_PROTOCOL=2\necho MUST_NOT_RUN >&2\nexit 2\n');
 const r=f.run(f.work,['--check-only']);assert.equal(r.status,0);assert.equal(report(r).startupReason,'installation_checks_passed');assert.equal(report(r).nodeStarted,false);
});
