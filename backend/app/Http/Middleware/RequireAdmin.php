<?php

namespace App\Http\Middleware;

use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class RequireAdmin
{
    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user() ?? $request->attributes->get('user');
        if (!$user instanceof User || !$user->is_activated || !$user->canManageBranding()) {
            return response()->json([
                'status' => 'error',
                'message' => 'Forbidden. Administrator access is required.',
            ], 403);
        }

        return $next($request);
    }
}
