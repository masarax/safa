<?php

namespace App\Http\Controllers;

use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use App\Support\BusinessPermissions;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;

class SyncDeltaController extends Controller
{
    use AuthorizeAccountContext;

    private const MAX_LIMIT = 250;

    /** @var array<string, class-string<Model>> */
    private const MODELS = [
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
        $cursor = max(0, (int) $request->query('cursor', 0));
        $limit = min(self::MAX_LIMIT, max(1, (int) $request->query('limit', 100)));
        $floor = (int) (DB::table('sync_change_floors')->where('account_id', $accountId)->value('floor_cursor') ?? 0);

        if ($cursor < $floor) {
            return response()->json([
                'status' => 'reset_required',
                'account_id' => $accountId,
                'cursor' => $cursor,
                'floor_cursor' => $floor,
                'next_cursor' => $cursor,
                'has_more' => false,
                'reset_required' => true,
                'changes' => [],
                'permissions' => $permissions,
                'user_permissions' => $permissions,
            ]);
        }

        $journal = DB::table('sync_changes')
            ->where('account_id', $accountId)
            ->where('id', '>', $cursor)
            ->orderBy('id')
            ->limit($limit + 1)
            ->get();

        $hasMore = $journal->count() > $limit;
        $scanned = $journal->take($limit)->values();
        $nextCursor = $scanned->isEmpty() ? $cursor : (int) $scanned->last()->id;

        $rowsByEntity = $this->loadRows($scanned, $accountId, $permissions);
        $changes = [];

        foreach ($scanned as $change) {
            $entity = (string) $change->entity;
            if (!isset(self::MODELS[$entity])) continue;

            $permission = BusinessPermissions::readPermissionForEntity($entity);
            if ($permission !== null && empty($permissions[$permission])) continue;

            $entityId = (int) $change->entity_id;
            $model = $rowsByEntity[$entity][$entityId] ?? null;
            $row = $model?->toArray() ?? [
                'id' => $entityId,
                'deleted_at' => $change->created_at,
                '_purged' => true,
            ];

            $changes[] = [
                'cursor' => (int) $change->id,
                'entity' => $entity,
                'row' => $row,
            ];
        }

        return response()->json([
            'status' => 'success',
            'account_id' => $accountId,
            'server_time' => time(),
            'cursor' => $cursor,
            'floor_cursor' => $floor,
            'next_cursor' => $nextCursor,
            'has_more' => $hasMore,
            'reset_required' => false,
            'changes' => $changes,
            'permissions' => $permissions,
            'user_permissions' => $permissions,
        ]);
    }

    /**
     * @return array<string, array<int, Model>>
     */
    private function loadRows(Collection $changes, int $accountId, array $permissions): array
    {
        $result = [];
        $idsByEntity = [];

        foreach ($changes as $change) {
            $entity = (string) $change->entity;
            if (!isset(self::MODELS[$entity])) continue;
            $permission = BusinessPermissions::readPermissionForEntity($entity);
            if ($permission !== null && empty($permissions[$permission])) continue;
            $idsByEntity[$entity][] = (int) $change->entity_id;
        }

        foreach ($idsByEntity as $entity => $ids) {
            $model = self::MODELS[$entity];
            $result[$entity] = $model::withTrashed()
                ->where('account_id', $accountId)
                ->whereIn('id', array_values(array_unique($ids)))
                ->get()
                ->keyBy('id')
                ->all();
        }

        return $result;
    }
}
