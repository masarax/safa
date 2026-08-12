<?php

namespace App\Http\Middleware;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Symfony\Component\HttpFoundation\Response;

class VerifyRefreshRequest
{
    public function handle(Request $request, Closure $next): Response
    {
        $refreshToken = trim((string) ($request->input('refresh_token') ?? $request->header('X-SAFA-REFRESH-TOKEN')));
        $deviceUuid = trim((string) ($request->input('device_token') ?? $request->input('device_uuid') ?? $request->header('X-SAFA-DEVICE-TOKEN')));
        $fingerprint = trim((string) ($request->input('fingerprint_token') ?? $request->input('fingerprint_hash') ?? $request->header('X-SAFA-FINGERPRINT-TOKEN')));
        if ($refreshToken === '' || $deviceUuid === '' || $fingerprint === '') return response()->json(['status' => 'error', 'message' => 'Missing refresh security credentials.'], 400);

        $session = AuthSession::query()->where('refresh_token_hash', AuthSession::tokenHash($refreshToken))->where('device_uuid', $deviceUuid)->where('is_revoked', false)->first();
        if (!$session || ($session->expires_at && $session->expires_at->isPast())) return response()->json(['status' => 'error', 'message' => 'Invalid or expired refresh session.'], 401);

        $user = User::find($session->user_id);
        if (!$user || !$user->is_activated) return response()->json(['status' => 'error', 'message' => 'User account is inactive or unavailable.'], 401);

        $binding = DeviceBinding::query()->where('user_id', $user->id)->where('device_uuid', $deviceUuid)->where('is_active', true)->first();
        if (!$binding || !hash_equals((string) $binding->fingerprint_hash, $fingerprint)) return response()->json(['status' => 'error', 'message' => 'Device fingerprint verification failed.'], 403);

        $rotatedRefreshToken = Str::random(64);
        DB::transaction(function () use ($session, $rotatedRefreshToken): void {
            $locked = AuthSession::query()->whereKey($session->id)->where('is_revoked', false)->lockForUpdate()->first();
            if (!$locked) abort(401, 'Refresh session is no longer valid.');
            $locked->refresh_token = $rotatedRefreshToken;
            $locked->save();
        });
        $request->merge(['refresh_token' => $rotatedRefreshToken]);
        return $next($request);
    }
}
