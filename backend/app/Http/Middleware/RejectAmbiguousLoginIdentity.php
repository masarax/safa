<?php

namespace App\Http\Middleware;

use App\Models\OperatorAccount;
use App\Models\User;
use App\Support\MobileNumber;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Detect ambiguous canonical/legacy identity without exposing it publicly.
 * The login controller consumes the request attribute and returns the same
 * generic credential failure used for unknown, inactive and wrong credentials.
 */
class RejectAmbiguousLoginIdentity
{
    public function handle(Request $request, Closure $next): Response
    {
        $identifier = MobileNumber::normalize((string) (
            $request->input('mobile')
            ?? $request->input('email')
            ?? $request->input('username')
        ));

        if ($identifier === '') {
            return $next($request);
        }

        $userIds = User::query()
            ->where('mobile', $identifier)
            ->pluck('id')
            ->map(fn ($id) => (int) $id)
            ->unique()
            ->values();

        $legacyOperators = OperatorAccount::query()->get()->filter(
            fn (OperatorAccount $operator): bool => MobileNumber::normalize((string) $operator->mobile) === $identifier
        );

        $identityIds = $userIds->map(fn (int $id) => "user:$id")->all();
        foreach ($legacyOperators as $operator) {
            $identityIds[] = $operator->user_id
                ? 'user:' . (int) $operator->user_id
                : 'legacy:' . (int) $operator->id;
        }

        if (count(array_unique($identityIds)) > 1) {
            $request->attributes->set('safa_login_identity_ambiguous', true);
        }

        return $next($request);
    }
}
