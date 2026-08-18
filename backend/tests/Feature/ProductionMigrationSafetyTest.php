<?php

namespace Tests\Feature;

use App\Support\ProductionMigrationSafety;
use RuntimeException;
use Tests\TestCase;

class ProductionMigrationSafetyTest extends TestCase
{
    public function test_all_current_production_migration_up_methods_are_non_destructive(): void
    {
        $files = glob(database_path('migrations/*.php')) ?: [];
        $this->assertNotEmpty($files);

        foreach ($files as $file) {
            $violations = ProductionMigrationSafety::violations((string) file_get_contents($file));
            $this->assertSame([], $violations, basename($file) . ' violates the live migration safety contract.');
        }
    }

    public function test_historical_tenant_hardening_preserves_unmapped_share_rows_instead_of_deleting_them(): void
    {
        $migration = (string) file_get_contents(database_path('migrations/2026_08_12_000002_harden_tenant_audit_integrity.php'));

        $this->assertStringContainsString("whereNull('account_id')->exists()", $migration);
        $this->assertStringContainsString('must be repaired before this migration can continue', $migration);
        $this->assertStringNotContainsString("whereNull('account_id')->delete()", $migration);
    }

    public function test_destructive_schema_and_data_operations_in_up_are_rejected(): void
    {
        $source = <<<'PHP'
<?php
return new class {
    public function up(): void
    {
        Schema::dropIfExists('legacy_table');
        Schema::table('users', function ($table) {
            $table->dropColumn('legacy_value');
            $table->renameColumn('name', 'display_name');
        });
        DB::table('transactions')->delete();
        DB::statement('TRUNCATE TABLE audit_logs');
    }

    public function down(): void {}
};
PHP;

        $violations = ProductionMigrationSafety::violations($source);

        $this->assertContains('dropIfExists()', $violations);
        $this->assertContains('dropColumn()', $violations);
        $this->assertContains('renameColumn()', $violations);
        $this->assertContains('delete()', $violations);
        $this->assertContains('raw TRUNCATE', $violations);
    }

    public function test_rollback_only_destructive_code_does_not_block_forward_update(): void
    {
        $source = <<<'PHP'
<?php
return new class {
    public function up(): void
    {
        Schema::table('customers', function ($table) {
            $table->string('external_reference')->nullable();
        });
        DB::table('customers')->whereNull('external_reference')->update(['external_reference' => '']);
    }

    public function down(): void
    {
        Schema::table('customers', function ($table) {
            $table->dropColumn('external_reference');
        });
        DB::table('customers')->delete();
    }
};
PHP;

        $this->assertSame([], ProductionMigrationSafety::violations($source));
    }

    public function test_raw_destructive_sql_in_up_is_rejected(): void
    {
        foreach ([
            "DB::statement('DROP TABLE legacy_rows');" => 'raw DROP',
            "DB::statement('ALTER TABLE users DROP COLUMN old_code');" => 'raw DROP',
            "DB::statement('DELETE FROM audit_logs');" => 'raw DELETE',
            "DB::statement('RENAME TABLE users TO old_users');" => 'raw RENAME TABLE',
        ] as $statement => $expected) {
            $source = "<?php return new class { public function up(): void { {$statement} } public function down(): void {} };";
            $this->assertContains($expected, ProductionMigrationSafety::violations($source));
        }
    }

    public function test_pending_migration_file_guard_fails_closed_before_artisan_migrate(): void
    {
        $name = '2099_12_31_235959_destructive_contract_probe';
        $path = database_path('migrations/' . $name . '.php');
        file_put_contents($path, <<<'PHP'
<?php
return new class {
    public function up(): void { Schema::drop('customers'); }
    public function down(): void {}
};
PHP);

        try {
            $this->expectException(RuntimeException::class);
            $this->expectExceptionMessage('Unsafe production migration blocked');
            ProductionMigrationSafety::assertPendingMigrationsAreSafe([$name]);
        } finally {
            @unlink($path);
        }
    }

    public function test_database_update_service_invokes_safety_guard_before_forward_migrate(): void
    {
        $service = (string) file_get_contents(app_path('Services/DatabaseUpdateService.php'));
        $guardPosition = strpos($service, 'ProductionMigrationSafety::assertPendingMigrationsAreSafe($pendingBefore)');
        $migratePosition = strpos($service, "Artisan::call('migrate', ['--force' => true])");

        $this->assertNotFalse($guardPosition);
        $this->assertNotFalse($migratePosition);
        $this->assertLessThan($migratePosition, $guardPosition);
    }
}
