<?php

namespace App\Support;

use Illuminate\Support\Facades\DB;

final class OperationalMetrics
{
    private const LATENCY_BUCKETS_MS = [10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000];
    private const MOBILE_EVENTS = ['crash', 'anr', 'nonfatal', 'sync_success', 'sync_failure', 'sync_retry', 'auth_refresh_failure'];

    public static function recordRequest(string $route, int $status, float $durationMs): void
    {
        self::mutate(function (array &$state) use ($route, $status, $durationMs): void {
            $route = self::safeDimension($route, 120, 'unknown');
            $key = hash('sha256', $route);
            $entry = $state['requests'][$key] ?? [
                'route' => $route,
                'count' => 0,
                'errors' => 0,
                'status_classes' => [],
                'latency_buckets_ms' => array_fill_keys(array_map('strval', self::LATENCY_BUCKETS_MS), 0),
                'latency_overflow' => 0,
            ];
            $entry['count']++;
            if ($status >= 500) $entry['errors']++;
            $class = intdiv(max(100, min(599, $status)), 100) . 'xx';
            $entry['status_classes'][$class] = ($entry['status_classes'][$class] ?? 0) + 1;
            $bucketed = false;
            foreach (self::LATENCY_BUCKETS_MS as $bucket) {
                if ($durationMs <= $bucket) {
                    $entry['latency_buckets_ms'][(string) $bucket]++;
                    $bucketed = true;
                    break;
                }
            }
            if (!$bucketed) $entry['latency_overflow']++;
            $state['requests'][$key] = $entry;
            $state['request_total'] = ($state['request_total'] ?? 0) + 1;
            if ($status >= 500) $state['request_errors'] = ($state['request_errors'] ?? 0) + 1;
        });
    }

    public static function recordMobile(array $event): void
    {
        $type = (string) ($event['event_type'] ?? '');
        if (!in_array($type, self::MOBILE_EVENTS, true)) return;
        $release = self::safeDimension((string) ($event['release'] ?? 'unknown'), 48, 'unknown');
        $endpoint = self::safeDimension((string) ($event['endpoint'] ?? 'none'), 80, 'none');
        $reason = self::safeDimension((string) ($event['reason'] ?? 'none'), 64, 'none');

        self::mutate(function (array &$state) use ($type, $release, $endpoint, $reason, $event): void {
            $key = hash('sha256', implode('|', [$type, $release, $endpoint, $reason]));
            $entry = $state['mobile'][$key] ?? [
                'event_type' => $type,
                'release' => $release,
                'endpoint' => $endpoint,
                'reason' => $reason,
                'count' => 0,
                'duration_ms_total' => 0,
                'bytes_total' => 0,
                'pending_count_max' => 0,
                'oldest_pending_seconds_max' => 0,
            ];
            $entry['count'] += max(1, min(1000, (int) ($event['count'] ?? 1)));
            $entry['duration_ms_total'] += max(0, min(600000, (int) ($event['duration_ms'] ?? 0)));
            $entry['bytes_total'] += max(0, min(100000000, (int) ($event['bytes'] ?? 0)));
            $entry['pending_count_max'] = max($entry['pending_count_max'], max(0, min(1000000, (int) ($event['pending_count'] ?? 0))));
            $entry['oldest_pending_seconds_max'] = max($entry['oldest_pending_seconds_max'], max(0, min(31536000, (int) ($event['oldest_pending_seconds'] ?? 0))));
            $state['mobile'][$key] = $entry;
            $state['mobile_total'] = ($state['mobile_total'] ?? 0) + 1;
        });
    }

    public static function snapshot(): array
    {
        $state = self::read();
        foreach (($state['requests'] ?? []) as $key => $entry) {
            $state['requests'][$key]['latency_ms'] = [
                'p50' => self::quantile($entry, 0.50),
                'p95' => self::quantile($entry, 0.95),
                'p99' => self::quantile($entry, 0.99),
            ];
        }
        $state['database'] = self::databaseProbe();
        $state['generated_at'] = now()->toIso8601String();
        return $state;
    }

    public static function resetForTests(): void
    {
        if (!app()->environment('testing')) return;
        @unlink(self::path());
    }

