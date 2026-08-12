<?php

namespace App\Http\Middleware;

use App\Models\AuthSession;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class VerifyActiveAuthSession
{
    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user() ?? $request->attributes->get('user');
        $accessToken = $request->bearerToken();

        if (!$user || !$accessToken || !$user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized: authenticated session required.'], 401);
        }

        $session = AuthSession::query()
            ->where('user_id', $user->id)
            ->where('access_token_hash', AuthSession::tokenHash($accessToken))
            ->where('is_revoked', false)
            ->first();

        if (!$session) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized: session is no longer valid.'], 401);
        }

        if ($session->expires_at && $session->expires_at->isPast()) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized: session has expired.'], 401);
        }

        $request->attributes->set('auth_session', $session);
        return $next($request);
    }
}
