<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Validation\ValidationException;

class UserAccountShare extends Model
{
    protected $table = 'user_account_shares';

    protected $fillable = [
        'owner_user_id',
        'account_id',
        'shared_with_user_id',
        'permissions_override',
    ];

    protected $casts = [
        'permissions_override' => 'array',
    ];

    protected static function booted(): void
    {
        static::saving(function (self $share): void {
            $accountId = (int) $share->account_id;
            $ownerUserId = (int) $share->owner_user_id;
            $account = $accountId > 0 ? Account::query()->find($accountId) : null;

            if (!$account || (int) $account->owner_user_id !== $ownerUserId) {
                throw ValidationException::withMessages([
                    'account_id' => ['Account shares must be created by the account owner context.'],
                ]);
            }
        });
    }

    public function owner()
    {
        return $this->belongsTo(User::class, 'owner_user_id');
    }

    public function sharedWith()
    {
        return $this->belongsTo(User::class, 'shared_with_user_id');
    }

    public function account()
    {
        return $this->belongsTo(Account::class, 'account_id');
    }
}
