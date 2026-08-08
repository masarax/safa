<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

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
