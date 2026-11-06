# Executed by Termux bash -c; arguments are supplied separately, never interpolated.
set -u
umask 077
work=$1
scheduled=$2
execution=$3
attempt=$4
fail() {
  printf 'INTERLINK_RESULT {"version":1,"executionId":"%s","attemptId":"%s","scheduledAt":%s,"exitCode":%s,"nodeStarted":false,"startupReason":"%s","events":[],"windows":{}}\n' "$execution" "$attempt" "$scheduled" "$2" "$1"
  exit "$2"
}
# Refuse unsafe identifiers before formatting JSON. Android generates all these values.
case "$execution:$attempt" in *[!A-Za-z0-9_:-]*|'':*) exit 64;; esac
case "$scheduled" in ''|*[!0-9]*) exit 64;; esac
[ -d "$work" ] || fail project_directory_missing 40
cd "$work" || fail project_directory_unavailable 40
[ -r run-scheduled.sh ] || fail scheduled_wrapper_missing 41
[ -r lib/run-report.js ] && [ -r lib/run-telemetry.js ] && [ -r scheduled.js ] || fail bot_update_required 42
# This literal marks the wrapper version that can safely return correlated results.
grep -q '^INTERLINK_WRAPPER_PROTOCOL=2$' run-scheduled.sh || fail bot_update_required 42
command -v node >/dev/null 2>&1 || fail node_missing 43
command -v flock >/dev/null 2>&1 || fail flock_missing 43
command -v timeout >/dev/null 2>&1 || fail timeout_missing 43
node -e 'if(Number(process.versions.node.split(".")[0])<22)process.exit(1)' >/dev/null 2>&1 || fail node_version_unsupported 43
node -e 'for(const p of ["axios","https-proxy-agent","socks-proxy-agent"])require.resolve(p)' >/dev/null 2>&1 || fail dependencies_missing 44
[ -w . ] || fail project_not_writable 45
if [ "${5:-}" = "--check-only" ]; then fail installation_checks_passed 0; fi
# The wrapper's private logs never enter the callback. Only its protocol line is forwarded.
output=$(bash run-scheduled.sh --scheduled-at "$scheduled" --execution-id "$execution" --attempt-id "$attempt" 2>/dev/null)
code=$?
found=false
while IFS= read -r line; do
  case "$line" in
    'INTERLINK_RESULT '*) printf '%s\n' "$line"; found=true;;
  esac
done <<< "$output"
if [ "$found" = false ]; then
  [ "$code" -ne 0 ] || code=71
  fail wrapper_exit_without_report "$code"
fi
exit "$code"
