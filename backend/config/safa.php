<?php

return [
    // Read environment-backed installation state here so production config caching
    // keeps the value available without calling env() from application runtime.
    'installed' => filter_var(env('APP_INSTALLED', false), FILTER_VALIDATE_BOOL),

    // Tests normally bypass installation/update gating. Feature tests can opt in
    // with Config::set('safa.enforce_update_checks_in_tests', true).
    'enforce_update_checks_in_tests' => false,
];
