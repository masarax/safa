<?php

namespace App\Http\Controllers;

use App\Models\Account;
use App\Models\Customer;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class OpsSyntheticController extends Controller
{
    public function __invoke(Request $request): JsonResponse
    {
        $expected = trim((string) config('observability.ops_key', ''));
        $provided = trim((string) $request->header('X-SAFA-OPS-KEY', ''));
        if ($expected === '' || $provided === '' || !hash_equals($expected, $provided)) abort(404);

        $accountId = (int) config('observability.synthetic_account_id', 0);
        if ($accountId <= 0 || !Account::whereKey($accountId)->exists()) {
            return response()->json(['status' => 'degraded', 'check' => 'synthetic_persistence'], 503)
                ->header('Cache-Control', 'no-store');
        }

        DB::beginTransaction();
        try {
            $localId = (int) (microtime(true) * 1000000);
            $customer = Customer::create([
                'account_id' => $accountId,
                'local_id' => $localId,
                'name' => 'SAFA Synthetic Probe',
                'phone' => '',
                'address' => '',
                'timestamp' => time(),
            ]);
            $persisted = Customer::whereKey($customer->id)
                ->where('account_id', $accountId)
                ->where('local_id', $localId)
                ->exists();
            DB::rollBack();

            return response()->json([
                'status' => $persisted ? 'ok' : 'degraded',
                'check' => 'synthetic_persistence',
            ], $persisted ? 200 : 503)->header('Cache-Control', 'no-store');
        } catch (\Throwable) {
            if (DB::transactionLevel() > 0) DB::rollBack();
            return response()->json(['status' => 'degraded', 'check' => 'synthetic_persistence'], 503)
                ->header('Cache-Control', 'no-store');
        }
    }
}
