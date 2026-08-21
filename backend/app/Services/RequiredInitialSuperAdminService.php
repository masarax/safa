<?php

namespace App\Services;

use App\Models\Account;
use App\Models\User;
use App\Support\CredentialVerifier;
use App\Support\FirstRunSetupState;
use App\Support\OneTimeFrontendMigrationState;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use RuntimeException;

final class RequiredInitialSuperAdminService
{
    public const NAME = 'NAZMUS SAKIB';
    public const EMAIL = 'sakib.masarax@gmail.com';
    public const INITIAL_PIN = '123456';

    public function needsProvisioning(): bool
    {
        if (!Schema::hasTable('users')) {
            return true;
        }

        $user = User::query()
            ->whereRaw('LOWER(email) = ?', [self::EMAIL])
            ->first();

        if (!$user instanceof User) {
            return true;
        }

        return trim((string) $user->name) !== self::NAME
            || !$user->isSuperAdmin()
            || !(bool) $user->is_activated
            || !CredentialVerifier::verify(self::INITIAL_PIN, [
                $user->pin_hash,
                $user->password,
            ]);
    }

    public function provisionOnce(): User
    {
        if (!OneTimeFrontendMigrationState::required()) {
            throw new RuntimeException('Required initial SuperAdmin provisioning is no longer available.');
        }
        if (!Schema::hasTable('users')) {
            throw new RuntimeException('Users table is unavailable after data migration.');
        }

        return DB::transaction(function (): User {
            $user = User::query()
                ->whereRaw('LOWER(email) = ?', [self::EMAIL])
                ->lockForUpdate()
                ->first();

            $hash = Hash::make(self::INITIAL_PIN);
            $attributes = [
                'name' => self::NAME,
                'email' => self::EMAIL,
                'password' => $hash,
                'pin_hash' => $hash,
                'role' => User::ROLE_SUPERADMIN,
                'is_activated' => true,
                'permissions' => User::permissionsForRole(User::ROLE_SUPERADMIN),
            ];

            if ($user instanceof User) {
                $user->fill($attributes);
                $user->save();
            } else {
                $user = User::query()->create($attributes + ['mobile' => null]);
            }

            // A completely new installation still needs one usable account after
            // the automatic owner is provisioned. Existing business databases are
            // never modified with an extra account merely because the owner row was
            // missing.
            if (Schema::hasTable('accounts') && Account::query()->count() === 0) {
                Account::query()->create([
                    'name' => 'SAFA Account',
                    'balance' => 0,
                    'owner_user_id' => $user->id,
                ]);
            }

            if (Schema::hasTable(FirstRunSetupState::TABLE)) {
                DB::table(FirstRunSetupState::TABLE)
                    ->where('id', 1)
                    ->whereNull('completed_at')
                    ->update([
                        'completed_at' => now(),
                        'updated_at' => now(),
                    ]);
            }

            return $user;
        }, 3);
    }
}
