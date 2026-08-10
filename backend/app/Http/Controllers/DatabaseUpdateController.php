<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\URL;

class DatabaseUpdateController extends Controller
{
    /**
     * Return migrations that are not recorded as executed.
     * A completely fresh database must be treated as needing every migration.
     * Database/update errors must never be converted into an empty list,
     * otherwise the application would continue into DB-dependent routes and
     * produce a generic 500 page before the update screen can be shown.
     */
    public static function pendingMigrations(): array
    {
        $files = glob(database_path('migrations/*.php')) ?: [];
        $migrationNames = array_map(fn ($file) => basename($file, '.php'), $files);

        if (!$files) {
            return [];
        }

        try {
            if (!Schema::hasTable('migrations')) {
                // Fresh database: nothing has been executed yet.
                return $migrationNames;
            }

            self::healLegacyMigrationRecords($files);
            $ran = DB::table('migrations')->pluck('migration')->all();
            $ran = array_flip($ran);

            return array_values(array_filter(
                $migrationNames,
                fn ($name) => !isset($ran[$name])
            ));
        } catch (\Throwable $e) {
            report($e);

            // Fail closed for the installer: if the migration state cannot be
            // read, show the update screen instead of executing the normal app.
            return $migrationNames;
        }
    }

    public function show(Request $request)
    {
        $pendingMigrations = self::pendingMigrations();

        if (!$pendingMigrations) {
            return redirect()->route('home')->with('info', 'Database is already up to date.');
        }

        return view('install_update', [
            'pendingMigrations' => $pendingMigrations,
            'updateUrl' => URL::temporarySignedRoute(
                'install.update-process',
                now()->addMinutes(15)
            ),
        ]);
    }

    /**
     * Execute the pending migrations through a short-lived signed URL.
     * This deliberately does not depend on a database-backed Laravel session,
     * because the session table itself may be one of the migrations being fixed.
     */
    public function process(Request $request)
    {
        if (!$request->hasValidSignature()) {
            return $request->expectsJson()
                ? response()->json(['status' => 'error', 'message' => 'Unauthorized database update request. The update link is invalid or expired.'], 403)
                : response('Unauthorized database update request. The update link is invalid or expired.', 403);
        }

        try {
            $files = glob(database_path('migrations/*.php')) ?: [];
            self::healLegacyMigrationRecords($files);

            Artisan::call('migrate', ['--force' => true]);
            $output = trim(Artisan::output());

            // Clear only runtime caches after a successful schema update.
            foreach (['config:clear', 'cache:clear', 'view:clear'] as $command) {
                try {
                    Artisan::call($command);
                } catch (\Throwable $ignored) {
                    // Cache cleanup must not turn a successful migration into a failure.
                }
            }

            if (self::pendingMigrations()) {
                return back()->with('error', 'Database update completed, but some schema updates are still pending. Please open the update page again.');
            }

            return redirect()->route('home')->with('success', 'Database schema updated successfully without any data loss.');
        } catch (\Throwable $e) {
            report($e);
            return back()->with('error', 'Database update failed. No destructive database operation was performed. Please check the server error log.');
        }
    }

    /**
     * Mark a legacy migration as completed only when its schema contract is
     * already present. Missing columns/tables remain genuinely pending so the
     * normal Laravel migration can add them.
     */
    private static function healLegacyMigrationRecords(array $migrationFiles): void
    {
        if (!Schema::hasTable('migrations')) {
            try {
                Artisan::call('migrate:install');
            } catch (\Throwable $e) {
                return;
            }
        }

        $ran = DB::table('migrations')->pluck('migration')->all();
        $contracts = self::schemaContracts();
        $batch = ((int) DB::table('migrations')->max('batch')) + 1;
        $batch = max(1, $batch);

        foreach ($migrationFiles as $file) {
            $name = basename($file, '.php');
            if (in_array($name, $ran, true) || !isset($contracts[$name])) {
                continue;
            }

            $complete = true;
            foreach ($contracts[$name] as $table => $columns) {
                if (!Schema::hasTable($table)) {
                    $complete = false;
                    break;
                }
                foreach ($columns as $column) {
                    if (!Schema::hasColumn($table, $column)) {
                        $complete = false;
                        break 2;
                    }
                }
            }

            if ($complete) {
                DB::table('migrations')->insert([
                    'migration' => $name,
                    'batch' => $batch,
                ]);
            }
        }
    }

    private static function schemaContracts(): array
    {
        return [
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
    }
}
