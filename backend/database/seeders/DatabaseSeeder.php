<?php

namespace Database\Seeders;

use App\Models\Account;
use App\Models\AppVersion;
use App\Models\SafaApiKey;
use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use RuntimeException;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed the clean initial application state.
     *
     * Personal bootstrap credentials intentionally live only here. They are
     * never inserted by migrations or read from INITIAL_SUPERADMIN_* env keys.
     */
    public function run(): void
    {
        $adminMobile = '0536308965';
        $adminPin = '123456';
        $adminEmail = 'sakib@masarax.com';

        $admin = User::updateOrCreate(
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

        $account = Account::updateOrCreate(
            ['name' => 'SAFA Account'],
            [
                'owner_user_id' => $admin->id,
                'balance'       => 0,
            ]
        );

        $apiKey = trim((string) env('SAFA_API_KEY', ''));
        $apiSecret = trim((string) env('SAFA_API_SECRET', ''));

        if ($apiKey === '' || $apiSecret === '') {
            throw new RuntimeException(
                'SAFA_API_KEY and SAFA_API_SECRET must be configured in the backend .env before running the database seeder.'
            );
        }

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
