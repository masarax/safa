<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        if (!Schema::hasTable('auth_sessions')) return;

        $indexes = Schema::getIndexes('auth_sessions');
        $indexNames = array_values(array_filter(array_map(
            static fn (array $index): ?string => isset($index['name']) ? (string) $index['name'] : null,
            $indexes
        )));

        Schema::table('auth_sessions', function (Blueprint $table) use ($indexNames): void {
            // The pre-encryption schema indexed the raw refresh/session token
            // columns. Hash columns now own those lookups; keeping a full index
            // would also prevent widening the ciphertext columns safely on MySQL.
            if (in_array('auth_sessions_refresh_token_index', $indexNames, true)) {
                $table->dropIndex('auth_sessions_refresh_token_index');
            }
            if (in_array('auth_sessions_session_token_index', $indexNames, true)) {
                $table->dropIndex('auth_sessions_session_token_index');
            }
        });

        Schema::table('auth_sessions', function (Blueprint $table): void {
            $table->text('refresh_token')->change();
            $table->text('session_token')->change();
        });
    }

    public function down(): void
    {
        // Intentionally do not shrink encrypted ciphertext back to VARCHAR(255):
        // doing so could truncate active credentials and corrupt live sessions.
    }
};
