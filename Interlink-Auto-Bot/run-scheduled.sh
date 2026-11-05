#!/data/data/com.termux/files/usr/bin/bash
set -eu
umask 077
cd "$(dirname "$0")"
mkdir -p logs .runtime
# Kernel lock is released on exit, SIGKILL, or reboot. Never unlink this file.
exec 9>.runtime/execution.lock
flock -n 9 || exit 0
# No wake lock between runs. Companion holds a timeout-bounded lock during this run.
# timeout bounds authentication too, and kills descendants if graceful exit stalls.
timeout -k 2s 480s node scheduled.js >> "logs/claim-$(date +%Y-%m-%d).log" 2>&1
