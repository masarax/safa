<?php

namespace Database\Seeders;

use App\Models\Account;
use App\Models\AppVersion;
use App\Models\SafaApiKey;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;
use RuntimeException;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed only non-personal system configuration.
     *
     * Migrations are schema-only. Personal administrator credentials are
     * provisioned explicitly through `php artisan safa:provision-admin`.
     */
    public function run(): void
    {
        $apiKey = trim((string) env('SAFA_API_KEY', ''));
        if ($apiKey === '') {
            throw new RuntimeException('SAFA_API_KEY must be configured in the backend .env before seeding system data.');
        }

        $account = Account::firstOrCreate(
            ['name' => 'SAFA Account'],
            ['owner_user_id' => null, 'balance' => 0]
        );

        // The legacy api_secret column remains for database compatibility, but
        // the Android client never receives or stores this server-side secret.
        SafaApiKey::updateOrCreate(
            ['client_name' => 'SAFA Mobile Client'],
            [
                'account_id' => $account->id,
                'api_key' => $apiKey,
                'api_secret' => Str::random(64),
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
