#!/data/data/com.termux/files/usr/bin/bash
set -eu
cd "$(dirname "$0")"
command -v crontab >/dev/null || { echo 'Install scheduler first: pkg install cronie'; exit 1; }
chmod +x run-scheduled.sh
job="26 1,5,9,13,17,21 * * * $PWD/run-scheduled.sh # interlink-scheduled-claim"
{ crontab -l 2>/dev/null | grep -v '# interlink-scheduled-claim' || true; echo "$job"; } | crontab -
crond 2>/dev/null || true
printf 'Installed local-time claim windows (start at xx:26, stop by ~xx:34).\n'
printf 'Ensure Termux is allowed to run in background. Cron must restart after reboot.\n'
crontab -l | grep 'interlink-scheduled-claim'
