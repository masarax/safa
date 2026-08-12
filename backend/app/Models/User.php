<?php

namespace App\Models;

use App\Support\MobileNumber;
use Database\Factories\UserFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\HasEvents;
use Illuminate\Database\Eloquent\HasFactory;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;

#[Fillable(['name', 'email', 'password', 'role', 'mobile', 'pin_hash', 'is_activated', 'permissions'])]
#[Hidden(['password', 'remember_token', 'pin_hash'])]
class User extends Authenticatable
{
    use HasFactory, Notifiable;

    public const ROLE_SUPERADMIN = 'superadmin';
    public const ROLE_ADMIN = 'admin';
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
        });
    }

    public static function roles(): array
    {
        return [self::ROLE_SUPERADMIN, self::ROLE_ADMIN, self::ROLE_USER];
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

    public function isSuperAdmin(): bool
    {
        return $this->role === self::ROLE_SUPERADMIN;
    }

    public function isAdmin(): bool
    {
        return $this->role === self::ROLE_ADMIN;
    }

    public function canManageUsers(): bool
    {
        return $this->isSuperAdmin();
    }

    public function getFormattedPermissions(): array
    {
        if ($this->isSuperAdmin()) {
            return static::defaultPermissions(true);
        }

        $userPerms = $this->permissions;
        if (is_string($userPerms)) {
            $userPerms = json_decode($userPerms, true) ?: [];
        }
        if (!is_array($userPerms)) {
            $userPerms = [];
        }

        $defaults = static::defaultPermissions(false);
        foreach ($defaults as $flag => $value) {
            if (array_key_exists($flag, $userPerms)) {
                $defaults[$flag] = (bool) $userPerms[$flag];
            }
        }

        return $defaults;
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
