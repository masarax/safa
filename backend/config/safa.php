<?php

return [
    // Tests normally bypass installation/update gating. Feature tests can opt in
    // with Config::set('safa.enforce_update_checks_in_tests', true).
    'enforce_update_checks_in_tests' => false,
];
