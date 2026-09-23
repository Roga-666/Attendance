# Attendance

Attendance is a private, offline Android app for keeping your own work-attendance record.

## Included

- **Tardy & call-out tracking** for today or any earlier date
- Optional **exact late duration** (hours and minutes)
- **Hours worked** tracking with one editable entry per date
- **Last 30 days, current month, current year, and all-time** filters
- A cumulative hours total and separate tardy/call-out counts
- Editable history with deletion confirmation
- Native **PDF export and Android share sheet** for the active mode and filter
- Edge swipe or menu button to open the left-side navigation
- Fully local SQLite storage—no account, ads, analytics, internet permission, or cloud sync

## Install the ready-made APK

1. Copy `Attendance-v1.0.0.apk` to your Android phone.
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

App records are stored in the app's private local database. Removing the app also removes those records. PDF files are created in the app cache only when Export is tapped, then shared only to the destination selected in Android's share sheet.
