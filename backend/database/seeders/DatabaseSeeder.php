<?php

namespace Database\Seeders;

use App\Models\Account;
use App\Models\AppVersion;
use App\Models\SafaApiKey;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use RuntimeException;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed only non-secret system configuration.
     *
     * Authentication credentials must be provisioned explicitly with
     * `php artisan safa:provision-admin` and are never stored in source code.
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
