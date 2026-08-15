<?php

use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;

use App\Http\Middleware\CheckInstalled;
use App\Http\Middleware\EnsureNotInstalled;
use App\Http\Middleware\SecurityHeaders;
use App\Http\Middleware\VerifyMultiLevelToken;
use App\Http\Middleware\VerifyActiveAuthSession;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
    )
    ->withMiddleware(function (Middleware $middleware): void {
        $middleware->append(SecurityHeaders::class);
        $middleware->redirectGuestsTo('/login');

        $middleware->alias([
            'check.installed' => CheckInstalled::class,
            'ensure.not.installed' => EnsureNotInstalled::class,
            'security.headers' => SecurityHeaders::class,
            'verify.multilevel.token' => VerifyMultiLevelToken::class,
            'verify.active.session' => VerifyActiveAuthSession::class,
        ]);

        $middleware->appendToGroup('web', CheckInstalled::class);
        $middleware->appendToGroup('api', CheckInstalled::class);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->is('app/api/*'),
        );
    })->create();
