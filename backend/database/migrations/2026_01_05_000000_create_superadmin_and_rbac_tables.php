<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    /**
     * Run the migrations.
     *
     * This migration is schema-only. Initial users must be provisioned by
     * DatabaseSeeder and must never be created as a side effect of a migration.
     */
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            if (!Schema::hasColumn('users', 'mobile')) {
                $table->string('mobile', 20)->nullable()->unique()->after('email');
            }
            if (!Schema::hasColumn('users', 'pin_hash')) {
                $table->string('pin_hash', 255)->nullable()->after('mobile');
            }
            if (!Schema::hasColumn('users', 'role')) {
                $table->enum('role', ['superadmin', 'manager', 'staff'])->default('staff')->after('pin_hash');
            }
            if (!Schema::hasColumn('users', 'permissions')) {
                $table->json('permissions')->nullable()->after('role');
            }
            if (!Schema::hasColumn('users', 'is_activated')) {
                $table->boolean('is_activated')->default(true);
            }
        });

        if (!Schema::hasTable('operator_accounts')) {
            Schema::create('operator_accounts', function (Blueprint $table) {
                $table->id();
                $table->foreignId('user_id')->nullable()->constrained('users')->onDelete('cascade');
                $table->string('name');
                $table->string('email')->nullable();
                $table->string('mobile', 30)->unique();
                $table->enum('role', ['superadmin', 'manager', 'staff'])->default('staff');
                $table->string('pin_hash', 255)->nullable();
                $table->boolean('is_activated')->default(true);
                $table->json('permissions')->nullable();
                $table->timestamps();
            });
        }
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('operator_accounts');

        Schema::table('users', function (Blueprint $table) {
            if (Schema::hasColumn('users', 'permissions')) {
                $table->dropColumn('permissions');
            }
            if (Schema::hasColumn('users', 'is_activated')) {
                $table->dropColumn('is_activated');
            }
            if (Schema::hasColumn('users', 'pin_hash')) {
                $table->dropColumn('pin_hash');
            }
            if (Schema::hasColumn('users', 'mobile')) {
                $table->dropColumn('mobile');
            }
            if (Schema::hasColumn('users', 'role')) {
                $table->dropColumn('role');
            }
        });
    }
};
