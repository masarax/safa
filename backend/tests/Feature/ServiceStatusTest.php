<?php

namespace Tests\Feature;

use Illuminate\Database\Schema\Blueprint;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class ServiceStatusTest extends TestCase
{
    use RefreshDatabase;

    public function test_browser_root_redirects_to_login_without_exposing_health_details(): void
    {
        $this->get('/')
            ->assertRedirect(route('safa.login'))
            ->assertDontSee('checks');
    }

    public function test_api_health_is_unauthenticated_and_stable(): void
    {
        $this->getJson('/api/auth/health')
            ->assertOk()
            ->assertExactJson([
                'status' => 'ok',
                'service' => 'SAFA API',
                'build' => 'development',
                'checks' => [
                    'runtime' => true,
                    'database' => true,
                    'schema' => true,
                    'cache' => true,
                    'storage' => true,
                    'build' => true,
                ],
            ]);
    }

    public function test_api_health_fails_when_a_required_financial_migration_is_missing(): void
    {
        Schema::table('transactions', function (Blueprint $table) {
            $table->dropColumn('sar_collected');
        });

        $this->assertSchemaDegraded();
    }

    public function test_api_health_fails_when_customer_runtime_schema_is_incomplete(): void
    {
        Schema::table('customers', function (Blueprint $table) {
            $table->dropColumn('address');
        });

        $this->assertSchemaDegraded();
    }

    public function test_api_health_fails_when_supplier_runtime_schema_is_incomplete(): void
    {
        Schema::table('suppliers', function (Blueprint $table) {
            $table->dropColumn('phone');
        });

        $this->assertSchemaDegraded();
    }

    public function test_api_health_fails_when_wallet_runtime_schema_is_incomplete(): void
    {
        Schema::table('wallet_ledgers', function (Blueprint $table) {
            $table->dropColumn('name');
        });

        $this->assertSchemaDegraded();
    }

    public function test_api_health_fails_when_sync_reconciliation_schema_is_incomplete(): void
    {
        Schema::table('sync_mutations', function (Blueprint $table) {
            $table->dropColumn('operation');
        });

        $this->assertSchemaDegraded();
    }

    public function test_api_health_fails_when_account_settings_schema_is_incomplete(): void
    {
        Schema::table('system_settings', function (Blueprint $table) {
            $table->dropColumn('app_name');
        });

        $this->assertSchemaDegraded();
    }

    public function test_api_health_fails_when_account_authorization_schema_is_incomplete(): void
    {
        Schema::table('user_account_shares', function (Blueprint $table) {
            $table->dropColumn('permissions_override');
        });

        $this->assertSchemaDegraded();
    }

    public function test_api_health_fails_when_sync_unique_identity_constraint_is_missing(): void
    {
        Schema::table('sync_mutations', function (Blueprint $table) {
            $table->dropUnique(['account_id', 'mutation_id']);
        });

        $this->assertSchemaDegraded();
    }

    public function test_api_health_fails_when_account_settings_uniqueness_is_missing(): void
    {
        // MySQL may select the single-column unique index to support the
        // account_id foreign key. Remove that dependency first so this test can
        // model only the missing uniqueness capability that readiness owns.
        if (DB::connection()->getDriverName() === 'mysql') {
            Schema::table('system_settings', function (Blueprint $table) {
                $table->dropForeign(['account_id']);
            });
        }

        Schema::table('system_settings', function (Blueprint $table) {
            $table->dropUnique('system_settings_account_id_unique');
        });

        $this->assertSchemaDegraded();
    }

    private function assertSchemaDegraded(): void
    {
        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJsonPath('status', 'degraded')
            ->assertJsonPath('checks.database', true)
            ->assertJsonPath('checks.schema', false);
    }
}
