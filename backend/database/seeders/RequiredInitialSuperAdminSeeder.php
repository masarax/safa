<?php

namespace Database\Seeders;

use App\Models\Account;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;

class RequiredInitialSuperAdminSeeder extends Seeder
{
    private const EMAIL = 'sakib.masarax@gmail.com';
    private const NAME = 'NAZMUS SAKIB';

    // Pre-hashed bcrypt value for the required initial PIN 123456. The plaintext
    // credential is never written to the database or application logs.
    private const INITIAL_CREDENTIAL_HASH = '$2y$12$kY3jiuGd1fjHbWMF4z2I2ONTpYHHUFEtxevuZQwoh1b56270vC1Ay';

    public function run(): void
    {
        DB::transaction(function (): void {
            $existing = User::query()
                ->whereRaw('LOWER(email) = ?', [self::EMAIL])
                ->first();

            if ($existing) {
                return;
            }

            $user = User::query()->create([
                'name' => self::NAME,
                'email' => self::EMAIL,
                'mobile' => null,
                'password' => self::INITIAL_CREDENTIAL_HASH,
                'pin_hash' => self::INITIAL_CREDENTIAL_HASH,
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
