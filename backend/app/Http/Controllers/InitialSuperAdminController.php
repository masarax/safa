<?php

namespace App\Http\Controllers;

use App\Models\Account;
use App\Models\User;
use App\Support\InitialSuperAdminBootstrap;
use App\Support\MobileNumber;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Validator;
use Illuminate\View\View;

class InitialSuperAdminController extends Controller
{
    public function show(Request $request): View|RedirectResponse
    {
        if (!InitialSuperAdminBootstrap::schemaReady() || DatabaseUpdateController::pendingMigrations()) {
            return redirect()->route('system.update.show');
        }

        abort_if(InitialSuperAdminBootstrap::privilegedUserExists(), 404);

        $language = strtolower((string) $request->query(
            'lang',
            $request->session()->get('safa_web_language', 'en')
        ));
        if (!in_array($language, ['en', 'bn'], true)) {
            $language = 'en';
        }
        $request->session()->put('safa_web_language', $language);

        return view('initial_superadmin', [
            'language' => $language,
            'maintenanceConfigured' => InitialSuperAdminBootstrap::maintenanceConfigured(),
        ]);
    }

    public function store(Request $request): RedirectResponse
    {
        if (!InitialSuperAdminBootstrap::schemaReady() || DatabaseUpdateController::pendingMigrations()) {
            return redirect()->route('system.update.show');
        }

        abort_if(InitialSuperAdminBootstrap::privilegedUserExists(), 404);

        $expectedToken = trim((string) config('safa.maintenance_token', ''));
        $providedToken = trim((string) $request->input('maintenance_token', ''));
        abort_unless(
            $expectedToken !== '' && $providedToken !== '' && hash_equals($expectedToken, $providedToken),
            403,
            'Maintenance authorization failed.'
        );

        $safeInput = $request->only(['name', 'mobile', 'email', 'language']);
        $validator = Validator::make($request->all(), [
            'name' => ['required', 'string', 'max:255'],
            'mobile' => ['required', 'string', 'max:30'],
            'email' => ['required', 'string', 'email:rfc', 'max:255'],
            'pin' => ['required', 'string', 'regex:/^\d{6}$/', 'confirmed'],
            'language' => ['nullable', 'in:en,bn'],
        ]);

        if ($validator->fails()) {
            return back()->withErrors($validator)->withInput($safeInput);
        }

        $validated = $validator->validated();
        $mobile = MobileNumber::normalize((string) $validated['mobile']);
        $email = strtolower(trim((string) $validated['email']));
        $pin = (string) $validated['pin'];

        if ($mobile === '' || !MobileNumber::isValid($mobile)) {
            return back()->withErrors([
                'mobile' => 'Enter a valid mobile number.',
            ])->withInput($safeInput);
        }

        $lock = Cache::lock(InitialSuperAdminBootstrap::LOCK_KEY, 15);
        $acquired = false;

        try {
            $acquired = $lock->get();
            if (!$acquired) {
                return back()->with('error', 'Super Admin setup is already in progress. Try again shortly.')
                    ->withInput($safeInput);
            }

            $user = DB::transaction(function () use ($validated, $mobile, $email, $pin): ?User {
                if (InitialSuperAdminBootstrap::privilegedUserExists()) {
                    return null;
                }

                if (User::query()->where('mobile', $mobile)->exists()) {
                    throw new \DomainException('mobile');
                }

                if (User::query()->whereRaw('LOWER(email) = ?', [$email])->exists()) {
                    throw new \DomainException('email');
                }

                $hash = Hash::make($pin);
                $user = User::query()->create([
                    'name' => trim((string) $validated['name']),
                    'mobile' => $mobile,
                    'email' => $email,
                    'password' => $hash,
                    'pin_hash' => $hash,
                    'role' => User::ROLE_SUPERADMIN,
                    'is_activated' => true,
                    'permissions' => User::permissionsForRole(User::ROLE_SUPERADMIN),
                ]);

                if (!Account::query()->exists()) {
                    Account::query()->create([
                        'owner_user_id' => $user->id,
                        'name' => 'SAFA Account',
                        'balance' => 0,
                    ]);
                }

                return $user;
            }, 3);
        } catch (\DomainException $e) {
            $field = in_array($e->getMessage(), ['mobile', 'email'], true) ? $e->getMessage() : 'email';
            $message = $field === 'mobile'
                ? 'This mobile number is already in use.'
                : 'This email address is already in use.';

            return back()->withErrors([$field => $message])->withInput($safeInput);
        } catch (\Throwable $e) {
            report($e);

            return back()->with('error', 'Super Admin setup could not be completed. No existing business data was intentionally changed.')
                ->withInput($safeInput);
        } finally {
            if ($acquired) {
                $lock->release();
            }
        }

        if (!$user) {
            return redirect()->route('safa.login');
        }

        $this->markInstalled();
        $language = in_array(($validated['language'] ?? 'en'), ['en', 'bn'], true)
            ? (string) ($validated['language'] ?? 'en')
            : 'en';

        return redirect()->route('safa.login', ['lang' => $language])->with(
            'success',
            $language === 'bn'
                ? 'সুপার অ্যাডমিন তৈরি হয়েছে। এখন লগইন করুন।'
                : 'Super Admin created. You can sign in now.'
        );
    }

    private function markInstalled(): void
    {
        if (app()->environment('testing') || is_file(storage_path('installed'))) {
            return;
        }

        if (@file_put_contents(storage_path('installed'), now()->toIso8601String() . PHP_EOL, LOCK_EX) === false) {
            report(new \RuntimeException('Unable to write the installed marker after Super Admin bootstrap.'));
        }
    }
}
