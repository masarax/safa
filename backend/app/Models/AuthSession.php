<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;

class AuthSession extends Model
{
    /**
     * 31 base-62 characters provide well over 128 bits of entropy while their
     * Laravel-encrypted ciphertext remains within the legacy VARCHAR(255)
     * production schema during a rolling deployment. The forward migration
     * still widens those columns to TEXT for long-term storage headroom.
     */
    private const OPAQUE_TOKEN_LENGTH = 31;

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
            // Production deployments may briefly run application code before a
            // newly uploaded migration has been applied. Never turn an otherwise
            // valid mobile login into HTTP 500 merely because the optional token
            // lookup indexes are not present yet; the constrained encrypted-token
            // compatibility lookup below remains fail-closed until migration.
            if (!self::supportsTokenHashes()) return;

            foreach (['access_token', 'refresh_token', 'session_token'] as $field) {
                if ($session->isDirty($field)) {
                    $value = (string) $session->getAttribute($field);
                    $hashField = $field . '_hash';
                    $session->setAttribute($hashField, $value !== '' ? hash('sha256', $value) : null);
                }
            }
        });
    }

    public static function newOpaqueToken(): string
    {
        return Str::random(self::OPAQUE_TOKEN_LENGTH);
    }

    public static function tokenHash(?string $token): ?string
    {
        $token = trim((string) $token);
        return $token === '' ? null : hash('sha256', $token);
    }

    public static function supportsTokenHashes(): bool
    {
        if (!Schema::hasTable('auth_sessions')) return false;

        foreach (['access_token_hash', 'refresh_token_hash', 'session_token_hash'] as $column) {
            if (!Schema::hasColumn('auth_sessions', $column)) return false;
        }

        return true;
    }

    public static function findActiveByAccessToken(
        string $accessToken,
        ?int $userId = null,
        ?string $deviceUuid = null
    ): ?self {
        $query = self::activeQuery($userId, $deviceUuid);

        if (self::supportsTokenHashes()) {
            $session = (clone $query)
                ->where('access_token_hash', self::tokenHash($accessToken))
                ->first();
            if ($session) return $session;
        }

        return $query->get()->first(
            fn (self $candidate): bool => self::tokenMatches($candidate, 'access_token', $accessToken)
        );
    }

    /**
     * Normal API authorization deliberately excludes the long-lived refresh
     * credential. Access + active server session + device/session proof is
     * sufficient; refresh remains reserved for the rotation endpoint.
     */
    public static function findActiveByAccessSessionStack(
        int $userId,
        string $deviceUuid,
        string $accessToken,
        string $sessionToken
    ): ?self {
        $query = self::activeQuery($userId, $deviceUuid);

        if (self::supportsTokenHashes()) {
            $session = (clone $query)
                ->where('access_token_hash', self::tokenHash($accessToken))
                ->where('session_token_hash', self::tokenHash($sessionToken))
                ->first();
            if ($session) return $session;
        }

        return $query->get()->first(function (self $candidate) use ($accessToken, $sessionToken): bool {
            return self::tokenMatches($candidate, 'access_token', $accessToken)
                && self::tokenMatches($candidate, 'session_token', $sessionToken);
        });
    }

    /**
     * Legacy compatibility lookup retained for older tests/callers during the
     * staged protocol transition. New business authorization must use
     * findActiveByAccessSessionStack().
     */
    public static function findActiveByTokenStack(
        int $userId,
        string $deviceUuid,
        string $accessToken,
        string $refreshToken,
        string $sessionToken
    ): ?self {
        $query = self::activeQuery($userId, $deviceUuid);

        if (self::supportsTokenHashes()) {
            $session = (clone $query)
                ->where('access_token_hash', self::tokenHash($accessToken))
                ->where('refresh_token_hash', self::tokenHash($refreshToken))
                ->where('session_token_hash', self::tokenHash($sessionToken))
                ->first();
            if ($session) return $session;
        }

        return $query->get()->first(function (self $candidate) use ($accessToken, $refreshToken, $sessionToken): bool {
            return self::tokenMatches($candidate, 'access_token', $accessToken)
                && self::tokenMatches($candidate, 'refresh_token', $refreshToken)
                && self::tokenMatches($candidate, 'session_token', $sessionToken);
        });
    }

    public static function findActiveByRefreshToken(
        string $refreshToken,
        string $deviceUuid,
        bool $lockForUpdate = false
    ): ?self {
        $query = self::activeQuery(null, $deviceUuid);

        if (self::supportsTokenHashes()) {
            $hashQuery = (clone $query)->where('refresh_token_hash', self::tokenHash($refreshToken));
            if ($lockForUpdate) $hashQuery->lockForUpdate();
            $session = $hashQuery->first();
            if ($session) return $session;
        }

        $fallbackQuery = clone $query;
        if ($lockForUpdate) $fallbackQuery->lockForUpdate();

        return $fallbackQuery->get()->first(
            fn (self $candidate): bool => self::tokenMatches($candidate, 'refresh_token', $refreshToken)
        );
    }

    private static function activeQuery(?int $userId = null, ?string $deviceUuid = null): Builder
    {
        $query = self::query()->where('is_revoked', false);
        if ($userId !== null) $query->where('user_id', $userId);
        if ($deviceUuid !== null && $deviceUuid !== '') $query->where('device_uuid', $deviceUuid);
        return $query;
    }

    private static function tokenMatches(self $session, string $field, string $expected): bool
    {
        try {
            $actual = (string) $session->getAttribute($field);
        } catch (\Throwable) {
            return false;
        }

        return $actual !== '' && hash_equals($actual, $expected);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
