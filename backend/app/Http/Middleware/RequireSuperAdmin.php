<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class RequireSuperAdmin
{
    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user() ?? $request->attributes->get('user');
        if (!$user || !$user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized.'], 401);
        }

        if ($user->role !== 'superadmin') {
            return response()->json(['status' => 'error', 'message' => 'Forbidden: SuperAdmin access required.'], 403);
        }

        return $next($request);
    }
}
