<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;

/** Backward-compatible /api/v1 bridge over the existing Laravel business controllers. */
class VersionedApiProxyController extends Controller
{
    public function __invoke(Request $request, ?string $path = null)
    {
        $path = trim((string) $path, '/');
        abort_if($path === '' || str_starts_with($path, 'v1/'), 404);

        // Sync-down is the high-volume endpoint and therefore gets the bounded
        // page implementation instead of the legacy unbounded collection.
        if ($request->isMethod('GET') && $path === 'sync/down') {
            return app(SyncPageController::class)($request);
        }

        $server = $request->server->all();
        $server['REQUEST_URI'] = '/api/' . $path;
        $server['PATH_INFO'] = '/api/' . $path;
        $subRequest = Request::create('/api/' . $path, $request->method(), $request->all(), $request->cookies->all(), $request->allFiles(), $server, $request->getContent());
        $subRequest->headers->replace($request->headers->all());
        $subRequest->setUserResolver($request->getUserResolver());
        return app()->handle($subRequest);
    }
}
