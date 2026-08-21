<?php

namespace App\Http\Controllers;

use App\Services\DatabaseUpdateService;
use App\Support\ReleaseUpdateState;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\RateLimiter;

class ReleaseUpdateController extends Controller
{
    public function show(Request $request): Response|RedirectResponse
    {
        if (!ReleaseUpdateState::required()) {
            return $this->normalEntry($request);
        }

        return response()->view('release_update', [
            'language' => $this->language($request),
        ]);
    }

    public function run(Request $request, DatabaseUpdateService $updates): RedirectResponse
    {
        if (!ReleaseUpdateState::required()) {
            return $this->normalEntry($request);
        }

        $language = $this->language($request);
        $this->consumeAttempt('release-update|' . $request->ip(), 5);

        try {
            $result = $updates->runReleaseUpdate();
            if ($result['busy']) {
                return redirect()->route('system.update.show', ['lang' => $language])->with(
                    'info',
                    $language === 'bn'
                        ? 'আপডেট ইতিমধ্যে চলছে। শেষ হলে আবার চেষ্টা করুন।'
                        : 'The update is already running. Try again after it finishes.'
                );
            }

            return redirect()->route('safa.login', ['lang' => $language])->with(
                'success',
                $language === 'bn' ? 'সিস্টেম আপডেট সফল হয়েছে।' : 'System update completed successfully.'
            );
        } catch (\Throwable $e) {
            report($e);

            return redirect()->route('system.update.show', ['lang' => $language])->with(
                'error',
                $language === 'bn'
                    ? 'আপডেট সম্পন্ন হয়নি। আবার চেষ্টা করুন বা সার্ভার লগ পরীক্ষা করুন।'
                    : 'The update did not complete. Try again or check the server logs.'
            );
        }
    }

    private function normalEntry(Request $request): RedirectResponse
    {
        return $request->user()
            ? redirect()->route('safa.app')
            : redirect()->route('safa.login');
    }

    private function consumeAttempt(string $key, int $maxAttempts): void
    {
        if (RateLimiter::tooManyAttempts($key, $maxAttempts)) {
            abort(429, 'Too many update attempts.');
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
