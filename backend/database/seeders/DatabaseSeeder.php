<?php

namespace Database\Seeders;

use App\Models\Account;
use App\Models\AppVersion;
use App\Models\SafaApiKey;
use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    public function run(): void
    {
        User::firstOrCreate(
            ['email' => 'test@example.com'],
            ['name' => 'Test User', 'password' => bcrypt('password')]
        );

        User::firstOrCreate(
            ['mobile' => '01700000000'],
            [
                'name'         => 'Super Admin',
                'email'        => 'superadmin@safa.local',
                'password'     => bcrypt('123456'),
                'role'         => 'superadmin',
                'is_activated' => false,
                'permissions'  => User::defaultPermissions(true),
            ]
        );

        $account = Account::firstOrCreate(['name' => 'SAFA Dev Account']);

        SafaApiKey::firstOrCreate(
            ['api_key' => env('SAFA_API_KEY', 'safa_test_api_key_2026')],
            [
                'account_id'  => $account->id,
                'client_name' => 'Android App Dev',
                'api_secret'  => env('SAFA_API_SECRET', 'safa_test_secret_32byteslong_2026'),
                'is_active'   => true,
            ]
        );

        AppVersion::firstOrCreate(
            ['platform' => 'android'],
            ['min_version_code' => 1, 'latest_version_code' => 1, 'force_update' => false]
        );
    }
}
