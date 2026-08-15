<?php

namespace App\Models;

use App\Support\MobileNumber;
use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;

#[Fillable(['name', 'email', 'password', 'role', 'mobile', 'pin_hash', 'is_activated', 'permissions'])]
#[Hidden(['password', 'remember_token', 'pin_hash'])]
class User extends Authenticatable
{
    use HasFactory, Notifiable;

    public const ROLE_SUPERADMIN = 'superadmin';
    public const ROLE_ADMIN = 'admin';

    /**
     * `manager` is retained as the storage/API value for the Business User tier
     * so already-installed Android clients and legacy rows remain compatible.
     */
    public const ROLE_BUSINESS_USER = 'manager';
    public const ROLE_USER = 'user';

    protected static function booted(): void
    {
        static::saving(function (self $user): void {
            if ($user->isDirty('mobile') && is_string($user->mobile)) {
                $normalized = MobileNumber::normalize($user->mobile);
                if ($normalized !== '') {
                    $user->mobile = $normalized;
                }
            }

            if ($user->isDirty('role')) {
                $user->role = static::normalizeRole((string) $user->role);
            }

            // Role presets are authoritative. Persisting the effective preset
            // keeps legacy/operator compatibility data deterministic as well.
            $user->permissions = static::permissionsForRole((string) $user->role);
        });
    }

    public static function roles(): array
    {
        return [
            self::ROLE_SUPERADMIN,
            self::ROLE_ADMIN,
            self::ROLE_BUSINESS_USER,
            self::ROLE_USER,
        ];
    }

    public static function normalizeRole(string $role): string
    {
        return match (strtolower(trim($role))) {
            self::ROLE_SUPERADMIN => self::ROLE_SUPERADMIN,
            self::ROLE_ADMIN => self::ROLE_ADMIN,
            self::ROLE_BUSINESS_USER, 'business', 'business_user', 'business-user' => self::ROLE_BUSINESS_USER,
            self::ROLE_USER, 'staff', 'normal', 'normal_user', 'normal-user', 'owner' => self::ROLE_USER,
            default => self::ROLE_USER,
        };
    }

    public static function roleLabel(string $role): string
    {
        return match (static::normalizeRole($role)) {
            self::ROLE_SUPERADMIN => 'Super Admin',
            self::ROLE_ADMIN => 'Admin',
            self::ROLE_BUSINESS_USER => 'Business User',
            default => 'Normal User',
        };
    }

    public static function roleRank(string $role): int
    {
        return match (static::normalizeRole($role)) {
            self::ROLE_SUPERADMIN => 40,
            self::ROLE_ADMIN => 30,
            self::ROLE_BUSINESS_USER => 20,
            default => 10,
        };
    }

    public static function defaultPermissions(bool $defaultState = false): array
    {
        return [
            'can_view_customers' => $defaultState,
            'can_add_customers' => $defaultState,
            'can_edit_customers' => $defaultState,
            'can_delete_customers' => $defaultState,
            'can_view_suppliers' => $defaultState,
            'can_add_suppliers' => $defaultState,
            'can_edit_suppliers' => $defaultState,
            'can_delete_suppliers' => $defaultState,
            'can_view_transactions' => $defaultState,
            'can_add_transactions' => $defaultState,
            'can_edit_transactions' => $defaultState,
            'can_delete_transactions' => $defaultState,
            'can_manage_wallet' => $defaultState,
            'can_manage_expenses' => $defaultState,
            'can_view_reports' => $defaultState,
        ];
    }

    public static function permissionsForRole(string $role): array
    {
        $normalized = static::normalizeRole($role);
        if (in_array($normalized, [self::ROLE_SUPERADMIN, self::ROLE_ADMIN], true)) {
            return static::defaultPermissions(true);
        }

        $permissions = static::defaultPermissions(false);
        foreach ([
            'can_view_customers',
            'can_add_customers',
            'can_edit_customers',
            'can_delete_customers',
            'can_manage_expenses',
        ] as $permission) {
            $permissions[$permission] = true;
        }

        if ($normalized === self::ROLE_BUSINESS_USER) {
            foreach ([
                'can_view_suppliers',
                'can_add_suppliers',
                'can_edit_suppliers',
                'can_delete_suppliers',
                'can_view_transactions',
                'can_add_transactions',
                'can_edit_transactions',
                'can_delete_transactions',
            ] as $permission) {
                $permissions[$permission] = true;
            }
        }

        return $permissions;
    }

    public function isSuperAdmin(): bool
    {
        return static::normalizeRole((string) $this->role) === self::ROLE_SUPERADMIN;
    }

    public function isAdmin(): bool
    {
        return static::normalizeRole((string) $this->role) === self::ROLE_ADMIN;
    }

    public function isBusinessUser(): bool
    {
        return static::normalizeRole((string) $this->role) === self::ROLE_BUSINESS_USER;
    }

    public function canManageUsers(): bool
    {
        return $this->isSuperAdmin() || $this->isAdmin();
    }

    public function canManageBranding(): bool
    {
        return $this->isSuperAdmin() || $this->isAdmin();
    }

    public function canManageRole(string $targetRole): bool
    {
        return $this->canManageUsers()
            && static::roleRank((string) $this->role) > static::roleRank($targetRole);
    }

    public function getFormattedPermissions(): array
    {
        return static::permissionsForRole((string) $this->role);
    }

    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password' => 'hashed',
            'is_activated' => 'boolean',
            'permissions' => 'array',
        ];
    }

    public function deviceBindings()
    {
        return $this->hasMany(DeviceBinding::class);
    }

    public function authSessions()
    {
        return $this->hasMany(AuthSession::class);
    }

    public function ownedAccountShares()
    {
        return $this->hasMany(UserAccountShare::class, 'owner_user_id');
    }

    public function receivedAccountShares()
    {
        return $this->hasMany(UserAccountShare::class, 'shared_with_user_id');
    }
}
