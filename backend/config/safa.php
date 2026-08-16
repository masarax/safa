<?php

return [
    'installed' => filter_var(env('APP_INSTALLED', false), FILTER_VALIDATE_BOOL),

    // Recovery writes are allowed only before an activated Super Admin exists.
    // SAFA_SETUP_TOKEN is retained only as a compatibility fallback for servers
    // that already configured the previous release; no DB password/APP_KEY fallback exists.
    'maintenance_token' => (string) env('SAFA_MAINTENANCE_TOKEN', env('SAFA_SETUP_TOKEN', '')),

    // Initial identity is server-managed and consumed only when no Super Admin exists.
    // Re-running the seeder never overwrites an existing administrator credential.
    'initial_admin' => [
        'name' => (string) env('SAFA_INITIAL_ADMIN_NAME', ''),
        'mobile' => (string) env('SAFA_INITIAL_ADMIN_MOBILE', ''),
        'email' => (string) env('SAFA_INITIAL_ADMIN_EMAIL', ''),
        'pin' => (string) env('SAFA_INITIAL_ADMIN_PIN', ''),
    ],

    'enforce_update_checks_in_tests' => false,
];
