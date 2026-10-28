# Jawad TV — custom launcher for TCL Android TV

A dark home screen for Android TV: big clock + date (Gregorian and Hijri),
an offline prayer-time card for Jeddah (Umm al-Qura method), and a row of
all installed apps with their real banners/icons.

## Build options

**Option 1 — GitHub builds it for you (no install).**
Push this folder to a GitHub repository. The workflow in
`.github/workflows/build.yml` builds `JawadTV.apk` automatically and
publishes it under the repository's **Releases** tab.

**Option 2 — Android Studio.**
Open this folder in Android Studio, let it sync, then Build ▸ Build APK(s),
or connect the TV over ADB (`adb connect <TV-IP>`) and press Run.

## Install on the TV

1. On the TV: Settings ▸ Device Preferences ▸ Security & restrictions ▸
   allow Unknown sources for the file manager you use.
2. Copy `JawadTV.apk` to a USB stick (or use the "Send Files to TV" app).
3. Open the APK on the TV and install it.
4. Press the Home button ▸ choose **Jawad TV** ▸ Always.

## Remote controls

- OK on a tile → open the app
- Long-press OK on a tile → pin / unpin it to the front of the row
- Last tile → Android TV Settings

## Change the city

Edit `latitude` / `longitude` at the top of `MainActivity.kt` and the
`city` string in `res/values/strings.xml`.
