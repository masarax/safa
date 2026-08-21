<?php

namespace App\Http\Middleware;

use App\Support\FirstRunSetupState;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class UseFirstRunRuntimeStores
{
    public function handle(Request $request, Closure $next): Response
    {
        if (FirstRunSetupState::shouldUseFileRuntimeStores()) {
            // Production normally stores sessions/cache in MySQL. Those tables do
            // not exist before the first migration, so only the first-run window
            // temporarily uses local file stores. Normal requests automatically
            // return to the configured database-backed stores after completion.
            config([
                'session.driver' => 'file',
                'cache.default' => 'file',
            ]);
        }

        return $next($request);
    }
}
