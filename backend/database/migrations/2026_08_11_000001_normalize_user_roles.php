<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    private function isMysql(): bool
    {
        return Schema::getConnection()->getDriverName() === 'mysql';
    }

    public function up(): void
    {
        if (Schema::hasTable('users') && Schema::hasColumn('users', 'role')) {
            DB::table('users')->where('role', 'manager')->update(['role' => 'admin']);
            DB::table('users')->where('role', 'staff')->update(['role' => 'user']);
            if ($this->isMysql()) DB::statement("ALTER TABLE `users` MODIFY `role` ENUM('superadmin','admin','user') NOT NULL DEFAULT 'user'");
        }

        if (Schema::hasTable('operator_accounts') && Schema::hasColumn('operator_accounts', 'role')) {
            DB::table('operator_accounts')->where('role', 'manager')->update(['role' => 'admin']);
            DB::table('operator_accounts')->where('role', 'staff')->update(['role' => 'user']);
            if ($this->isMysql()) DB::statement("ALTER TABLE `operator_accounts` MODIFY `role` ENUM('superadmin','admin','user') NOT NULL DEFAULT 'user'");
        }
    }

    public function down(): void
    {
        if (Schema::hasTable('users') && Schema::hasColumn('users', 'role')) {
            DB::table('users')->where('role', 'admin')->update(['role' => 'manager']);
            DB::table('users')->where('role', 'user')->update(['role' => 'staff']);
            if ($this->isMysql()) DB::statement("ALTER TABLE `users` MODIFY `role` ENUM('superadmin','manager','staff') NOT NULL DEFAULT 'staff'");
        }

        if (Schema::hasTable('operator_accounts') && Schema::hasColumn('operator_accounts', 'role')) {
            DB::table('operator_accounts')->where('role', 'admin')->update(['role' => 'manager']);
            DB::table('operator_accounts')->where('role', 'user')->update(['role' => 'staff']);
            if ($this->isMysql()) DB::statement("ALTER TABLE `operator_accounts` MODIFY `role` ENUM('superadmin','manager','staff') NOT NULL DEFAULT 'staff'");
        }
    }
};
