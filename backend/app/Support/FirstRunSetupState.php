<?php

namespace App\Support;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

final class FirstRunSetupState
{
    public const TABLE = 'safa_installation_state';
    public const SESSION_CLAIM = 'safa_first_run_claim';

    /**
     * Tables whose rows prove that this database has already been claimed by a
     * real installation. Infrastructure/reference tables may legitimately exist
     * before first-run setup, so their mere presence must not hide the setup UI.
     */
    private const CLAIMED_DATA_TABLES = [
        'users',
        'accounts',
        'customers',
        'suppliers',
        'transactions',
        'wallet_ledgers',
        'wallet_batches',
        'supplier_deposits',
        'expenses_incomes',
        'user_account_shares',
        'device_bindings',
        'auth_sessions',
    ];

    public static function databaseInitializationRequired(): bool
    {
        try {
            // A persisted installation-state row is the durable boundary between
            // the database phase and the first-admin/completed phases.
            if (self::row() !== null) {
                return false;
            }

            // Never expose public bootstrap over a database containing identity or
            // business data. This keeps legacy/partially initialized recovery fail
            // closed while allowing an empty schema prepared by an earlier deploy.
            foreach (self::CLAIMED_DATA_TABLES as $table) {
                if (self::tableHasRows($table)) {
                    return false;
                }
            }

            // A completely pristine database and an empty, unclaimed pre-created
            // schema are both valid first-run states. The bootstrap service still
            // runs only reviewed forward migrations and refuses destructive work.
            return true;
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

    private static function tableHasRows(string $table): bool
    {
        return Schema::hasTable($table) && DB::table($table)->limit(1)->exists();
    }
}
