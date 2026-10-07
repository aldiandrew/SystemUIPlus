# SystemUI Plus

SystemUI Plus is a unified Android application that combines the ClockOS and Duos status-bar customization features into one app.

It is designed for non-root devices and does not require Xposed or Accessibility permission. Shizuku is used for the privileged SystemUI shell operations required to control the native status-bar clock.

## Current features

### Unified custom status bar

- One combined SystemUI Plus controller for the custom clock and custom indicators.
- Native SystemUI can be disabled in portrait mode while SystemUI Plus renders the custom elements.
- Landscape mode intentionally restores and keeps the native SystemUI status bar.
- Automatic state recovery/restart handling when the app or configuration is recreated.
- A Quick Settings tile for Duos/custom status-bar control.

### Clock customization

- 12-hour or 24-hour clock format.
- Optional date display.
- Multiple date formats, including day-of-week variants.
- Normal, lowercase, and uppercase date styles.
- Adjustable clock size from 10sp to 22sp.
- Automatic clock positioning or manual horizontal/vertical positioning.
- Horizontal clock position: -100dp to +100dp.
- Vertical clock position: -20dp to +20dp.
- Reset clock position to the default automatic position.
- Live status-bar preview.

### Status-bar logo

The clock area can optionally display a logo on the left or right side of the clock.

Available logo styles:

- Sakura
- Slash
- Apple
- Beats
- Biohazard
- Heart
- ROG
- Windows

### Custom indicators

- Three visual styles:
  - Duo
  - Compact
  - Pill
- Adjustable indicator size from 28dp to 60dp.
- Automatic indicator positioning or manual positioning.
- Horizontal indicator offset: -24dp to +24dp.
- Vertical indicator offset: -24dp to +24dp.
- Reset indicator position to the default automatic position.
- Live indicator preview.

### Notification icons

- Optional Notification Listener integration for custom status-bar notification icons.
- Notification icon handling is grouped to follow Android notification-group behavior.
- Notification metadata is processed locally for rendering.

### Permissions and setup

The app includes a first-run onboarding flow with:

- Language selection on the first screen.
- Animated multi-step navigation.
- Skip available from the second screen onward.
- Acknowledgement of the non-root, non-Xposed, and non-Accessibility design.
- Shizuku setup/status guidance.
- Theme preferences.
- Pure-black theme option.
- Direct setup actions for Shizuku, notification access, overlay permission, and phone-state permission.
- Final feature overview and persistent onboarding completion.

The selected onboarding language can be either the device language or English.

### Appearance

- Material 3 interface.
- Dynamic device accent colors / Monet integration.
- Follow system, always dark, or always light theme.
- Optional pure-black dark theme.
- Rounded cards and gradient surfaces are shared between onboarding and the main app for visual continuity.

### Settings and maintenance

- Settings backup and restore through the Android system file picker.
- Battery optimization exemption option.
- Optional disabling of the app's service notifications.
- Persistent settings for clock, indicators, language, theme, and onboarding completion.

## Requirements

- Android 13 (API 33) or newer.
- Shizuku for privileged SystemUI operations.
- Display-over-other-apps permission for custom overlays.
- Notification access for custom notification icons.
- Phone-state permission where required by the indicator renderer.

Root, Xposed, and Accessibility permission are not required.

## Privacy

SystemUI Plus does not include an advertising SDK, analytics SDK, account system, cloud synchronization, or the Android `INTERNET` permission.

The optional Notification Listener service is used only to read notification metadata locally for the custom status-bar notification icons. Phone state, Wi-Fi, connectivity, and overlay information used by the status-bar renderer are processed locally.

Shizuku is used for the privileged SystemUI shell operations required by the application.

Backup files are handled through the Android system file picker and are not uploaded by SystemUI Plus.

## Permissions used

The application declares only permissions required for its Android services and status-bar functionality, including:

- Shizuku API access
- Display over other apps
- Foreground services
- Foreground service special-use
- Network state information
- Phone state
- Wi-Fi state
- Notification posting
- Battery optimization exemption flow

The app does not request general Internet access.

## Licenses

AndroidX, Jetpack Compose, and Material 3 are distributed under the Apache License 2.0.

Shizuku is distributed under its own open-source license:
https://github.com/RikkaApps/Shizuku

Plus Jakarta Sans is distributed under the SIL Open Font License 1.1:
https://scripts.sil.org/OFL

## Source repository

https://github.com/aldiandrew/SystemUIPlus

## Developer

Insomdroid
