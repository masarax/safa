<?php

namespace Database\Seeders;

use App\Models\AppVersion;
use App\Models\SafaApiKey;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;
use RuntimeException;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed system configuration and the workspace foundation for any existing
     * SuperAdmin. Administrator credentials are deliberately never embedded in
     * source; identity is still provisioned explicitly with `safa:provision-admin`.
     */
    public function run(): void
    {
        $apiKey = trim((string) env('SAFA_API_KEY', ''));
        $apiSecret = trim((string) env('SAFA_API_SECRET', ''));

        if ($apiKey === '' || $apiSecret === '') {
            throw new RuntimeException('SAFA_API_KEY and SAFA_API_SECRET must be configured in backend .env before seeding.');
        }

        SafaApiKey::updateOrCreate(
            ['client_name' => 'SAFA Mobile Client'],
            [
                'account_id' => null,
                'api_key' => $apiKey,
                'api_secret' => $apiSecret,
                'is_active' => true,
            ]
        );

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
    }
}
