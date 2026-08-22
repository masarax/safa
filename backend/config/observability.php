<?php

return [
    // Required for /api/ops/* endpoints. Keep this secret outside Git and rotate
    // it independently from public mobile API client identifiers.
    'ops_key' => env('SAFA_OPS_METRICS_KEY', ''),

    // Dedicated non-financial account used only inside a rollback-only database
    // transaction by the persistence synthetic. It must not be a customer account.
    'synthetic_account_id' => (int) env('SAFA_SYNTHETIC_ACCOUNT_ID', 0),
];
