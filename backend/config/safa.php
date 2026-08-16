<?php

return [
    // Read environment-backed installation state here so production config caching
    // keeps the value available without calling env() from application runtime.
    'installed' => filter_var(env('APP_INSTALLED', false), FILTER_VALIDATE_BOOL),

    // Optional one-time/recovery ownership secret for /index. If it is not set,
    // SetupController accepts the already-configured database password instead.
    // The value is never rendered into HTML or committed to source control.
    'setup_token' => (string) env('SAFA_SETUP_TOKEN', ''),

    // Tests normally bypass installation/update gating. Feature tests can opt in
    // with Config::set('safa.enforce_update_checks_in_tests', true).
    'enforce_update_checks_in_tests' => false,
];
