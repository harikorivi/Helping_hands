#!/data/data/com.termux/files/usr/bin/bash
# First-time setup or in-place upgrade. Never reads or replaces credentials.
set -eu
umask 077
cd "$(dirname "$0")"
if [ "$PWD" != /data/data/com.termux/files/home/Helping_hands/Interlink-Auto-Bot ]; then
  printf '%s\n' 'Use the standard Termux checkout: ~/Helping_hands/Interlink-Auto-Bot.' >&2
  exit 1
fi
for executable in node npm flock timeout; do
  command -v "$executable" >/dev/null || { printf 'Missing command: %s. Install nodejs-lts util-linux coreutils.\n' "$executable" >&2; exit 1; }
done
node -e 'if (Number(process.versions.node.split(".")[0]) < 22) process.exit(1)' || { printf '%s\n' 'Node.js 22 or newer is required.' >&2; exit 1; }
mkdir -p .runtime
exec 9>.runtime/execution.lock
flock -n 9 || { printf '%s\n' 'A bot run is active. Wait for it to finish, then repeat setup.' >&2; exit 1; }
npm ci --ignore-scripts
npm test
mkdir -p "$HOME/.termux"
settings="$HOME/.termux/termux.properties"
if [ -f "$settings" ]; then
  backup=$(mktemp "$HOME/.termux/termux.properties.backup.XXXXXX")
  cp -p "$settings" "$backup"
fi
touch "$settings"
temporary=$(mktemp "$HOME/.termux/termux.properties.XXXXXX")
trap 'rm -f "$temporary"' EXIT
sed '/^[[:space:]]*#*[[:space:]]*allow-external-apps[[:space:]]*=/d' "$settings" > "$temporary"
printf '\nallow-external-apps=true\n' >> "$temporary"
chmod 600 "$temporary"
mv "$temporary" "$settings"
chmod 700 run-scheduled.sh run-on-demand.sh
termux-reload-settings
printf '\nSetup complete. Credentials were not changed.\nNew users: run node index.js --login, then install the companion APK.\nExisting users: keep stored credentials; log in only if authentication cannot refresh.\n'
