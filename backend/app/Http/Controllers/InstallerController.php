<?php

namespace App\Http\Controllers;

use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

/**
 * Retired installer compatibility shell.
 *
 * SAFA production is installer-less. Public installer/update routes are hard
 * 404s. The only retained functionality is non-destructive schema inspection
 * used by migration-audit tests and legacy boot diagnostics.
 */
class InstallerController extends Controller
{
    public function index() { return $this->retired(); }
    public function testDb() { return $this->retired(); }
    public function process() { return $this->retired(); }
    public function success() { return $this->retired(); }
    public function updateView() { return $this->retired(); }
    public function updateProcess() { return $this->retired(); }

    private function retired()
    {
        return response()->json([
            'status' => 'not_found',
            'message' => 'Not found.',
        ], 404);
    }

    public static function autoHealExistingSchema(array $migrationFiles): void
    {
        try {
            if (!Schema::hasTable('migrations')) {
                try {
                    Artisan::call('migrate:install');
                } catch (\Throwable) {
                    return;
                }
            }

            $executedMigrations = DB::table('migrations')->pluck('migration')->toArray();

            $migrationSchemaMap = [
                '0001_01_01_000000_create_users_table' => [
                    'users' => ['id', 'name', 'email', 'password'],
                    'password_reset_tokens' => ['email', 'token'],
                    'sessions' => ['id', 'user_id', 'payload', 'last_activity'],
                ],
                '0001_01_01_000001_create_cache_table' => [
                    'cache' => ['key', 'value', 'expiration'],
                    'cache_locks' => ['key', 'owner', 'expiration'],
                ],
                '0001_01_01_000002_create_jobs_table' => [
                    'jobs' => ['id', 'queue', 'payload', 'attempts'],
                    'job_batches' => ['id', 'name', 'total_jobs', 'pending_jobs', 'failed_jobs'],
                    'failed_jobs' => ['id', 'uuid', 'connection', 'queue', 'payload', 'exception'],
                ],
                '2026_01_01_000000_create_safa_tables' => [
                    'accounts' => ['id', 'name', 'balance'],
                    'customers' => ['id', 'account_id', 'local_id', 'name', 'phone'],
                    'suppliers' => ['id', 'account_id', 'local_id', 'name', 'phone'],
                    'transactions' => ['id', 'account_id', 'local_id', 'type', 'amount'],
                    'rates' => ['id', 'account_id', 'currency_pair', 'rate'],
                    'safa_api_keys' => ['id', 'client_name', 'api_key', 'api_secret'],
                    'audit_logs' => ['id', 'action', 'endpoint'],
                    'app_versions' => ['id', 'platform', 'min_version_code'],
                    'roles' => ['id', 'name', 'slug'],
                    'permissions' => ['id', 'name', 'slug'],
                    'role_permission' => ['role_id', 'permission_id'],
                ],
                '2026_01_02_000000_expand_safa_and_wallet_tables' => [
                    'transactions' => ['customer_id', 'supplier_id', 'amount_sar', 'customer_rate', 'supplier_rate', 'amount_bdt', 'receiver_name', 'receiver_phone', 'receiver_account_type', 'receiver_account_no', 'wallet_batch_id', 'notes'],
                    'wallet_ledgers' => ['id', 'account_id', 'local_id', 'name'],
                    'wallet_batches' => ['id', 'account_id', 'local_id', 'ledger_id', 'rate', 'initial_bdt', 'remaining_bdt'],
                    'supplier_deposits' => ['id', 'account_id', 'local_id', 'supplier_id', 'amount_sar', 'rate', 'amount_bdt'],
                    'expenses_incomes' => ['id', 'account_id', 'local_id', 'title', 'amount', 'currency', 'is_expense'],
                ],
                '2026_01_03_000000_add_deleted_at_to_sync_tables' => [
                    'customers' => ['timestamp', 'deleted_at'],
                    'suppliers' => ['timestamp', 'deleted_at'],
                    'transactions' => ['timestamp', 'deleted_at'],
                    'supplier_deposits' => ['timestamp', 'deleted_at'],
                    'expenses_incomes' => ['timestamp', 'deleted_at'],
                    'wallet_batches' => ['timestamp', 'deleted_at'],
                    'wallet_ledgers' => ['timestamp', 'deleted_at'],
                ],
                '2026_01_04_000000_create_device_bindings_and_tokens_tables' => [
                    'device_bindings' => ['id', 'user_id', 'device_uuid', 'fingerprint_hash'],
                    'auth_sessions' => ['id', 'user_id', 'device_uuid', 'access_token', 'refresh_token', 'session_token'],
                ],
                '2026_01_05_000000_create_superadmin_and_rbac_tables' => [
                    'users' => ['mobile', 'pin_hash', 'role', 'permissions', 'is_activated'],
                    'operator_accounts' => ['id', 'name', 'mobile', 'role'],
                ],
                '2026_01_06_000000_create_account_shares_table' => [
                    'user_account_shares' => ['id', 'owner_user_id', 'account_id', 'shared_with_user_id'],
                ],
                '2026_01_07_000000_create_system_settings_table' => [
                    'system_settings' => ['id', 'app_name', 'app_logo_url', 'app_version', 'local_currency', 'foreign_currency'],
                ],
            ];

            foreach ($migrationFiles as $file) {
                $name = basename($file, '.php');
                if (in_array($name, $executedMigrations, true) || !isset($migrationSchemaMap[$name])) {
                    continue;
                }

                $hasCompleteSchema = true;
                foreach ($migrationSchemaMap[$name] as $tableName => $requiredColumns) {
                    if (!Schema::hasTable($tableName)) {
                        $hasCompleteSchema = false;
                        break;
                    }
                    foreach ($requiredColumns as $column) {
                        if (!Schema::hasColumn($tableName, $column)) {
                            $hasCompleteSchema = false;
                            break 2;
                        }
                    }
                }

                if ($hasCompleteSchema) {
                    DB::table('migrations')->insert([
                        'migration' => $name,
                        'batch' => 1,
                    ]);
                }
            }
        } catch (\Throwable) {
            // Audit helpers must never break normal application boot.
        }
    }

    public static function getPendingMigrations(): array
    {
        try {
            $migrationFiles = glob(database_path('migrations/*.php')) ?: [];
            if ($migrationFiles === []) {
                return [];
            }

            static::autoHealExistingSchema($migrationFiles);

            if (!Schema::hasTable('migrations')) {
                return array_map(fn ($file) => basename($file, '.php'), $migrationFiles);
            }

            $executedMigrations = DB::table('migrations')->pluck('migration')->toArray();
            $pending = [];
            foreach ($migrationFiles as $file) {
                $name = basename($file, '.php');
                if (!in_array($name, $executedMigrations, true)) {
                    $pending[] = $name;
                }
            }

            return $pending;
        } catch (\Throwable) {
            return [];
        }
    }
}
