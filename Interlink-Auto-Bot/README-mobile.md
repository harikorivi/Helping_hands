# Interlink Termux scheduled two-window bot

Unofficial script. Check Interlink's terms before using automation. Not tested against live servers.

## Daily local-time schedule

The scheduler launches at 01:26, 05:26, 09:26, 13:26, 17:26, 21:26 (phone local time). Each run checks eligibility and attempts **at most one successful claim** in each window:

- First: :26:00 through :29:59.
- Second: :31:00 through :33:59.
- Exit after the second window, whether or not claims succeeded. The script does not attempt to bypass server eligibility rules.

A first successful claim does not end the run; it waits for the second window. If the server says not claimable, it retries periodically inside that window. The API response `statusCode: 200` is used as the claim acceptance indicator; this has **not** been verified against the live API. A server response may still require further validation in the official app.

## Installation

In Termux, after pulling your Git repository:

```sh
cd ~/Interlink-Auto-Bot
npm install --ignore-scripts
pkg install cronie
bash setup-schedule.sh
crontab -l
```

First-time login requires interactive OTP entry. Run `node index.js --once` during a scheduled window while watching the terminal; it may need login ID, passcode, and OTP. Outside a window it will skip past windows and may wait for the next slot in the current hour, so test around a scheduled time. `run-scheduled.sh` runs without an interactive terminal, so expired sessions requiring OTP will not be able to log in unattended.

## Security and reliability

HTTPS verification is enabled. Access and refresh tokens are stored locally in plaintext files with restrictive file permissions; keep them private. `.gitignore` excludes credentials and logs. Check `git ls-files token.txt refresh_token.txt device.txt proxies.txt` before pushing. If tokens were committed in the past, remove them from history and rotate them.

Cron must remain active and be restarted after reboot. Android battery management may delay or kill jobs; **exact execution is not guaranteed**. Use `date` to check the phone's timezone. View results with `tail -n 60 logs/claim-$(date +%Y-%m-%d).log`. Do not commit logs or tokens.
