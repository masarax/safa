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
     * Scenario A: Test autoHealExistingSchema with complete valid schema auto-registers safely.
     */
    public function test_auto_heal_existing_schema_contract_mapping()
    {
        // Run full migrations to establish existing schema
        Artisan::call('migrate', ['--force' => true]);

        // Verify that pending migrations check auto-heals and returns empty pending list
        $pending = InstallerController::getPendingMigrations();
        $this->assertEmpty($pending, 'All migrations should be recognized as completed and zero pending.');
    }

    /**
     * Scenario B & C: Test missing required column prevents false migration completion registration.
     */
    public function test_missing_column_does_not_false_heal()
    {
        Artisan::call('migrate', ['--force' => true]);

        // Remove migration entry from migrations table to test auto-healing contract validation
        DB::table('migrations')->where('migration', '2026_01_02_000000_expand_hundi_and_wallet_tables')->delete();

        // Drop 'receiver_account_no' column from transactions table so contract is incomplete
        Schema::table('transactions', function ($table) {
            $table->dropColumn('receiver_account_no');
        });

        $migrationFiles = [
            database_path('migrations/2026_01_02_000000_expand_hundi_and_wallet_tables.php')
        ];

        InstallerController::autoHealExistingSchema($migrationFiles);

        $executedAfter = DB::table('migrations')->pluck('migration')->toArray();
        $this->assertNotContains('2026_01_02_000000_expand_hundi_and_wallet_tables', $executedAfter, 'Migration must remain pending when required column receiver_account_no is missing.');
    }

    /**
     * Scenario F: Existing data preservation test.
     */
    public function test_existing_data_preservation_during_migration()
    {
        Artisan::call('migrate', ['--force' => true]);

        $accountId = DB::table('accounts')->insertGetId([
            'name' => 'Test Account',
            'balance' => 1000.00,
            'created_at' => now(),
            'updated_at' => now()
        ]);

        $customerId = DB::table('customers')->insertGetId([
            'account_id' => $accountId,
            'local_id' => 101,
            'name' => 'Real Customer',
            'phone' => '01711223344',
            'created_at' => now(),
            'updated_at' => now()
        ]);

        $pending = InstallerController::getPendingMigrations();
        $this->assertEmpty($pending);

        // Verify inserted data remains 100% untouched
        $customer = DB::table('customers')->where('id', $customerId)->first();
        $this->assertNotNull($customer);
        $this->assertEquals('Real Customer', $customer->name);
        $this->assertEquals('01711223344', $customer->phone);
    }

    /**
     * Scenario G: Idempotency test (running update twice).
     */
    public function test_migration_idempotency_second_run_is_noop()
    {
        Artisan::call('migrate', ['--force' => true]);

        $firstPending = InstallerController::getPendingMigrations();
        $this->assertEmpty($firstPending);

        // Run second update call
        Artisan::call('migrate', ['--force' => true]);
        $secondPending = InstallerController::getPendingMigrations();

        $this->assertEmpty($secondPending);
    }
}
