<?php

namespace Tests\Feature;

use App\Http\Controllers\DatabaseUpdateController;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class Phase3SchemaContractTest extends TestCase
{
    use RefreshDatabase;

    public function test_auto_heal_existing_schema_contract_mapping(): void
    {
        Artisan::call('migrate', ['--force' => true]);

        $pending = DatabaseUpdateController::pendingMigrations();

        $this->assertEmpty($pending, 'All migrations should be recognized as completed and zero pending.');
    }

    public function test_missing_column_does_not_false_heal(): void
    {
        Artisan::call('migrate', ['--force' => true]);

        DB::table('migrations')
            ->where('migration', '2026_01_02_000000_expand_safa_and_wallet_tables')
            ->delete();

        Schema::table('transactions', function ($table) {
            $table->dropColumn('receiver_account_no');
        });

        $pending = DatabaseUpdateController::pendingMigrations();

        $this->assertContains(
            '2026_01_02_000000_expand_safa_and_wallet_tables',
            $pending,
            'Migration must remain pending when a required schema column is absent.'
        );
        $this->assertDatabaseMissing('migrations', [
            'migration' => '2026_01_02_000000_expand_safa_and_wallet_tables',
        ]);
    }

    public function test_existing_data_preservation_during_migration_state_check(): void
    {
        Artisan::call('migrate', ['--force' => true]);

        $accountId = DB::table('accounts')->insertGetId([
            'name' => 'Test Account',
            'balance' => 1000.00,
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $customerId = DB::table('customers')->insertGetId([
            'account_id' => $accountId,
            'local_id' => 101,
            'name' => 'Real Customer',
            'phone' => '01711223344',
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        $pending = DatabaseUpdateController::pendingMigrations();
        $this->assertEmpty($pending);

        $customer = DB::table('customers')->where('id', $customerId)->first();
        $this->assertNotNull($customer);
        $this->assertSame('Real Customer', $customer->name);
        $this->assertSame('01711223344', $customer->phone);
    }

    public function test_migration_idempotency_second_run_is_noop(): void
    {
        Artisan::call('migrate', ['--force' => true]);

        $firstPending = DatabaseUpdateController::pendingMigrations();
        $this->assertEmpty($firstPending);

        Artisan::call('migrate', ['--force' => true]);
        $secondPending = DatabaseUpdateController::pendingMigrations();

        $this->assertEmpty($secondPending);
    }
}
