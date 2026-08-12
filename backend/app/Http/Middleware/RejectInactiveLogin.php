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
        $identifier = $this->normalizeDigits(trim((string) (
            $request->input('mobile')
            ?? $request->input('email')
            ?? $request->input('username')
        )));

        if ($identifier !== '') {
            $user = $this->findUser($identifier);

            if ($user && !$user->is_activated) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'This account is inactive. Please contact an administrator.'
                ], 403);
            }

            if (!$user && DB::getSchemaBuilder()->hasTable('operator_accounts')) {
                $normalized = preg_replace('/\D+/', '', $identifier) ?? '';
                $operatorQuery = DB::table('operator_accounts');
                if ($normalized !== '') {
                    $operatorQuery->whereRaw(
                        "REPLACE(REPLACE(REPLACE(REPLACE(mobile, ' ', ''), '-', ''), '(', ''), ')', '') = ?",
                        [$normalized]
                    );
                } else {
                    $operatorQuery->where('mobile', $identifier);
                }
                $operator = $operatorQuery->first();

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

        $normalized = preg_replace('/\D+/', '', $identifier) ?? '';
        if ($normalized === '') return null;

        return User::whereRaw(
            "REPLACE(REPLACE(REPLACE(REPLACE(mobile, ' ', ''), '-', ''), '(', ''), ')', '') = ?",
            [$normalized]
        )->first();
    }

    private function normalizeDigits(string $value): string
    {
        return strtr($value, [
            '٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4',
            '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9',
            '۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3', '۴' => '4',
            '۵' => '5', '۶' => '6', '۷' => '7', '۸' => '8', '۹' => '9',
        ]);
    }
}
