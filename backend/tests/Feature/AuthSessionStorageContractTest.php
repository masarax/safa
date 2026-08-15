<?php

namespace Tests\Feature;

use App\Models\AuthSession;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Str;
use Tests\TestCase;

class AuthSessionStorageContractTest extends TestCase
{
    use RefreshDatabase;

    public function test_encrypted_refresh_and_session_tokens_use_unbounded_text_columns(): void
    {
        $user = User::factory()->create();

        AuthSession::create([
            'user_id' => $user->id,
            'device_uuid' => 'storage-contract-device',
            'access_token' => str_repeat('a', 320),
            'refresh_token' => Str::random(64),
            'session_token' => Str::random(64),
            'expires_at' => now()->addDay(),
            'is_revoked' => false,
        ]);

        $raw = DB::table('auth_sessions')->where('user_id', $user->id)->first();

        $this->assertNotNull($raw);
        $this->assertGreaterThan(255, strlen((string) $raw->refresh_token));
        $this->assertGreaterThan(255, strlen((string) $raw->session_token));
        $this->assertSame('text', Schema::getColumnType('auth_sessions', 'refresh_token'));
        $this->assertSame('text', Schema::getColumnType('auth_sessions', 'session_token'));
    }
}
