<?php

namespace App\Providers;

use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use App\Observers\SyncChangeObserver;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        //
    }

    public function boot(): void
    {
        foreach ([
            Customer::class,
            Supplier::class,
            WalletLedger::class,
            SupplierDeposit::class,
            WalletBatch::class,
            Transaction::class,
            ExpenseIncome::class,
        ] as $model) {
            $model::observe(SyncChangeObserver::class);
        }

        // Canonical API policy: 60 requests/minute per authenticated user/session
        // or device. The Android client API key is intentionally only one part of
        // the key because it is shared by all installations of the public client.
        RateLimiter::for('api', function (Request $request) {
            $apiKey = (string) ($request->header('X-SAFA-API-KEY') ?: 'no-client-key');
            $identity = $request->user()?->getAuthIdentifier()
                ?: $request->header('X-SAFA-SESSION-TOKEN')
                ?: $request->header('X-SAFA-DEVICE-TOKEN')
                ?: ($request->bearerToken() ? hash('sha256', $request->bearerToken()) : null)
                ?: $request->ip();

            return Limit::perMinute(60)->by(hash('sha256', $apiKey . '|' . (string) $identity));
        });
    }
}
