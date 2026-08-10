<?php

namespace App\Http\Middleware;

use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class RejectInactiveLogin
{
    public function handle(Request $request, Closure $next): Response
    {
        $identifier = trim((string) (
            $request->input('mobile')
            ?? $request->input('email')
            ?? $request->input('username')
        ));

        if ($identifier !== '') {
            $user = User::where('mobile', $identifier)
                ->orWhere('email', $identifier)
                ->first();

            if ($user && !$user->is_activated) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'This account is inactive. Please contact an administrator.'
                ], 403);
            }
        }

        return $next($request);
    }
}
