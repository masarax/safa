<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Legacy compatibility middleware.
 *
 * Account activation must only be evaluated after credential verification in
 * the canonical login controller. Looking up activation state here creates an
 * account-existence/status oracle before authentication.
 */
class RejectInactiveLogin
{
    public function handle(Request $request, Closure $next): Response
    {
        return $next($request);
    }
}
