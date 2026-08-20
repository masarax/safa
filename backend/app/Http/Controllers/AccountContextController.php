<?php

namespace App\Http\Controllers;

use App\Models\Account;
use App\Models\User;
use App\Models\UserAccountShare;
use App\Support\MobileNumber;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;

class AccountContextController extends Controller
{
    use AuthorizeAccountContext;

    public function index(Request $request)
    {
        // Listing accounts is the chooser/bootstrap operation itself, so it must
        // not require an active account first. The protected route middleware has
        // already authenticated the user; this method only enumerates account IDs
        // that user is allowed to select.
        $user = $request->user() ?? $request->attributes->get('user');
        if (!$user || !(bool) $user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Authenticated user is required.'], 401);
        }

        if ($user->isSuperAdmin()) {
            $visibleAccounts = Account::query()->orderBy('id')->get();
            if ($visibleAccounts->isEmpty()) {
                $visibleAccounts = collect([
                    Account::create([
                        'name' => 'SAFA Account',
                        'balance' => 0,
                        'owner_user_id' => $user->id,
                    ]),
                ]);
            }

            $ownerIds = $visibleAccounts
                ->pluck('owner_user_id')
                ->filter(fn ($id) => (int) $id > 0)
                ->map(fn ($id) => (int) $id)
                ->unique()
                ->values();
            $ownerNames = User::query()->whereIn('id', $ownerIds)->pluck('name', 'id');

            $accounts = $visibleAccounts->map(function ($account) use ($user, $ownerNames) {
                $ownerId = (int) ($account->owner_user_id ?? 0);
                $isOwner = $ownerId === (int) $user->id;

                return [
                    'account_id' => (int) $account->id,
                    'owner_user_id' => $ownerId,
                    'owner_name' => (string) ($ownerNames[$ownerId] ?? $account->name ?? 'SAFA Account'),
                    'role' => $isOwner ? 'OWNER' : 'SUPERADMIN',
                    'permissions_override' => null,
                    'is_owner' => $isOwner,
                ];
            })->values();
        } else {
            $owned = Account::where('owner_user_id', $user->id)->orderBy('id')->get();
            $shares = UserAccountShare::with('owner')
                ->where('shared_with_user_id', $user->id)
                ->get();

            // Preserve the existing single-account bootstrap behavior without making
            // an ambiguous selection when the user already has multiple choices.
            if ($owned->isEmpty() && $shares->isEmpty()) {
                $owned = collect([
                    Account::create([
                        'name' => trim(($user->name ?: 'SAFA') . ' Account'),
                        'balance' => 0,
                        'owner_user_id' => $user->id,
                    ]),
                ]);
            }

            $accounts = $owned->map(fn ($account) => [
                'account_id' => (int) $account->id,
                'owner_user_id' => (int) $user->id,
                'owner_name' => $user->name,
                'role' => 'OWNER',
                'permissions_override' => null,
                'is_owner' => true,
            ])->values();

            foreach ($shares as $share) {
                $account = Account::find($share->account_id);
                if (!$account) continue;
                $accounts->push([
                    'account_id' => (int) $account->id,
                    'owner_user_id' => (int) $share->owner_user_id,
                    'owner_name' => $share->owner?->name ?? 'Unknown Owner',
                    'role' => 'MEMBER',
                    'permissions_override' => $share->permissions_override,
                    'share_id' => (int) $share->id,
                    'is_owner' => false,
                ]);
            }

            $accounts = $accounts->unique('account_id')->values();
        }

        $authorizedIds = $accounts->pluck('account_id')->map(fn ($id) => (int) $id)->all();
        $activeAccountId = null;

        $headerAccountId = $request->header('X-SAFA-ACCOUNT-ID');
        if ($headerAccountId !== null && ctype_digit((string) $headerAccountId)) {
            $candidate = (int) $headerAccountId;
            if (in_array($candidate, $authorizedIds, true)) $activeAccountId = $candidate;
        }

        if ($activeAccountId === null && $request->hasSession()) {
            $sessionAccountId = $request->session()->get('safa_active_account_id');
            if ($sessionAccountId !== null && ctype_digit((string) $sessionAccountId)) {
                $candidate = (int) $sessionAccountId;
                if (in_array($candidate, $authorizedIds, true)) $activeAccountId = $candidate;
            }
        }

        if ($activeAccountId === null && $request->bearerToken()) {
            $payload = AuthJWTController::verifyJwt($request->bearerToken());
            $candidate = (int) ($payload['account_id'] ?? 0);
            if ($candidate > 0 && in_array($candidate, $authorizedIds, true)) $activeAccountId = $candidate;
        }

        if ($activeAccountId === null && count($authorizedIds) === 1) {
            $activeAccountId = $authorizedIds[0];
        }

        return response()->json([
            'status' => 'success',
            'active_account_id' => $activeAccountId,
            'accounts' => $accounts,
        ]);
    }

