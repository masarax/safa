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
            if (!$payload) {
                return ['error' => response()->json(['status' => 'error', 'message' => 'Unauthorized: Invalid or expired access token.'], 401)];
            }
            if (!$user && isset($payload['user_id'])) {
                $user = User::find((int) $payload['user_id']);
            }
        }

        if ($token && (!$user || !$user->is_activated)) {
            return ['error' => response()->json(['status' => 'error', 'message' => 'Unauthorized: User account not found or deactivated.'], 401)];
        }

        $requestedAccountId = null;
        if ($payload && isset($payload['account_id'])) $requestedAccountId = (int) $payload['account_id'];
        if (!$requestedAccountId) {
            $headerAccountId = $request->header('X-SAFA-ACCOUNT-ID') ?? $request->input('account_id');
            if ($headerAccountId !== null && is_numeric($headerAccountId)) $requestedAccountId = (int) $headerAccountId;
        }

        $hasOwnerColumn = Schema::hasColumn('accounts', 'owner_user_id');

        if ($user) {
            // New account-context schema: strictly resolve owned/shared accounts.
            if ($hasOwnerColumn) {
                $ownedAccount = Account::query()->where('owner_user_id', $user->id)->orderBy('id')->first();
                if (!$ownedAccount) {
                    $legacyAccount = Account::find($user->id);
                    if ($legacyAccount) {
                        $legacyAccount->owner_user_id = $user->id;
                        $legacyAccount->save();
                        $ownedAccount = $legacyAccount;
                    }
                }
                if (!$ownedAccount) {
                    $ownedAccount = Account::create([
                        'name' => trim(($user->name ?: 'SAFA') . ' Account'),
                        'balance' => 0.00,
                        'owner_user_id' => $user->id,
                    ]);
                }

                $targetAccountId = $requestedAccountId ?: (int) $ownedAccount->id;
                $targetAccount = Account::find($targetAccountId);
                if (!$targetAccount) {
                    return ['error' => response()->json(['status' => 'error', 'message' => 'Forbidden: Requested account does not exist.'], 403)];
                }

                if ((int) $targetAccount->owner_user_id === (int) $user->id
                    || ((int) $targetAccount->owner_user_id === 0 && (int) $targetAccount->id === (int) $user->id)) {
                    return ['user' => $user, 'account_id' => (int) $targetAccount->id];
                }

                if ($user->role === 'superadmin') {
                    return ['user' => $user, 'account_id' => (int) $targetAccount->id];
                }

                $shareExists = UserAccountShare::where('shared_with_user_id', $user->id)
                    ->where(function ($q) use ($targetAccountId, $targetAccount) {
                        $q->where('account_id', $targetAccountId)
                          ->orWhere('owner_user_id', (int) $targetAccount->owner_user_id);
                    })
                    ->exists();

                if (!$shareExists) {
                    return ['error' => response()->json(['status' => 'error', 'message' => 'Forbidden: You do not have authorization to access this account context.'], 403)];
                }

                return ['user' => $user, 'account_id' => (int) $targetAccount->id];
            }

            // Legacy production schema fallback. This is deliberately conservative:
            // prefer the historical user-id/account-id convention, then the account
            // referenced by the authenticated API key, then the first account. This
            // keeps existing business data reachable until the owner_user_id migration
            // is actually executed, instead of turning every sync request into HTTP 500.
            $legacyAccount = Account::find($requestedAccountId ?: (int) $user->id);
            if (!$legacyAccount) {
                $apiKey = $request->header('X-SAFA-API-KEY');
                if ($apiKey) {
                    $keyRecord = SafaApiKey::where('api_key', $apiKey)->where('is_active', true)->first();
                    if ($keyRecord?->account_id) {
                        $legacyAccount = Account::find((int) $keyRecord->account_id);
                    }
                }
            }
            if (!$legacyAccount) {
                $legacyAccount = Account::query()->orderBy('id')->first();
            }
            if (!$legacyAccount) {
                $legacyAccount = Account::create([
                    'name' => trim(($user->name ?: 'SAFA') . ' Account'),
                    'balance' => 0.00,
                ]);
            }
            return ['user' => $user, 'account_id' => (int) $legacyAccount->id];
        }

        $apiKey = $request->header('X-SAFA-API-KEY');
        if ($apiKey) {
            $keyRecord = SafaApiKey::where('api_key', $apiKey)->where('is_active', true)->first();
            if ($keyRecord && $keyRecord->account_id) {
                return ['user' => null, 'account_id' => (int) $keyRecord->account_id];
            }
        }

        return ['error' => response()->json(['status' => 'error', 'message' => 'Unauthorized: authenticated user or account API key is required.'], 401)];
    }
}
