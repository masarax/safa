<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        if (!Schema::hasColumn('auth_sessions', 'access_token_hash')) {
            Schema::table('auth_sessions', function (Blueprint $table) {
                $table->string('access_token_hash', 64)->nullable()->unique()->after('access_token');
                $table->string('refresh_token_hash', 64)->nullable()->unique()->after('refresh_token');
                $table->string('session_token_hash', 64)->nullable()->index()->after('session_token');
            });
        }
    }

    public function down(): void
    {
        Schema::table('auth_sessions', function (Blueprint $table) {
            foreach (['access_token_hash', 'refresh_token_hash', 'session_token_hash'] as $column) {
                if (Schema::hasColumn('auth_sessions', $column)) {
                    $table->dropColumn($column);
                }
            }
        });
    }
};
