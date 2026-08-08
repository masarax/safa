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
        // 1. Seed SuperAdmin: Nazmus Sakib (Mobile: 0536308965, 6-digit PIN: 123456)
        User::updateOrCreate(
            ['mobile' => '0536308965'],
            [
                'name'         => 'Nazmus Sakib',
                'email'        => 'sakib@masarax.com',
                'password'     => bcrypt('123456'),
                'pin_hash'     => bcrypt('123456'),
                'role'         => 'superadmin',
                'is_activated' => true,
                'permissions'  => User::defaultPermissions(true),
            ]
        );

        $account = Account::firstOrCreate(['name' => 'SAFA Account']);

        // 2. Seed API Key Record matching 256-bit Cryptographic Secret Keys
        $apiKey = env('SAFA_API_KEY', 'safa_key_7f8a9e0b1c2d3e4f5a6b7c8d9e0f1a2b');
        $apiSecret = env('SAFA_API_SECRET', 'safa_sec_9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b');

        SafaApiKey::updateOrCreate(
            ['api_key' => $apiKey],
            [
                'account_id'  => $account->id,
                'client_name' => 'SAFA Mobile Client',
                'api_secret'  => $apiSecret,
                'is_active'   => true,
            ]
        );

        AppVersion::firstOrCreate(
            ['platform' => 'android'],
            ['min_version_code' => 1, 'latest_version_code' => 1, 'force_update' => false]
        );
    }
}
