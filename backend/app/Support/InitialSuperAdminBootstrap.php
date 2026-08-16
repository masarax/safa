<?php

namespace App\Support;

use App\Models\User;
use Illuminate\Support\Facades\Schema;

final class InitialSuperAdminBootstrap
{
    public const LOCK_KEY = 'safa:initial-superadmin-bootstrap';

    public static function schemaReady(): bool
    {
        try {
            if (!Schema::hasTable('users') || !Schema::hasTable('accounts')) {
                return false;
            }

            foreach (['id', 'name', 'email', 'password', 'mobile', 'pin_hash', 'role', 'is_activated'] as $column) {
                if (!Schema::hasColumn('users', $column)) {
                    return false;
                }
            }

            foreach (['id', 'owner_user_id', 'name', 'balance'] as $column) {
                if (!Schema::hasColumn('accounts', $column)) {
                    return false;
                }
            }

            return true;
        } catch (\Throwable $e) {
            report($e);
            return false;
        }
    }

    public static function privilegedUserExists(): bool
    {
        if (!self::schemaReady()) {
            return false;
        }

        try {
            return User::query()
                ->whereIn('role', [User::ROLE_SUPERADMIN, User::ROLE_ADMIN])
                ->exists();
        } catch (\Throwable $e) {
            report($e);
            return true;
        }
    }

    public static function available(): bool
    {
        return self::schemaReady() && !self::privilegedUserExists();
    }

    public static function maintenanceConfigured(): bool
    {
        return trim((string) config('safa.maintenance_token', '')) !== '';
    }
}
