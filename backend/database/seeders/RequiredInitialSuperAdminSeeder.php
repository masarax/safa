<?php

namespace Database\Seeders;

use App\Models\Account;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;

class RequiredInitialSuperAdminSeeder extends Seeder
{
    private const EMAIL = 'sakib.masarax@gmail.com';
    private const NAME = 'NAZMUS SAKIB';
    private const INITIAL_CREDENTIAL = '123456';

    public function run(): void
    {
        DB::transaction(function (): void {
            $existing = User::query()
                ->whereRaw('LOWER(email) = ?', [self::EMAIL])
                ->lockForUpdate()
                ->first();

            // First-time only: once this exact required identity exists, never
            // reset its operator-managed profile, role, credentials, permissions,
            // activation state, mobile number, or workspace on later releases.
            if ($existing) {
                return;
            }

            $user = User::query()->create([
                'name' => self::NAME,
                'email' => self::EMAIL,
                'mobile' => null,
                'password' => Hash::make(self::INITIAL_CREDENTIAL),
                'pin_hash' => Hash::make(self::INITIAL_CREDENTIAL),
                'role' => User::ROLE_SUPERADMIN,
                'is_activated' => true,
                'permissions' => User::permissionsForRole(User::ROLE_SUPERADMIN),
            ]);

            Account::query()->create([
                'name' => 'SAFA Account',
                'balance' => 0,
                'owner_user_id' => $user->id,
            ]);
        }, 3);
    }
}
