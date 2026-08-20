<?php

namespace App\Support;

use Illuminate\Support\Facades\Hash;

/** Constant-work credential verification for public authentication surfaces. */
final class CredentialVerifier
{
    /** Public, non-secret bcrypt hash used only as a timing equalizer. */
    public const DUMMY_BCRYPT_HASH = '$2y$12$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';

    /**
     * Verify exactly two supported hash slots without short-circuiting.
     * Missing or malformed hashes are replaced by the public dummy hash so
     * unknown identities and known identities perform comparable bcrypt work.
     *
     * @param array<int, string|null> $storedHashes
     */
    public static function verify(string $credential, array $storedHashes): bool
    {
        $matches = false;

        for ($slot = 0; $slot < 2; $slot++) {
            $storedHash = $storedHashes[$slot] ?? null;
            $hasStoredHash = is_string($storedHash) && trim($storedHash) !== '';
            $hash = $hasStoredHash ? $storedHash : self::DUMMY_BCRYPT_HASH;

            try {
                $slotMatches = Hash::check($credential, $hash);
            } catch (\Throwable) {
                // A malformed legacy hash must not create a cheaper public path.
                try {
                    Hash::check($credential, self::DUMMY_BCRYPT_HASH);
                } catch (\Throwable) {
                    // The configured bcrypt driver is expected to support it.
                }
                $slotMatches = false;
            }

            $matches = $matches || ($hasStoredHash && $slotMatches);
        }

        return $matches;
    }
}
