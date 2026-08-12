<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Str;

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

    protected $hidden = [
        'access_token',
        'refresh_token',
        'session_token',
        'access_token_hash',
        'refresh_token_hash',
        'session_token_hash',
    ];

    protected function casts(): array
    {
        return [
            'access_token' => 'encrypted',
            'refresh_token' => 'encrypted',
            'session_token' => 'encrypted',
            'is_revoked' => 'boolean',
            'expires_at' => 'datetime',
        ];
    }

    protected static function booted(): void
    {
        static::saving(function (self $session): void {
            foreach (['access_token', 'refresh_token', 'session_token'] as $field) {
                if ($session->isDirty($field)) {
                    $value = (string) $session->getAttribute($field);
                    $hashField = $field . '_hash';
                    $session->setAttribute($hashField, $value !== '' ? hash('sha256', $value) : null);
                }
            }
        });
    }

    public static function tokenHash(?string $token): ?string
    {
        $token = trim((string) $token);
        return $token === '' ? null : hash('sha256', $token);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
