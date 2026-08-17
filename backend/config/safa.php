<?php

return [
    'installed' => filter_var(env('APP_INSTALLED', false), FILTER_VALIDATE_BOOL),

    // Temporary compatibility for the current pre-SuperAdmin web recovery path.
    // This maintenance key is removed by the follow-up installer-removal change.
    'maintenance_token' => (string) env('SAFA_MAINTENANCE_TOKEN', env('SAFA_SETUP_TOKEN', '')),

    'enforce_update_checks_in_tests' => false,
];
