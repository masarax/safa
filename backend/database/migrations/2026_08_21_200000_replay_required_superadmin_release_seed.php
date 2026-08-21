<?php

use Illuminate\Database\Migrations\Migration;

return new class extends Migration
{
    /**
     * This marker migration intentionally performs no direct data writes.
     *
     * Its presence re-opens the release update gate for installations that
     * completed the previous release before the required SuperAdmin row was
     * actually persisted. DatabaseUpdateService runs ReleaseDataUpdateSeeder
     * immediately after forward migrations, and that idempotent seeder creates
     * the required account only when it is absent.
     */
    public function up(): void
    {
        // Intentionally empty. ReleaseDataUpdateSeeder owns the data write.
    }

    public function down(): void
    {
        // Never delete or mutate a privileged production identity on rollback.
    }
};
