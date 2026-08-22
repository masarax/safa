<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;

class BackupHealthController extends Controller
{
    public function __invoke(): JsonResponse
    {
        $full = $this->status(
            (string) config('safa.dr.full_status_file'),
            (int) config('safa.dr.full_max_age_seconds', 93600),
        );
        $binlog = $this->status(
            (string) config('safa.dr.binlog_status_file'),
            (int) config('safa.dr.binlog_max_age_seconds', 900),
        );

        $configured = (bool) config('safa.dr.status_required', false);
        $ready = $full['fresh'] && $binlog['fresh'];
        $healthy = !$configured || $ready;

        return response()->json([
            'status' => $healthy ? 'ok' : 'degraded',
            'configured' => $configured,
            'checks' => [
                'full_backup' => $full['fresh'],
                'binlog_archive' => $binlog['fresh'],
            ],
            'age_seconds' => [
                'full_backup' => $full['age_seconds'],
                'binlog_archive' => $binlog['age_seconds'],
            ],
        ], $healthy ? 200 : 503)->header('Cache-Control', 'no-store');
    }

    /** @return array{fresh: bool, age_seconds: ?int} */
    private function status(string $path, int $maxAgeSeconds): array
    {
        if ($path === '' || $maxAgeSeconds <= 0 || !is_file($path)) {
            return ['fresh' => false, 'age_seconds' => null];
        }

        try {
            $status = json_decode((string) file_get_contents($path), true, 32, JSON_THROW_ON_ERROR);
            $completed = is_array($status) ? (int) ($status['completed_at_epoch'] ?? 0) : 0;
            if ($completed <= 0 || $completed > time() + 300) {
                return ['fresh' => false, 'age_seconds' => null];
            }

            $age = max(0, time() - $completed);
            return ['fresh' => $age <= $maxAgeSeconds, 'age_seconds' => $age];
        } catch (\Throwable) {
            return ['fresh' => false, 'age_seconds' => null];
        }
    }
}
