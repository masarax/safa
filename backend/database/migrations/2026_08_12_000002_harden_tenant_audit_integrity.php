<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        if (Schema::hasTable('audit_logs') && !Schema::hasColumn('audit_logs', 'account_id')) {
            Schema::table('audit_logs', function (Blueprint $table) {
                $table->foreignId('account_id')->nullable()->after('user_id')->constrained('accounts')->nullOnDelete();
                $table->index(['account_id', 'created_at'], 'audit_account_created_idx');
            });
        }

        if (Schema::hasTable('user_account_shares') && Schema::hasColumn('user_account_shares', 'account_id')) {
            // A legacy NULL share cannot be assigned to an account without business
            // knowledge. Preserve the row and stop before tightening the schema so an
            // operator can repair the mapping explicitly instead of losing data.
            if (DB::table('user_account_shares')->whereNull('account_id')->exists()) {
                throw new RuntimeException(
                    'Legacy user account shares without account_id must be repaired before this migration can continue.'
                );
            }

            try {
                Schema::table('user_account_shares', function (Blueprint $table) {
                    $table->unsignedBigInteger('account_id')->nullable(false)->change();
                });
            } catch (\Throwable $e) {
                // SQLite/MySQL versions can differ in CHANGE support. The application
                // layer still rejects NULL account shares; do not make migration fail.
            }
        }

        foreach (['sync_mutations', 'auth_sessions'] as $tableName) {
            if (Schema::hasTable($tableName) && Schema::hasColumn($tableName, 'account_id')) {
                $index = $tableName . '_account_created_idx';
                try {
                    Schema::table($tableName, function (Blueprint $table) use ($index) {
                        $table->index(['account_id', 'created_at'], $index);
                    });
                } catch (\Throwable $e) {
                    // Index may already exist in a legacy installation.
                }
            }
        }
    }

    public function down(): void
    {
        if (Schema::hasTable('audit_logs') && Schema::hasColumn('audit_logs', 'account_id')) {
            Schema::table('audit_logs', function (Blueprint $table) {
                try { $table->dropForeign(['account_id']); } catch (\Throwable $e) { }
                try { $table->dropIndex('audit_account_created_idx'); } catch (\Throwable $e) { }
                $table->dropColumn('account_id');
            });
        }
    }
};
