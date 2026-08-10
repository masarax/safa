<?php

namespace App\Http\Middleware;

use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class AllowInitialSuperAdminActivation
{
    public function handle(Request $request, Closure $next): Response
    {
        $activeSuperAdminExists = User::where('role', 'superadmin')
            ->where('is_activated', true)
            ->exists();

        if ($activeSuperAdminExists) {
            return response()->json([
                'status' => 'error',
                'message' => 'SuperAdmin activation is already completed.'
            ], 403);
        }

        return $next($request);
    }
}
