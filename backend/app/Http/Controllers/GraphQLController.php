<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;

/**
 * Retired GraphQL compatibility endpoint.
 *
 * SAFA has one canonical business API: versioned REST under /api/v1. Keeping a
 * second query/mutation implementation caused authorization, deletion, paging
 * and financial-contract drift. The endpoint remains only to return an explicit
 * migration response to old integrations; it performs no business reads/writes.
 */
class GraphQLController extends Controller
{
    public function handle(Request $request)
    {
        return response()->json([
            'errors' => [[
                'message' => 'GraphQL is deprecated. Use the versioned REST API.',
                'extensions' => [
                    'code' => 'GRAPHQL_DEPRECATED',
                    'rest_base' => '/api/v1',
                ],
            ]],
        ], 410);
    }
}
