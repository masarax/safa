<?php

namespace App\Http\Controllers;

use App\Models\User;
use App\Support\CredentialVerifier;
use App\Support\MobileNumber;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\View\View;

class WebAuthController extends Controller
{
    public function showLogin(Request $request): View|RedirectResponse
    {
        if ($request->user()) return redirect()->route('safa.app');
        return view('auth.login');
    }

    public function login(Request $request): RedirectResponse
    {
        $validated = $request->validate([
            'identity' => ['required', 'string', 'max:255'],
            'credential' => ['required', 'string', 'min:6', 'max:255'],
        ]);

        $rawIdentity = trim((string) $validated['identity']);
        $credential = $this->normalizeDigits((string) $validated['credential']);
        $isEmail = filter_var($rawIdentity, FILTER_VALIDATE_EMAIL) !== false;
        $user = $isEmail
            ? User::query()->where('email', strtolower($rawIdentity))->first()
            : User::query()->where('mobile', MobileNumber::normalize($rawIdentity))->first();

        $credentialMatches = $this->credentialMatches($user, $credential);
        if (!$user || !$credentialMatches || !(bool) $user->is_activated) {
            return back()
                ->withInput($request->only('identity'))
                ->withErrors(['identity' => $this->failureMessage($request, $isEmail)]);
        }

        Auth::login($user);
        $request->session()->regenerate();

        return redirect()->intended(route('safa.app'));
    }

    public function logout(Request $request): RedirectResponse
    {
        Auth::guard('web')->logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();
        return redirect()->route('safa.login');
    }

    private function credentialMatches(?User $user, string $credential): bool
    {
        $looksLikePin = preg_match('/^\d{6}$/', $credential) === 1;
        $storedHash = $looksLikePin
            ? ($user?->pin_hash ?: $user?->password)
            : ($user?->password ?: $user?->pin_hash);

        return CredentialVerifier::verify($credential, $storedHash);
    }

    private function failureMessage(Request $request, bool $isEmail): string
    {
        $bn = $request->session()->get('safa_web_language', 'en') === 'bn';
        if ($isEmail) {
            return $bn
                ? 'ইমেইল বা পাসওয়ার্ড সঠিক নয়।'
                : 'Email or password is incorrect.';
        }

        return $bn
            ? 'মোবাইল নম্বর বা পিন সঠিক নয়।'
            : 'Mobile number or PIN is incorrect.';
    }

    private function normalizeDigits(string $value): string
    {
        return strtr(trim($value), [
            '٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9',
            '۰'=>'0','۱'=>'1','۲'=>'2','۳'=>'3','۴'=>'4','۵'=>'5','۶'=>'6','۷'=>'7','۸'=>'8','۹'=>'9',
            '০'=>'0','১'=>'1','২'=>'2','৩'=>'3','৪'=>'4','৫'=>'5','৬'=>'6','৭'=>'7','৮'=>'8','৯'=>'9',
        ]);
    }
}
