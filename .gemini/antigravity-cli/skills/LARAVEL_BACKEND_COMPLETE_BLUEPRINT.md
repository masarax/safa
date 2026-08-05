# Laravel Backend Blueprint
**Project:** `com.safa.account`

Robust backend infrastructure to support the `com.safa.account` Hundi management system, featuring secure authentication, multi-tenancy, and real-time ledger updates.

## 1. Authentication & Security (Sanctum/Passport)
API Authentication relies on Laravel Sanctum for lightweight token-based auth for mobile devices.

```php
// routes/api.php
Route::post('/auth/login', [AuthController::class, 'login']);

Route::middleware(['auth:sanctum', 'throttle:api'])->group(function () {
    Route::post('/sync/push', [SyncController::class, 'push']);
    Route::get('/sync/pull', [SyncController::class, 'pull']);
    Route::get('/ledger/verify', [LedgerController::class, 'verifyIntegrity']);
});
```

## 2. Global Rate Limiting & Throttling
Prevent DDoS and brute force attacks.
```php
// app/Providers/RouteServiceProvider.php
RateLimiter::for('api', function (Request $request) {
    return Limit::perMinute(60)->by($request->user()?->id ?: $request->ip());
});
```

## 3. Multi-Tenant Eloquent Scope
Automatic isolation of tenant data.
```php
// app/Models/Traits/TenantScoped.php
trait TenantScoped {
    protected static function booted() {
        static::addGlobalScope('tenant', function (Builder $builder) {
            if (auth()->hasUser()) {
                $builder->where('tenant_id', auth()->user()->tenant_id);
            }
        });

        static::creating(function ($model) {
            if (auth()->hasUser()) {
                $model->tenant_id = auth()->user()->tenant_id;
            }
        });
    }
}
```

## 4. Real-time WebSockets (Pusher/Laravel Reverb)
Push updates to active clients when a transaction is recorded.

```php
// app/Events/TransactionCreated.php
class TransactionCreated implements ShouldBroadcast {
    use Dispatchable, InteractsWithSockets, SerializesModels;

    public $transaction;

    public function __construct(Transaction $transaction) {
        $this->transaction = $transaction;
    }

    public function broadcastOn() {
        return new PrivateChannel('tenant.'.$this->transaction->tenant_id);
    }
}
```

## 5. Core Database Migrations
```php
Schema::create('transactions', function (Blueprint $table) {
    $table->uuid('id')->primary();
    $table->uuid('tenant_id')->index();
    $table->string('description')->nullable();
    $table->timestamp('transaction_date');
    $table->string('hash_prev')->nullable();
    $table->string('hash_current');
    $table->timestamps();
});

Schema::create('journal_entries', function (Blueprint $table) {
    $table->uuid('id')->primary();
    $table->uuid('transaction_id')->index();
    $table->uuid('account_id')->index();
    $table->decimal('amount', 19, 4); // + Debit, - Credit
    $table->timestamps();
});
```
