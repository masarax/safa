# SAFA System Skill 05: Enterprise Security & Data Protection Spec

## 1. Local Encryption (SQLCipher & Android KeyStore)
- The local SQLite Room database is encrypted using **SQLCipher 4.5.4**.
- Passphrase keys are stored securely in the hardware-backed **Android KeyStore**.
- Biometric authentication (`BiometricPrompt` in `ui/BiometricHelper.kt`) guards access to key retrieval and app unlocks.

---

## 2. API Communication & HMAC Signatures
All network communications between the Android client and Laravel API use HTTPS with HMAC-SHA256 request signing:

```
Header: X-SAFA-Signature = HMAC-SHA256(Payload + Timestamp, SAFA_API_SECRET)
Header: X-SAFA-Key = SAFA_API_KEY
Header: X-SAFA-Timestamp = UNIX_TIMESTAMP
```

---

## 3. Environment & Secret Management
- Secrets must **NEVER** be committed to version control.
- Android secrets are loaded via secrets-gradle-plugin from `app/.env` (ignored by git).
- Backend environment variables are configured in `backend/.env` (ignored by git).
- Reference templates are maintained in `.env.example` files containing non-sensitive placeholder variables.
