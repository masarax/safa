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
];
