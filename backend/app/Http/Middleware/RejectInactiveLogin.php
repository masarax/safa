<?php

namespace App\Http\Middleware;

use App\Models\User;
use App\Support\MobileNumber;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpFoundation\Response;

class RejectInactiveLogin
{
    public function handle(Request $request, Closure $next): Response
    {
        $rawIdentifier = trim((string) (
            $request->input('mobile')
            ?? $request->input('email')
            ?? $request->input('username')
        ));

        $identifier = MobileNumber::normalize($rawIdentifier);

        if ($identifier !== '') {
            $user = $this->findUser($identifier);

            if ($user && !$user->is_activated) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'This account is inactive. Please contact an administrator.'
                ], 403);
            }

            if (!$user && DB::getSchemaBuilder()->hasTable('operator_accounts')) {
                $operator = DB::table('operator_accounts')
                    ->whereRaw(
                        "REPLACE(REPLACE(REPLACE(REPLACE(mobile, ' ', ''), '-', ''), '(', ''), ')', '') = ?",
                        [$identifier]
                    )
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

    private function findUser(string $identifier): ?User
    {
        $user = User::where('mobile', $identifier)
            ->orWhere('email', $identifier)
            ->first();
        if ($user) return $user;

        $normalized = MobileNumber::normalize($identifier);
        if ($normalized === '') return null;

        return User::whereRaw(
            "REPLACE(REPLACE(REPLACE(REPLACE(mobile, ' ', ''), '-', ''), '(', ''), ')', '') = ?",
            [$normalized]
        )->first();
    }
}
