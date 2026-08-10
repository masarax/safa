<?php

namespace App\Http\Middleware;

use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
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

            if (!$user && DB::getSchemaBuilder()->hasTable('operator_accounts')) {
                $operator = DB::table('operator_accounts')
                    ->where('mobile', $identifier)
                    ->first();

                if ($operator && !(bool) $operator->is_activated) {
                    return response()->json([
                        'status' => 'error',
                        'message' => 'This operator account is inactive. Please contact an administrator.'
                    ], 403);
                }
            }
        }

        return $next($request);
    }
}
