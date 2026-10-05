# SystemUI Plus

SystemUI Plus combines the ClockOS and Duos status-bar customization features into one Android application.

**Developer: Insomdroid**

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

## Source Code

The complete source code is maintained in this repository:

https://github.com/aldiandrew/SystemUIPlus

The Android application source is under:
`app/src/main/java/`

## Privacy Policy

**Last updated: October 5, 2026**

SystemUI Plus is designed to process the information it needs locally on the device.

### Data collection

SystemUI Plus does not include an advertising SDK, analytics SDK, account system, or cloud synchronization service. The application does not request the Android `INTERNET` permission.

SystemUI Plus does not sell, rent, or share personal data with third parties.

### Notification access

The optional Notification Listener service is used to read notification metadata locally so the application can display notification icons in its custom status-bar renderer.

Notification content and metadata accessed for this purpose are processed locally by SystemUI Plus and are not uploaded by the application.

### Shizuku

Shizuku is used to execute the SystemUI shell commands required to hide or restore native status-bar elements and manage the custom portrait/landscape behavior.

SystemUI Plus does not use Shizuku to transmit data to a remote server.

### Overlay permission

The "Display over other apps" permission is required because the custom clock and system indicators are rendered with Android overlay windows.

### Phone state and connectivity permissions

Phone state, Wi-Fi, and connectivity information is read locally only for status-bar indicator rendering. It is not sent to a remote service by SystemUI Plus.

### Backup and restore

When the user creates a backup, SystemUI Plus writes the selected settings to a file through Android's system file picker.

When the user restores a backup, SystemUI Plus reads only the file selected by the user.

Backup files are not uploaded by SystemUI Plus.

### External links

The Source Code, Licenses, and Privacy Policy links in the application open GitHub or another external page in the user's browser. The browser and the linked website may have their own privacy policies and data practices.

### Shizuku availability

SystemUI Plus requests Shizuku permission only when it has not already been granted. The permission grant is managed by Shizuku and is not requested again merely because the Shizuku service is restarted.

While SystemUI Plus is already active, its custom clock and system-indicator overlay services can continue running if the Shizuku service temporarily stops. Privileged operations that change native SystemUI state, including disabling or restoring the native status bar, require Shizuku to be available again. SystemUI Plus therefore keeps the active custom mode instead of disabling its overlays when Shizuku temporarily goes offline.

## Licenses

SystemUI Plus uses open-source Android libraries and the bundled Plus Jakarta Sans font. Each dependency remains subject to its own license.

### AndroidX and Jetpack Compose

AndroidX and Jetpack Compose libraries are distributed under the Apache License 2.0.

https://www.apache.org/licenses/LICENSE-2.0

### Material 3

Jetpack Compose Material 3 is part of the AndroidX project and is distributed under the Apache License 2.0.

https://github.com/androidx/androidx

### Shizuku

SystemUI Plus uses the Shizuku API and provider.

https://github.com/RikkaApps/Shizuku

### Plus Jakarta Sans

The bundled Plus Jakarta Sans font is distributed under the SIL Open Font License 1.1.

https://scripts.sil.org/OFL

The build also includes the font license in its third-party notices.

## Repository

https://github.com/aldiandrew/SystemUIPlus
