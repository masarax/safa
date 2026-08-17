<?php

namespace Database\Seeders;

use App\Models\AppVersion;
use Illuminate\Database\Seeder;

class ReleaseDataUpdateSeeder extends Seeder
{
    /**
     * Apply non-destructive, idempotent reference/workspace updates required by
     * the current release. Authentication identities and credential hashes are
     * intentionally outside this updater.
     */
    public function run(): void
    {
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
