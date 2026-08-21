<?php

return [
    'installed' => filter_var(env('APP_INSTALLED', false), FILTER_VALIDATE_BOOL),

    'enforce_update_checks_in_tests' => false,

    // Public Android client identifier. This is not an authentication secret;
    // protected API routes still require a valid user JWT and active session.
    'mobile_client_key' => env('SAFA_API_KEY', 'safa_key_public_client_id'),
    'canonical_mobile_client_key' => 'safa_key_public_client_id',
];
