<?php

namespace App\Http\Controllers;

use App\Support\OperationalMetrics;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;

class MobileTelemetryController extends Controller
{
    public function __invoke(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'event_type' => ['required', 'string', Rule::in(['crash', 'anr', 'nonfatal', 'sync_success', 'sync_failure', 'sync_retry', 'auth_refresh_failure'])],
            'release' => ['required', 'string', 'max:48', 'regex:/^[A-Za-z0-9._-]+$/'],
            'endpoint' => ['nullable', 'string', 'max:80', 'regex:/^[A-Za-z0-9_.:\/-]+$/'],
            'reason' => ['nullable', 'string', 'max:64', 'regex:/^[A-Za-z0-9_.:-]+$/'],
            'stack_fingerprint' => ['nullable', 'string', 'max:64', 'regex:/^[a-f0-9]{16,64}$/'],
            'duration_ms' => ['nullable', 'integer', 'min:0', 'max:600000'],
            'bytes' => ['nullable', 'integer', 'min:0', 'max:100000000'],
            'pending_count' => ['nullable', 'integer', 'min:0', 'max:1000000'],
            'oldest_pending_seconds' => ['nullable', 'integer', 'min:0', 'max:31536000'],
            'count' => ['nullable', 'integer', 'min:1', 'max:1000'],
        ]);

        // Store aggregates only. Do not retain arbitrary event bodies, device IDs,
        // account identifiers, exception messages or financial payloads.
        OperationalMetrics::recordMobile($validated);

        return response()->json(['status' => 'accepted'], 202)
            ->header('Cache-Control', 'no-store');
    }
}
