<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Transaction;
use App\Models\Customer;
use App\Models\Supplier;
use Illuminate\Support\Facades\Validator;

class SyncController extends Controller
{
    public function syncUp(Request $request)
    {
        $apiKey  = $request->header('X-SAFA-API-KEY');
        $keyRecord = \App\Models\SafaApiKey::where('api_key', $apiKey)
            ->where('is_active', true)
            ->first();

        // Require a valid key record with a bound account_id for multi-tenant isolation
        if (! $keyRecord || ! isset($keyRecord->account_id)) {
            return response()->json(['message' => 'Unauthorized.'], 401);
        }

        $accountId = (int) $keyRecord->account_id;
        $data = $request->only(['transactions', 'customers', 'suppliers']);

        if (isset($data['transactions']) && is_array($data['transactions'])) {
            foreach ($data['transactions'] as $tx) {
                $validator = Validator::make($tx, [
                    'local_id'  => 'required|integer',
                    'type'      => 'required|string|in:Pending,Delivered,Cancelled',
                    'amount'    => 'required|numeric|min:0',
                    'timestamp' => 'required|integer',
                ]);

                if ($validator->fails()) {
                    continue; // Skip malformed records silently
                }

                Transaction::updateOrCreate(
                    ['account_id' => $accountId, 'local_id' => (int) $tx['local_id']],
                    [
                        'account_id' => $accountId,
                        'type'       => $tx['type'],
                        'amount'     => (float) $tx['amount'],
                        'hash'       => $tx['hash'] ?? null,
                        'timestamp'  => (int) $tx['timestamp'],
                    ]
                );
            }
        }

        if (isset($data['customers']) && is_array($data['customers'])) {
            foreach ($data['customers'] as $c) {
                if (empty($c['name'])) {
                    continue;
                }
                Customer::updateOrCreate(
                    ['account_id' => $accountId, 'local_id' => (int) ($c['local_id'] ?? 0)],
                    [
                        'account_id' => $accountId,
                        'name'       => substr((string) $c['name'], 0, 255),
                        'phone'      => substr((string) ($c['phone'] ?? ''), 0, 50),
                    ]
                );
            }
        }

        if (isset($data['suppliers']) && is_array($data['suppliers'])) {
            foreach ($data['suppliers'] as $s) {
                if (empty($s['name'])) {
                    continue;
                }
                Supplier::updateOrCreate(
                    ['account_id' => $accountId, 'local_id' => (int) ($s['local_id'] ?? 0)],
                    [
                        'account_id' => $accountId,
                        'name'       => substr((string) $s['name'], 0, 255),
                        'phone'      => substr((string) ($s['phone'] ?? ''), 0, 50),
                    ]
                );
            }
        }

        return response()->json(['status' => 'success']);
    }

    public function syncDown(Request $request)
    {
        $apiKey    = $request->header('X-SAFA-API-KEY');
        $keyRecord = \App\Models\SafaApiKey::where('api_key', $apiKey)
            ->where('is_active', true)
            ->first();

        if (! $keyRecord || ! isset($keyRecord->account_id)) {
            return response()->json(['message' => 'Unauthorized.'], 401);
        }

        $accountId = (int) $keyRecord->account_id;

        return response()->json([
            'transactions' => Transaction::where('account_id', $accountId)->get(),
            'customers'    => Customer::where('account_id', $accountId)->get(),
            'suppliers'    => Supplier::where('account_id', $accountId)->get(),
        ]);
    }
}

