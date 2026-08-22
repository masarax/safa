<?php

namespace App\Http\Controllers;

use App\Models\SystemSetting;
use App\Models\User;
use App\Support\BusinessPermissions;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\App;
use Illuminate\View\View;

class WebAppController extends Controller
{
    public function index(Request $request): View
    {
        $user = $request->user();
        abort_unless($user && $user->is_activated, 403);

        $accountResponse = app(AccountContextController::class)->index($request);
        $accountPayload = $accountResponse->getData(true);
        $accounts = $accountPayload['accounts'] ?? [];
        $activeAccountId = isset($accountPayload['active_account_id'])
            ? (int) $accountPayload['active_account_id']
            : null;

        if ($activeAccountId && $request->hasSession()) {
            $request->session()->put('safa_active_account_id', $activeAccountId);
        }

        // Branding belongs to the active business account. The legacy/global
        // row remains a read-only fallback for accounts that have not yet saved
        // their own branding settings.
        $setting = $activeAccountId
            ? SystemSetting::query()->where('account_id', $activeAccountId)->orderBy('id')->first()
            : null;
        $setting ??= SystemSetting::query()->whereNull('account_id')->orderBy('id')->first();

        $language = $request->session()->get('safa_web_language', 'en');
        if (!in_array($language, ['en', 'bn'], true)) $language = 'en';
        App::setLocale($language);
        $permissions = BusinessPermissions::effective($user, (int) ($activeAccountId ?? 0));
        $webCopy = array_replace(
            (array) trans('web.js'),
            (array) trans('web_runtime'),
        );

        return view('safa.app', [
            'user' => $user,
            'roleLabel' => User::roleLabel((string) $user->role),
            'permissions' => $permissions,
            'accounts' => $accounts,
            'activeAccountId' => $activeAccountId,
            'setting' => $setting,
            'logoSource' => $setting?->webLogoSource() ?: '/safa-logo.png',
            'captainName' => $user->name,
            'language' => $language,
            'webCopy' => $webCopy,
            'canManageUsers' => $user->canManageUsers(),
            'canManageSystemSettings' => $user->canManageBranding(),
            'isSuperAdmin' => $user->isSuperAdmin(),
        ]);
    }
}
