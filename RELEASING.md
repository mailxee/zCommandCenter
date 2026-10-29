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

## GitHub secrets

Add these under **Settings → Secrets and variables → Actions → New repository secret**:

- `RELEASE_KEYSTORE_BASE64`: the upload keystore, base64-encoded on one line
  (`base64 -w0 upload-keystore.p12` on Linux, `base64 -i upload-keystore.p12` on macOS).
- `RELEASE_KEYSTORE_PASSWORD`: the keystore password.

Secrets are not exposed to workflows run from forks' pull requests, and the release workflow only
runs on a manual start or a version tag, so the key stays private when the repository is public.

## Building a release

1. `versionCode` is the workflow's run number, so each run can be uploaded (Play rejects a
   versionCode it has seen before). Change `versionName` in `app/build.gradle.kts` when you want
   the version users see to change.
2. Run **Actions → Release build → Run workflow**, or push a tag such as `v1.0.0`.
3. Download the `manage-everything-release` artifact and upload the `.aab` in the Play Console.

Use **Play App Signing** (the default for new apps): Google keeps the key that signs what users
install, and this upload key only proves uploads come from you. If the upload key is ever lost
or leaked, Google can reset it.

## Keep a backup of the upload key

Store the keystore file and its password somewhere safe outside GitHub (a password manager).
GitHub secrets can't be read back once saved.
