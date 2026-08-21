<?php

namespace App\Support;

use App\Models\User;
use App\Models\UserAccountShare;

final class BusinessPermissions
{
    public static function effective(?User $user, int $accountId): array
    {
        if (!$user) {
            return User::defaultPermissions(true);
        }

        $permissions = $user->getFormattedPermissions();
        if ($accountId <= 0) {
            return $permissions;
        }

        $share = UserAccountShare::query()
            ->where('shared_with_user_id', $user->id)
            ->where('account_id', $accountId)
            ->where('owner_user_id', '!=', $user->id)
            ->first();

        if (!$share || !is_array($share->permissions_override)) {
            return $permissions;
        }

        // A share may only reduce the recipient's base role permissions; it can
        // never elevate them. This applies to every role, including SuperAdmin.
        foreach ($share->permissions_override as $key => $allowed) {
            if (array_key_exists($key, $permissions)) {
                $permissions[$key] = (bool) $permissions[$key] && (bool) $allowed;
            }
        }

        // The Android sharing UI grants coarse entity access. If an owner turns
        // off a category's read access, mutation permissions for that category
        // must also be disabled even when the recipient's base role allows them.
        $readToMutations = [
            'can_view_customers' => ['can_add_customers', 'can_edit_customers', 'can_delete_customers'],
            'can_view_suppliers' => ['can_add_suppliers', 'can_edit_suppliers', 'can_delete_suppliers'],
            'can_view_transactions' => ['can_add_transactions', 'can_edit_transactions', 'can_delete_transactions'],
        ];

        foreach ($readToMutations as $readPermission => $mutationPermissions) {
            if (array_key_exists($readPermission, $share->permissions_override)
                && !(bool) $share->permissions_override[$readPermission]) {
                foreach ($mutationPermissions as $permission) {
                    if (array_key_exists($permission, $permissions)) {
                        $permissions[$permission] = false;
                    }
                }
            }
        }

        return $permissions;
    }

    public static function allows(?User $user, int $accountId, string $permission): bool
    {
        return !empty(self::effective($user, $accountId)[$permission]);
    }

    public static function readPermissionForEntity(string $entity): ?string
    {
        return match ($entity) {
            'customers' => 'can_view_customers',
            'suppliers' => 'can_view_suppliers',
            'transactions' => 'can_view_transactions',
            'wallet_ledgers', 'wallet_batches', 'supplier_deposits' => 'can_manage_wallet',
            'expenses_incomes' => 'can_manage_expenses',
            default => null,
        };
    }

    public static function mutationPermissionForEntity(string $entity, string $operation, bool $exists): ?string
    {
        if (in_array($entity, ['wallet_ledgers', 'wallet_batches', 'supplier_deposits'], true)) {
            return 'can_manage_wallet';
        }

        if ($entity === 'expenses_incomes') {
            return 'can_manage_expenses';
        }

        $prefix = match ($entity) {
            'customers' => 'customers',
            'suppliers' => 'suppliers',
            'transactions' => 'transactions',
            default => null,
        };
        if (!$prefix) {
            return null;
        }

        return match ($operation) {
            'DELETE' => "can_delete_{$prefix}",
            'CREATE' => "can_add_{$prefix}",
            'UPDATE', 'RESTORE' => "can_edit_{$prefix}",
            default => $exists ? "can_edit_{$prefix}" : "can_add_{$prefix}",
        };
    }
}
