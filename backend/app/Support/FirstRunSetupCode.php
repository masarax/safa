<?php

namespace App\Support;

use Illuminate\Support\Facades\File;
use RuntimeException;

final class FirstRunSetupCode
{
    private const RELATIVE_PATH = 'app/private/safa-first-run-setup-code.txt';

    public static function ensure(): void
    {
        if (!FirstRunSetupState::databaseInitializationRequired()) {
            return;
        }

        $path = self::path();
        File::ensureDirectoryExists(dirname($path));
        if (is_file($path)) {
            return;
        }

        $code = strtoupper(bin2hex(random_bytes(16)));
        $handle = @fopen($path, 'x');
        if ($handle === false) {
            // Another request may have created it between the existence check and
            // exclusive create. Treat an existing private code as authoritative.
            if (is_file($path)) {
                return;
            }
            throw new RuntimeException('Unable to create the private first-run setup code.');
        }

        try {
            if (fwrite($handle, $code . PHP_EOL) === false) {
                throw new RuntimeException('Unable to write the private first-run setup code.');
            }
        } finally {
            fclose($handle);
        }

        @chmod($path, 0600);
    }

    public static function verify(string $candidate): bool
    {
        $candidate = strtoupper(trim($candidate));
        if (preg_match('/^[A-F0-9]{32}$/', $candidate) !== 1 || !is_file(self::path())) {
            return false;
        }

        $stored = strtoupper(trim((string) @file_get_contents(self::path())));
        return preg_match('/^[A-F0-9]{32}$/', $stored) === 1
            && hash_equals($stored, $candidate);
    }

    public static function destroy(): void
    {
        if (is_file(self::path())) {
            @unlink(self::path());
        }
    }

    public static function path(): string
    {
        return storage_path(self::RELATIVE_PATH);
    }

    public static function operatorPath(): string
    {
        return 'backend/storage/' . self::RELATIVE_PATH;
    }
}
