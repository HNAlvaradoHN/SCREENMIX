# ScreenMix release signing

This repository is public. Release signing material must never be committed, uploaded as a normal repository file, pasted into issues, or printed in workflow logs.

## One-time signing key

Create the signing key on a trusted local computer. Keep the original keystore offline in at least two secure backups.

A privacy-friendly example that does not embed a personal name or location in the public signing certificate:

```bash
keytool -genkeypair -v \
  -keystore screenmix-release.jks \
  -storetype JKS \
  -alias screenmix \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=ScreenMix"
```

Choose strong, unique passwords when prompted. Do not reuse an account password.

The generated `screenmix-release.jks` is ignored by Git and must remain private.

## GitHub Actions secrets

In the GitHub repository, open **Settings → Secrets and variables → Actions** and create these repository secrets:

- `SCREENMIX_KEYSTORE_BASE64`
- `SCREENMIX_KEYSTORE_PASSWORD`
- `SCREENMIX_KEY_ALIAS`
- `SCREENMIX_KEY_PASSWORD`

Use `screenmix` for `SCREENMIX_KEY_ALIAS` if the example command above was used.

Convert the keystore to one-line Base64 locally and paste only that output into `SCREENMIX_KEYSTORE_BASE64`.

Linux:

```bash
base64 -w 0 screenmix-release.jks
```

macOS:

```bash
base64 < screenmix-release.jks | tr -d '\n'
```

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("screenmix-release.jks"))
```

Do not save the Base64 value in this repository.

## Publishing a release

1. Confirm `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Commit and push the final code.
3. Create and push a tag that exactly matches `v<versionName>`, for example `v0.3.2`.
4. The `Android Release` workflow:
   - checks that the tag matches the app version;
   - reconstructs the keystore only inside the temporary GitHub runner;
   - builds the signed Release APK;
   - verifies the APK signature with `apksigner`;
   - generates a SHA-256 checksum;
   - publishes both files to GitHub Releases;
   - removes the temporary keystore.

If any required secret is missing, the release job fails instead of publishing an unsigned APK.

## Key safety

The signing key defines the update identity of the APK. Future ScreenMix updates must use the same signing key so Android can install them over an existing installation.

Never:

- commit or upload the keystore to the public repository;
- paste signing passwords into source files, workflow YAML, issues, discussions, or release notes;
- include identifying personal information in the signing certificate unless intentionally public;
- delete the only copy of the keystore.

Keep at least two encrypted/offline backups of the keystore and store the passwords separately.
