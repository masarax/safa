<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class ExpenseIncome extends Model
{
    protected $table = 'expenses_incomes';

    protected $fillable = [
        'account_id',
        'local_id',
        'title',
        'amount',
        'currency',
        'is_expense',
        'category',
        'timestamp',
    ];
}
