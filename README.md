# ScreenMix

ScreenMix is an independent Android screenshot workflow project.

It monitors newly created screenshots and presents two focused actions: **Copy & Delete** and **Copy & Save**. Sharing actions have been intentionally removed to keep the workflow simple and reliable.

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

GitHub Actions builds the debug APK automatically from `main` so compile errors can be caught without publishing a release.

## Automatic restart

If screenshot monitoring was enabled before a reboot, ScreenMix listens for Android's `BOOT_COMPLETED` broadcast and starts the monitor service again automatically. The same recovery path is used after an app update through `MY_PACKAGE_REPLACED`.

A device reboot does not normally require the user to grant ScreenMix's runtime permissions again. Android/OEM battery restrictions or permissions manually revoked by the user can still prevent background operation.

## Development priorities

1. Keep **Copy & Delete** reliable after the original gallery image is removed.
2. Keep **Copy & Save** unchanged and dependable.
3. Maintain safe temporary-file cleanup.
4. Keep screenshot monitoring reliable across app restarts and device reboots.
5. Build and test ScreenMix independently on Android.

## License

See [LICENSE](LICENSE). The upstream MIT notice remains intact.
