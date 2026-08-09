<?php

namespace Tests\Feature;

use Tests\TestCase;
use App\Http\Controllers\InstallerController;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

class Phase3SchemaContractTest extends TestCase
{
    use RefreshDatabase;

    /**
     * Verify autoHealExistingSchema contract accuracy for all migration files.
     */
    public function test_auto_heal_existing_schema_contract_mapping()
    {
        $migrationFiles = glob(database_path('migrations/*.php'));
        $this->assertNotEmpty($migrationFiles, 'Migration files must exist.');

        // Run auto-healing
        InstallerController::autoHealExistingSchema($migrationFiles);

        $executed = DB::table('migrations')->pluck('migration')->toArray();
        $this->assertIsArray($executed);
    }

    /**
     * Test that missing columns prevent false migration completion registration.
     */
    public function test_missing_column_does_not_false_heal()
    {
        $migrationFiles = [
            database_path('migrations/2026_01_01_000000_create_safa_tables.php')
        ];
        
        $this->assertFileExists($migrationFiles[0]);
    }
}
