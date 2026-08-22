<?php

namespace Tests\Feature;

use App\Http\Controllers\AuthJWTController;
use App\Http\Controllers\SecureAuthController;
use App\Http\Middleware\VerifyMultiLevelToken;
use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class Issue238RefreshTokenExposureTest extends TestCase
{
    use RefreshDatabase;

    public function test_normal_business_authorization_does_not_require_refresh_token(): void
    {
        [$user, $access, $refresh, $session, $device, $fingerprint] = $this->sessionFixture();

        $request = Request::create('/api/customers', 'GET');
        $request->headers->set('Authorization', 'Bearer ' . $access);
        $request->headers->set('X-SAFA-DEVICE-TOKEN', $device);
        $request->headers->set('X-SAFA-SESSION-TOKEN', $session);
        $request->headers->set('X-SAFA-FINGERPRINT-TOKEN', $fingerprint);

        $response = app(VerifyMultiLevelToken::class)->handle(
            $request,
            fn () => response()->json(['user_id' => $request->user()?->id])
        );

        $this->assertSame(200, $response->getStatusCode());
        $this->assertSame($user->id, $response->getData(true)['user_id']);

        // A stale/forged legacy refresh header is ignored on normal routes;
        // access/session/device revocation remains authoritative.
        $request->headers->set('X-SAFA-REFRESH-TOKEN', $refresh . '-stale');
        $response = app(VerifyMultiLevelToken::class)->handle(
            $request,
            fn () => response()->json(['ok' => true])
        );
        $this->assertSame(200, $response->getStatusCode());
    }

    public function test_revoked_or_wrong_session_remains_rejected_without_refresh_header(): void
    {
        [, $access, , $session, $device, $fingerprint, $authSession] = $this->sessionFixture(true);

        $wrongSessionRequest = $this->businessRequest($access, $device, $session . '-wrong', $fingerprint);
        $this->assertSame(
            401,
            app(VerifyMultiLevelToken::class)->handle($wrongSessionRequest, fn () => response('unexpected'))->getStatusCode()
        );

        $authSession->update(['is_revoked' => true]);
        $revokedRequest = $this->businessRequest($access, $device, $session, $fingerprint);
        $this->assertSame(
            401,
            app(VerifyMultiLevelToken::class)->handle($revokedRequest, fn () => response('unexpected'))->getStatusCode()
        );
    }

    public function test_refresh_endpoint_still_requires_and_rotates_refresh_credential(): void
    {
        [, , $refresh, , $device, $fingerprint] = $this->sessionFixture();
        $controller = app(SecureAuthController::class);

        $missing = Request::create('/api/auth/refresh', 'POST', [
            'device_token' => $device,
            'fingerprint_token' => $fingerprint,
        ]);
        $this->assertSame(400, $controller->refresh($missing)->getStatusCode());

        $request = Request::create('/api/auth/refresh', 'POST', [
            'refresh_token' => $refresh,
            'device_token' => $device,
            'fingerprint_token' => $fingerprint,
        ]);
        $response = $controller->refresh($request);
        $body = $response->getData(true);

        $this->assertSame(200, $response->getStatusCode());
        $this->assertNotSame($refresh, $body['tokens']['refresh_token']);
        $this->assertNotEmpty($body['tokens']['access_token']);
    }

    private function businessRequest(string $access, string $device, string $session, string $fingerprint): Request
    {
        $request = Request::create('/api/transactions', 'GET');
        $request->headers->set('Authorization', 'Bearer ' . $access);
        $request->headers->set('X-SAFA-DEVICE-TOKEN', $device);
        $request->headers->set('X-SAFA-SESSION-TOKEN', $session);
        $request->headers->set('X-SAFA-FINGERPRINT-TOKEN', $fingerprint);
        return $request;
    }

    /** @return array<int, mixed> */
    private function sessionFixture(bool $returnModel = false): array
    {
        $user = User::factory()->create(['is_activated' => true]);
        $device = 'DEVICE-' . $user->id;
        $fingerprint = 'FP-' . $user->id;
        $sessionToken = AuthSession::newOpaqueToken();
        $refreshToken = AuthSession::newOpaqueToken();
        $accessToken = AuthJWTController::generateJwt([
            'iss' => config('app.url', 'safa-backend'),
            'sub' => $user->id,
            'user_id' => $user->id,
            'device_uuid' => $device,
            'session_token' => $sessionToken,
            'iat' => time(),
            'exp' => time() + 3600,
        ]);

        DeviceBinding::create([
            'user_id' => $user->id,
            'device_uuid' => $device,
            'device_model' => 'Test Device',
            'fingerprint_hash' => $fingerprint,
            'is_active' => true,
            'bound_at' => now(),
        ]);
        $authSession = AuthSession::create([
            'user_id' => $user->id,
            'device_uuid' => $device,
            'access_token' => $accessToken,
            'refresh_token' => $refreshToken,
            'session_token' => $sessionToken,
            'expires_at' => now()->addDay(),
            'is_revoked' => false,
        ]);

        $fixture = [$user, $accessToken, $refreshToken, $sessionToken, $device, $fingerprint];
        if ($returnModel) $fixture[] = $authSession;
        return $fixture;
    }
}
