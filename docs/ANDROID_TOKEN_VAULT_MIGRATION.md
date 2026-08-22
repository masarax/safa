# Android token vault migration

## Purpose

Safa v3 credential storage moves authentication secrets out of `EncryptedSharedPreferences` into a versioned vault encrypted directly by a non-exportable Android Keystore AES key.

The protected generation contains the access token, refresh token, session token, device token and fingerprint token as one authenticated payload. Non-secret application metadata remains in normal app-private `SharedPreferences`.

## Cryptographic contract

- Production key provider: `AndroidKeyStore`.
- Key algorithm: AES.
- Cipher: `AES/GCM/NoPadding`.
- A fresh randomized 12-byte nonce is generated for every generation write.
- The GCM authentication tag is 128 bits.
- Associated data binds ciphertext to `safa-token-vault|v3|auth_generation` so a ciphertext cannot be moved to another vault field without authentication failure.
- Raw key material is never exported or written to preferences, files, logs or backups.
- The app manifest keeps Android backup disabled.

## Atomic generation writes

All five sensitive credential values are encoded into one versioned JSON payload and encrypted once. A successful write is synchronously committed and immediately decrypted/read back before it is considered durable.

This avoids mixed generations such as a new access token with an old refresh or session token. Individual token updates use read-modify-write on the same generation boundary.

## Upgrade from the legacy vault

`LegacySecurePreferences` is a read-only compatibility adapter for installed releases that used AndroidX Security Crypto. New credentials are never written through that adapter.

Migration order is deliberately fail-safe:

1. Read the old plain pre-v2 preference snapshot and the current legacy encrypted preference snapshot. The encrypted snapshot wins for duplicate keys.
2. Build one complete `TokenGeneration` from the five sensitive legacy fields.
3. Encrypt and synchronously commit that generation into `TokenVault`.
4. Read it back and verify equality.
5. Copy only non-secret legacy metadata into `safa_secure_metadata_v3`.
6. Synchronously commit `secure_vault_v3_migration_complete=true`.
7. Only after both durable checkpoints succeed, delete the old preference files.

If the process stops after step 4, the legacy files remain. The next launch sees the already-authenticated v3 generation, completes metadata migration and then deletes the legacy stores. The migration is therefore idempotent and never requires a half-old/half-new token generation.

## Corruption and key invalidation

A GCM authentication failure, malformed envelope or unusable Keystore key is treated as an invalid local session. Safa does not return partial or unauthenticated credential data.

The controlled reset path removes the encrypted generation, deletes the v3 Keystore alias when possible, clears active-account and biometric-session metadata, and requires normal authentication to establish a fresh generation. No plaintext recovery fallback exists.

If the legacy encrypted vault itself is already unreadable during upgrade, its secret generation cannot be authenticated safely. Safa fails closed, preserves only non-secret recoverable metadata, retires the unreadable legacy store and requires login again.

## Security Crypto compatibility horizon

The production token read/write path no longer uses `EncryptedSharedPreferences` or `MasterKey`. AndroidX Security Crypto `1.1.0` remains temporarily as a migration-only reader so users upgrading directly from pre-v3 installed builds can decrypt their existing vault once.

Do not remove that compatibility reader in the same release that first ships the migration: doing so would make valid installed legacy ciphertext unreadable and force an unnecessary logout.

Removal gate for the dependency and `LegacySecurePreferences`:

- at least one production release carrying the v3 migration has completed its supported upgrade horizon;
- upgrade telemetry/support evidence shows the legacy migration population has converged;
- direct upgrades from any still-supported pre-v3 version are no longer required, or an explicit intermediate-upgrade policy is in place;
- migration and session-reset regression tests remain green after removal.

## Verification requirements

Android CI must cover:

- v3 generation round trip without plaintext secret persistence;
- wrong AAD and ciphertext modification rejection;
- successful legacy-to-v3 migration;
- crash after verified v3 write but before legacy cleanup, followed by successful idempotent rerun;
- runtime vault corruption/key failure causing a controlled session reset;
- preservation of device/fingerprint identity when normal logout clears authenticated session tokens;
- existing account-binding and refresh-token lifecycle tests.
