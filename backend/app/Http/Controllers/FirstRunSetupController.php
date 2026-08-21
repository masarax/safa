<?php

namespace App\Http\Controllers;

use App\Services\FirstRunDatabaseBootstrapService;
use App\Support\FirstRunSetupCode;
use App\Support\FirstRunSetupState;
use App\Support\MobileNumber;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\Validator;

class FirstRunSetupController extends Controller
{
    public function showDatabase(Request $request): Response
    {
        abort_unless(FirstRunSetupState::databaseInitializationRequired(), 404);
        FirstRunSetupCode::ensure();

        return response()->view('first_run_database', [
            'pendingMigrations' => DatabaseUpdateController::pendingMigrations(),
            'setupCodePath' => FirstRunSetupCode::operatorPath(),
        ]);
    }

    public function runDatabase(Request $request, FirstRunDatabaseBootstrapService $bootstrap): RedirectResponse
    {
        // State must be checked before rate limiting so the migration endpoint
        // disappears as a hard 404 immediately after the schema is initialized.
        abort_unless(FirstRunSetupState::databaseInitializationRequired(), 404);
        $this->consumeAttempt('first-run-database|' . $request->ip(), 5);

        $setupCode = (string) $request->input('setup_code', '');
        if (!FirstRunSetupCode::verify($setupCode)) {
            // Never flash the deployment-owned code back into session data.
            return back()->with('error', 'The one-time setup code is invalid.');
        }

        $claim = bin2hex(random_bytes(32));
        $request->session()->put(FirstRunSetupState::SESSION_CLAIM, $claim);

        try {
            $result = $bootstrap->initializeDatabase($claim);
            if ($result['busy']) {
                return redirect()->route('setup.database.show')->with('info', 'Database initialization is already running.');
            }

            // From this point the schema exists, so the public migration route is
            // retired permanently. The private deployment code must die with it.
            FirstRunSetupCode::destroy();
            return redirect()->route('setup.admin.show')->with('success', 'Database initialized successfully. Create the first SuperAdmin to finish setup.');
        } catch (\Throwable $e) {
            report($e);

            if (FirstRunSetupState::adminCompletionRequired() && FirstRunSetupState::claimMatches($claim)) {
                FirstRunSetupCode::destroy();
                return redirect()->route('setup.admin.show')->with('info', 'Database schema is initialized. Finish the first SuperAdmin setup.');
            }

            $request->session()->forget(FirstRunSetupState::SESSION_CLAIM);
            return redirect()->route('setup.database.show')->with('error', 'Database initialization failed. Check the server logs and database configuration.');
        }
    }

    public function showAdmin(Request $request): Response
    {
        $this->authorizeClaim($request);

        return response()->view('first_run_admin');
    }

    public function createAdmin(Request $request, FirstRunDatabaseBootstrapService $bootstrap): RedirectResponse
    {
        // Claim/state authorization happens before the limiter so completed or
        // foreign setup sessions never reveal this retired endpoint as HTTP 429.
        $claim = $this->authorizeClaim($request);
        $this->consumeAttempt('first-run-admin|' . $request->ip(), 5);

        $validator = Validator::make($request->all(), [
            'name' => ['required', 'string', 'max:255'],
            'mobile' => ['required', 'string', 'max:32'],
            'email' => ['required', 'email:rfc', 'max:255'],
            'pin' => ['required', 'string', 'regex:/^\\d{6}$/', 'confirmed'],
        ]);
        $validator->after(function ($validator) use ($request): void {
            $mobile = MobileNumber::normalize((string) $request->input('mobile'));
            if ($mobile === '' || !MobileNumber::isValid($mobile)) {
                $validator->errors()->add('mobile', 'Enter a valid mobile number.');
            }
        });

        if ($validator->fails()) {
            return back()->withErrors($validator)->withInput($request->except(['pin', 'pin_confirmation']));
        }

        try {
            $bootstrap->createInitialSuperAdmin($claim, [
                'name' => (string) $request->input('name'),
                'mobile' => (string) $request->input('mobile'),
                'email' => (string) $request->input('email'),
                'pin' => (string) $request->input('pin'),
            ]);
        } catch (\Throwable $e) {
            report($e);
            return back()->withInput($request->except(['pin', 'pin_confirmation']))
                ->with('error', 'First SuperAdmin setup failed. No default credentials were created.');
        }

        $request->session()->forget(FirstRunSetupState::SESSION_CLAIM);
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route('safa.login')->with('success', 'Setup completed. Sign in with the SuperAdmin credentials you just created.');
    }

    private function authorizeClaim(Request $request): string
    {
        abort_unless(FirstRunSetupState::adminCompletionRequired(), 404);

        $claim = (string) $request->session()->get(FirstRunSetupState::SESSION_CLAIM, '');
        abort_unless(FirstRunSetupState::claimMatches($claim), 403, 'First-run setup belongs to the browser session that initialized the database.');

        return $claim;
    }

    private function consumeAttempt(string $key, int $maxAttempts): void
    {
        if (RateLimiter::tooManyAttempts($key, $maxAttempts)) {
            abort(429, 'Too many first-run setup attempts.');
        }

        // During first-run the global middleware has already selected the file
        // cache store, so this remains available even before cache tables exist.
        RateLimiter::hit($key, 60);
    }
}
