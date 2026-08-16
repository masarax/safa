<?php

namespace App\Http\Controllers;

use App\Models\User;
use App\Support\InitialSuperAdminBootstrap;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\View\View;

class DatabaseUpdateController extends Controller
{
    public static function pendingMigrations(): array
    {
        $files = glob(database_path('migrations/*.php')) ?: [];
        $migrationNames = array_map(fn ($file) => basename($file, '.php'), $files);

        if (!$files) {
            return [];
        }

        try {
            if (!Schema::hasTable('migrations')) {
                return $migrationNames;
            }

            self::healLegacyMigrationRecords($files);
            $ran = array_flip(DB::table('migrations')->pluck('migration')->all());

            return array_values(array_filter(
                $migrationNames,
                fn ($name) => !isset($ran[$name])
            ));
        } catch (\Throwable $e) {
            report($e);
            return $migrationNames;
        }
    }

    public function show(Request $request): View|RedirectResponse
    {
        $recoveryMode = !$this->hasActiveSuperAdmin();
        if (!$recoveryMode) {
            if (!$request->user()) {
                return redirect()->route('safa.login');
            }
            $this->authorizeSuperAdmin($request);
        }

        $pendingMigrations = self::pendingMigrations();

        return view('install_update', [
            'pendingMigrations' => $pendingMigrations,
            'recoveryMode' => $recoveryMode,
            'initialAdminConfigured' => $this->initialAdminConfigured(),
            'initialSuperAdminBootstrapAvailable' => $recoveryMode
                && !$pendingMigrations
                && InitialSuperAdminBootstrap::available(),
        ]);
    }

    public function migrate(Request $request): RedirectResponse
    {
        if ($redirect = $this->redirectGuestWhenInitialized($request)) {
            return $redirect;
        }
        $recoveryMode = !$this->hasActiveSuperAdmin();
        $this->authorizeMaintenance($request);

        if (!self::pendingMigrations()) {
            return redirect()->route('system.update.show')->with('info', 'No pending migrations.');
        }

        try {
            $files = glob(database_path('migrations/*.php')) ?: [];
            self::healLegacyMigrationRecords($files);

            if (Artisan::call('migrate', ['--force' => true]) !== 0) {
                throw new \RuntimeException('Migration command returned a non-zero exit code.');
            }

            try {
                Artisan::call('optimize:clear');
            } catch (\Throwable $cacheError) {
                report($cacheError);
            }

            if (self::pendingMigrations()) {
                return redirect()->route('system.update.show')->with(
                    'error',
                    'Migration completed, but database changes are still pending. Review the server log before retrying.'
                );
            }

            // A recovered fresh database still needs the independent seed/bootstrap
            // action. An already initialized SuperAdmin session can immediately
            // resume the normal application after the schema gate is cleared.
            if ($recoveryMode) {
                return redirect()->route('system.update.show')->with('success', 'Database migration completed successfully.');
            }

            return redirect()->route('safa.app')->with('success', 'Database migration completed successfully.');
        } catch (\Throwable $e) {
            report($e);

            return redirect()->route('system.update.show')->with(
                'error',
                'Database migration failed. Existing data was not intentionally removed.'
            );
        }
    }

    public function seed(Request $request): RedirectResponse
    {
        if ($redirect = $this->redirectGuestWhenInitialized($request)) {
            return $redirect;
        }
        $recoveryMode = !$this->hasActiveSuperAdmin();
        $this->authorizeMaintenance($request);

        if (self::pendingMigrations()) {
            return redirect()->route('system.update.show')->with('error', 'Run Migration before Run Seed.');
        }

        try {
            if (Artisan::call('db:seed', ['--class' => DatabaseSeeder::class, '--force' => true]) !== 0) {
                throw new \RuntimeException('Database seed returned a non-zero exit code.');
            }

            if (!$this->hasActiveSuperAdmin()) {
                throw new \RuntimeException('Seed completed without an activated Super Admin.');
            }

            $this->markInstalled();

            try {
                Artisan::call('optimize:clear');
            } catch (\Throwable $cacheError) {
                report($cacheError);
            }

            if ($recoveryMode) {
                return redirect()->route('safa.login')->with('success', 'Seed completed. Sign in with the configured Super Admin account.');
            }

            return redirect()->route('system.update.show')->with('success', 'Seed completed successfully.');
        } catch (\Throwable $e) {
            report($e);

            $message = $recoveryMode
                ? 'Seed failed. Configure the server maintenance key and complete SAFA_INITIAL_ADMIN_* values, then retry.'
                : 'Seed failed. Existing business data was not intentionally removed.';

            return redirect()->route('system.update.show')->with('error', $message);
        }
    }

    private function authorizeMaintenance(Request $request): void
    {
        if ($this->hasActiveSuperAdmin()) {
            $this->authorizeSuperAdmin($request);
            return;
        }

        $expected = trim((string) config('safa.maintenance_token', ''));
        $provided = trim((string) $request->input('maintenance_token', ''));
        abort_unless(
            $expected !== '' && $provided !== '' && hash_equals($expected, $provided),
            403,
            'Maintenance authorization failed.'
        );
    }

    private function authorizeSuperAdmin(Request $request): User
    {
        $user = $request->user();
        abort_unless(
            $user instanceof User && (bool) $user->is_activated && $user->isSuperAdmin(),
            403,
            'Only an activated SuperAdmin can run system maintenance.'
        );

        return $user;
    }

    private function redirectGuestWhenInitialized(Request $request): ?RedirectResponse
    {
        return $this->hasActiveSuperAdmin() && !$request->user()
            ? redirect()->route('safa.login')
            : null;
    }

    private function hasActiveSuperAdmin(): bool
    {
        try {
            return Schema::hasTable('users')
                && Schema::hasColumn('users', 'role')
                && Schema::hasColumn('users', 'is_activated')
                && User::query()
                    ->where('role', User::ROLE_SUPERADMIN)
                    ->where('is_activated', true)
                    ->exists();
        } catch (\Throwable $e) {
            report($e);
            return false;
        }
    }

    private function initialAdminConfigured(): bool
    {
        $admin = (array) config('safa.initial_admin', []);

        return trim((string) ($admin['name'] ?? '')) !== ''
            && trim((string) ($admin['mobile'] ?? '')) !== ''
            && filter_var(trim((string) ($admin['email'] ?? '')), FILTER_VALIDATE_EMAIL) !== false
            && preg_match('/^\d{6}$/', trim((string) ($admin['pin'] ?? ''))) === 1;
    }

    private function markInstalled(): void
    {
        if (app()->environment('testing') || is_file(storage_path('installed'))) {
            return;
        }

        @file_put_contents(storage_path('installed'), now()->toIso8601String() . PHP_EOL, LOCK_EX);
    }

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
            '2026_08_12_000001_harden_auth_session_storage' => [
                'auth_sessions' => ['access_token_hash', 'refresh_token_hash', 'session_token_hash'],
            ],
        ];
    }
}
