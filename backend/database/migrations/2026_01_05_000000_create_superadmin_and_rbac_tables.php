<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            if (!Schema::hasColumn('users', 'mobile')) $table->string('mobile', 20)->nullable()->unique()->after('email');
            if (!Schema::hasColumn('users', 'pin_hash')) $table->string('pin_hash', 255)->nullable()->after('mobile');
            // Role is intentionally a string. The application has legacy-compatible
            // roles (superadmin/admin/manager/staff/user), and a DB ENUM made SQLite
            // tests and future role evolution brittle.
            if (!Schema::hasColumn('users', 'role')) $table->string('role', 30)->default('user')->after('pin_hash');
            if (!Schema::hasColumn('users', 'permissions')) $table->json('permissions')->nullable()->after('role');
            if (!Schema::hasColumn('users', 'is_activated')) $table->boolean('is_activated')->default(true);
        });

        if (!Schema::hasTable('operator_accounts')) {
            Schema::create('operator_accounts', function (Blueprint $table) {
                $table->id();
                $table->foreignId('user_id')->nullable()->constrained('users')->onDelete('cascade');
                $table->string('name');
                $table->string('email')->nullable();
                $table->string('mobile', 30)->unique();
                $table->string('role', 30)->default('user');
                $table->string('pin_hash', 255)->nullable();
                $table->boolean('is_activated')->default(true);
                $table->json('permissions')->nullable();
                $table->timestamps();
            });
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('operator_accounts');
        Schema::table('users', function (Blueprint $table) {
            foreach (['permissions', 'is_activated', 'pin_hash', 'mobile', 'role'] as $column) if (Schema::hasColumn('users', $column)) $table->dropColumn($column);
        });
    }
};
