<?php

namespace App\Http\Controllers;

use App\Models\Account;
use App\Models\SystemSetting;
use App\Models\User;
use App\Models\UserAccountShare;
use App\Support\MobileNumber;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use Illuminate\View\View;

class WebAuthController extends Controller
{
    public function showLogin(Request $request): View|RedirectResponse
    {
        if ($request->user()) return redirect()->route('safa.app');

        $language = strtolower((string) $request->query('lang', $request->session()->get('safa_web_language', 'en')));
        if (!in_array($language, ['en', 'bn'], true)) $language = 'en';
        $request->session()->put('safa_web_language', $language);

        $setting = Schema::hasTable('system_settings') ? SystemSetting::first() : null;

        return view('safa.login', [
            'language' => $language,
            'appName' => $setting?->app_name ?: 'SAFA',
            'captainName' => null,
            'logoSource' => $setting?->webLogoSource() ?: '/safa-logo.png',
        ]);
    }

    public function login(Request $request): RedirectResponse
    {
        $validated = $request->validate([
            'identity' => ['required', 'string', 'max:255'],
            'credential' => ['required', 'string', 'min:6', 'max:255'],
            'language' => ['nullable', 'in:en,bn'],
        ]);

        $identity = trim((string) $validated['identity']);
        $user = $this->findUser($identity);
        if (!$user || !(bool) $user->is_activated || !$this->credentialMatches($user, (string) $validated['credential'])) {
            return back()->withInput(['identity' => $identity])->withErrors([
                'auth' => $this->failureIdentityType($identity),
            ]);
        }

        Auth::login($user, false);
        $request->session()->regenerate();
        $request->session()->put('safa_web_language', $validated['language'] ?? 'en');

        $ownedIds = Account::query()->where('owner_user_id', $user->id)->pluck('id');
        $sharedIds = UserAccountShare::query()->where('shared_with_user_id', $user->id)->pluck('account_id');
        $authorizedIds = $ownedIds->merge($sharedIds)->map(fn ($id) => (int) $id)->unique()->values();
        if ($authorizedIds->count() === 1) {
            $request->session()->put('safa_active_account_id', $authorizedIds->first());
        } else {
            $request->session()->forget('safa_active_account_id');
        }

        return redirect()->intended(route('safa.app'));
    }

    public function logout(Request $request): RedirectResponse
    {
        Auth::logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return redirect()->route('safa.login');
    }

    private function findUser(string $identity): ?User
    {
        if (!Schema::hasTable('users')) return null;

        if (filter_var($identity, FILTER_VALIDATE_EMAIL)) {
            $query = User::query()->whereRaw('LOWER(email) = ?', [strtolower($identity)]);
            return $query->count() === 1 ? $query->first() : null;
        }

        $mobile = MobileNumber::normalize($identity);
        if ($mobile === '' || !MobileNumber::isValid($mobile)) return null;
        $query = User::query()->where('mobile', $mobile);

        return $query->count() === 1 ? $query->first() : null;
    }

    private function credentialMatches(User $user, string $credential): bool
    {
        $normalized = $this->normalizeDigits(trim($credential));
        $candidate = preg_match('/^\d{6}$/', $normalized) ? $normalized : $credential;

        foreach (array_filter([$user->pin_hash, $user->password]) as $hash) {
            try {
                if (Hash::check($candidate, (string) $hash)) return true;
            } catch (\Throwable) {
                // Invalid legacy hashes are treated as a failed credential check.
            }
        }

        return false;
    }

    private function failureIdentityType(string $identity): string
    {
        // Failure copy is selected only from the submitted identifier's shape.
        // It must never depend on whether an account exists, is active, or which
        // credential hash matched, so the UI cannot become a user-enumeration side channel.
        return str_contains($identity, '@') ? 'email' : 'mobile';
    }

    private function normalizeDigits(string $value): string
    {
        return strtr($value, [
            '٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9',
            '۰'=>'0','۱'=>'1','۲'=>'2','۳'=>'3','۴'=>'4','۵'=>'5','۶'=>'6','۷'=>'7','۸'=>'8','۹'=>'9',
            '০'=>'0','১'=>'1','২'=>'2','৩'=>'3','৪'=>'4','৫'=>'5','৬'=>'6','৭'=>'7','৮'=>'8','৯'=>'9',
        ]);
    }
}
