#!/data/data/com.termux/files/usr/bin/bash
# Invoked at 01:26, 05:26, 09:26, 13:26, 17:26 and 21:26 local time.
set -u
cd "$(dirname "$0")" || exit 1
mkdir -p logs
# Avoid concurrent invocations if an earlier window has not finished.
if ! mkdir .scheduled-claim.lock 2>/dev/null; then
  echo "Another claim window is active; skipping." >&2
  exit 0
fi
trap 'rmdir .scheduled-claim.lock 2>/dev/null || true' EXIT
node index.js --once >> "logs/claim-$(date +%Y-%m-%d).log" 2>&1
