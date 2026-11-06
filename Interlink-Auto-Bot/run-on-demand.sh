#!/data/data/com.termux/files/usr/bin/bash
set -eu
umask 077
cd "$(dirname "$0")"
case "${1:-}" in test|claim) ;; *) exit 2 ;; esac
mkdir -p logs .runtime
exec 9>.runtime/execution.lock
# Distinguish busy from success in the Android callback.
flock -n 9 || exit 14
logfile="logs/on-demand-$(date +%Y-%m-%d).log"
node lib/log-retention.js >> "$logfile" 2>&1 || true
timeout -k 2s 100s node on-demand.js "$1" >> "$logfile" 2>&1
