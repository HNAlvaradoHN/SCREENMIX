# ScreenMix

ScreenMix is an independent Android screenshot workflow project.

It monitors newly created screenshots and presents quick actions to copy, share, save, or delete them. The current development goal is to improve clipboard reliability for **Copy & Delete** while keeping the original screenshot out of the gallery.

## Project status

ScreenMix is under active development. The current codebase starts from the open-source BoltShot project and is being separated into its own application identity before functional changes are introduced.

Current application identity:

- App name: **ScreenMix**
- Android application ID: `com.screenmix.app`
- Initial ScreenMix version: `0.1.0`
- Minimum Android version: Android 8.0 / API 26

## Privacy

ScreenMix is intended to operate locally on the Android device.

This public repository must not contain private user data, screenshots, credentials, signing keys, local configuration, or other personal files. Development artifacts and signing material should remain outside version control.

## Upstream attribution

ScreenMix is based on portions of **BoltShot**, originally released by its author under the MIT License.

The original MIT copyright and permission notice are preserved in [LICENSE](LICENSE), as required by that license.

ScreenMix is an independent project and is not presented as an official BoltShot release or update.

## Build

The project uses Android Gradle tooling and Kotlin/Compose.

A standard debug build is intended to be produced with:

```bash
./gradlew assembleDebug
```

Build/signing setup will be finalized as part of the ScreenMix migration.

## Automatic restart

If screenshot monitoring was enabled before a reboot, ScreenMix listens for Android's `BOOT_COMPLETED` broadcast and starts the monitor service again automatically. The same recovery path is used after an app update through `MY_PACKAGE_REPLACED`.

A device reboot does not normally require the user to grant ScreenMix's runtime permissions again. Android/OEM battery restrictions or permissions manually revoked by the user can still prevent background operation.

## Development priorities

1. Complete the independent ScreenMix application identity.
2. Repair **Copy & Delete** so the clipboard remains usable after the gallery screenshot is removed.
3. Add safe temporary-file cleanup.
4. Build and test a separate ScreenMix APK on Android.
5. Verify behavior with multiple keyboards and target applications.

## License

See [LICENSE](LICENSE). The upstream MIT notice remains intact.
