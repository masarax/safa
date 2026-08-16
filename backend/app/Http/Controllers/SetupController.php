<?php

namespace App\Http\Controllers;

use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use Illuminate\Validation\ValidationException;
use Illuminate\View\View;

class SetupController extends Controller
{
    public function show(Request $request): View|RedirectResponse
    {
        $initialized = $this->hasActiveSuperAdmin();

        if ($initialized) {
            $user = $request->user();
            if (!$user) {
                return redirect()->route('safa.login');
            }

            abort_unless($user instanceof User && $user->is_activated && $user->isSuperAdmin(), 403);
        }

        return view('safa.setup', [
            'initialized' => $initialized,
            'pendingMigrations' => DatabaseUpdateController::pendingMigrations(),
            'apiCredentialsConfigured' => trim((string) env('SAFA_API_KEY', '')) !== ''
                && trim((string) env('SAFA_API_SECRET', '')) !== '',
        ]);
    }

    public function bootstrap(Request $request): RedirectResponse
    {
        if ($this->hasActiveSuperAdmin()) {
            abort(403, 'Initial setup is already complete.');
        }

        $validated = $request->validate([
            'setup_secret' => ['required', 'string', 'max:1024'],
            'name' => ['required', 'string', 'min:2', 'max:100'],
            'mobile' => ['required', 'string', 'min:5', 'max:30'],
            'email' => ['required', 'email:rfc', 'max:255'],
            'pin' => ['required', 'digits:6', 'confirmed'],
        ]);

        $this->assertSetupSecret((string) $validated['setup_secret']);

        try {
            if (Artisan::call('migrate', ['--force' => true]) !== 0) {
                throw new \RuntimeException('Database migration returned a non-zero exit code.');
            }

            if ($this->hasActiveSuperAdmin()) {
                abort(409, 'Initial setup was completed by another request.');
            }

            $mobile = preg_replace('/\D+/', '', (string) $validated['mobile']) ?? '';
            if ($mobile === '') {
                throw ValidationException::withMessages(['mobile' => 'Enter a valid mobile number.']);
            }

            $user = DB::transaction(function () use ($validated, $mobile): User {
                $email = strtolower(trim((string) $validated['email']));
                $user = User::query()
                    ->where('mobile', $mobile)
                    ->orWhere('email', $email)
                    ->first() ?? new User();

                $user->name = trim((string) $validated['name']);
                $user->mobile = $mobile;
                $user->email = $email;
                $user->password = Hash::make((string) $validated['pin']);
                $user->pin_hash = Hash::make((string) $validated['pin']);
                $user->role = User::ROLE_SUPERADMIN;
                $user->is_activated = true;
                $user->permissions = User::permissionsForRole(User::ROLE_SUPERADMIN);
                $user->save();

                return $user;
            });

            if (Artisan::call('db:seed', ['--class' => DatabaseSeeder::class, '--force' => true]) !== 0) {
                throw new \RuntimeException('Database seed returned a non-zero exit code.');
            }

            if (!is_file(storage_path('installed'))) {
                @file_put_contents(storage_path('installed'), now()->toIso8601String() . PHP_EOL, LOCK_EX);
            }

            try {
                Artisan::call('optimize:clear');
            } catch (\Throwable $cacheError) {
                report($cacheError);
            }

            Auth::login($user);
            if ($request->hasSession()) {
                $request->session()->regenerate();
            }

            $message = 'SAFA setup completed. Database, reference data, workspace, and Super Admin are ready.';
            if (trim((string) env('SAFA_API_KEY', '')) === '' || trim((string) env('SAFA_API_SECRET', '')) === '') {
                $message .= ' Configure SAFA_API_KEY and SAFA_API_SECRET before Android API access.';
            }

            return redirect()->route('safa.app')->with('success', $message);
        } catch (ValidationException $e) {
            throw $e;
        } catch (\Throwable $e) {
            report($e);

            return back()->withInput($request->except(['setup_secret', 'pin', 'pin_confirmation']))
                ->with('error', 'Setup could not be completed. Existing data was not intentionally removed. Review the server log and try again.');
        }
    }

    public function seed(Request $request): RedirectResponse
    {
        $user = $request->user();
        abort_unless($user instanceof User && $user->is_activated && $user->isSuperAdmin(), 403);

        if (DatabaseUpdateController::pendingMigrations()) {
            return redirect()->route('system.update.show')
                ->with('info', 'Run the pending database update before refreshing seed data.');
        }

        try {
            if (Artisan::call('db:seed', ['--class' => DatabaseSeeder::class, '--force' => true]) !== 0) {
                throw new \RuntimeException('Database seed returned a non-zero exit code.');
            }

            return redirect()->route('safa.setup')
                ->with('success', 'Reference data and workspace seed were refreshed safely. Existing business data was preserved.');
        } catch (\Throwable $e) {
            report($e);
            return redirect()->route('safa.setup')
                ->with('error', 'Seed refresh failed. Existing business data was not intentionally removed.');
        }
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

    private function assertSetupSecret(string $provided): void
    {
        $configured = array_values(array_filter([
            trim((string) config('safa.setup_token', '')),
            trim((string) config('database.connections.' . config('database.default') . '.password', '')),
        ], static fn (string $value): bool => $value !== ''));

        if (!$configured) {
            throw ValidationException::withMessages([
                'setup_secret' => 'Initial setup is locked. Configure SAFA_SETUP_TOKEN in the server environment first.',
            ]);
        }

        foreach ($configured as $secret) {
            if (hash_equals($secret, $provided)) {
                return;
            }
        }

        throw ValidationException::withMessages([
            'setup_secret' => 'The setup key is not valid.',
        ]);
    }
}
