<?php

namespace App\Http\Middleware;

use App\Models\OperatorAccount;
use App\Models\User;
use App\Support\MobileNumber;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Prevents credential login from guessing when the same canonical mobile
 * identifies more than one account across the current and legacy models.
 *
 * A legacy operator linked to the exact same User is one identity and is
 * therefore allowed. Unlinked or differently-linked records are ambiguous
 * and must be resolved administratively before authentication can continue.
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
                ? "user:" . (int) $operator->user_id
                : "legacy:" . (int) $operator->id;
        }

        $identityIds = array_values(array_unique($identityIds));

        if (count($identityIds) > 1) {
            return response()->json([
                'status' => 'error',
                'message' => 'Multiple accounts match this mobile number. Please contact an administrator.',
            ], 409);
        }

        return $next($request);
    }
}
