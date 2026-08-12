<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        foreach (['customers', 'suppliers'] as $table) {
            if (!Schema::hasTable($table)) continue;

            Schema::table($table, function (Blueprint $blueprint) use ($table) {
                if (!Schema::hasColumn($table, 'avatar_color')) {
                    $blueprint->string('avatar_color', 20)->nullable()->after('phone');
                }
                if (!Schema::hasColumn($table, 'avatar_emoji')) {
                    $blueprint->string('avatar_emoji', 16)->nullable()->after('avatar_color');
                }
            });
        }
    }

    public function down(): void
    {
        foreach (['customers', 'suppliers'] as $table) {
            if (!Schema::hasTable($table)) continue;
            Schema::table($table, function (Blueprint $blueprint) use ($table) {
                if (Schema::hasColumn($table, 'avatar_emoji')) $blueprint->dropColumn('avatar_emoji');
                if (Schema::hasColumn($table, 'avatar_color')) $blueprint->dropColumn('avatar_color');
            });
        }
    }
};
