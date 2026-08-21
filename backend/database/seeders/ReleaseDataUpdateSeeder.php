<?php

namespace Database\Seeders;

use App\Models\AppVersion;
use Illuminate\Database\Seeder;

class ReleaseDataUpdateSeeder extends Seeder
{
    /**
     * Apply non-destructive, idempotent data required by the current release.
     * The required initial SuperAdmin seeder creates its identity only when the
     * configured email is absent; later release updates never reset its secret.
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

        $this->call(RequiredInitialSuperAdminSeeder::class);
        $this->call(SuperAdminWorkspaceSeeder::class);
        $this->call(CoreReferenceSeeder::class);
    }
}
