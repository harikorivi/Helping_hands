'use strict';
const os = require('node:os');
const { setTimeout: delay } = require('node:timers/promises');
// Linux/Android os.uptime uses /proc/uptime or CLOCK_BOOTTIME, including suspend.
// performance.now uses CLOCK_MONOTONIC there, which excludes suspend.
function createClock({ wall = Date.now, elapsed = () => os.uptime() * 1000,
  zone = () => Intl.DateTimeFormat().resolvedOptions().timeZone } = {}) {
  const started = wall(), boot = elapsed(), initialZone = zone();
  function now() {
    const current = wall();
    if (zone() !== initialZone || Math.abs(current - started - (elapsed() - boot)) > 5000) {
      const error = new Error('Clock or timezone changed'); error.code = 'CLOCK_CHANGED'; throw error;
    }
    return current;
  }
  return { now, started, zone: initialZone };
}
async function boundedSleep(ms, now, signal, wait = delay) {
  const until = now() + ms;
  // Re-evaluate wall time on each wake, including the first callback after suspension.
  while (now() < until) await wait(Math.min(1000, until - now()), undefined, { signal });
}
module.exports = { createClock, boundedSleep };
