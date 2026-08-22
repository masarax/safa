# Android Play release and signing runbook

## Immutable release identity

SAFA's Android package is `com.safa.account`. Every publishable build must come from the repository release workflow at the exact commit SHA that has already passed the full Android Production CI gate. One eligible invocation produces both:

- a signed minified APK for controlled direct/internal distribution; and
- a signed Android App Bundle (`.aab`) for Google Play.

`SAFA_VERSION_CODE` must increase monotonically beyond `release/android-last-published-version-code.txt`. `SAFA_VERSION_NAME`, commit SHA, Git ref, APK/AAB filenames, signing certificate SHA-256, artifact SHA-256 values and the CycloneDX Android SBOM are retained together in the release artifact.

## Signing ownership

The repository never stores a keystore, password or raw signing key. GitHub Actions receives the release/upload keystore only through protected repository/environment secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_STORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

For Google Play production distribution, enable **Play App Signing**. Google holds the app-signing key used for device APKs; SAFA's protected CI key is the upload/release key used to authenticate submitted AABs. Restrict Play Console and GitHub release-secret administration to the smallest approved operator group with MFA.

Record the Play App Signing certificate and upload certificate SHA-256 fingerprints in the organization's password/secret-management system, not Git. Compare the CI `build-identity.txt` certificate fingerprint with the approved upload certificate before uploading a production AAB.

## Upload-key loss or compromise

If the upload key is lost or suspected compromised:

1. stop release workflow use of the affected secret;
2. do not rotate package name or create a second Play application;
3. use the Google Play Console upload-key reset/recovery process while retaining the Play App Signing key;
4. create the replacement upload key outside Git, update only protected CI secrets and administrative records;
5. run a fresh full Android Production CI and release workflow; and
6. verify the replacement certificate fingerprint and artifact provenance before Play upload.

If the Play App Signing key itself requires upgrade/recovery, follow the Play Console key-management process and require two authorized operators to review the change.

## Track promotion

Production publication is intentionally **not automated** by the repository workflow. A human/operator approval boundary remains between creation of a signed AAB and Play publication.

Recommended promotion sequence:

1. **Internal testing** — upload the exact CI-produced AAB; verify install/upgrade, authentication, sync, offline behavior and crash reporting with production-like configuration.
2. **Closed testing** — expand to the approved tester cohort and monitor crash/ANR, API and sync SLOs.
3. **Production staged rollout** — begin with a small percentage, review telemetry and user-impact signals, then progressively increase.
4. **Full rollout** only after the staged release remains within the documented SLO/error budgets.

Never rebuild an AAB locally after testing. Promote the same checksum-verified artifact between tracks.

## Rollback / stop rollout

Google Play does not permit reusing/decreasing `versionCode`. For a bad staged release:

- stop/hold the rollout immediately;
- if a previous version is still serving unaffected users, keep it in place;
- prepare a corrected build with a higher `SAFA_VERSION_CODE` from a new reviewed PR;
- require full CI and produce new signed APK/AAB artifacts from the fixed SHA;
- publish the fixed AAB through internal/closed validation before replacing the bad production release.

Keep R8 `mapping.txt`, SBOM, checksum and build identity for every published version so Play crash traces can be deobfuscated and supply-chain evidence remains available.

## Pre-publication checklist

- exact release SHA is on `main` and full Android Production CI is green;
- release workflow eligibility validation passed for that SHA/tag;
- package name is exactly `com.safa.account`;
- `targetSdk` meets the current Google Play requirement (API 36 or newer for the 31 August 2026 policy baseline; update through platform-maintenance issue #240 as requirements change);
- `versionCode` is higher than every previously published code;
- APK signature verification and AAB `jarsigner -verify -strict` passed;
- APK and AAB checksums, SBOM and `build-identity.txt` are retained together;
- signing certificate fingerprint matches the approved upload certificate;
- R8 mapping is retained for the exact version;
- Android developer/organization identity, package registration, Play App Signing and Play Console access are current;
- privacy/data-safety declarations and release notes match the shipped behavior;
- internal/closed testing completed before production promotion;
- an operator is assigned to watch rollout telemetry and can stop rollout quickly.

Production Play credentials, service-account credentials and developer identity documents must remain outside this repository.
