<?php

namespace App\Console\Commands;

use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

class PruneSyncChanges extends Command
{
    protected $signature = 'safa:prune-sync-changes {--days=90 : Retain at least this many days of change history}';

    protected $description = 'Compact the sync change journal while recording reset floors for long-offline clients.';

    public function handle(): int
    {
        $days = max(30, (int) $this->option('days'));
        $cutoff = now()->subDays($days);
        $accounts = DB::table('sync_changes')
            ->where('created_at', '<', $cutoff)
            ->distinct()
            ->pluck('account_id');

        $deleted = 0;
        foreach ($accounts as $accountId) {
            $accountId = (int) $accountId;
            $maxPrunable = (int) (DB::table('sync_changes')
                ->where('account_id', $accountId)
                ->where('created_at', '<', $cutoff)
                ->max('id') ?? 0);
            if ($maxPrunable <= 0) continue;

            $deleted += DB::transaction(function () use ($accountId, $maxPrunable): int {
                $existingFloor = (int) (DB::table('sync_change_floors')
                    ->where('account_id', $accountId)
                    ->lockForUpdate()
                    ->value('floor_cursor') ?? 0);
                $floor = max($existingFloor, $maxPrunable);
                $now = now();

                DB::table('sync_change_floors')->updateOrInsert(
                    ['account_id' => $accountId],
                    ['floor_cursor' => $floor, 'created_at' => $now, 'updated_at' => $now],
                );

                return DB::table('sync_changes')
                    ->where('account_id', $accountId)
                    ->where('id', '<=', $floor)
                    ->delete();
            }, 3);
        }

        $this->info("Pruned {$deleted} sync change rows. Clients behind a recorded floor must bootstrap again.");
        return self::SUCCESS;
    }
}
