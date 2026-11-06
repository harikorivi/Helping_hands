'use strict';
const fs = require('node:fs');
const path = require('node:path');
function localDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`;
}
function pruneLogs(directory, now = new Date()) {
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate()-1,12);
  const cutoff = localDate(yesterday);
  const result = { deleted: 0, errors: 0 };
  try {
    const stat = fs.lstatSync(directory);
    if (!stat.isDirectory() || stat.isSymbolicLink()) return result;
  } catch (error) { if (error.code !== 'ENOENT') result.errors++; return result; }
  let entries;
  try { entries = fs.readdirSync(directory,{withFileTypes:true}); }
  catch { result.errors++; return result; }
  for (const entry of entries) {
    if (!entry.isFile() || entry.isSymbolicLink()) continue;
    const match = /^(?:claim|on-demand)-(\d{4}-\d{2}-\d{2})\.log$/.exec(entry.name);
    if (!match) continue;
    const [year,month,day] = match[1].split('-').map(Number);
    const parsed = new Date(year,month-1,day,12);
    if (localDate(parsed) !== match[1] || match[1] >= cutoff) continue;
    try { fs.unlinkSync(path.join(directory,entry.name)); result.deleted++; }
    catch (error) { if (error.code !== 'ENOENT') result.errors++; }
  }
  return result;
}
module.exports = { pruneLogs };
if (require.main === module) {
  const result = pruneLogs(path.join(__dirname,'..','logs'));
  console.log(JSON.stringify({time:new Date().toISOString(),event:'LOG_RETENTION',...result}));
  if (result.errors) process.exitCode=1;
}
