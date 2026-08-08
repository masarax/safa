<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

class AuthSession extends Model
{
    protected $table = 'auth_sessions';

    protected $fillable = [
        'user_id',
        'device_uuid',
        'access_token',
        'refresh_token',
        'session_token',
        'expires_at',
        'is_revoked',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'is_revoked' => 'boolean',
            'expires_at' => 'datetime',
        ];
    }

    /**
     * Get the user that owns the auth session.
     */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
