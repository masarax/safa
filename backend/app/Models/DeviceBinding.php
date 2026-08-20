<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Validation\ValidationException;

class DeviceBinding extends Model
{
    protected $table = 'device_bindings';

    protected $fillable = [
        'user_id',
        'device_uuid',
        'device_model',
        'fingerprint_hash',
        'is_active',
        'bound_at',
    ];

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'is_active' => 'boolean',
            'bound_at' => 'datetime',
        ];
    }

    protected static function booted(): void
    {
        static::saving(function (self $binding): void {
            $user = User::query()->find((int) $binding->user_id);
            if (!$user || !(bool) $user->is_activated) {
                throw ValidationException::withMessages([
                    'device_uuid' => ['Device binding requires an active user account.'],
                ]);
            }

            if (
                $binding->exists
                && $binding->isDirty('is_active')
                && !(bool) $binding->getRawOriginal('is_active')
                && (bool) $binding->is_active
            ) {
                throw ValidationException::withMessages([
                    'device_uuid' => ['A revoked device must be restored through the authorized recovery flow.'],
                ]);
            }
        });
    }

    /**
     * Get the user that owns the device binding.
     */
    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
