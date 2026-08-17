<?php

return [
    'installed' => filter_var(env('APP_INSTALLED', false), FILTER_VALIDATE_BOOL),

    'enforce_update_checks_in_tests' => false,
];
