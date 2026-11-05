#!/data/data/com.termux/files/usr/bin/bash
set -eu
umask 077
cd "$(dirname "$0")"
case "${1:-}" in test|claim) ;; *) exit 2 ;; esac
mkdir -p logs .runtime
exec 9>.runtime/execution.lock
# Distinguish busy from success in the Android callback.
flock -n 9 || exit 14
timeout -k 2s 100s node on-demand.js "$1" >> "logs/on-demand-$(date +%Y-%m-%d).log" 2>&1
