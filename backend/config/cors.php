<?php

return [

    'paths' => ['api/*', 'sanctum/csrf-cookie'],

    // Keep the browser surface explicit. CORS is not an authentication layer,
    // but wildcard methods unnecessarily widen the cross-origin attack surface.
    'allowed_methods' => ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],

    'allowed_origins' => array_values(array_filter(array_map('trim', explode(',', env('CORS_ALLOWED_ORIGINS', 'https://safa.masarax.com'))))),

    'allowed_origins_patterns' => [],

    'allowed_headers' => [
        'Accept',
        'Content-Type',
        'X-Requested-With',
        'Authorization',
        'X-SAFA-API-KEY',
        'X-SAFA-SIGNATURE',
        'X-SAFA-TIMESTAMP',
        'X-SAFA-NONCE',
    ],

    'exposed_headers' => [],
    'max_age' => 86400,
    'supports_credentials' => true,

];
