<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Schema;
use App\Models\User;
use App\Models\SafaApiKey;
use App\Models\Account;
use App\Models\UserAccountShare;

trait AuthorizeAccountContext
{
    protected function resolveAuthorizedAccountContext(Request $request): array
    {
        $user = $request->user() ?? $request->attributes->get('user');
        $token = $request->bearerToken() ?? $request->header('X-SAFA-ACCESS-TOKEN') ?? $request->input('access_token');
        $payload = null;

        if ($token) {
            $payload = AuthJWTController::verifyJwt($token);
            if (!$payload) return ['error' => response()->json(['status' => 'error', 'message' => 'Unauthorized: Invalid or expired access token.'], 401)];
            if (!$user && isset($payload['user_id'])) $user = User::find((int) $payload['user_id']);
        }

        if ($token && (!$user || !$user->is_activated)) return ['error' => response()->json(['status' => 'error', 'message' => 'Unauthorized: User account not found or deactivated.'], 401)];

        $requestedAccountId = null;
        if ($payload && isset($payload['account_id'])) $requestedAccountId = (int) $payload['account_id'];
        if (!$requestedAccountId) {
            $headerAccountId = $request->header('X-SAFA-ACCOUNT-ID') ?? $request->input('account_id');
            if ($headerAccountId !== null && is_numeric($headerAccountId)) $requestedAccountId = (int) $headerAccountId;
        }

        $hasOwnerColumn = Schema::hasColumn('accounts', 'owner_user_id');

        if ($user) {
            if ($hasOwnerColumn) {
                $ownedAccount = Account::query()->where('owner_user_id', $user->id)->orderBy('id')->first();
                if (!$ownedAccount) {
                    $legacyAccount = Account::find($user->id);
                    if ($legacyAccount && (int) ($legacyAccount->owner_user_id ?? 0) === 0) {
                        $legacyAccount->owner_user_id = $user->id;
                        $legacyAccount->save();
                        $ownedAccount = $legacyAccount;
                    }
                }
                if (!$ownedAccount && !$requestedAccountId) {
                    $ownedAccount = Account::create(['name' => trim(($user->name ?: 'SAFA') . ' Account'), 'balance' => 0.00, 'owner_user_id' => $user->id]);
                }

                $targetAccountId = $requestedAccountId ?: (int) $ownedAccount->id;
                $targetAccount = Account::find($targetAccountId);
                if (!$targetAccount) return ['error' => response()->json(['status' => 'error', 'message' => 'Forbidden: Requested account does not exist.'], 403)];

                if ($user->role === 'superadmin' || (int) $targetAccount->owner_user_id === (int) $user->id) {
                    return ['user' => $user, 'account_id' => (int) $targetAccount->id];
                }

                // Shared access is always scoped to the exact account. A share on one
                // account must never authorize another account owned by the same owner.
                $shareExists = UserAccountShare::query()
                    ->where('shared_with_user_id', $user->id)
                    ->where('account_id', (int) $targetAccount->id)
                    ->where('owner_user_id', (int) $targetAccount->owner_user_id)
                    ->exists();

                if (!$shareExists) return ['error' => response()->json(['status' => 'error', 'message' => 'Forbidden: You do not have authorization to access this account context.'], 403)];
                return ['user' => $user, 'account_id' => (int) $targetAccount->id];
            }

            // Legacy schema: never fall back to an arbitrary/first account.
            $candidateAccountId = $requestedAccountId ?: (int) $user->id;
            $legacyAccount = Account::find($candidateAccountId);
            if ($legacyAccount && (int) $legacyAccount->id === (int) $user->id) return ['user' => $user, 'account_id' => (int) $legacyAccount->id];

            $apiKey = $request->header('X-SAFA-API-KEY');
            if ($apiKey) {
                $keyRecord = SafaApiKey::where('api_key', $apiKey)->where('is_active', true)->first();
                if ($keyRecord?->account_id && (int) $keyRecord->account_id === (int) $candidateAccountId) return ['user' => $user, 'account_id' => (int) $keyRecord->account_id];
            }
            return ['error' => response()->json(['status' => 'error', 'message' => 'Forbidden: account context cannot be resolved safely on the legacy schema.'], 403)];
        }

        $apiKey = $request->header('X-SAFA-API-KEY');
        if ($apiKey) {
            $keyRecord = SafaApiKey::where('api_key', $apiKey)->where('is_active', true)->first();
            if ($keyRecord && $keyRecord->account_id) return ['user' => null, 'account_id' => (int) $keyRecord->account_id];
        }

        return ['error' => response()->json(['status' => 'error', 'message' => 'Unauthorized: authenticated user or account API key is required.'], 401)];
    }
}
