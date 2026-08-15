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
        // API-key-only requests are a test-only convenience for domain/sync
        // tests. They can never execute in a production environment.
        if (app()->environment('testing') && $request->header('X-SAFA-API-KEY') && !$request->bearerToken()) {
            return $next($request);
        }

        $user = $request->user() ?? $request->attributes->get('user');
        $accessToken = $request->bearerToken();

        if (!$user || !$accessToken || !$user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized: authenticated session required.'], 401);
        }

        $session = AuthSession::findActiveByAccessToken($accessToken, (int) $user->id);

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
