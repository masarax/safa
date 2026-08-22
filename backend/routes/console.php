<?php

use App\Models\AuditLog;
use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;

Artisan::command('inspire', function () {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

Artisan::command('safa:audit-prune', function () {
    $days = max(30, min(3650, (int) config('safa.audit_retention_days', 180)));
    $deleted = AuditLog::query()
        ->where('created_at', '<', now()->subDays($days))
        ->delete();

    $this->info("Pruned {$deleted} audit records older than {$days} days.");
})->purpose('Prune SAFA audit evidence beyond the configured retention window');

Schedule::command('safa:audit-prune')
    ->dailyAt('02:30')
    ->withoutOverlapping();
