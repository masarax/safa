<?php

namespace App\Http\Controllers;

use App\Models\Account;
use App\Models\User;
use App\Models\UserAccountShare;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;

class AccountContextController extends Controller
{
    use AuthorizeAccountContext;

    public function index(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];

        $user = $context['user'];
        if (!$user) return response()->json(['status' => 'error', 'message' => 'Authenticated user is required.'], 401);

        $owned = Account::where('owner_user_id', $user->id)->orderBy('id')->get();
        $shares = UserAccountShare::with('owner')
            ->where('shared_with_user_id', $user->id)
            ->get();

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

        return response()->json([
            'status' => 'success',
            'active_account_id' => (int) $context['account_id'],
            'accounts' => $accounts->unique('account_id')->values(),
        ]);
    }

    public function switch(Request $request)
    {
        $validator = Validator::make($request->all(), ['account_id' => 'required|integer|min:1']);
        if ($validator->fails()) {
            return response()->json(['status' => 'error', 'message' => 'Invalid account_id.', 'errors' => $validator->errors()], 422);
        }

        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];

        $request->session()->put('safa_active_account_id', (int) $context['account_id']);
        return response()->json([
            'status' => 'success',
            'message' => 'Active account changed successfully.',
            'active_account_id' => (int) $context['account_id'],
        ]);
    }

    public function share(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $owner = $context['user'];
        if (!$owner) return response()->json(['status' => 'error', 'message' => 'Authenticated user is required.'], 401);

        $validator = Validator::make($request->all(), [
            'mobile' => 'required|string',
            'account_id' => 'required|integer|min:1',
            'permissions_override' => 'nullable|array',
        ]);
        if ($validator->fails()) {
            return response()->json(['status' => 'error', 'message' => 'Validation failed.', 'errors' => $validator->errors()], 422);
        }

        $accountId = (int) $request->input('account_id');
        $targetContextRequest = Request::create($request->getRequestUri(), 'GET', ['account_id' => $accountId]);
        foreach ($request->headers->all() as $key => $values) $targetContextRequest->headers->set($key, $values[0] ?? '');
        $targetContextRequest->setUserResolver(fn () => $owner);
        $authorized = $this->resolveAuthorizedAccountContext($targetContextRequest);
        if (isset($authorized['error']) || (int) ($authorized['account_id'] ?? 0) !== $accountId) {
            return response()->json(['status' => 'error', 'message' => 'You are not authorized to share this account.'], 403);
        }

        $target = User::where('mobile', trim($request->input('mobile')))->first();
        if (!$target) return response()->json(['status' => 'error', 'message' => 'Target user not found.'], 404);
        if ((int) $target->id === (int) $owner->id) return response()->json(['status' => 'error', 'message' => 'Cannot share an account with yourself.'], 422);

        $share = UserAccountShare::updateOrCreate(
            ['owner_user_id' => $owner->id, 'shared_with_user_id' => $target->id, 'account_id' => $accountId],
            ['permissions_override' => $request->input('permissions_override')]
        );

        return response()->json(['status' => 'success', 'message' => 'Account access shared successfully.', 'share' => $share]);
    }
}
