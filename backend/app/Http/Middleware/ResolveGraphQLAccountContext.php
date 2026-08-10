<?php

namespace App\Http\Middleware;

use App\Models\Account;
use App\Models\UserAccountShare;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class ResolveGraphQLAccountContext
{
    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user() ?? $request->attributes->get('user');
        if (!$user || !$user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized.'], 401);
        }

        $requestedId = $request->header('X-SAFA-ACCOUNT-ID') ?? $request->input('account_id');
        $requestedId = is_numeric($requestedId) ? (int) $requestedId : 0;

        $account = null;
        if ($requestedId > 0) {
            $account = Account::find($requestedId);
            if (!$account) {
                return response()->json(['status' => 'error', 'message' => 'Requested account does not exist.'], 403);
            }
        } else {
            $account = Account::where('owner_user_id', $user->id)->orderBy('id')->first();
            if (!$account) {
                $legacyAccount = Account::find($user->id);
                if ($legacyAccount && ((int) $legacyAccount->owner_user_id === 0 || $legacyAccount->owner_user_id === null)) {
                    $account = $legacyAccount;
                }
            }
            if (!$account) {
                $share = UserAccountShare::where('shared_with_user_id', $user->id)->orderBy('id')->first();
                $account = $share ? Account::find($share->account_id) : null;
            }
        }

        if (!$account) {
            return response()->json(['status' => 'error', 'message' => 'No accessible account context found.'], 403);
        }

        $isLegacyOwner = ((int) $account->owner_user_id === 0 || $account->owner_user_id === null)
            && (int) $account->id === (int) $user->id;

        if ($user->role !== 'superadmin' && (int) $account->owner_user_id !== (int) $user->id && !$isLegacyOwner) {
            $shared = UserAccountShare::where('shared_with_user_id', $user->id)
                ->where('account_id', $account->id)
                ->exists();
            if (!$shared) {
                return response()->json(['status' => 'error', 'message' => 'Forbidden: account context is not authorized.'], 403);
            }
        }

        $user->setAttribute('account_id', (int) $account->id);
        $request->attributes->set('active_account_id', (int) $account->id);

        return $next($request);
    }
}
