<?php

namespace Tests\Feature;

use Tests\TestCase;

class WebFlowEventContractTest extends TestCase
{
    public function test_standalone_due_and_advance_cancel_is_normalized_before_main_flow_listener(): void
    {
        $guard = (string) file_get_contents(public_path('safa-web-events.js'));
        $routes = (string) file_get_contents(base_path('routes/web.php'));

        $this->assertStringContainsString('button[data-flow-back]', $guard);
        $this->assertStringContainsString('input[name="adjustment_amount"]', $guard);
        $this->assertStringContainsString("removeAttribute('data-flow-back')", $guard);
        $this->assertStringContainsString("setAttribute('data-flow-cancel'", $guard);
        $this->assertStringContainsString('$guard . "\\n" . $runtime', $routes);
    }
}
