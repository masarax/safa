<?php

namespace Database\Seeders;

use App\Models\SafaApiKey;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed production-safe reference/configuration data and workspace state.
     * When run interactively and no Super Admin exists, the first administrator
     * is prompted for securely instead of being read from source or environment.
     */
    public function run(): void
    {
        $this->call(InitialSuperAdminSeeder::class);

        $apiKey = trim((string) env('SAFA_API_KEY', ''));
        $apiSecret = trim((string) env('SAFA_API_SECRET', ''));

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

        $this->call(ReleaseDataUpdateSeeder::class);
    }
}
