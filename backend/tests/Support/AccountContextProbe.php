<?php

namespace Tests\Support;

use App\Http\Controllers\AuthorizeAccountContext;
use Illuminate\Http\Request;

final class AccountContextProbe
{
    use AuthorizeAccountContext;

    public function resolve(Request $request): array
    {
        return $this->resolveAuthorizedAccountContext($request);
    }
}
