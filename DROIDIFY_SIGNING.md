# Attendance: protected Android updates

Attendance already has a long-lived signing key in GitHub Actions secrets and a pinned certificate fingerprint in `.github/workflows/release.yml`. **Do not rotate or replace that key** if you want to preserve your installed app's data and update it normally.

For each future release, update the versionName and **increase the integer Android versionCode** in `app/build.gradle`; then create a matching `vX.Y.Z` tag. The signed workflow now compares the new APK's package name, signing certificate, and versionCode with the prior published APK and refuses incompatible or duplicate releases.

The Attendance app package ID must remain `com.rolando.attendance`. A GitHub Pages F-Droid catalog should import the signed `Attendance-v*.apk` release, not debug builds. Back up the original keystore and passwords securely.
