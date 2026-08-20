<?php

namespace App\Support;

use Illuminate\Support\Facades\Hash;

/** Constant-work credential verification for public authentication surfaces. */
final class CredentialVerifier
{
    /**
     * Public, non-secret bcrypt hash used only when no account/hash exists.
     * Cost 12 matches Laravel's default bcrypt work factor used by this app.
     */
    public const DUMMY_BCRYPT_HASH = '$2y$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';

    public static function verify(string $credential, ?string $storedHash): bool
    {
        $hasStoredHash = is_string($storedHash) && trim($storedHash) !== '';
        $hash = $hasStoredHash ? $storedHash : self::DUMMY_BCRYPT_HASH;

        try {
            $matches = Hash::check($credential, $hash);
        } catch (\Throwable) {
            // Malformed legacy hashes still execute one supported bcrypt check so
            // the public failure path remains comparable to a missing identity.
            try {
                Hash::check($credential, self::DUMMY_BCRYPT_HASH);
            } catch (\Throwable) {
                // The framework bcrypt driver is expected to support this hash.
            }
            return false;
        }

        return $hasStoredHash && $matches;
    }
}
