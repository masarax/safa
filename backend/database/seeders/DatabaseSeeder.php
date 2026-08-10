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
        // Initial credentials belong exclusively to the seeder.
        // Migrations and environment configuration do not define a default user.
        $adminMobile = '0536308965';
        $adminPin = '123456';
        $adminEmail = 'sakib@masarax.com';

        User::updateOrCreate(
            ['mobile' => $adminMobile],
            [
                'name'         => 'Nazmus Sakib',
                'email'        => $adminEmail,
                'password'     => bcrypt($adminPin),
                'pin_hash'     => bcrypt($adminPin),
                'role'         => 'superadmin',
                'is_activated' => true,
                'permissions'  => User::defaultPermissions(true),
            ]
        );

        $account = Account::firstOrCreate(['name' => 'SAFA Account']);

        $apiKey = env('SAFA_API_KEY', 'safa_key_' . bin2hex(random_bytes(16)));
        $apiSecret = env('SAFA_API_SECRET', 'safa_sec_' . bin2hex(random_bytes(32)));

        SafaApiKey::updateOrCreate(
            ['client_name' => 'SAFA Mobile Client'],
            [
                'account_id' => $account->id,
                'api_key' => $apiKey,
                'api_secret' => $apiSecret,
                'is_active' => true,
            ]
        );

        AppVersion::firstOrCreate(
            ['platform' => 'android'],
            ['min_version_code' => 1, 'latest_version_code' => 1, 'force_update' => false]
        );
    }
}
