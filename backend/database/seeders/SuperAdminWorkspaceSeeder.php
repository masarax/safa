<?php

namespace Database\Seeders;

use App\Models\Account;
use App\Models\User;
use Illuminate\Database\Seeder;

class SuperAdminWorkspaceSeeder extends Seeder
{
    /**
     * Repair the non-secret workspace state required by existing SuperAdmins.
     * This seeder never creates identities, passwords, PINs, API credentials or
     * any other authentication secret.
     */
    public function run(): void
    {
        $superAdmins = User::query()
            ->where('role', User::ROLE_SUPERADMIN)
            ->orderBy('id')
            ->get();

        if ($superAdmins->isEmpty()) {
            return;
        }

        foreach ($superAdmins as $superAdmin) {
            $superAdmin->permissions = User::permissionsForRole(User::ROLE_SUPERADMIN);
            $superAdmin->save();
        }

        if (!Account::query()->exists()) {
            $owner = $superAdmins->first();
            Account::create([
                'name' => 'SAFA Account',
                'balance' => 0,
                'owner_user_id' => $owner->id,
            ]);
        }
    }
}
