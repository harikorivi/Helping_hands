#!/data/data/com.termux/files/usr/bin/bash
INTERLINK_WRAPPER_PROTOCOL=2
set -eu
umask 077
cd "$(dirname "$0")"
mkdir -p logs .runtime
# Kernel lock is released on exit, SIGKILL, or reboot. Never unlink this file.
exec 9>.runtime/execution.lock
logfile="logs/claim-$(date +%Y-%m-%d).log"
# Keep the callback channel separate from all private console output.
exec 3>&1
exec >> "$logfile" 2>&1
finish() {
  code=$?
  trap - EXIT
  node lib/run-report.js "$@" --wrapper-exit "$code" --wrapper-stage "$stage" >&3 2>/dev/null || true
  exit "$code"
}
stage=lock_acquisition
trap 'finish "$@"' EXIT
printf '{"event":"SCHEDULED_WRAPPER_STARTED","epochSeconds":%s}\n' "$(date +%s)"
flock -n 9 || { printf '%s\n' '{"event":"SCHEDULED_WRAPPER_BUSY"}'; exit 14; }
# Only recognized daily logs older than yesterday; run under the shared lock.
node lib/log-retention.js || true
# No wake lock between runs. Companion holds a timeout-bounded lock during this run.
# timeout bounds authentication too, and kills descendants if graceful exit stalls.
stage=node_execution
timeout -k 2s 480s node scheduled.js "$@"
stage=finished