    private static function databaseProbe(): array
    {
        $started = hrtime(true);
        try {
            DB::select('SELECT 1');
            $result = [
                'healthy' => true,
                'probe_latency_ms' => round((hrtime(true) - $started) / 1_000_000, 3),
                'driver' => DB::connection()->getDriverName(),
            ];
            if ($result['driver'] === 'mysql') {
                $statusRows = DB::select("SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Max_used_connections')");
                $variableRows = DB::select("SHOW VARIABLES WHERE Variable_name = 'max_connections'");
                $status = [];
                foreach ($statusRows as $row) $status[(string) $row->Variable_name] = (int) $row->Value;
                $maxConnections = isset($variableRows[0]) ? (int) $variableRows[0]->Value : 0;
                $connected = (int) ($status['Threads_connected'] ?? 0);
                $result += [
                    'threads_connected' => $connected,
                    'threads_running' => (int) ($status['Threads_running'] ?? 0),
                    'max_used_connections' => (int) ($status['Max_used_connections'] ?? 0),
                    'max_connections' => $maxConnections,
                    'connection_saturation' => $maxConnections > 0 ? round($connected / $maxConnections, 4) : null,
                ];
            }
            return $result;
        } catch (\Throwable) {
            return [
                'healthy' => false,
                'probe_latency_ms' => round((hrtime(true) - $started) / 1_000_000, 3),
                'driver' => null,
                'connection_saturation' => null,
            ];
        }
    }

    private static function quantile(array $entry, float $quantile): ?int
    {
        $count = (int) ($entry['count'] ?? 0);
        if ($count <= 0) return null;
        $target = max(1, (int) ceil($count * $quantile));
        $seen = 0;
        foreach (self::LATENCY_BUCKETS_MS as $bucket) {
            $seen += (int) (($entry['latency_buckets_ms'] ?? [])[(string) $bucket] ?? 0);
            if ($seen >= $target) return $bucket;
        }
        return 10001;
    }

    private static function safeDimension(string $value, int $max, string $fallback): string
    {
        $value = trim($value);
        if ($value === '' || preg_match('/^[A-Za-z0-9_\.\/:{}-]+$/', $value) !== 1) return $fallback;
        return substr($value, 0, $max);
    }

    private static function mutate(callable $mutator): void
    {
        try {
            $path = self::path();
            $directory = dirname($path);
            if (!is_dir($directory)) @mkdir($directory, 0700, true);
            $handle = @fopen($path, 'c+');
            if ($handle === false) return;
            try {
                if (!flock($handle, LOCK_EX)) return;
                rewind($handle);
                $decoded = json_decode((string) stream_get_contents($handle), true);
                $state = is_array($decoded) ? $decoded : ['version' => 1, 'requests' => [], 'mobile' => []];
                $mutator($state);
                $state['version'] = 1;
                $state['updated_at'] = gmdate(DATE_ATOM);
                rewind($handle);
                ftruncate($handle, 0);
                fwrite($handle, json_encode($state, JSON_UNESCAPED_SLASHES | JSON_INVALID_UTF8_SUBSTITUTE));
                fflush($handle);
                flock($handle, LOCK_UN);
            } finally {
                fclose($handle);
            }
        } catch (\Throwable) {
            // Observability must never fail a business request.
        }
    }

    private static function read(): array
    {
        try {
            $path = self::path();
            if (!is_file($path)) return ['version' => 1, 'requests' => [], 'mobile' => []];
            $handle = @fopen($path, 'r');
            if ($handle === false) return ['version' => 1, 'requests' => [], 'mobile' => []];
            try {
                if (!flock($handle, LOCK_SH)) return ['version' => 1, 'requests' => [], 'mobile' => []];
                $decoded = json_decode((string) stream_get_contents($handle), true);
                flock($handle, LOCK_UN);
                return is_array($decoded) ? $decoded : ['version' => 1, 'requests' => [], 'mobile' => []];
            } finally {
                fclose($handle);
            }
        } catch (\Throwable) {
            return ['version' => 1, 'requests' => [], 'mobile' => []];
        }
    }

    private static function path(): string
    {
        return storage_path('app/observability/metrics.json');
    }
}
