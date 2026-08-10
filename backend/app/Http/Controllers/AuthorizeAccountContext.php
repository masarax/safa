<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\User;
use App\Models\SafaApiKey;
use App\Models\Account;
use App\Models\UserAccountShare;
use App\Http\Controllers\AuthJWTController;

trait AuthorizeAccountContext
{
    /**
     * Resolve and authorize current authenticated user and account context.
     * Returns array ['user' => ?User, 'account_id' => int] or ['error' => Response]
     */
    protected function resolveAuthorizedAccountContext(Request $request): array
    {
        $user = $request->user() ?? $request->attributes->get('user');
        $token = $request->bearerToken() ?? $request->header('X-SAFA-ACCESS-TOKEN') ?? $request->input('access_token');
        $payload = null;

        if ($token) {
            $payload = AuthJWTController::verifyJwt($token);
            if (!$payload) {
                return ['error' => response()->json([
                    'status' => 'error',
                    'message' => 'Unauthorized: Invalid or expired access token.'
                ], 401)];
            }
            if (!$user && isset($payload['user_id'])) {
                $user = User::find($payload['user_id']);
            }
        }

        if ($token && (!$user || !$user->is_activated)) {
            return ['error' => response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: User account not found or deactivated.'
            ], 401)];
        }

        $requestedAccountId = null;
        if ($payload && isset($payload['account_id'])) {
            $requestedAccountId = (int) $payload['account_id'];
        }
        if (!$requestedAccountId) {
            $headerAccountId = $request->header('X-SAFA-ACCOUNT-ID') ?? $request->input('account_id');
            if ($headerAccountId && is_numeric($headerAccountId)) {
                $requestedAccountId = (int) $headerAccountId;
            }
        }

        if ($user) {
            $userPrimaryAccountId = (int) $user->id;
            $targetAccountId = $requestedAccountId ?: $userPrimaryAccountId;

            if ($user->role === 'superadmin' || $targetAccountId === $userPrimaryAccountId || $targetAccountId === 1) {
                return ['user' => $user, 'account_id' => $targetAccountId];
            }

            // Verify explicit account share
            $shareExists = UserAccountShare::where('shared_with_user_id', $user->id)
                ->where(function ($q) use ($targetAccountId) {
                    $q->where('account_id', $targetAccountId)->orWhere('owner_user_id', $targetAccountId);
                })
                ->exists();

            if (!$shareExists) {
                return ['error' => response()->json([
                    'status' => 'error',
                    'message' => 'Forbidden: You do not have authorization to access this account context.'
                ], 403)];
            }

            return ['user' => $user, 'account_id' => $targetAccountId];
        }

        // Fallback for API Key (Headless / API integration)
        $apiKey = $request->header('X-SAFA-API-KEY');
        if ($apiKey) {
            $keyRecord = SafaApiKey::where('api_key', $apiKey)->where('is_active', true)->first();
            if ($keyRecord && $keyRecord->account_id) {
                return ['user' => null, 'account_id' => (int) $keyRecord->account_id];
            }
        }

        $defaultAccount = Account::firstOrCreate(
            ['name' => 'SAFA Default Account'],
            ['balance' => 0.00]
        );

        return ['user' => null, 'account_id' => (int) $defaultAccount->id];
    }
}
