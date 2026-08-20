<?php

namespace App\Services;

use App\Http\Controllers\DatabaseUpdateController;
use App\Models\Account;
use App\Models\User;
use App\Support\FirstRunSetupState;
use App\Support\MobileNumber;
use App\Support\ProductionMigrationSafety;
use Database\Seeders\ReleaseDataUpdateSeeder;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use RuntimeException;

class FirstRunDatabaseBootstrapService
{
    public const LOCK_FILE = 'framework/safa-first-run-bootstrap.lock';

    /** @return array{busy: bool, migrated: int} */
    public function initializeDatabase(string $claim): array
    {
        $this->assertClaimFormat($claim);

        return $this->withLock(function () use ($claim): array {
            if (!FirstRunSetupState::databaseInitializationRequired()) {
                throw new RuntimeException('First-run database initialization is no longer available.');
            }

            $pending = DatabaseUpdateController::pendingMigrations();
            if ($pending === []) {
                throw new RuntimeException('No initial database migrations were found.');
            }

            ProductionMigrationSafety::assertPendingMigrationsAreSafe($pending);

            if (Artisan::call('migrate', ['--force' => true]) !== 0) {
                throw new RuntimeException('Initial forward migration command failed.');
            }

            if (!Schema::hasTable(FirstRunSetupState::TABLE)) {
                throw new RuntimeException('Installation state table was not created by the initial migrations.');
            }

            DB::table(FirstRunSetupState::TABLE)->updateOrInsert(
                ['id' => 1],
                [
                    'bootstrap_claim_hash' => hash('sha256', $claim),
                    'database_initialized_at' => now(),
                    'completed_at' => null,
                    'created_at' => now(),
                    'updated_at' => now(),
                ]
            );

            if (Artisan::call('db:seed', [
                '--class' => ReleaseDataUpdateSeeder::class,
                '--force' => true,
            ]) !== 0) {
                throw new RuntimeException('Initial reference-data seed failed.');
            }

            if (DatabaseUpdateController::pendingMigrations() !== []) {
                throw new RuntimeException('Initial database setup completed with pending migrations remaining.');
            }

            Artisan::call('optimize:clear');

            return ['busy' => false, 'migrated' => count($pending)];
        });
    }

    /**
     * @param array{name:string,mobile:string,email:string,pin:string} $input
     */
    public function createInitialSuperAdmin(string $claim, array $input): User
    {
        $this->assertClaimFormat($claim);

        $result = $this->withLock(function () use ($claim, $input): User {
            return DB::transaction(function () use ($claim, $input): User {
                $state = DB::table(FirstRunSetupState::TABLE)->where('id', 1)->lockForUpdate()->first();
                if ($state === null || $state->completed_at !== null) {
                    throw new RuntimeException('First-run administrator setup is no longer available.');
                }
                if (!hash_equals((string) $state->bootstrap_claim_hash, hash('sha256', $claim))) {
                    throw new RuntimeException('This browser session does not own the active first-run setup.');
                }
                if (User::query()->exists()) {
                    throw new RuntimeException('A user already exists; public first-run administrator creation is blocked.');
                }

                $mobile = MobileNumber::normalize($input['mobile']);
                if ($mobile === '' || !MobileNumber::isValid($mobile)) {
                    throw new RuntimeException('Initial SuperAdmin mobile number is invalid.');
                }

                $email = strtolower(trim($input['email']));
                if (filter_var($email, FILTER_VALIDATE_EMAIL) === false) {
                    throw new RuntimeException('Initial SuperAdmin email address is invalid.');
                }

                $hash = Hash::make($input['pin']);
                $user = User::query()->create([
                    'name' => trim($input['name']),
                    'mobile' => $mobile,
                    'email' => $email,
                    'password' => $hash,
                    'pin_hash' => $hash,
                    'role' => User::ROLE_SUPERADMIN,
                    'is_activated' => true,
                    'permissions' => User::permissionsForRole(User::ROLE_SUPERADMIN),
                ]);

                Account::query()->create([
                    'name' => 'SAFA Account',
                    'balance' => 0,
                    'owner_user_id' => $user->id,
                ]);

                DB::table(FirstRunSetupState::TABLE)->where('id', 1)->update([
                    'completed_at' => now(),
                    'updated_at' => now(),
                ]);

                return $user;
            }, 3);
        });

        if (!$result instanceof User) {
            throw new RuntimeException('Initial SuperAdmin setup did not complete.');
        }

        Artisan::call('optimize:clear');

        return $result;
    }

    private function assertClaimFormat(string $claim): void
    {
        if (preg_match('/^[a-f0-9]{64}$/', $claim) !== 1) {
            throw new RuntimeException('Invalid first-run setup claim.');
        }
    }

    private function withLock(callable $callback): mixed
    {
        $lockPath = storage_path(self::LOCK_FILE);
        File::ensureDirectoryExists(dirname($lockPath));
        $handle = @fopen($lockPath, 'c+');
        if ($handle === false) {
            throw new RuntimeException('Unable to open the first-run setup lock.');
        }

        $locked = false;
        try {
            $locked = flock($handle, LOCK_EX | LOCK_NB);
            if (!$locked) {
                return ['busy' => true, 'migrated' => 0];
            }

            return $callback();
        } finally {
            if ($locked) {
                flock($handle, LOCK_UN);
            }
            fclose($handle);
        }
    }
}
