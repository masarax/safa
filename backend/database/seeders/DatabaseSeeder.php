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
     * Seed system configuration and the development SuperAdmin account.
     *
     * The credentials below are intentionally development-only bootstrap
     * credentials for this new project. They should be changed before any
     * production deployment.
     */
    public function run(): void
    {
        $apiKey = trim((string) env('SAFA_API_KEY', ''));
        $apiSecret = trim((string) env('SAFA_API_SECRET', ''));

        if ($apiKey === '' || $apiSecret === '') {
            throw new RuntimeException('SAFA_API_KEY and SAFA_API_SECRET must be configured in backend .env before seeding.');
        }

        $adminMobile = '0536308965';
        $adminEmail = $adminMobile . '@safa.local';

        $admin = User::updateOrCreate(
            ['mobile' => $adminMobile],
            [
                'name' => 'Nazmus Sakib',
                'email' => $adminEmail,
                'password' => Hash::make('123456'),
                'pin_hash' => Hash::make('123456'),
                'role' => User::ROLE_SUPERADMIN,
                'is_activated' => true,
                'permissions' => User::defaultPermissions(true),
            ]
        );

        $account = Account::updateOrCreate(
            ['name' => 'SAFA Account'],
            ['owner_user_id' => $admin->id, 'balance' => 0]
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
