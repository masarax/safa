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
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

/** Bounded server-authoritative web workspace snapshot and paginated collections. */
class WebWorkspaceController extends Controller
{
    use AuthorizeAccountContext;

    private const DEFAULT_PAGE_SIZE = 50;
    private const MAX_PAGE_SIZE = 100;

    public function index(Request $request): JsonResponse
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $permissions = $this->effectivePermissions($request, $accountId);

        if ($request->filled('collection')) {
            return $this->collectionPage($request, $accountId, $permissions);
        }

        $setting = SystemSetting::first();
        $pageSize = $this->pageSize($request);
        $collections = [];
        $pagination = [];
        foreach ($this->collectionNames() as $collection) {
            $page = $this->readCollection($request, $accountId, $permissions, $collection, 1, $pageSize, false);
            $collections[$collection] = $page['items'];
            $pagination[$collection] = $page['pagination'];
        }

        // Rates are reference data, but still keep the query bounded in case old
        // deployments accumulated duplicate currency-pair rows.
        $latestRates = Rate::query()
            ->where('account_id', $accountId)
            ->orderByDesc('id')
            ->limit(25)
            ->get()
            ->unique('currency_pair')
            ->values();

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
            ...$collections,
            'pagination' => $pagination,
        ]);
    }

    private function collectionPage(Request $request, int $accountId, array $permissions): JsonResponse
    {
        $collection = trim((string) $request->input('collection'));
        if (!in_array($collection, $this->collectionNames(), true)) {
            return response()->json(['status' => 'error', 'message' => 'Unknown workspace collection.'], 404);
        }

        $page = max(1, (int) $request->integer('page', 1));
        $result = $this->readCollection(
            $request,
            $accountId,
            $permissions,
            $collection,
            $page,
            $this->pageSize($request),
            true
        );
        if (!$result['allowed']) {
            return response()->json(['status' => 'error', 'message' => 'Forbidden: collection permission required.'], 403);
        }

        return response()->json([
            'status' => 'success',
            'account_id' => $accountId,
            'collection' => $collection,
            'items' => $result['items'],
            'pagination' => $result['pagination'],
        ]);
    }

    private function readCollection(
        Request $request,
        int $accountId,
        array $permissions,
        string $collection,
        int $page,
        int $pageSize,
        bool $applyFilters
    ): array {
        [$allowed, $query, $ascending] = $this->collectionQuery($accountId, $permissions, $collection);
        if (!$allowed) {
            return [
                'allowed' => false,
                'items' => [],
                'pagination' => $this->paginationMeta($collection, $page, $pageSize, 0),
            ];
        }

        if ($applyFilters) $this->applyCollectionFilters($request, $query, $collection);
        if ($ascending) {
            $query->orderBy('timestamp')->orderBy('id');
        } else {
            $query->orderByDesc('timestamp')->orderByDesc('id');
        }

        $total = (clone $query)->count();
        $items = $query->forPage($page, $pageSize)->get();

        return [
            'allowed' => true,
            'items' => $items,
            'pagination' => $this->paginationMeta($collection, $page, $pageSize, $total),
        ];
    }

    private function collectionQuery(int $accountId, array $permissions, string $collection): array
    {
        $canReadWalletStock = !empty($permissions['can_manage_wallet']) || !empty($permissions['can_add_transactions']);
        $canReadSupplierLedger = !empty($permissions['can_manage_wallet']) || !empty($permissions['can_view_suppliers']);

        return match ($collection) {
            'customers' => [!empty($permissions['can_view_customers']), Customer::query()->where('account_id', $accountId)->whereNull('deleted_at'), false],
            'suppliers' => [!empty($permissions['can_view_suppliers']), Supplier::query()->where('account_id', $accountId)->whereNull('deleted_at'), false],
            'transactions' => [!empty($permissions['can_view_transactions']), Transaction::query()->where('account_id', $accountId)->whereNull('deleted_at'), false],
            'supplier_deposits' => [$canReadSupplierLedger, SupplierDeposit::query()->where('account_id', $accountId)->whereNull('deleted_at'), false],
            'wallet_ledgers' => [$canReadWalletStock, WalletLedger::query()->where('account_id', $accountId)->whereNull('deleted_at'), false],
            'wallet_batches' => [$canReadWalletStock, WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at'), true],
            'expenses' => [!empty($permissions['can_manage_expenses']), ExpenseIncome::query()->where('account_id', $accountId)->whereNull('deleted_at'), false],
            default => [false, Customer::query()->whereRaw('1 = 0'), false],
        };
    }

    private function applyCollectionFilters(Request $request, Builder $query, string $collection): void
    {
        $positiveIntegerFilters = match ($collection) {
            'transactions' => ['customer_id', 'supplier_id', 'wallet_batch_id'],
            'supplier_deposits' => ['supplier_id'],
            'wallet_batches' => ['ledger_id', 'supplier_id', 'supplier_deposit_id'],
            default => [],
        };

        foreach ($positiveIntegerFilters as $field) {
            $value = (int) $request->integer($field, 0);
            if ($value > 0) $query->where($field, $value);
        }
    }

    private function paginationMeta(string $collection, int $page, int $pageSize, int $total): array
    {
        $lastPage = max(1, (int) ceil($total / max(1, $pageSize)));

        return [
            'collection' => $collection,
            'page' => $page,
            'per_page' => $pageSize,
            'total' => $total,
            'last_page' => $lastPage,
            'has_more' => $page < $lastPage,
        ];
    }

    private function pageSize(Request $request): int
    {
        return min(self::MAX_PAGE_SIZE, max(1, (int) $request->integer('per_page', self::DEFAULT_PAGE_SIZE)));
    }

    private function collectionNames(): array
    {
        return [
            'customers',
            'suppliers',
            'transactions',
            'supplier_deposits',
            'wallet_ledgers',
            'wallet_batches',
            'expenses',
        ];
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
