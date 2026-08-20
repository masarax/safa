<?php

namespace App\Support;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

final class FirstRunSetupState
{
    public const TABLE = 'safa_installation_state';
    public const SESSION_CLAIM = 'safa_first_run_claim';

    public static function databaseInitializationRequired(): bool
    {
        try {
            // Only a genuinely pristine database may enter public first-run
            // initialization. A partial/legacy schema is never treated as fresh.
            return !Schema::hasTable('migrations')
                && !Schema::hasTable('users')
                && !Schema::hasTable('accounts');
        } catch (\Throwable $e) {
            report($e);
            return false;
        }
    }

    public static function adminCompletionRequired(): bool
    {
        try {
            $state = self::row();
            if ($state === null || $state->completed_at !== null || !Schema::hasTable('users')) {
                return false;
            }

            return !DB::table('users')
                ->where('role', 'superadmin')
                ->where('is_activated', true)
                ->exists();
        } catch (\Throwable $e) {
            report($e);
            return false;
        }
    }

    public static function shouldUseFileRuntimeStores(): bool
    {
        return self::databaseInitializationRequired() || self::adminCompletionRequired();
    }

    public static function claimMatches(?string $claim): bool
    {
        if ($claim === null || preg_match('/^[a-f0-9]{64}$/', $claim) !== 1) {
            return false;
        }

        try {
            $state = self::row();
            return $state !== null
                && $state->completed_at === null
                && is_string($state->bootstrap_claim_hash)
                && hash_equals($state->bootstrap_claim_hash, hash('sha256', $claim));
        } catch (\Throwable $e) {
            report($e);
            return false;
        }
    }

    public static function row(): ?object
    {
        if (!Schema::hasTable(self::TABLE)) {
            return null;
        }

        return DB::table(self::TABLE)->where('id', 1)->first();
    }
}
