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
        if (!$user || !$user->is_activated) return response()->json(['status' => 'error', 'message' => 'Unauthorized.'], 401);

        $requestedId = $request->header('X-SAFA-ACCOUNT-ID') ?? $request->input('account_id');
        $requestedId = is_numeric($requestedId) ? (int) $requestedId : 0;
        $account = $requestedId > 0 ? Account::find($requestedId) : Account::where('owner_user_id', $user->id)->orderBy('id')->first();
        if (!$account) return response()->json(['status' => 'error', 'message' => 'No accessible account context found.'], 403);

        if ($user->role !== 'superadmin' && (int) $account->owner_user_id !== (int) $user->id) {
            $shared = UserAccountShare::query()->where('shared_with_user_id', $user->id)->where('account_id', $account->id)->where('owner_user_id', (int) $account->owner_user_id)->exists();
            if (!$shared) return response()->json(['status' => 'error', 'message' => 'Forbidden: account context is not authorized.'], 403);
        }

        $user->setAttribute('account_id', (int) $account->id);
        $request->attributes->set('active_account_id', (int) $account->id);
        return $next($request);
    }
}
