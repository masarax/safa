<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        foreach (['customers', 'suppliers'] as $tableName) {
            if (!Schema::hasTable($tableName) || Schema::hasColumn($tableName, 'address')) continue;
            Schema::table($tableName, function (Blueprint $table) { $table->string('address', 500)->nullable()->after('phone'); });
        }
    }

    public function down(): void
    {
        foreach (['customers', 'suppliers'] as $tableName) {
            if (!Schema::hasTable($tableName) || !Schema::hasColumn($tableName, 'address')) continue;
            Schema::table($tableName, function (Blueprint $table) { $table->dropColumn('address'); });
        }
    }
};
