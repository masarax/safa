<?php

namespace App\Http\Controllers;

use App\Models\Account;
use App\Models\SafaApiKey;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Http\Request;

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
                return ['error' => response()->json(['status' => 'error', 'message' => 'Unauthorized: invalid or expired access token.'], 401)];
            }
            if (!$user && isset($payload['user_id'])) {
                $user = User::find((int) $payload['user_id']);
            }
        }

        if ($token && (!$user || !$user->is_activated)) {
            return ['error' => response()->json(['status' => 'error', 'message' => 'Unauthorized: user account not found or deactivated.'], 401)];
        }

        // Explicit Android context must win over the account embedded in the JWT
        // so a user can switch from their own account to an explicitly shared one.
        $requestedAccountId = 0;
        $headerAccountId = $request->header('X-SAFA-ACCOUNT-ID');
        if ($headerAccountId !== null && ctype_digit((string) $headerAccountId)) {
            $requestedAccountId = (int) $headerAccountId;
        }

        if ($requestedAccountId <= 0 && $request->hasSession()) {
            $sessionAccountId = $request->session()->get('safa_active_account_id');
            if ($sessionAccountId !== null && ctype_digit((string) $sessionAccountId)) {
                $requestedAccountId = (int) $sessionAccountId;
            }
        }

        if ($requestedAccountId <= 0) {
            $requestedAccountId = (int) ($payload['account_id'] ?? 0);
        }

        // Explicit account-selection endpoints can supply account_id when no
        // header/session/token context is available. Authorization below still
        // verifies ownership/share membership before the context is accepted.
        if ($requestedAccountId <= 0) {
            $inputAccountId = $request->input('account_id');
            if ($inputAccountId !== null && ctype_digit((string) $inputAccountId)) {
                $requestedAccountId = (int) $inputAccountId;
            }
        }

        if ($user) {
            if ($requestedAccountId > 0) {
                $targetAccount = Account::query()->find($requestedAccountId);
                if (!$targetAccount) {
                    return ['error' => response()->json(['status' => 'error', 'message' => 'Forbidden: requested account does not exist.'], 403)];
                }
            } else {
                // No chooser is required for the implicit/default context: every
                // authenticated user enters their own primary account.
                $targetAccount = Account::query()
                    ->where('owner_user_id', $user->id)
                    ->orderBy('id')
                    ->first();

                if (!$targetAccount) {
                    $targetAccount = Account::create([
                        'name' => trim(($user->name ?: 'SAFA') . ' Account'),
                        'balance' => 0,
                        'owner_user_id' => $user->id,
                    ]);
                }
            }

            if ((int) $targetAccount->owner_user_id === (int) $user->id) {
                $request->attributes->set('active_account_id', (int) $targetAccount->id);
                return ['user' => $user, 'account_id' => (int) $targetAccount->id];
            }

            // Cross-account access is explicit delegation only. Role level,
            // including SuperAdmin, never grants implicit tenant visibility.
            $shareExists = UserAccountShare::query()
                ->where('shared_with_user_id', $user->id)
                ->where('account_id', (int) $targetAccount->id)
                ->where('owner_user_id', (int) $targetAccount->owner_user_id)
                ->exists();

            if (!$shareExists) {
                return ['error' => response()->json(['status' => 'error', 'message' => 'Forbidden: you do not have authorization to access this account context.'], 403)];
            }

            $request->attributes->set('active_account_id', (int) $targetAccount->id);
            return ['user' => $user, 'account_id' => (int) $targetAccount->id];
        }

        $apiKey = $request->header('X-SAFA-API-KEY');
        if ($apiKey) {
            $keyRecord = SafaApiKey::where('api_key', $apiKey)->where('is_active', true)->first();
            if ($keyRecord?->account_id) {
                $request->attributes->set('active_account_id', (int) $keyRecord->account_id);
                return ['user' => null, 'account_id' => (int) $keyRecord->account_id];
            }
        }

        return ['error' => response()->json(['status' => 'error', 'message' => 'Unauthorized: authenticated user or account API key is required.'], 401)];
    }
}
