<?php

namespace App\Models;

// use Illuminate\Contracts\Auth\MustVerifyEmail;
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
    /** @use HasFactory<UserFactory> */
    use HasFactory, Notifiable;

    /**
     * Get default RBAC permissions map.
     */
    public static function defaultPermissions(bool $defaultState = false): array
    {
        return [
            'can_view_customers'     => $defaultState,
            'can_add_customers'      => $defaultState,
            'can_edit_customers'     => $defaultState,
            'can_delete_customers'   => $defaultState,
            'can_view_suppliers'     => $defaultState,
            'can_add_suppliers'      => $defaultState,
            'can_edit_suppliers'     => $defaultState,
            'can_delete_suppliers'   => $defaultState,
            'can_view_transactions'  => $defaultState,
            'can_add_transactions'   => $defaultState,
            'can_edit_transactions'  => $defaultState,
            'can_delete_transactions' => $defaultState,
            'can_manage_wallet'      => $defaultState,
            'can_manage_expenses'    => $defaultState,
            'can_view_reports'       => $defaultState,
        ];
    }

    /**
     * Get fully formatted granular permissions map for this user.
     */
    public function getFormattedPermissions(): array
    {
        if ($this->role === 'superadmin') {
            return static::defaultPermissions(true);
        }

        $userPerms = $this->permissions;
        if (is_string($userPerms)) {
            $userPerms = json_decode($userPerms, true) ?: [];
        }
        if (!is_array($userPerms)) {
            $userPerms = [];
        }

        $defaults = static::defaultPermissions($this->role === 'manager');

        foreach ($defaults as $flag => $val) {
            if (array_key_exists($flag, $userPerms)) {
                $defaults[$flag] = (bool) $userPerms[$flag];
            }
        }

        return $defaults;
    }

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password'          => 'hashed',
            'is_activated'      => 'boolean',
            'permissions'       => 'array',
        ];
    }

    /**
     * Get the device bindings for the user.
     */
    public function deviceBindings()
    {
        return $this->hasMany(DeviceBinding::class);
    }

    /**
     * Get the auth sessions for the user.
     */
    public function authSessions()
    {
        return $this->hasMany(AuthSession::class);
    }

    /**
     * Get account shares owned by this user.
     */
    public function ownedAccountShares()
    {
        return $this->hasMany(UserAccountShare::class, 'owner_user_id');
    }

    /**
     * Get account shares received by this user.
     */
    public function receivedAccountShares()
    {
        return $this->hasMany(UserAccountShare::class, 'shared_with_user_id');
    }
}


