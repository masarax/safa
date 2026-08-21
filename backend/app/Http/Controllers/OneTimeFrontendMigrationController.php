<?php

namespace App\Http\Controllers;

use App\Services\DatabaseUpdateService;
use App\Services\FirstRunDatabaseBootstrapService;
use App\Support\FirstRunSetupCode;
use App\Support\FirstRunSetupState;
use App\Support\OneTimeFrontendMigrationState;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\RateLimiter;

class OneTimeFrontendMigrationController extends Controller
{
    public function show(Request $request): Response
    {
        abort_unless(OneTimeFrontendMigrationState::required(), 404);

        $requiresSetupCode = FirstRunSetupState::databaseInitializationRequired();
        if ($requiresSetupCode) {
            FirstRunSetupCode::ensure();
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
        FirstRunDatabaseBootstrapService $bootstrap
    ): RedirectResponse {
        // Check the durable one-time state before rate limiting so a consumed URL
        // is permanently indistinguishable from a route that does not exist.
        abort_unless(OneTimeFrontendMigrationState::required(), 404);

        $language = $this->language($request);
        $this->consumeAttempt('frontend-data-migration|' . $request->ip(), 5);

        $firstDatabaseInitialization = FirstRunSetupState::databaseInitializationRequired();
        $claim = null;

        // A completely empty database would otherwise let the first random web
        // visitor become the first SuperAdmin after migration. Preserve the
        // deployment-owner proof for that one case while keeping existing-data
        // migrations as a single frontend click.
        if ($firstDatabaseInitialization) {
            FirstRunSetupCode::ensure();
            $setupCode = (string) $request->input('setup_code', '');
            if (!FirstRunSetupCode::verify($setupCode)) {
                return redirect()->route('frontend.migration.show', ['lang' => $language])->with(
                    'error',
                    $language === 'bn'
                        ? 'খালি ডাটাবেজের প্রথম সেটআপের জন্য server-private setup code সঠিক নয়।'
                        : 'The server-private setup code is invalid for first setup on an empty database.'
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

            OneTimeFrontendMigrationState::markCompleted();
            FirstRunSetupCode::destroy();

            if (
                $claim !== null
                && FirstRunSetupState::adminCompletionRequired()
                && FirstRunSetupState::claimMatches($claim)
            ) {
                return redirect()->route('setup.admin.show', ['lang' => $language])->with(
                    'success',
                    $language === 'bn'
                        ? 'ডাটা মাইগ্রেশন সফল হয়েছে। এখন প্রথম SuperAdmin তৈরি করুন।'
                        : 'Data migration completed. Create the first SuperAdmin to finish setup.'
                );
            }

            if (FirstRunSetupState::adminCompletionRequired()) {
                return redirect()->route('setup.index', ['lang' => $language])->with(
                    'success',
                    $language === 'bn'
                        ? 'ডাটা মাইগ্রেশন সফল হয়েছে। এখন SuperAdmin সেটআপ শেষ করুন।'
                        : 'Data migration completed. Finish the SuperAdmin setup.'
                );
            }

            return redirect('/')->with(
                'success',
                $language === 'bn'
                    ? 'ডাটা মাইগ্রেশন সফল হয়েছে। একবারের migration option স্থায়ীভাবে বন্ধ হয়েছে।'
                    : 'Data migration completed. The one-time migration option is now permanently closed.'
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
