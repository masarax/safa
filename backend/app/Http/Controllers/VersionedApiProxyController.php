<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;

/**
 * Backward-compatible API version bridge. /api/v1/* is the canonical mobile
 * contract while the existing /api/* routes remain available during migration.
 */
class VersionedApiProxyController extends Controller
{
    public function __invoke(Request $request, ?string $path = null)
    {
        $path = trim((string) $path, '/');
        abort_if($path === '' || str_starts_with($path, 'v1/'), 404);

        $server = $request->server->all();
        $server['REQUEST_URI'] = '/api/' . $path;
        $server['PATH_INFO'] = '/api/' . $path;
        $subRequest = Request::create(
            '/api/' . $path,
            $request->method(),
            $request->all(),
            $request->cookies->all(),
            $request->allFiles(),
            $server,
            $request->getContent()
        );
        $subRequest->headers->replace($request->headers->all());
        $subRequest->setUserResolver($request->getUserResolver());

        return app()->handle($subRequest);
    }
}
