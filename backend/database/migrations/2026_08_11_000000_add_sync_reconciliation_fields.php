<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    private array $entities = [
        'customers', 'suppliers', 'wallet_ledgers', 'supplier_deposits',
        'wallet_batches', 'transactions', 'expenses_incomes',
    ];

    public function up(): void
    {
        foreach ($this->entities as $table) {
            if (!Schema::hasTable($table)) continue;
            Schema::table($table, function (Blueprint $blueprint) use ($table) {
                if (!Schema::hasColumn($table, 'sync_version')) {
                    $blueprint->unsignedBigInteger('sync_version')->default(0)->after('timestamp');
                }
                if (!Schema::hasColumn($table, 'last_mutation_id')) {
                    $blueprint->string('last_mutation_id', 128)->nullable()->after('sync_version');
                    $blueprint->index('last_mutation_id');
                }
            });
        }

        if (!Schema::hasTable('sync_mutations')) {
            Schema::create('sync_mutations', function (Blueprint $table) {
                $table->id();
                $table->unsignedBigInteger('account_id');
                $table->string('mutation_id', 128);
                $table->string('entity', 64);
                $table->unsignedBigInteger('local_id');
                $table->unsignedBigInteger('server_id')->nullable();
                $table->string('operation', 20);
                $table->unsignedBigInteger('sync_version')->default(0);
                $table->json('response')->nullable();
                $table->timestamps();
                $table->unique(['account_id', 'mutation_id']);
                $table->index(['account_id', 'entity', 'local_id']);
                $table->foreign('account_id')->references('id')->on('accounts')->cascadeOnDelete();
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('sync_mutations');
        foreach ($this->entities as $table) {
            if (!Schema::hasTable($table)) continue;
            Schema::table($table, function (Blueprint $blueprint) use ($table) {
                if (Schema::hasColumn($table, 'last_mutation_id')) {
                    $blueprint->dropIndex($table . '_last_mutation_id_index');
                    $blueprint->dropColumn('last_mutation_id');
                }
                if (Schema::hasColumn($table, 'sync_version')) {
                    $blueprint->dropColumn('sync_version');
                }
            });
        }
    }
};
