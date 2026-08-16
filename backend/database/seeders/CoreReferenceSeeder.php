<?php

namespace Database\Seeders;

use App\Models\Account;
use App\Models\Permission;
use App\Models\Role;
use App\Models\SystemSetting;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;

class CoreReferenceSeeder extends Seeder
{
    public function run(): void
    {
        $permissionIds = [];
        foreach (array_keys(User::defaultPermissions(false)) as $slug) {
            $permission = Permission::updateOrCreate(
                ['slug' => $slug],
                ['name' => Str::of($slug)->replace('_', ' ')->title()->toString()]
            );
            $permissionIds[$slug] = $permission->id;
        }

        $roles = [
            User::ROLE_SUPERADMIN => ['name' => 'Super Admin', 'description' => 'Full system and business access.'],
            User::ROLE_ADMIN => ['name' => 'Admin', 'description' => 'Administrative and full business access.'],
            User::ROLE_BUSINESS_USER => ['name' => 'Business User', 'description' => 'Business operations access based on the manager preset.'],
            User::ROLE_USER => ['name' => 'Normal User', 'description' => 'Standard customer and expense access.'],
        ];

        foreach ($roles as $slug => $metadata) {
            $role = Role::updateOrCreate(
                ['slug' => $slug],
                ['name' => $metadata['name'], 'description' => $metadata['description']]
            );

            $preset = User::permissionsForRole($slug);
            $allowed = [];
            foreach ($preset as $permissionSlug => $enabled) {
                if ($enabled && isset($permissionIds[$permissionSlug])) {
                    $allowed[] = $permissionIds[$permissionSlug];
                }
            }
            $role->permissions()->sync($allowed);
        }

        $account = Account::query()->orderBy('id')->first();
        $setting = SystemSetting::query()->orderBy('id')->first() ?? new SystemSetting();

        if (!$setting->exists) {
            $setting->app_name = 'SAFA';
            $setting->app_version = '1.0.0';
            $setting->local_currency = 'BDT';
            $setting->foreign_currency = 'SAR';
            $setting->rate_based_mode = true;
            $setting->supplier_rate_enabled = true;
            $setting->wallet_rate_enabled = true;
        }

        if (!$setting->account_id && $account) {
            $setting->account_id = $account->id;
        }

        $setting->save();
    }
}
