<?php

namespace App\Support;

use Illuminate\Support\Facades\DB;
use RuntimeException;

final class ServerLocalId
{
    public const MAX_CLIENT_COMPATIBLE_ID = 2_147_483_647;

    private const SYNC_TABLES = [
        'customers',
        'suppliers',
        'transactions',
        'wallet_ledgers',
        'wallet_batches',
        'supplier_deposits',
        'expenses_incomes',
    ];

    public static function reserve(): int
    {
        for ($attempt = 0; $attempt < 64; $attempt++) {
            $candidate = random_int(1, self::MAX_CLIENT_COMPATIBLE_ID);

            $alreadyUsed = false;
            foreach (self::SYNC_TABLES as $table) {
                if (DB::table($table)->where('local_id', $candidate)->exists()) {
                    $alreadyUsed = true;
                    break;
                }
            }
            if ($alreadyUsed) continue;

            $inserted = DB::table('server_local_id_reservations')->insertOrIgnore([
                'local_id' => $candidate,
                'created_at' => now(),
            ]);
            if ($inserted === 1) return $candidate;
        }

        throw new RuntimeException('Unable to allocate a collision-safe server local ID.');
    }
}
