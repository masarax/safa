<?php

namespace Tests\Feature;

use App\Http\Middleware\AuditLogMiddleware;
use App\Models\AuditLog;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class AuditLogPrivacyTest extends TestCase
{
    use RefreshDatabase;

    public function test_mutation_audit_never_copies_raw_business_pii_credentials_or_upload_payloads(): void
    {
        $request = Request::create('/api/v1/transactions/123', 'PATCH', [
            'id' => 123,
            'local_id' => 456,
            'amount_sar' => '100.00',
            'receiver_name' => 'Sensitive Person',
            'receiver_phone' => '+966500000000',
            'receiver_account_no' => 'SA1234567890',
            'notes' => 'private business note',
            'pin' => '946281',
            'refresh_token' => 'refresh-secret',
            'logo_base64' => str_repeat('A', 1000),
            '_sync' => [
                'mutation_id' => 'mutation-12345678',
                'operation' => 'UPDATE',
                'secret_token' => 'nested-secret',
            ],
        ], [], [], [
            'REMOTE_ADDR' => '203.0.113.47',
            'HTTP_X_SAFA_REQUEST_ID' => 'request-12345678',
        ]);

        $response = (new AuditLogMiddleware())->handle(
            $request,
            fn () => response()->json(['status' => 'success'], 200)
        );

        $this->assertSame('request-12345678', $response->headers->get('X-SAFA-Request-ID'));

        $audit = AuditLog::query()->sole();
        $json = json_encode($audit->payload, JSON_THROW_ON_ERROR);

        $this->assertSame('203.0.113.0', $audit->ip_address);
        $this->assertSame(200, $audit->payload['result_status']);
        $this->assertSame('request-12345678', $audit->payload['correlation_id']);
        $this->assertSame(456, $audit->payload['local_id']);
        $this->assertSame('mutation-12345678', $audit->payload['mutation_id']);
        $this->assertSame('UPDATE', $audit->payload['operation']);

        foreach ([
            'Sensitive Person', '+966500000000', 'SA1234567890', 'private business note',
            '946281', 'refresh-secret', 'nested-secret', str_repeat('A', 100),
        ] as $forbiddenValue) {
            $this->assertStringNotContainsString($forbiddenValue, $json);
        }

        $this->assertNotContains('pin', $audit->payload['changed_fields']);
        $this->assertNotContains('refresh_token', $audit->payload['changed_fields']);
        $this->assertNotContains('logo_base64', $audit->payload['changed_fields']);
        $this->assertContains('amount_sar', $audit->payload['changed_fields']);
        $this->assertContains('receiver_name', $audit->payload['changed_fields']);
    }

    public function test_audit_prune_command_respects_configurable_retention_window(): void
    {
        $old = AuditLog::create([
            'action' => 'POST',
            'endpoint' => 'api/v1/customers',
            'payload' => ['resource' => 'customers', 'result_status' => 200],
        ]);
        $recent = AuditLog::create([
            'action' => 'PATCH',
            'endpoint' => 'api/v1/customers/1',
            'payload' => ['resource' => 'customers', 'result_status' => 200],
        ]);

        DB::table('audit_logs')->where('id', $old->id)->update([
            'created_at' => now()->subDays(31),
            'updated_at' => now()->subDays(31),
        ]);
        DB::table('audit_logs')->where('id', $recent->id)->update([
            'created_at' => now()->subDays(29),
            'updated_at' => now()->subDays(29),
        ]);

        $this->artisan('audit:prune', ['--days' => 30])->assertExitCode(0);

        $this->assertSame(1, AuditLog::query()->count());
        $this->assertDatabaseMissing('audit_logs', ['id' => $old->id]);
        $this->assertDatabaseHas('audit_logs', ['id' => $recent->id]);
    }
}
