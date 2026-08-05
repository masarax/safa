<?php

namespace App\Providers;

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
        // API rate limiting: 60 requests/minute per API key, fallback to IP
        RateLimiter::for('api', function (Request $request) {
            $key = $request->header('X-SAFA-API-KEY') ?? $request->ip();
            return Limit::perMinute(60)->by($key);
        });
    }
}
