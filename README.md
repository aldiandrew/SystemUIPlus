# SystemUI Plus

SystemUI Plus combines the ClockOS and Duos status-bar customization features into one Android application.

It is designed to work without root, Xposed, or Accessibility permission. Shizuku is used only where privileged SystemUI shell operations are required.

## Features

- Custom status-bar clock
- Custom system indicators
- Notification icon grouping that follows Android notification groups
- Automatic native SystemUI restoration when custom mode is disabled
- Portrait custom status bar with native SystemUI in landscape
- Material 3 Expressive-inspired AMOLED interface
- Device accent color with a fixed black AMOLED surface palette
- English or device-default application language
- Settings backup and restore
- Battery optimization exemption option
- Optional permanent disabling of the app's service notifications

## Source Code

The complete source code is maintained in this repository:

https://github.com/aldiandrew/SystemUIPlus

The Android application source is under:
`app/src/main/java/`

## Privacy, Licenses & Source

The complete source code, third-party license information, and privacy information are maintained in this repository.

SystemUI Plus does not include an advertising SDK, analytics SDK, account system, cloud synchronization, or the Android `INTERNET` permission.

The optional Notification Listener service is used only to read notification metadata locally for the custom status-bar notification icons. Phone state, Wi-Fi, connectivity, and overlay data used by the status-bar renderer are processed locally.

Shizuku is used for the privileged SystemUI shell operations required by the application. Backup files are handled through the Android system file picker and are not uploaded by SystemUI Plus.

### Licenses

AndroidX, Jetpack Compose, and Material 3 are distributed under the Apache License 2.0.

Shizuku is distributed under its own open-source license:
https://github.com/RikkaApps/Shizuku

Plus Jakarta Sans is distributed under the SIL Open Font License 1.1:
https://scripts.sil.org/OFL

Disabling the app's service notifications uses Android's `POST_NOTIFICATIONS` permission. When disabled, foreground-service notices are hidden from the notification drawer on Android 13+; Android may still show the running service in the system Active apps/Task Manager.

Battery optimization exemption uses Android's `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` flow. This reduces standard background power restrictions but cannot override every manufacturer-specific process killer.

Source repository:
https://github.com/aldiandrew/SystemUIPlus

## Developer

Insomdroid

## Repository

https://github.com/aldiandrew/SystemUIPlus
