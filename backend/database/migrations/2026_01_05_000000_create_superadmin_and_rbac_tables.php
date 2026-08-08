<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;

return new class extends Migration {
    /**
     * Run the migrations.
     */
    public function up(): void
    {
        Schema::table('users', function (Blueprint $table) {
            if (!Schema::hasColumn('users', 'role')) {
                $table->enum('role', ['superadmin', 'manager', 'staff'])->default('staff');
            }
            if (!Schema::hasColumn('users', 'mobile')) {
                $table->string('mobile', 30)->nullable()->unique();
            }
            if (!Schema::hasColumn('users', 'pin_hash')) {
                $table->string('pin_hash', 255)->nullable();
            }
            if (!Schema::hasColumn('users', 'is_activated')) {
                $table->boolean('is_activated')->default(false);
            }
            if (!Schema::hasColumn('users', 'permissions')) {
                $table->json('permissions')->nullable();
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

        // Seed initial SuperAdmin user if missing: mobile = '01700000000', role = 'superadmin', is_activated = false
        $existingSuperAdmin = DB::table('users')
            ->where('mobile', '01700000000')
            ->orWhere('role', 'superadmin')
            ->first();

        $allPermissions = [
            'can_view_customers'     => true,
            'can_add_customers'      => true,
            'can_edit_customers'     => true,
            'can_delete_customers'   => true,
            'can_view_suppliers'     => true,
            'can_add_suppliers'      => true,
            'can_edit_suppliers'     => true,
            'can_delete_suppliers'   => true,
            'can_view_transactions'  => true,
            'can_add_transactions'   => true,
            'can_edit_transactions'  => true,
            'can_delete_transactions' => true,
            'can_manage_wallet'      => true,
            'can_manage_expenses'    => true,
            'can_view_reports'       => true,
        ];

        if (!$existingSuperAdmin) {
            DB::table('users')->insert([
                'name'         => 'Super Admin',
                'email'        => 'superadmin@safa.local',
                'password'     => Hash::make('123456'),
                'role'         => 'superadmin',
                'mobile'       => '01700000000',
                'pin_hash'     => null,
                'is_activated' => false,
                'permissions'  => json_encode($allPermissions),
                'created_at'   => now(),
                'updated_at'   => now(),
            ]);
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
