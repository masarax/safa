<?php

namespace App\Models\Concerns;

use App\Support\ServerLocalId;
use Illuminate\Database\Eloquent\Model;

trait UsesCollisionSafeLocalId
{
    public static function bootUsesCollisionSafeLocalId(): void
    {
        static::creating(function (Model $model): void {
            $localId = (int) ($model->getAttribute('local_id') ?? 0);

            // Android local-first identities are positive 32-bit integers. Legacy
            // server paths generated millisecond/microsecond clock values far
            // outside that range; normalize only those server-generated values.
            if ($localId <= 0 || $localId > ServerLocalId::MAX_CLIENT_COMPATIBLE_ID) {
                $model->setAttribute('local_id', ServerLocalId::reserve());
            }
        });
    }
}
