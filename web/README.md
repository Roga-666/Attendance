# Attendance web app

This folder contains the mobile-first web edition of Attendance. It is deliberately separate from the Android project under `app/`, while sharing the same repository and backup-file format.

## Public GitHub Pages site

The `Deploy web app to GitHub Pages` workflow publishes this folder whenever
`web/` changes on the `main` branch. The public site is available at:

`https://roga-666.github.io/Attendance/`

GitHub serves the site over HTTPS. Each browser continues to keep its own
attendance records locally; publishing the app does not publish user records.

## Feature parity

- Tardy and call-out entry, summaries, filters, histories, editing, and deletion
- Optional exact late duration
- Decimal or H:MM hours entry
- Current week, last week, current pay period, and last pay period totals
- Configurable pay-period dates
- Light/dark themes and default opening screen
- Printable PDF reports through the browser print dialog
- Version 2 JSON export/import compatible with the Android app
- Installable PWA behavior when served over HTTPS

Records are stored in the browser's private local storage. Clearing site data removes them unless a JSON backup was exported first.

## Run on the NAS

The included Compose file serves the site on port `8110`:

```bash
cd /home/rolando/Portainer/Attendance/web
docker compose up -d
```

Open `http://NAS-TAILSCALE-IP:8110` from a device on the same Tailscale network.

## Automatic updates

The files under `deploy/` define a systemd timer that runs every five minutes. It performs a safe fast-forward-only pull of `main`, then refreshes the Compose service.

Install the units once:

```bash
sudo cp /home/rolando/Portainer/Attendance/web/deploy/attendance-web-update.service /etc/systemd/system/
sudo cp /home/rolando/Portainer/Attendance/web/deploy/attendance-web-update.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now attendance-web-update.timer
```

Check it with:

```bash
systemctl status attendance-web-update.timer --no-pager
systemctl list-timers attendance-web-update.timer --no-pager
```

The update service intentionally uses `git pull --ff-only`. If files are edited directly on the NAS, it stops instead of overwriting them.

## PWA installation

Normal web use works over the Tailscale HTTP address. Browser installation, offline caching, and service workers require HTTPS (or localhost). Put the site behind an HTTPS reverse proxy or Tailscale HTTPS before using **Add to Home screen** as an installed PWA.
