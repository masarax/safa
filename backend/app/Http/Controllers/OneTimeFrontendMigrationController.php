<?php

namespace App\Http\Controllers;

use App\Services\DatabaseUpdateService;
use App\Services\FirstRunDatabaseBootstrapService;
use App\Services\RequiredInitialSuperAdminService;
use App\Support\FirstRunSetupCode;
use App\Support\FirstRunSetupState;
use App\Support\OneTimeFrontendMigrationState;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\RateLimiter;

class OneTimeFrontendMigrationController extends Controller
{
    public function show(Request $request, RequiredInitialSuperAdminService $requiredAdmin): Response
    {
        abort_unless(OneTimeFrontendMigrationState::required(), 404);

        $requiresSetupCode = $requiredAdmin->needsProvisioning();
        if ($requiresSetupCode) {
            FirstRunSetupCode::ensureForFrontendMigration();
        }

        return response()->view('frontend_data_migration', [
            'language' => $this->language($request),
            'pendingMigrations' => DatabaseUpdateController::pendingMigrations(),
            'requiresSetupCode' => $requiresSetupCode,
            'setupCodePath' => $requiresSetupCode ? FirstRunSetupCode::operatorPath() : null,
        ]);
    }

    public function run(
        Request $request,
        DatabaseUpdateService $updates,
        FirstRunDatabaseBootstrapService $bootstrap,
        RequiredInitialSuperAdminService $requiredAdmin
    ): RedirectResponse {
        // Check the durable one-time state before rate limiting so a consumed URL
        // is permanently indistinguishable from a route that does not exist.
        abort_unless(OneTimeFrontendMigrationState::required(), 404);

        $language = $this->language($request);
        $this->consumeAttempt('frontend-data-migration|' . $request->ip(), 5);

        $firstDatabaseInitialization = FirstRunSetupState::databaseInitializationRequired();
        $requiresSetupCode = $requiredAdmin->needsProvisioning();
        $claim = null;

        // Creating, promoting or resetting the required owner account is a
        // privileged first-run action. The public migration button cannot do it
        // unless the operator proves access to the server-private setup code.
        if ($requiresSetupCode) {
            FirstRunSetupCode::ensureForFrontendMigration();
            $setupCode = (string) $request->input('setup_code', '');
            if (!FirstRunSetupCode::verify($setupCode)) {
                return redirect()->route('frontend.migration.show', ['lang' => $language])->with(
                    'error',
                    $language === 'bn'
                        ? 'প্রথম SuperAdmin নিশ্চিত করার জন্য server-private setup code সঠিক নয়।'
                        : 'The server-private setup code is invalid for required SuperAdmin provisioning.'
                );
            }
        }

        try {
            if ($firstDatabaseInitialization) {
                $claim = bin2hex(random_bytes(32));
                $request->session()->put(FirstRunSetupState::SESSION_CLAIM, $claim);
                $result = $bootstrap->initializeDatabase($claim);
            } else {
                $result = $updates->runOneTimeFrontend();
            }

            if ($result['busy']) {
                return redirect()->route('frontend.migration.show', ['lang' => $language])->with(
                    'info',
                    $language === 'bn'
                        ? 'ডাটা মাইগ্রেশন ইতিমধ্যে চলছে। শেষ হলে আবার চেষ্টা করুন।'
                        : 'Data migration is already running. Try again after it finishes.'
                );
            }

            // Provision only while the one-time migration is still unconsumed.
            // A successful marker write below permanently removes this path, so
            // later deploys and /update runs can never reset this credential.
            if ($requiresSetupCode || $requiredAdmin->needsProvisioning()) {
                $requiredAdmin->provisionOnce();
            }

            OneTimeFrontendMigrationState::markCompleted();
            FirstRunSetupCode::destroy();
            $request->session()->forget(FirstRunSetupState::SESSION_CLAIM);

            return redirect()->route('safa.login', ['lang' => $language])->with(
                'success',
                $language === 'bn'
                    ? 'ডাটা মাইগ্রেশন এবং প্রথম SuperAdmin সেটআপ সফল হয়েছে। এখন লগইন করুন।'
                    : 'Data migration and required SuperAdmin setup completed successfully. Sign in now.'
            );
        } catch (\Throwable $e) {
            report($e);

            return redirect()->route('frontend.migration.show', ['lang' => $language])->with(
                'error',
                $language === 'bn'
                    ? 'ডাটা মাইগ্রেশন সম্পন্ন হয়নি। বিদ্যমান ডাটা reset বা delete করা হয়নি।'
                    : 'Data migration did not complete. Existing data was not reset or intentionally deleted.'
            );
        }
    }

    private function consumeAttempt(string $key, int $maxAttempts): void
    {
        if (RateLimiter::tooManyAttempts($key, $maxAttempts)) {
            abort(429, 'Too many data migration attempts.');
        }

        RateLimiter::hit($key, 60);
    }

    private function language(Request $request): string
    {
        $requested = strtolower(trim((string) ($request->input('language') ?: $request->query('lang', ''))));
        if (in_array($requested, ['en', 'bn'], true)) {
            $request->session()->put('safa_web_language', $requested);
            return $requested;
        }

        return (string) $request->session()->get('safa_web_language', 'en') === 'bn' ? 'bn' : 'en';
    }
}
