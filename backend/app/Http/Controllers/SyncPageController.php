<?php

namespace App\Http\Controllers;

use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\SyncChange;
use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use App\Support\BusinessPermissions;
use Illuminate\Http\Request;

class SyncPageController extends Controller
{
    use AuthorizeAccountContext;

    private const MAX_PAGE_SIZE = 250;

    private array $collections = [
        'transactions' => Transaction::class,
        'customers' => Customer::class,
        'suppliers' => Supplier::class,
        'wallet_batches' => WalletBatch::class,
        'wallet_ledgers' => WalletLedger::class,
        'supplier_deposits' => SupplierDeposit::class,
        'expenses_incomes' => ExpenseIncome::class,
    ];

    public function __invoke(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];

        $accountId = (int) $context['account_id'];
        $user = $context['user'] ?? $request->user();
        $permissions = BusinessPermissions::effective($user, $accountId);
        $perPage = min(self::MAX_PAGE_SIZE, max(1, (int) $request->query('per_page', 100)));

        if ($request->has('cursor')) {
            return $this->cursorResponse($request, $accountId, $permissions, $perPage);
        }

        return $this->legacyPageResponse($request, $accountId, $permissions, $perPage);
    }

    private function cursorResponse(Request $request, int $accountId, array $permissions, int $perPage)
    {
        $cursor = max(0, (int) $request->query('cursor', 0));
        $readScope = [];
        foreach (array_keys($this->collections) as $entity) {
            $required = BusinessPermissions::readPermissionForEntity($entity);
            $readScope[$entity] = $required === null || !empty($permissions[$required]);
        }
        $readable = array_keys(array_filter($readScope));
        $permissionScope = hash('sha256', json_encode($readScope, JSON_UNESCAPED_SLASHES));

        $changes = SyncChange::query()
            ->where('account_id', $accountId)
            ->where('id', '>', $cursor)
            ->when($readable !== [], fn ($query) => $query->whereIn('entity', $readable), fn ($query) => $query->whereRaw('1 = 0'))
            ->orderBy('id')
            ->limit($perPage + 1)
            ->get();

        $hasMore = $changes->count() > $perPage;
        $pageChanges = $changes->take($perPage);
        $nextCursor = $pageChanges->isEmpty() ? $cursor : (int) $pageChanges->last()->id;
        $data = array_fill_keys(array_keys($this->collections), []);

        foreach ($pageChanges as $change) {
            if (!array_key_exists($change->entity, $data)) continue;
            $snapshot = is_array($change->snapshot) ? $change->snapshot : json_decode((string) $change->snapshot, true);
            if (!is_array($snapshot)) continue;
            $data[$change->entity][] = $snapshot;
        }

        $highWater = (int) (SyncChange::query()
            ->where('account_id', $accountId)
            ->when($readable !== [], fn ($query) => $query->whereIn('entity', $readable), fn ($query) => $query->whereRaw('1 = 0'))
            ->max('id') ?? $cursor);

        return response()->json(array_merge([
            'status' => 'success',
            'protocol' => 'cursor-v1',
            'account_id' => $accountId,
            'server_time' => time(),
            'cursor' => $cursor,
            'next_cursor' => $nextCursor,
            'high_water' => max($cursor, $highWater),
            'permission_scope' => $permissionScope,
            'per_page' => $perPage,
            'has_more' => $hasMore,
            'permissions' => $permissions,
            'user_permissions' => $permissions,
        ], $data));
    }

    private function legacyPageResponse(Request $request, int $accountId, array $permissions, int $perPage)
    {
        $page = max(1, (int) $request->query('page', 1));
        $data = [];
        $meta = [];
        $hasMore = false;

        foreach ($this->collections as $key => $model) {
            $requiredPermission = BusinessPermissions::readPermissionForEntity($key);
            if ($requiredPermission !== null && empty($permissions[$requiredPermission])) {
                $data[$key] = [];
                $meta[$key] = [
                    'current_page' => $page,
                    'per_page' => $perPage,
                    'last_page' => 1,
                    'has_more' => false,
                    'total' => 0,
                ];
                continue;
            }

            $paginator = $model::withTrashed()
                ->where('account_id', $accountId)
                ->orderBy('id')
                ->paginate($perPage, ['*'], 'page', $page);
            $data[$key] = $paginator->items();
            $meta[$key] = [
                'current_page' => $paginator->currentPage(),
                'per_page' => $paginator->perPage(),
                'last_page' => $paginator->lastPage(),
                'has_more' => $paginator->hasMorePages(),
                'total' => $paginator->total(),
            ];
            $hasMore = $hasMore || $paginator->hasMorePages();
        }

        $response = response()->json(array_merge([
            'status' => 'success',
            'protocol' => 'legacy-page-v1',
            'account_id' => $accountId,
            'server_time' => time(),
            'page' => $page,
            'per_page' => $perPage,
            'has_more' => $hasMore,
            'meta' => $meta,
            'permissions' => $permissions,
            'user_permissions' => $permissions,
        ], $data));

        if ($request->is('api/sync/down')) {
            $response->headers->set('Deprecation', 'true');
            $response->headers->set('Sunset', 'Tue, 01 Dec 2026 00:00:00 GMT');
            $response->headers->set('Link', '</api/v1/sync/down>; rel="successor-version"');
        }

        return $response;
    }
}
