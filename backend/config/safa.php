<?php

return [
    'installed' => filter_var(env('APP_INSTALLED', false), FILTER_VALIDATE_BOOL),

    'enforce_update_checks_in_tests' => false,

    // Public Android client identifier. This is not an authentication secret;
    // protected API routes still require a valid user JWT and active session.
    'mobile_client_key' => env('SAFA_API_KEY', 'safa_key_public_client_id'),
    'canonical_mobile_client_key' => 'safa_key_public_client_id',

    // Operational audit evidence is deliberately short-lived and contains only
    // minimized event metadata. Operators can increase/decrease this value to
    // meet their documented legal/product retention requirement.
    'audit_retention_days' => max(1, (int) env('SAFA_AUDIT_RETENTION_DAYS', 90)),

    // Backup workers write small local heartbeat files only after encrypted
    // off-host artifacts have passed checksum verification. The public
    // backup-health endpoint exposes freshness only; paths and credentials are
    // never returned. Enable status_required after production cron jobs are live.
    'dr' => [
        'status_required' => filter_var(env('SAFA_BACKUP_STATUS_REQUIRED', false), FILTER_VALIDATE_BOOL),
        'full_status_file' => env('SAFA_FULL_BACKUP_STATUS_FILE', storage_path('app/dr/latest-full.json')),
        'binlog_status_file' => env('SAFA_BINLOG_BACKUP_STATUS_FILE', storage_path('app/dr/latest-binlog.json')),
        'full_max_age_seconds' => max(3600, (int) env('SAFA_FULL_BACKUP_MAX_AGE_SECONDS', 93600)),
        'binlog_max_age_seconds' => max(300, (int) env('SAFA_BINLOG_BACKUP_MAX_AGE_SECONDS', 900)),
    ],
];