    public function switch(Request $request)
    {
        $validator = Validator::make($request->all(), ['account_id' => 'required|integer|min:1']);
        if ($validator->fails()) {
            return response()->json(['status' => 'error', 'message' => 'Invalid account_id.', 'errors' => $validator->errors()], 422);
        }

        $requestedAccountId = (int) $request->input('account_id');

        // The access token may still contain the previous account_id. Resolve the
        // requested target explicitly against the authenticated user so A -> B
        // cannot silently resolve back to A.
        $targetContextRequest = Request::create($request->getRequestUri(), 'GET', [
            'account_id' => $requestedAccountId,
        ]);
        $targetContextRequest->setUserResolver(fn () => $request->user() ?? $request->attributes->get('user'));

        $context = $this->resolveAuthorizedAccountContext($targetContextRequest);
        if (isset($context['error'])) return $context['error'];

        if ((int) $context['account_id'] !== $requestedAccountId) {
            return response()->json([
                'status' => 'error',
                'message' => 'Requested account context could not be activated.',
            ], 409);
        }

        // API routes are stateless in production. Persist to a Laravel session
        // only when one actually exists; Android persists the returned ID and
        // sends it on subsequent calls as X-SAFA-ACCOUNT-ID.
        if ($request->hasSession()) {
            $request->session()->put('safa_active_account_id', $requestedAccountId);
        }
        $request->attributes->set('active_account_id', $requestedAccountId);

        return response()->json([
            'status' => 'success',
            'message' => 'Active account changed successfully.',
            'active_account_id' => $requestedAccountId,
            'context_header' => 'X-SAFA-ACCOUNT-ID',
        ]);
    }

    public function share(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $actor = $context['user'];
        if (!$actor) return response()->json(['status' => 'error', 'message' => 'Authenticated user is required.'], 401);

        $validator = Validator::make($request->all(), [
            'mobile' => 'required|string',
            'account_id' => 'required|integer|min:1',
            'permissions_override' => 'nullable|array',
        ]);
        if ($validator->fails()) {
            return response()->json(['status' => 'error', 'message' => 'Validation failed.', 'errors' => $validator->errors()], 422);
        }

        $mobile = MobileNumber::normalize((string) $request->input('mobile'));
        if (!MobileNumber::isValid($mobile)) {
            return response()->json([
                'status' => 'error',
                'message' => 'Validation failed.',
                'errors' => ['mobile' => ['The mobile number is not valid.']],
            ], 422);
        }

        $accountId = (int) $request->input('account_id');
        $account = Account::query()->find($accountId);
        if (!$account) {
            return response()->json(['status' => 'error', 'message' => 'Account not found.'], 404);
        }

        // Sharing is a delegation operation, not ordinary account access. Only
        // the real owner or the unrestricted SuperAdmin tier may create/replace
        // a share. A member who received access cannot delegate it onward.
        $accountOwnerId = (int) ($account->owner_user_id ?? 0);
        if ($accountOwnerId <= 0 || (!$actor->isSuperAdmin() && $accountOwnerId !== (int) $actor->id)) {
            return response()->json(['status' => 'error', 'message' => 'You are not authorized to share this account.'], 403);
        }

        $target = User::where('mobile', $mobile)->first();
        if (!$target) return response()->json(['status' => 'error', 'message' => 'Target user not found.'], 404);
        if ((int) $target->id === (int) $actor->id) return response()->json(['status' => 'error', 'message' => 'Cannot share an account with yourself.'], 422);
        if ((int) $target->id === $accountOwnerId) return response()->json(['status' => 'error', 'message' => 'Target user already owns this account.'], 422);

        // Always persist the authoritative account owner. SuperAdmin may perform
        // the delegation, but must never become the synthetic owner of that share.
        $share = UserAccountShare::updateOrCreate(
            ['owner_user_id' => $accountOwnerId, 'shared_with_user_id' => $target->id, 'account_id' => $accountId],
            ['permissions_override' => $request->input('permissions_override')]
        );

        return response()->json(['status' => 'success', 'message' => 'Account access shared successfully.', 'share' => $share]);
    }
}
