<?php

use App\Models\AuditLog;
use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;

Artisan::command('inspire', function () {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

Artisan::command('audit:prune {--days= : Override configured retention days}', function () {
    $configured = (int) config('safa.audit_retention_days', 90);
    $override = $this->option('days');
    $days = max(1, (int) ($override !== null && $override !== '' ? $override : $configured));
    $cutoff = now()->subDays($days);
    $deleted = AuditLog::query()->where('created_at', '<', $cutoff)->delete();

    $this->info("Pruned {$deleted} audit record(s) older than {$days} day(s).");
})->purpose('Prune minimized audit metadata outside the configured retention window');

Schedule::command('audit:prune')
    ->dailyAt('02:30')
    ->withoutOverlapping();
