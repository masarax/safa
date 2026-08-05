# Enterprise Security & Cryptography Specification
**Project:** `com.safa.account`

This document defines the baseline security architecture for the multi-tenant Hundi accounting system, ensuring zero-knowledge and robust cryptographic isolation on Android endpoints.

## 1. Android Keystore & Key Generation
Hardware-backed keystore protects symmetric and asymmetric keys. Keys are explicitly bound to biometric authentication where possible.

```kotlin
// Android Keystore AES-256-GCM Key Generation
val keyGenerator = KeyGenerator.getInstance(
    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
)
val keyGenParameterSpec = KeyGenParameterSpec.Builder(
    "safa_master_key",
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
).apply {
    setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    setUserAuthenticationRequired(true)
    setUserAuthenticationParameters(300, KeyProperties.AUTH_BIOMETRIC_STRONG)
    setKeySize(256)
}.build()

keyGenerator.init(keyGenParameterSpec)
keyGenerator.generateKey()
```

## 2. SQLCipher Database Encryption
Local storage via Room Database is encrypted using SQLCipher. The database passphrase is derived via PBKDF2/Argon2 from a biometric-bound master key or user PIN.

```kotlin
val passphrase = generateOrRetrieveDatabasePassphrase() // Derived from Keystore
val factory = SupportFactory(passphrase)

val db = Room.databaseBuilder(
    applicationContext,
    SafaDatabase::class.java, "safa_encrypted_db"
)
.openHelperFactory(factory)
.build()
```

## 3. Network Security Config & Certificate Pinning
To prevent MitM attacks, strict certificate pinning is enforced via OkHttp and Android's `network_security_config.xml`.

**res/xml/network_security_config.xml:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.safa-account.com</domain>
        <pin-set expiration="2027-01-01">
            <pin digest="SHA-256">7HIpactkIAq2Y49orFOOQKurWxmmSFZhBCoQYcRhJ3Y=</pin>
            <!-- Backup Pin -->
            <pin digest="SHA-256">fwza0LRMXouZHRC8Ei+4PyuldPDcf3UKgO/04cDM1oE=</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

**OkHttp Configuration:**
```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("api.safa-account.com", "sha256/7HIpactkIAq2Y49orFOOQKurWxmmSFZhBCoQYcRhJ3Y=")
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

## 4. Field-Level AES-GCM Encryption
Highly sensitive fields (like unstructured notes or specific rates) are encrypted at the application level *before* being inserted into the database or transmitted to the backend.

```kotlin
fun encryptNote(plaintext: String, secretKey: SecretKey): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    
    // Concat IV and Ciphertext for storage/transmission
    return iv + ciphertext 
}
```

## 5. R8 ProGuard & Root Detection
App integrity checks ensure the execution environment is not compromised. RootBeer or Play Integrity API is used alongside R8 obfuscation.

**proguard-rules.pro:**
```text
-repackageclasses ''
-flattenpackagehierarchy ''
-keepattributes Signature,*Annotation*,Exceptions
-keep class com.safa.account.models.** { *; }
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
```
