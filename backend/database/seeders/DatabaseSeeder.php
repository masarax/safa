<?php

namespace Database\Seeders;

use App\Models\AppVersion;
use App\Models\SafaApiKey;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed production-safe reference/configuration data and the workspace
     * foundation for an existing SuperAdmin. This seeder is intentionally
     * idempotent and never creates sample business records or deletes data.
     */
    public function run(): void
    {
        $apiKey = trim((string) env('SAFA_API_KEY', ''));
        $apiSecret = trim((string) env('SAFA_API_SECRET', ''));

        // API credentials remain server-managed secrets. Missing credentials
        // must not prevent the website/SuperAdmin bootstrap from completing,
        // and no insecure default credential is ever generated or committed.
        if ($apiKey !== '' && $apiSecret !== '') {
            SafaApiKey::updateOrCreate(
                ['client_name' => 'SAFA Mobile Client'],
                [
                    'account_id' => null,
                    'api_key' => $apiKey,
                    'api_secret' => $apiSecret,
                    'is_active' => true,
                ]
            );
        }

        AppVersion::updateOrCreate(
            ['platform' => 'android'],
            [
                'min_version_code' => 2,
                'latest_version_code' => 2,
                'force_update' => false,
                'update_url' => null,
            ]
        );

        $this->call(SuperAdminWorkspaceSeeder::class);
        $this->call(CoreReferenceSeeder::class);
    }
}
