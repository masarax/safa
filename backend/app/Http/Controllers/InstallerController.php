<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class InstallerController extends Controller
{
    /**
     * Show the installation form and system requirements.
     */
    public function index()
    {
        $requirements = [
            'php_version' => [
                'name' => 'PHP Version (>= 8.2)',
                'current' => PHP_VERSION,
                'satisfied' => version_compare(PHP_VERSION, '8.2.0', '>='),
            ],
            'pdo' => [
                'name' => 'PDO MySQL Extension',
                'current' => extension_loaded('pdo') && extension_loaded('pdo_mysql') ? 'Enabled' : 'Disabled',
                'satisfied' => extension_loaded('pdo') && extension_loaded('pdo_mysql'),
            ],
            'storage_writable' => [
                'name' => 'Storage Directory Writable',
                'current' => is_writable(storage_path()) ? 'Writable' : 'Not Writable',
                'satisfied' => is_writable(storage_path()),
            ],
            'bootstrap_writable' => [
                'name' => 'Bootstrap Cache Writable',
                'current' => is_writable(base_path('bootstrap/cache')) ? 'Writable' : 'Not Writable',
                'satisfied' => is_writable(base_path('bootstrap/cache')),
            ],
            'env_writable' => [
                'name' => '.env File Writable',
                'current' => (file_exists(base_path('.env')) && is_writable(base_path('.env'))) || is_writable(base_path()) ? 'Writable' : 'Not Writable',
                'satisfied' => (file_exists(base_path('.env')) && is_writable(base_path('.env'))) || is_writable(base_path()),
            ],
        ];

        $allRequirementsMet = collect($requirements)->every(fn ($item) => $item['satisfied']);

        $defaults = [
            'app_name' => env('APP_NAME', 'SAFA Backend'),
            'app_url' => env('APP_URL', request()->schemeAndHttpHost()),
            'db_host' => env('DB_HOST', 'localhost'),
            'db_port' => env('DB_PORT', '3306'),
            'db_name' => env('DB_DATABASE', 'safa'),
            'db_user' => env('DB_USERNAME', 'root'),
            'db_pass' => env('DB_PASSWORD', ''),
        ];

        return view('install', compact('requirements', 'allRequirementsMet', 'defaults'));
    }

    /**
     * AJAX endpoint to test database connection before submitting installer.
     */
    public function testDb(Request $request)
    {
        $dbHost = $request->input('db_host');
        $dbPort = $request->input('db_port', '3306');
        $dbName = $request->input('db_name');
        $dbUser = $request->input('db_user');
        $dbPass = $request->input('db_pass') ?? '';

        try {
            $dsn = "mysql:host={$dbHost};port={$dbPort};dbname={$dbName};charset=utf8mb4";
            $pdo = new \PDO($dsn, $dbUser, $dbPass, [
                \PDO::ATTR_ERRMODE => \PDO::ERRMODE_EXCEPTION,
                \PDO::ATTR_TIMEOUT => 5,
            ]);

            return response()->json([
                'success' => true,
                'message' => 'Database connection successful! / ডাটাবেস সংযোগ সফল হয়েছে!'
            ]);
        } catch (\Throwable $e) {
            return response()->json([
                'success' => false,
                'message' => "Database connection failed! Exception: " . $e->getMessage()
            ], 400);
        }
    }

    /**
     * Process installation: Test DB, write .env, run migrations, set lock file.
     */
    public function process(Request $request)
    {
        $validated = $request->validate([
            'app_name' => 'required|string|max:255',
            'app_url' => 'required|url',
            'db_host' => 'required|string',
            'db_port' => 'required|numeric',
            'db_name' => 'required|string',
            'db_user' => 'required|string',
            'db_pass' => 'nullable|string',
        ]);

        $dbHost = $validated['db_host'];
        $dbPort = $validated['db_port'];
        $dbName = $validated['db_name'];
        $dbUser = $validated['db_user'];
        $dbPass = $validated['db_pass'] ?? '';

        // 1. Test PDO connection BEFORE saving .env or running migrations
        try {
            $dsn = "mysql:host={$dbHost};port={$dbPort};dbname={$dbName};charset=utf8mb4";
            $pdo = new \PDO($dsn, $dbUser, $dbPass, [
                \PDO::ATTR_ERRMODE => \PDO::ERRMODE_EXCEPTION,
                \PDO::ATTR_TIMEOUT => 5,
            ]);
        } catch (\Throwable $e) {
            return back()
                ->withInput()
                ->with('error', "Database connection failed! Exception: " . $e->getMessage());
        }

        // 2. Generate APP_KEY if not set
        $appKey = env('APP_KEY');
        if (empty($appKey)) {
            $appKey = 'base64:' . base64_encode(Str::random(32));
        }

        // 3. Auto-generate API Security Credentials
        $apiKey = (env('SAFA_API_KEY') && !str_contains(env('SAFA_API_KEY'), 'test')) ? env('SAFA_API_KEY') : ('safa_key_' . bin2hex(random_bytes(16)));
        $apiSecret = (env('SAFA_API_SECRET') && !str_contains(env('SAFA_API_SECRET'), 'test')) ? env('SAFA_API_SECRET') : ('safa_sec_' . bin2hex(random_bytes(32)));

        // 4. Update or create .env file cleanly
        $envData = [
            'APP_NAME' => $validated['app_name'],
            'APP_ENV' => 'production',
            'APP_KEY' => $appKey,
            'APP_DEBUG' => 'false',
            'APP_URL' => $validated['app_url'],
            'DB_CONNECTION' => 'mysql',
            'DB_HOST' => $dbHost,
            'DB_PORT' => $dbPort,
            'DB_DATABASE' => $dbName,
            'DB_USERNAME' => $dbUser,
            'DB_PASSWORD' => $dbPass,
            'SAFA_API_KEY' => $apiKey,
            'SAFA_API_SECRET' => $apiSecret,
            'SESSION_DRIVER' => 'database',
            'CACHE_STORE' => 'database',
            'APP_INSTALLED' => 'true',
        ];

        $this->writeEnvironmentFile($envData);

        // 5. Dynamically configure current request in memory
        config([
            'app.name' => $validated['app_name'],
            'app.env' => 'production',
            'app.debug' => false,
            'app.url' => $validated['app_url'],
            'app.key' => $appKey,
            'database.default' => 'mysql',
            'database.connections.mysql.host' => $dbHost,
            'database.connections.mysql.port' => $dbPort,
            'database.connections.mysql.database' => $dbName,
            'database.connections.mysql.username' => $dbUser,
            'database.connections.mysql.password' => $dbPass,
            'session.driver' => 'database',
            'cache.default' => 'database',
        ]);

        DB::purge('mysql');
        DB::reconnect('mysql');

        // 6. Clear config cache safely
        try {
            Artisan::call('config:clear');
        } catch (\Throwable $e) {
            // Ignore config clear errors during installation boot
        }

        // 7. Run migrations inside try-catch block
        try {
            Artisan::call('migrate', ['--force' => true]);
        } catch (\Throwable $e) {
            return back()
                ->withInput()
                ->with('error', "Migration failed! Exception: " . $e->getMessage());
        }

        // 8. Create lock file
        file_put_contents(storage_path('installed'), date('Y-m-d H:i:s'));

        return redirect()->route('install.success')->with('success', 'System installation completed successfully!');
    }

    /**
     * Show installation success view.
     */
    public function success()
    {
        $apiUrl = rtrim(config('app.url', request()->schemeAndHttpHost()), '/') . '/api/';
        $apiKey = env('SAFA_API_KEY', 'Auto-Configured');
        $apiSecret = env('SAFA_API_SECRET', 'Auto-Configured');
        return view('install_success', compact('apiUrl', 'apiKey', 'apiSecret'));
    }

    /**
     * Helper to write array of key-values cleanly to base .env file.
     */
    protected function writeEnvironmentFile(array $data): void
    {
        $envPath = base_path('.env');
        $examplePath = base_path('.env.example');

        if (file_exists($envPath)) {
            $envContent = file_get_contents($envPath);
        } elseif (file_exists($examplePath)) {
            $envContent = file_get_contents($examplePath);
        } else {
            $envContent = '';
        }

        foreach ($data as $key => $value) {
            $valueStr = (string)$value;
            $formattedValue = (preg_match('/\s|#|\$|"/', $valueStr))
                ? '"' . str_replace('"', '\"', $valueStr) . '"'
                : $valueStr;

            $pattern = "/^#?\s*{$key}=.*/m";
            if (preg_match($pattern, $envContent)) {
                $envContent = preg_replace($pattern, "{$key}={$formattedValue}", $envContent);
            } else {
                $envContent .= "\n{$key}={$formattedValue}";
            }
        }

        file_put_contents($envPath, trim($envContent) . "\n");
    }

    /**
     * Helper to auto-register pre-existing database tables in the migrations table.
     * Prevents "Table already exists" errors when migrating legacy or imported databases on cPanel.
     */
    public static function autoHealExistingSchema(array $migrationFiles): void
    {
        try {
            if (!DB::schema()->hasTable('migrations')) {
                try {
                    Artisan::call('migrate:install');
                } catch (\Throwable $th) {
                    return;
                }
            }

            $executedMigrations = DB::table('migrations')->pluck('migration')->toArray();

            // Mapping migration files to their primary table indicator
            $migrationTableMap = [
                '0001_01_01_000000_create_users_table' => 'users',
                '0001_01_01_000001_create_cache_table' => 'cache',
                '0001_01_01_000002_create_jobs_table' => 'jobs',
                '2026_01_01_000000_create_safa_tables' => 'safa_users',
                '2026_01_02_000000_expand_hundi_and_wallet_tables' => 'safa_wallets',
                '2026_01_04_000000_create_device_bindings_and_tokens_tables' => 'safa_device_bindings',
                '2026_01_05_000000_create_superadmin_and_rbac_tables' => 'safa_superadmins',
                '2026_01_06_000000_create_account_shares_table' => 'safa_account_shares',
            ];

            foreach ($migrationFiles as $file) {
                $name = basename($file, '.php');
                if (in_array($name, $executedMigrations)) {
                    continue;
                }

                if (isset($migrationTableMap[$name])) {
                    $tableName = $migrationTableMap[$name];
                    if (DB::schema()->hasTable($tableName)) {
                        DB::table('migrations')->insert([
                            'migration' => $name,
                            'batch' => 1,
                        ]);
                    }
                }
            }
        } catch (\Throwable $e) {
            // Silence auto-healing exceptions
        }
    }

    /**
     * Helper to get list of pending un-executed migration files.
     */
    public static function getPendingMigrations(): array
    {
        try {
            $migrationFiles = glob(database_path('migrations/*.php'));
            if (empty($migrationFiles)) {
                return [];
            }

            // Perform auto-healing for any pre-existing tables before checking pending status
            static::autoHealExistingSchema($migrationFiles);

            if (!DB::schema()->hasTable('migrations')) {
                return array_map(fn($f) => basename($f, '.php'), $migrationFiles);
            }

            $executedMigrations = DB::table('migrations')->pluck('migration')->toArray();
            $pending = [];

            foreach ($migrationFiles as $file) {
                $name = basename($file, '.php');
                if (!in_array($name, $executedMigrations)) {
                    $pending[] = $name;
                }
            }

            return $pending;
        } catch (\Throwable $e) {
            return [];
        }
    }

    /**
     * Show manual database update screen when new migrations are detected.
     */
    public function updateView()
    {
        $pendingMigrations = static::getPendingMigrations();
        if (empty($pendingMigrations)) {
            return redirect()->route('home')->with('info', 'Database is already up to date.');
        }

        return view('install_update', compact('pendingMigrations'));
    }

    /**
     * Process manual database migration execution without dropping existing data.
     */
    public function updateProcess(Request $request)
    {
        try {
            $migrationFiles = glob(database_path('migrations/*.php'));
            static::autoHealExistingSchema($migrationFiles);

            Artisan::call('migrate', ['--force' => true]);

            try {
                Artisan::call('config:clear');
                Artisan::call('cache:clear');
                Artisan::call('view:clear');
            } catch (\Throwable $ce) {
                // Ignore cache clearing errors if any
            }
        } catch (\Throwable $e) {
            if (str_contains($e->getMessage(), 'already exists')) {
                try {
                    static::autoHealExistingSchema(glob(database_path('migrations/*.php')));
                    Artisan::call('migrate', ['--force' => true]);
                } catch (\Throwable $e2) {
                    return back()->with('error', 'Migration warning: ' . $e2->getMessage());
                }
            } else {
                return back()->with('error', 'Migration failed: ' . $e->getMessage());
            }
        }

        return redirect()->route('home')->with('success', 'Database schema updated successfully without any data loss!');
    }
}



