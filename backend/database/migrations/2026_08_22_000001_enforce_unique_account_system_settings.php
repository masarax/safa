<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        if (!Schema::hasTable('system_settings') || !Schema::hasColumn('system_settings', 'account_id')) {
            return;
        }

        // Older deployments could create duplicate scoped rows through a
        // read-then-create race. Keep the most recently updated row authoritative
        // for each account (id is the deterministic tie breaker). Preserve every
        // other row and its configuration values by demoting only its scope to
        // the nullable legacy/global pool; production migrations never delete
        // existing records.
        $duplicateAccountIds = DB::table('system_settings')
            ->whereNotNull('account_id')
            ->select('account_id')
            ->groupBy('account_id')
            ->havingRaw('COUNT(*) > 1')
            ->pluck('account_id');

        foreach ($duplicateAccountIds as $accountId) {
            DB::transaction(function () use ($accountId): void {
                $rows = DB::table('system_settings')
                    ->where('account_id', $accountId)
                    ->orderByDesc('updated_at')
                    ->orderByDesc('id')
                    ->get(['id']);

                $winner = $rows->first();
                if (!$winner) return;

                $legacyIds = $rows->skip(1)->pluck('id')->all();
                if ($legacyIds !== []) {
                    DB::table('system_settings')
                        ->whereIn('id', $legacyIds)
                        ->update([
                            'account_id' => null,
                            'updated_at' => now(),
                        ]);
                }
            }, 3);
        }

        Schema::table('system_settings', function (Blueprint $table): void {
            $table->unique('account_id', 'system_settings_account_id_unique');
        });
    }

    public function down(): void
    {
        if (!Schema::hasTable('system_settings')) return;

        Schema::table('system_settings', function (Blueprint $table): void {
            $table->dropUnique('system_settings_account_id_unique');
        });
    }
};
