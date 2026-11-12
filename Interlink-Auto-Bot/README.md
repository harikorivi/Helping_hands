# Interlink Auto Bot

A personal Android automation project with a Node.js API client and an **Interlink Scheduler** companion app. Validation candidate **2.0-rc4** includes automatic token refresh, six daily two-window runs and six user-visible Android alarm clocks with bounded foreground execution, an immediate connection test, and a separately confirmed single-claim action.

**Start with [README-mobile.md](README-mobile.md)** for a complete first-time installation. Existing users should follow its upgrade note to preserve credentials. All fixes are integrated into the source; install the matching APK from [android-companion/releases](android-companion/releases).

- Scheduled runs use the phone's local timezone: 01:26, 05:26, 09:26, 13:26, 17:26 and 21:26.
- Server eligibility is required. Claims are verified; uncertain submissions are not blindly retried.
- Credentials remain private in Termux. Do not commit token, device, proxy-credential or log files.
- Android execution is subject to Doze and manufacturer restrictions; force-stop recovery is not guaranteed.

The app releases its execution service and wake lock between sessions while preserving the existing Node workflow. Recorded OEM-requested force-stops remain outside ordinary app recovery. Physical overnight acceptance is pending; see the [service architecture decision](docs/ADR-ALARM-CLOCK-RC3.md) and [executed validation](android-companion/releases/VALIDATION-2.0-rc4.md).

Development checks:

```sh
npm ci --ignore-scripts
npm test
```

These tests use mocked requests and do not submit live claims. First-time authentication uses `node index.js --login`; scheduled and on-demand execution must use the documented shell wrappers. The old continuous `node index.js` and `--once` modes are retired.

Based on the original Interlink Auto Bot by Airdrop Insiders / vikitoshi. This companion is not the official Interlink application.
