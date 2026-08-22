<?php

namespace App\Support;

use Illuminate\Database\QueryException;
use Throwable;

final class DatabaseConcurrency
{
    public const TRANSACTION_ATTEMPTS = 3;

    public static function isRetryable(Throwable $exception): bool
    {
        for ($current = $exception; $current !== null; $current = $current->getPrevious()) {
            if ($current instanceof QueryException) {
                $sqlState = (string) ($current->errorInfo[0] ?? '');
                $driverCode = (int) ($current->errorInfo[1] ?? 0);
                if (in_array($sqlState, ['40001', '40P01'], true) || in_array($driverCode, [1205, 1213], true)) {
                    return true;
                }
            }

            $message = strtolower($current->getMessage());
            if (str_contains($message, 'deadlock') || str_contains($message, 'lock wait timeout')) {
                return true;
            }
        }

        return false;
    }
}
