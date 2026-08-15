<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        if (!Schema::hasTable('system_settings') || Schema::hasColumn('system_settings', 'captain_name')) {
            return;
        }

        Schema::table('system_settings', function (Blueprint $table): void {
            $table->string('captain_name')->nullable()->after('app_name');
        });
    }

    public function down(): void
    {
        if (!Schema::hasTable('system_settings') || !Schema::hasColumn('system_settings', 'captain_name')) {
            return;
        }

        Schema::table('system_settings', function (Blueprint $table): void {
            $table->dropColumn('captain_name');
        });
    }
};
