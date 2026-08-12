<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Enforce account ownership at the database layer for every cross-entity
     * reference. Controllers already validate these relationships, but the
     * database must remain safe if a future API, job, import or maintenance
     * command bypasses those controllers.
     */
    public function up(): void
    {
        $this->nullInvalidReference('supplier_deposits', 'supplier_id', 'suppliers');
        $this->nullInvalidReference('wallet_batches', 'ledger_id', 'wallet_ledgers');
        $this->nullInvalidReference('wallet_batches', 'supplier_id', 'suppliers');
        $this->nullInvalidReference('wallet_batches', 'supplier_deposit_id', 'supplier_deposits');
        $this->nullInvalidReference('transactions', 'customer_id', 'customers');
        $this->nullInvalidReference('transactions', 'supplier_id', 'suppliers');
        $this->nullInvalidReference('transactions', 'wallet_batch_id', 'wallet_batches');

        foreach (['customers', 'suppliers', 'wallet_ledgers', 'supplier_deposits', 'wallet_batches'] as $table) {
            Schema::table($table, function (Blueprint $blueprint) use ($table) {
                $blueprint->unique(['account_id', 'id'], $table . '_account_id_id_unique');
            });
        }

        Schema::table('supplier_deposits', function (Blueprint $table) {
            $table->foreign(['account_id', 'supplier_id'], 'supplier_deposits_account_supplier_fk')
                ->references(['account_id', 'id'])->on('suppliers')->restrictOnDelete();
        });

        Schema::table('wallet_batches', function (Blueprint $table) {
            $table->foreign(['account_id', 'ledger_id'], 'wallet_batches_account_ledger_fk')
                ->references(['account_id', 'id'])->on('wallet_ledgers')->restrictOnDelete();
            $table->foreign(['account_id', 'supplier_id'], 'wallet_batches_account_supplier_fk')
                ->references(['account_id', 'id'])->on('suppliers')->restrictOnDelete();
            $table->foreign(['account_id', 'supplier_deposit_id'], 'wallet_batches_account_deposit_fk')
                ->references(['account_id', 'id'])->on('supplier_deposits')->restrictOnDelete();
        });

        Schema::table('transactions', function (Blueprint $table) {
            $table->foreign(['account_id', 'customer_id'], 'transactions_account_customer_fk')
                ->references(['account_id', 'id'])->on('customers')->restrictOnDelete();
            $table->foreign(['account_id', 'supplier_id'], 'transactions_account_supplier_fk')
                ->references(['account_id', 'id'])->on('suppliers')->restrictOnDelete();
            $table->foreign(['account_id', 'wallet_batch_id'], 'transactions_account_wallet_batch_fk')
                ->references(['account_id', 'id'])->on('wallet_batches')->restrictOnDelete();
        });
    }

    private function nullInvalidReference(string $sourceTable, string $foreignColumn, string $targetTable): void
    {
        if (!Schema::hasTable($sourceTable) || !Schema::hasTable($targetTable)) return;

        DB::table($sourceTable)
            ->whereNotNull($foreignColumn)
            ->orderBy('id')
            ->get(['id', 'account_id', $foreignColumn])
            ->each(function ($row) use ($sourceTable, $foreignColumn, $targetTable) {
                if (!DB::table($targetTable)
                    ->where('id', $row->{$foreignColumn})
                    ->where('account_id', $row->account_id)
                    ->exists()) {
                    DB::table($sourceTable)->where('id', $row->id)->update([$foreignColumn => null]);
                }
            });
    }

    public function down(): void
    {
        Schema::table('transactions', function (Blueprint $table) {
            $table->dropForeign('transactions_account_customer_fk');
            $table->dropForeign('transactions_account_supplier_fk');
            $table->dropForeign('transactions_account_wallet_batch_fk');
        });

        Schema::table('wallet_batches', function (Blueprint $table) {
            $table->dropForeign('wallet_batches_account_ledger_fk');
            $table->dropForeign('wallet_batches_account_supplier_fk');
            $table->dropForeign('wallet_batches_account_deposit_fk');
        });

        Schema::table('supplier_deposits', function (Blueprint $table) {
            $table->dropForeign('supplier_deposits_account_supplier_fk');
        });

        foreach (['customers', 'suppliers', 'wallet_ledgers', 'supplier_deposits', 'wallet_batches'] as $table) {
            Schema::table($table, function (Blueprint $blueprint) use ($table) {
                $blueprint->dropUnique($table . '_account_id_id_unique');
            });
        }
    }
};
