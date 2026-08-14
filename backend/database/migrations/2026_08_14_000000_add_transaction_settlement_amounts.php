<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        $addSarCollected = !Schema::hasColumn('transactions', 'sar_collected');
        $addBdtDisbursed = !Schema::hasColumn('transactions', 'bdt_disbursed');
        Schema::table('transactions', function (Blueprint $table) use ($addSarCollected, $addBdtDisbursed) {
            if ($addSarCollected) {
                $table->decimal('sar_collected', 15, 2)->nullable()->after('amount_bdt');
            }
            if ($addBdtDisbursed) {
                $table->decimal('bdt_disbursed', 15, 2)->nullable()->after('sar_collected');
            }
        });

        // Older clients treated missing settlement fields as fully settled.
        // Backfill with that same meaning so the migration cannot turn every
        // historical transaction into an outstanding balance.
        DB::table('transactions')->whereNull('sar_collected')->update([
            'sar_collected' => DB::raw('amount_sar'),
        ]);
        DB::table('transactions')->whereNull('bdt_disbursed')->update([
            'bdt_disbursed' => DB::raw('amount_bdt'),
        ]);
    }

    public function down(): void
    {
        $columns = array_values(array_filter(
            ['sar_collected', 'bdt_disbursed'],
            fn (string $column): bool => Schema::hasColumn('transactions', $column),
        ));
        Schema::table('transactions', function (Blueprint $table) use ($columns) {
            if ($columns !== []) $table->dropColumn($columns);
        });
    }
};
