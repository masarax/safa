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
                ->first();

            // This provisioning path is intentionally first-time only. Once the
            // required identity exists, later releases must never reset its name,
            // role, password/PIN, permissions, activation state, or workspace.
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
