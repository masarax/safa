<?php

namespace Database\Seeders;

use App\Models\Account;
use App\Models\AppVersion;
use App\Models\SafaApiKey;
use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;
use RuntimeException;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Provision deterministic system configuration and the initial SuperAdmin.
     * Credentials are seeded here, never in migrations or runtime .env lookups.
     * Re-running the seeder does not reset an existing user's password/PIN.
     */
    public function run(): void
    {
        $apiKey = trim((string) env('SAFA_API_KEY', ''));
        $apiSecret = trim((string) env('SAFA_API_SECRET', ''));

        if ($apiKey === '' || $apiSecret === '') {
            throw new RuntimeException('SAFA_API_KEY and SAFA_API_SECRET must be configured in backend .env before seeding.');
        }

        $account = Account::firstOrCreate(
            ['name' => 'SAFA Account'],
            ['owner_user_id' => null, 'balance' => 0]
        );

        SafaApiKey::updateOrCreate(
            ['client_name' => 'SAFA Mobile Client'],
            [
                'account_id' => $account->id,
                'api_key' => $apiKey,
                'api_secret' => $apiSecret,
                'is_active' => true,
            ]
        );

        $superAdmin = User::firstOrCreate(
            ['mobile' => '0536308965'],
            [
                'name' => 'Nazmus Sakib',
                'email' => 'sakib@masarax.com',
                'mobile' => '0536308965',
                'pin_hash' => Hash::make('123456'),
                'password' => Hash::make('123456'),
                'role' => User::ROLE_SUPERADMIN,
                'permissions' => User::defaultPermissions(true),
                'is_activated' => true,
            ]
        );

        if (!$superAdmin->isSuperAdmin()) {
            throw new RuntimeException('Initial SuperAdmin mobile is already assigned to a non-SuperAdmin account. Resolve the account manually before seeding.');
        }

        AppVersion::updateOrCreate(
            ['platform' => 'android'],
            [
                'min_version_code' => 1,
                'latest_version_code' => 1,
                'force_update' => false,
                'update_url' => null,
            ]
        );
    }
}
