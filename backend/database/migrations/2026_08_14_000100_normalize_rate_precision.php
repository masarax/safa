<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (!Schema::hasTable('rates') || !Schema::hasColumn('rates', 'rate')) return;
        if (DB::table('rates')->where('rate', '<', 0)->orWhere('rate', '>=', '1000000')->exists()) {
            throw new RuntimeException('Existing rate data is outside the DECIMAL(10,4) contract; migration stopped without changing data.');
        }
        Schema::table('rates', function (Blueprint $table) {
            $table->decimal('rate', 10, 4)->change();
        });
    }

    public function down(): void
    {
        if (!Schema::hasTable('rates') || !Schema::hasColumn('rates', 'rate')) return;
        Schema::table('rates', function (Blueprint $table) {
            $table->decimal('rate', 15, 4)->change();
        });
    }
};
