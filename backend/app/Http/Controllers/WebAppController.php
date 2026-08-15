<?php

namespace App\Http\Controllers;

use App\Models\SystemSetting;
use App\Models\User;
use Illuminate\Http\Request;
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

        $setting = SystemSetting::first();
        $language = $request->session()->get('safa_web_language', 'en');
        if (!in_array($language, ['en', 'bn'], true)) $language = 'en';

        return view('safa.app', [
            'user' => $user,
            'roleLabel' => User::roleLabel((string) $user->role),
            'permissions' => $user->getFormattedPermissions(),
            'accounts' => $accounts,
            'activeAccountId' => $activeAccountId,
            'setting' => $setting,
            'logoSource' => $setting?->webLogoSource() ?: '/safa-logo.png',
            'language' => $language,
            'canManageUsers' => $user->canManageUsers(),
            'canManageSystemSettings' => $user->canManageBranding(),
            'isSuperAdmin' => $user->isSuperAdmin(),
        ]);
    }
}
