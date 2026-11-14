# Releasing to Google Play

The app's ID on Google Play is `com.xeeshan.manage_everything`. It can never change once the app
is published.

## Debug builds

Debug builds are a separate app, `com.xeeshan.manage_everything.debug`, named "Manage Everything
Debug". It installs beside the app from Google Play with its own data, and each debug build
installs over the last. To try changes with real data, export to Excel from the Play app and
import into the debug app.

## Signing

- **Debug builds** are signed with `app/signing/debug.keystore`, a public key kept in the
  repository on purpose (standard debug passwords), so every debug build installs over the last.
- **Release builds** are signed with the private **upload key**, which is never in the repository.
  Gradle reads it from environment variables (see `signingConfigs` in `app/build.gradle.kts`):

  | Variable | Value |
  | --- | --- |
  | `RELEASE_KEYSTORE_FILE` | Path to the upload keystore (`.p12`) |
  | `RELEASE_KEYSTORE_PASSWORD` | Its password |
  | `RELEASE_KEY_ALIAS` | Optional; `upload` by default |
  | `RELEASE_KEY_PASSWORD` | Optional; the keystore password by default |

  Without `RELEASE_KEYSTORE_FILE`, a release build comes out unsigned.

## Building a release

There are no GitHub workflows: release builds are made locally (or in a Claude Code session) on
request.

1. Provide the upload keystore file and its password for that build only. Never commit them;
   `.gitignore` keeps keystores out of the repository.
2. Choose a `VERSION_CODE` higher than the last one uploaded to Play (Play rejects a versionCode
   it has seen before; the Play Console shows the last one). Change `versionName` in
   `app/build.gradle.kts` when you want the version users see to change.
3. Build:

   ```
   RELEASE_KEYSTORE_FILE=/path/to/upload-keystore.p12 RELEASE_KEYSTORE_PASSWORD=... \
   VERSION_CODE=12 bash gradlew bundleRelease
   ```

4. Upload `app/build/outputs/bundle/release/app-release.aab` in the Play Console.

Use **Play App Signing** (the default for new apps): Google keeps the key that signs what users
install, and this upload key only proves uploads come from you. If the upload key is ever lost
or leaked, Google can reset it.

## Keep a backup of the upload key

Store the keystore file and its password somewhere safe (a password manager). Without them you
can't sign an update; Google can reset a lost upload key, but that takes time.
