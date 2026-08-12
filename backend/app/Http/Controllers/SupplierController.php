<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Supplier;
use Illuminate\Support\Facades\Validator;

class SupplierController extends Controller
{
    use AuthorizeAccountContext;

    public function index(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        return response()->json([
            'status' => 'success',
            'suppliers' => Supplier::where('account_id', $context['account_id'])->whereNull('deleted_at')->orderBy('name', 'asc')->get(),
        ]);
    }

    public function store(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
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

        $localId = (int) ($request->input('local_id') ?: floor(microtime(true) * 1000));
        $timestamp = (int) ($request->input('timestamp') ?: time());
        if ($timestamp > 2000000000) $timestamp = (int) floor($timestamp / 1000);
        if ($timestamp <= 0 || $timestamp > time() + 86400) $timestamp = time();

        $supplier = Supplier::withTrashed()->updateOrCreate(
            ['account_id' => $context['account_id'], 'local_id' => $localId],
            [
                'name' => substr($request->input('name'), 0, 255),
                'phone' => substr((string) $request->input('phone', ''), 0, 50),
                'avatar_color' => substr((string) ($request->input('avatar_color') ?? ''), 0, 20) ?: null,
                'avatar_emoji' => substr((string) ($request->input('avatar_emoji') ?? ''), 0, 16) ?: null,
                'address' => substr((string) $request->input('address', ''), 0, 500),
                'timestamp' => $timestamp,
                'deleted_at' => null,
            ]
        );
        if ($supplier->trashed()) $supplier->restore();

        return response()->json(['status' => 'success', 'message' => 'Supplier saved successfully.', 'id' => (int) $supplier->id, 'supplier' => $supplier], 201);
    }

    public function update(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $supplier = Supplier::withTrashed()->where('account_id', $context['account_id'])->where(function ($q) use ($id) {
            $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
        })->first();
        if (!$supplier) return response()->json(['status' => 'error', 'message' => 'Supplier not found.'], 404);

        foreach (['name', 'phone', 'address'] as $field) {
            if ($request->has($field)) {
                $max = $field === 'address' ? 500 : ($field === 'name' ? 255 : 50);
                $supplier->{$field} = substr((string) $request->input($field, ''), 0, $max);
            }
        }
        if ($request->has('avatar_color')) $supplier->avatar_color = substr((string) ($request->input('avatar_color') ?? ''), 0, 20) ?: null;
        if ($request->has('avatar_emoji')) $supplier->avatar_emoji = substr((string) ($request->input('avatar_emoji') ?? ''), 0, 16) ?: null;
        if ($request->has('timestamp')) {
            $timestamp = (int) $request->input('timestamp');
            if ($timestamp > 2000000000) $timestamp = (int) floor($timestamp / 1000);
            if ($timestamp > 0 && $timestamp <= time() + 86400) $supplier->timestamp = $timestamp;
        }
        $supplier->deleted_at = null;
        $supplier->save();

        return response()->json(['status' => 'success', 'message' => 'Supplier updated successfully.', 'id' => (int) $supplier->id, 'supplier' => $supplier]);
    }

    public function destroy(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        if (!$request->boolean('confirmed')) return response()->json([
            'status' => 'confirmation_required',
            'message' => 'Confirmation required before deleting supplier.',
            'requires_confirmation' => true,
        ], 409);

        $supplier = Supplier::where('account_id', $context['account_id'])->where(function ($q) use ($id) {
            $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
        })->first();
        if (!$supplier) return response()->json(['status' => 'error', 'message' => 'Supplier not found.'], 404);

        $supplier->delete();
        return response()->json(['status' => 'success', 'message' => 'Supplier deleted successfully.', 'id' => (int) $supplier->id]);
    }
}
