<?php

use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

use App\Http\Middleware\CheckInstalled;
use App\Http\Middleware\SecurityHeaders;
use App\Http\Middleware\UseFirstRunRuntimeStores;
use App\Http\Middleware\VerifyMultiLevelToken;
use App\Http\Middleware\VerifyActiveAuthSession;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        then: function (): void {
            Route::middleware('web')->group(base_path('routes/setup.php'));
        },
    )
    ->withMiddleware(function (Middleware $middleware): void {
        $middleware->append(SecurityHeaders::class);
        $middleware->redirectGuestsTo('/login');

        $middleware->alias([
            'check.installed' => CheckInstalled::class,
            'security.headers' => SecurityHeaders::class,
            'verify.multilevel.token' => VerifyMultiLevelToken::class,
            'verify.active.session' => VerifyActiveAuthSession::class,
        ]);

        // The configured production stores are database-backed. Before the first
        // migration those tables do not exist, so the first middleware switches
        // only the one-time setup window to file-backed session/cache stores.
        $middleware->prependToGroup('web', UseFirstRunRuntimeStores::class);
        $middleware->prependToGroup('api', UseFirstRunRuntimeStores::class);
        $middleware->appendToGroup('web', CheckInstalled::class);
        $middleware->appendToGroup('api', CheckInstalled::class);
    })
    ->withExceptions(function (Exceptions $exceptions): void {
        $exceptions->shouldRenderJsonWhen(
            fn (Request $request) => $request->is('api/*') || $request->is('app/api/*'),
        );
    })->create();
