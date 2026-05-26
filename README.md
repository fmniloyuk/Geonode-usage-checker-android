# GeoNode Usage Checker Android App

A simple native Android app that stores multiple GeoNode accounts locally and checks usage statistics from:

`https://monitor.geonode.com/monitor-light/proxies`

## Features

- Add/update/delete multiple GeoNode accounts
- Fields:
  - Account name
  - GEONODE_USERNAME
  - GEONODE_PASSWORD
- Select a saved account from a dropdown
- Check usage statistics using Basic Authentication
- Shows used bandwidth, total bandwidth, remaining bandwidth, usage percentage, and current fast bandwidth

## Build APK in Android Studio

1. Open Android Studio.
2. Choose **Open**.
3. Select this `GeoNodeUsageChecker` folder.
4. Let Gradle sync finish.
5. Go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. The APK will be generated under:

`app/build/outputs/apk/debug/app-debug.apk`

## Important security note

This app stores GeoNode credentials in Android SharedPreferences. That is acceptable for personal/internal testing, but for production you should encrypt credentials with Android Keystore / EncryptedSharedPreferences.
