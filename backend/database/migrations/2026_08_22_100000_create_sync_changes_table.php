<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    private array $entities = [
        'customers',
        'suppliers',
        'wallet_ledgers',
        'supplier_deposits',
        'wallet_batches',
        'transactions',
        'expenses_incomes',
    ];

    public function up(): void
    {
        Schema::create('sync_changes', function (Blueprint $table) {
            $table->id();
            $table->unsignedBigInteger('account_id');
            $table->string('entity', 64);
            $table->unsignedBigInteger('record_id');
            $table->string('operation', 16);
            $table->json('snapshot');
            $table->timestamp('created_at')->useCurrent();

            $table->index(['account_id', 'id']);
            $table->index(['account_id', 'entity', 'record_id']);
            $table->foreign('account_id')->references('id')->on('accounts')->cascadeOnDelete();
        });

        // Existing installations bootstrap from cursor 0. Backfill in bounded
        // chunks so migration memory does not scale with total account history.
        foreach ($this->entities as $entity) {
            if (!Schema::hasTable($entity)) continue;

            DB::table($entity)
                ->orderBy('id')
                ->chunkById(500, function ($rows) use ($entity) {
                    $now = now();
                    $changes = [];
                    foreach ($rows as $row) {
                        $snapshot = (array) $row;
                        $accountId = (int) ($snapshot['account_id'] ?? 0);
                        $recordId = (int) ($snapshot['id'] ?? 0);
                        if ($accountId <= 0 || $recordId <= 0) continue;

                        $changes[] = [
                            'account_id' => $accountId,
                            'entity' => $entity,
                            'record_id' => $recordId,
                            'operation' => empty($snapshot['deleted_at']) ? 'UPSERT' : 'DELETE',
                            'snapshot' => json_encode($snapshot, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRESERVE_ZERO_FRACTION),
                            'created_at' => $now,
                        ];
                    }

                    if ($changes !== []) DB::table('sync_changes')->insert($changes);
                });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('sync_changes');
    }
};
