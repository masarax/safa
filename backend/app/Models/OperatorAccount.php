<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class OperatorAccount extends Model
{
    use HasFactory;

    protected $table = 'operator_accounts';

    protected $fillable = [
        'user_id',
        'name',
        'email',
        'mobile',
        'role',
        'pin_hash',
        'is_activated',
        'permissions',
    ];

    protected $hidden = [
        'pin_hash',
    ];

    protected function casts(): array
    {
        return [
            'is_activated' => 'boolean',
            'permissions'  => 'array',
        ];
    }

    public function user()
    {
        return $this->belongsTo(User::class);
    }
}
