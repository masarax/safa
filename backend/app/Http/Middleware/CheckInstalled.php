<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class CheckInstalled
{
    /**
     * Handle an incoming request.
     *
     * @param  \Illuminate\Http\Request  $request
     * @param  \Closure(\Illuminate\Http\Request): (\Symfony\Component\HttpFoundation\Response)  $next
     * @return \Symfony\Component\HttpFoundation\Response
     */
    public function handle(Request $request, Closure $next): Response
    {
        $isInstalled = file_exists(storage_path('installed')) || env('APP_INSTALLED') == true || env('APP_INSTALLED') === 'true';

        if (!$isInstalled) {
            if ($request->is('api/*')) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'System installation required. Please visit /install to complete setup.'
                ], 503);
            }

            if (!$request->expectsJson() && !$request->is('install*')) {
                return redirect('/install');
            }
        }

        return $next($request);
    }
}
