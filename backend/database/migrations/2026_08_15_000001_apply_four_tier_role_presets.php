<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    private function role(string $role): string
    {
        return match (strtolower(trim($role))) {
            'superadmin' => 'superadmin',
            'admin' => 'admin',
            'manager', 'business', 'business_user', 'business-user' => 'manager',
            default => 'user',
        };
    }

    private function permissions(string $role): array
    {
        $all = [
            'can_view_customers' => false,
            'can_add_customers' => false,
            'can_edit_customers' => false,
            'can_delete_customers' => false,
            'can_view_suppliers' => false,
            'can_add_suppliers' => false,
            'can_edit_suppliers' => false,
            'can_delete_suppliers' => false,
            'can_view_transactions' => false,
            'can_add_transactions' => false,
            'can_edit_transactions' => false,
            'can_delete_transactions' => false,
            'can_manage_wallet' => false,
            'can_manage_expenses' => false,
            'can_view_reports' => false,
        ];

        if (in_array($role, ['superadmin', 'admin'], true)) {
            return array_map(static fn () => true, $all);
        }

        foreach ([
            'can_view_customers',
            'can_add_customers',
            'can_edit_customers',
            'can_delete_customers',
            'can_manage_expenses',
        ] as $key) {
            $all[$key] = true;
        }

        if ($role === 'manager') {
            foreach ([
                'can_view_suppliers',
                'can_add_suppliers',
                'can_edit_suppliers',
                'can_delete_suppliers',
                'can_view_transactions',
                'can_add_transactions',
                'can_edit_transactions',
                'can_delete_transactions',
            ] as $key) {
                $all[$key] = true;
            }
        }

        return $all;
    }

    public function up(): void
    {
        if (Schema::hasTable('users')) {
            DB::table('users')->select(['id', 'role'])->orderBy('id')->chunkById(100, function ($rows): void {
                foreach ($rows as $row) {
                    $role = $this->role((string) $row->role);
                    DB::table('users')->where('id', $row->id)->update([
                        'role' => $role,
                        'permissions' => json_encode($this->permissions($role)),
                        'updated_at' => now(),
                    ]);
                }
            });
        }

        if (Schema::hasTable('operator_accounts')) {
            DB::table('operator_accounts')->select(['id', 'role'])->orderBy('id')->chunkById(100, function ($rows): void {
                foreach ($rows as $row) {
                    $role = $this->role((string) $row->role);
                    DB::table('operator_accounts')->where('id', $row->id)->update([
                        'role' => $role,
                        'permissions' => json_encode($this->permissions($role)),
                        'updated_at' => now(),
                    ]);
                }
            });
        }
    }

    public function down(): void
    {
        // Role migration is intentionally one-way. Restoring ambiguous legacy
        // staff/manager permissions would be unsafe and non-deterministic.
    }
};
