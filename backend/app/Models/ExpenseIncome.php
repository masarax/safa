<?php

namespace App\Models;

use App\Models\Concerns\RecordsSyncChanges;
use App\Models\Concerns\UsesCollisionSafeLocalId;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class ExpenseIncome extends Model
{
    use SoftDeletes, UsesCollisionSafeLocalId, RecordsSyncChanges;

    protected $table = 'expenses_incomes';

    protected $fillable = [
        'account_id', 'local_id', 'title', 'amount', 'currency', 'is_expense',
        'category', 'timestamp', 'deleted_at',
    ];

    protected $casts = [
        'amount' => 'decimal:2',
        'is_expense' => 'boolean',
    ];
}
