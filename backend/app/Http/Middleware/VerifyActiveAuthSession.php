<?php

namespace App\Http\Middleware;

use App\Models\AuthSession;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class VerifyActiveAuthSession
{
    /**
     * Ensure the authenticated JWT is still the current, non-revoked session
     * and that the backing user is still active.
     */
    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user() ?? $request->attributes->get('user');
        $accessToken = $request->bearerToken();

        if (!$user || !$accessToken) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: authenticated session required.'
            ], 401);
        }

        if (!$user->is_activated) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: user account is inactive.'
            ], 401);
        }

        $session = AuthSession::where('user_id', $user->id)
            ->where('access_token', $accessToken)
            ->where('is_revoked', false)
            ->first();

        if (!$session) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: session is no longer valid.'
            ], 401);
        }

        if ($session->expires_at && $session->expires_at->isPast()) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: session has expired.'
            ], 401);
        }

        return $next($request);
    }
}
