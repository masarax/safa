<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Customer;
use Illuminate\Support\Facades\Validator;

class CustomerController extends Controller
{
    use AuthorizeAccountContext;

    public function index(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $validator = Validator::make($request->query(), [
            'page' => 'sometimes|integer|min:1|max:1000000',
            'per_page' => 'sometimes|integer|min:1|max:200',
        ]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'message' => 'Invalid pagination parameters.', 'errors' => $validator->errors()], 422);

        $page = (int) $request->query('page', 1);
        $perPage = (int) $request->query('per_page', 50);
        $paginator = Customer::where('account_id', $accountId)
            ->whereNull('deleted_at')
            ->orderBy('name', 'asc')
            ->orderBy('id', 'asc')
            ->paginate($perPage, ['*'], 'page', $page);

        return response()->json([
            'status' => 'success',
            'customers' => $paginator->items(),
            'pagination' => [
                'current_page' => $paginator->currentPage(),
                'per_page' => $paginator->perPage(),
                'last_page' => $paginator->lastPage(),
                'total' => $paginator->total(),
                'has_more' => $paginator->hasMorePages(),
            ],
        ]);
    }

    public function store(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $validator = Validator::make($request->all(), [
            'name' => 'required|string|max:255',
            'phone' => 'nullable|string|max:50',
            'avatar_color' => 'nullable|string|max:20',
            'avatar_emoji' => 'nullable|string|max:16',
            'address' => 'nullable|string|max:500',
            'local_id' => 'nullable|integer|min:1',
            'timestamp' => 'nullable|integer|min:1',
        ]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);

        $localId = (int) ($request->input('local_id') ?? 0);
        $lookupLocalId = $localId > 0 ? $localId : (int) floor(microtime(true) * 1000);
        $timestamp = (int) ($request->input('timestamp') ?? time());
        if ($timestamp > 2000000000) $timestamp = (int) floor($timestamp / 1000);
        if ($timestamp <= 0 || $timestamp > time() + 86400) $timestamp = time();

        $customer = Customer::withTrashed()->updateOrCreate(
            ['account_id' => $accountId, 'local_id' => $lookupLocalId],
            [
                'name' => substr($request->input('name'), 0, 255),
                'phone' => substr((string) ($request->input('phone') ?? ''), 0, 50),
                'avatar_color' => substr((string) ($request->input('avatar_color') ?? ''), 0, 20) ?: null,
                'avatar_emoji' => substr((string) ($request->input('avatar_emoji') ?? ''), 0, 16) ?: null,
                'address' => substr((string) ($request->input('address') ?? ''), 0, 500),
                'timestamp' => $timestamp,
                'deleted_at' => null,
            ]
        );

        if ($customer->trashed()) $customer->restore();

        return response()->json([
            'status' => 'success',
            'message' => 'Customer saved successfully.',
            'id' => (int) $customer->id,
            'customer' => $customer,
        ], 201);
    }

    public function update(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];
        $customer = Customer::withTrashed()->where('account_id', $accountId)->where(function ($q) use ($id) {
            $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
        })->first();

        if (!$customer) return response()->json(['status' => 'error', 'message' => 'Customer not found.'], 404);

        if ($request->has('name')) $customer->name = substr($request->input('name'), 0, 255);
        if ($request->has('phone')) $customer->phone = substr((string) ($request->input('phone') ?? ''), 0, 50);
        if ($request->has('avatar_color')) $customer->avatar_color = substr((string) ($request->input('avatar_color') ?? ''), 0, 20) ?: null;
        if ($request->has('avatar_emoji')) $customer->avatar_emoji = substr((string) ($request->input('avatar_emoji') ?? ''), 0, 16) ?: null;
        if ($request->has('address')) $customer->address = substr((string) ($request->input('address') ?? ''), 0, 500);

        if ($request->has('timestamp')) {
            $timestamp = (int) $request->input('timestamp');
            if ($timestamp > 2000000000) $timestamp = (int) floor($timestamp / 1000);
            if ($timestamp > 0 && $timestamp <= time() + 86400) $customer->timestamp = $timestamp;
        }

        $customer->deleted_at = null;
        $customer->save();

        return response()->json(['status' => 'success', 'message' => 'Customer updated successfully.', 'id' => (int) $customer->id, 'customer' => $customer]);
    }

    public function destroy(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        if (!$request->boolean('confirmed')) {
            return response()->json([
                'status' => 'confirmation_required',
                'message' => 'Confirmation required before deleting customer.',
                'requires_confirmation' => true,
            ], 409);
        }

        $customer = Customer::where('account_id', $context['account_id'])->where(function ($q) use ($id) {
            $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
        })->first();
        if (!$customer) return response()->json(['status' => 'error', 'message' => 'Customer not found.'], 404);

        $customer->delete();
        return response()->json(['status' => 'success', 'message' => 'Customer deleted successfully.', 'id' => (int) $customer->id]);
    }
}
