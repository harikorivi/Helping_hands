'use strict';
const HOURS = [1, 5, 9, 13, 17, 21];
function slotsFor(now) {
  const d = new Date(now);
  if (!HOURS.includes(d.getHours()) || d.getMinutes() < 26 || d.getMinutes() >= 34) return [];
  const date = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
  const at = m => new Date(d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), m).getTime();
  // Local civil key also suppresses duplicates if DST repeats an hour.
  return [[26,30],[31,34]].map(([start,end],i) => ({
    key: `${date}-${d.getHours()}-${i+1}`, number: i+1, start: at(start), end: at(end)
  }));
}
function confirmed(response, before, after) {
  const timestamp = v => typeof v === 'number' ? v : Date.parse(v);
  return response?.statusCode === 200 && Number.isFinite(timestamp(before)) &&
    Number.isFinite(timestamp(after)) && timestamp(after) > timestamp(before);
}
module.exports = { slotsFor, confirmed };
