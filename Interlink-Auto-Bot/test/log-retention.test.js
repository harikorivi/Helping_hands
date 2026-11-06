const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
const {pruneLogs}=require('../lib/log-retention');
function fixture(action) {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'log-retention-'));
  try { action(root); } finally {fs.rmSync(root,{recursive:true,force:true});}
}
test('only recognized old logs are deleted; credentials, state, future logs and unknown files survive',()=>fixture(root=>{
  const names=['claim-2026-11-04.log','on-demand-2026-11-03.log','claim-2026-11-05.log','claim-2026-11-06.log','on-demand-2026-11-06.log','claim-2026-11-07.log','claim-2026-02-30.log','credentials.json','state.json','notes.log'];
  for(const name of names)fs.writeFileSync(path.join(root,name),'keep private content');
  const result=pruneLogs(root,new Date(2026,10,6,0,1));
  assert.deepEqual(result,{deleted:2,errors:0});
  for(const name of names.slice(2))assert.ok(fs.existsSync(path.join(root,name)),name);
}));
test('calendar cutoff handles month/year boundaries rather than elapsed 48 hours',()=>fixture(root=>{
  for(const name of ['claim-2025-12-30.log','claim-2025-12-31.log','claim-2026-01-01.log'])fs.writeFileSync(path.join(root,name),'');
  assert.equal(pruneLogs(root,new Date(2026,0,1,23,59)).deleted,1);
  assert.ok(fs.existsSync(path.join(root,'claim-2025-12-31.log')));
}));
test('never follows symlinked files or directories, and missing log directory is harmless',()=>fixture(root=>{
  const elsewhere=path.join(root,'outside');fs.mkdirSync(elsewhere);
  fs.writeFileSync(path.join(elsewhere,'claim-2020-01-01.log'),'private');
  const logs=path.join(root,'logs');fs.mkdirSync(logs);
  fs.symlinkSync(path.join(elsewhere,'claim-2020-01-01.log'),path.join(logs,'claim-2020-01-01.log'));
  fs.mkdirSync(path.join(logs,'on-demand-2020-01-01.log'));
  assert.equal(pruneLogs(logs).deleted,0);
  const linked=path.join(root,'linked');fs.symlinkSync(elsewhere,linked);
  assert.equal(pruneLogs(linked).deleted,0);
  assert.deepEqual(pruneLogs(path.join(root,'absent')),{deleted:0,errors:0});
  assert.equal(fs.readFileSync(path.join(elsewhere,'claim-2020-01-01.log'),'utf8'),'private');
}));
