<?php

namespace App\Http\Controllers;

use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Rate;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\SystemSetting;
use App\Models\Transaction;
use App\Models\UserAccountShare;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/** One server-authoritative snapshot matching the Android repository state. */
class WebWorkspaceController extends Controller
{
    use AuthorizeAccountContext;

    public function index(Request $request): JsonResponse
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $permissions = $this->effectivePermissions($request, $accountId);
        $setting = SystemSetting::first();
        $canReadWalletStock = !empty($permissions['can_manage_wallet']) || !empty($permissions['can_add_transactions']);
        $canReadSupplierLedger = !empty($permissions['can_manage_wallet']) || !empty($permissions['can_view_suppliers']);
        $latestRates = Rate::query()->where('account_id', $accountId)->orderByDesc('id')->get()->unique('currency_pair')->values();

        return response()->json([
            'status' => 'success',
            'account_id' => $accountId,
            'user' => [
                'id' => (int) $request->user()->id,
                'name' => (string) $request->user()->name,
                'mobile' => (string) ($request->user()->mobile ?? ''),
                'email' => (string) ($request->user()->email ?? ''),
                'role' => (string) $request->user()->role,
            ],
            'permissions' => $permissions,
            'settings' => [
                'app_name' => $setting?->app_name ?: 'SAFA',
                'app_logo_url' => $setting?->webLogoSource() ?: '/safa-logo.png',
                'app_version' => $setting?->app_version ?: '1.0.0',
                'local_currency' => $setting?->local_currency ?: 'BDT',
                'foreign_currency' => $setting?->foreign_currency ?: 'SAR',
                'rate_based_mode' => (bool) ($setting?->rate_based_mode ?? true),
                'supplier_rate_enabled' => (bool) ($setting?->supplier_rate_enabled ?? true),
                'wallet_rate_enabled' => (bool) ($setting?->wallet_rate_enabled ?? true),
            ],
            'rates' => $latestRates,
            'customers' => !empty($permissions['can_view_customers']) ? Customer::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get() : [],
            'suppliers' => !empty($permissions['can_view_suppliers']) ? Supplier::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get() : [],
            'transactions' => !empty($permissions['can_view_transactions']) ? Transaction::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get() : [],
            'supplier_deposits' => $canReadSupplierLedger ? SupplierDeposit::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get() : [],
            'wallet_ledgers' => $canReadWalletStock ? WalletLedger::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get() : [],
            'wallet_batches' => $canReadWalletStock ? WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderBy('timestamp')->orderBy('id')->get() : [],
            'expenses' => !empty($permissions['can_manage_expenses']) ? ExpenseIncome::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get() : [],
        ]);
    }

    private function effectivePermissions(Request $request, int $accountId): array
    {
        $user = $request->user();
        $permissions = $user?->getFormattedPermissions() ?? [];
        if (!$user) return $permissions;
        $share = UserAccountShare::query()->where('shared_with_user_id', $user->id)->where('account_id', $accountId)->where('owner_user_id', '!=', $user->id)->first();
        if ($share && is_array($share->permissions_override)) {
            foreach ($share->permissions_override as $key => $allowed) {
                if (array_key_exists($key, $permissions)) $permissions[$key] = (bool) $permissions[$key] && (bool) $allowed;
            }
        }
        return $permissions;
    }
}
