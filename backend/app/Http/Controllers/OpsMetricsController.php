<?php

namespace App\Http\Controllers;

use App\Support\OperationalMetrics;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class OpsMetricsController extends Controller
{
    public function __invoke(Request $request): JsonResponse
    {
        $expected = trim((string) config('observability.ops_key', ''));
        $provided = trim((string) $request->header('X-SAFA-OPS-KEY', ''));
        if ($expected === '' || $provided === '' || !hash_equals($expected, $provided)) {
            abort(404);
        }

        return response()->json(OperationalMetrics::snapshot())
            ->header('Cache-Control', 'no-store');
    }
}
