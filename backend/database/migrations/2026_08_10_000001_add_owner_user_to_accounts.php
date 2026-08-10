<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        if (!Schema::hasColumn('accounts', 'owner_user_id')) {
            Schema::table('accounts', function (Blueprint $table) {
                $table->foreignId('owner_user_id')->nullable()->after('id')->constrained('users')->nullOnDelete();
                $table->index('owner_user_id');
            });
        }

        // Preserve the existing convention where an account id was previously
        // treated as the user id whenever that user exists.
        DB::statement("
            UPDATE accounts a
            INNER JOIN users u ON u.id = a.id
            SET a.owner_user_id = u.id
            WHERE a.owner_user_id IS NULL
        ");

        // Legacy/default business data commonly lives in account #1. If that
        // account is still unowned, attach it to the first active superadmin so
        // existing production data remains reachable after the context fix.
        $defaultAccount = DB::table('accounts')->where('id', 1)->whereNull('owner_user_id')->first();
        if ($defaultAccount) {
            $superAdmin = DB::table('users')
                ->where('role', 'superadmin')
                ->where('is_activated', true)
                ->orderBy('id')
                ->first();

            if ($superAdmin) {
                DB::table('accounts')
                    ->where('id', 1)
                    ->update(['owner_user_id' => $superAdmin->id, 'updated_at' => now()]);
            }
        }
    }

    public function down(): void
    {
        if (Schema::hasColumn('accounts', 'owner_user_id')) {
            Schema::table('accounts', function (Blueprint $table) {
                $table->dropForeign(['owner_user_id']);
                $table->dropColumn('owner_user_id');
            });
        }
    }
};
