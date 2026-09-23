# Attendance

Attendance is a private, offline Android app for keeping your own work-attendance record.

## Included

- **Tardy & call-out tracking** for today or any earlier date
- Optional **exact late duration** (hours and minutes)
- **Hours worked** tracking with one editable entry per date
- **Last 30 days, chosen month, chosen year, and all-time** filters
- A cumulative hours total and separate tardy/call-out counts
- Dedicated Tardy and Call-Out history tabs opened from the summary counters
- Explicit Edit and Delete controls for correcting tardies and call-outs
- Native **PDF export and Android share sheet** for the active mode and filter
- Light and dark themes plus a choice of default opening mode
- Full JSON backup/import for moving records between devices
- Optional Google Drive sync with visible create-file, link-file, sync, and disconnect controls
- Edge swipe or menu button to open the left-side navigation
- App-logo shortcut to the selected default screen and consistent circular navigation icons
- Private SQLite storage with no ads, analytics, or direct internet permission

## Install the ready-made APK

1. Copy `Attendance-v1.3.2.apk` to your Android phone.
2. Open it and allow installation from the app you used to open the file when Android asks.
3. Install **Attendance**.

The included APK is signed with a project-specific development certificate. Keep the same signing key for future builds if you want updates to install over this version without uninstalling it.

## Build the source

Open this folder in a current Android Studio release and run the `app` configuration, or use:

```bash
./gradlew assembleDebug
```

Requirements: JDK 17 and Android SDK 35. The project uses only Android platform APIs and has no third-party runtime dependencies.

## Data and exports

App records are stored in the app's private local database. Removing the app also removes those records unless you exported a backup or linked a Google Drive backup first. Drive sync uses Android's system file picker, so Attendance never receives your Google password or broad access to your Drive. PDF files are created in the app cache only when Export is tapped, then shared only to the destination selected in Android's share sheet.
