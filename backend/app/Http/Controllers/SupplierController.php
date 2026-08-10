<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Supplier;
use App\Models\SafaApiKey;
use App\Models\Account;
use Illuminate\Support\Facades\Validator;

class SupplierController extends Controller
{
    use AuthorizeAccountContext;

    public function index(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $suppliers = Supplier::where('account_id', $accountId)
            ->whereNull('deleted_at')
            ->orderBy('name', 'asc')
            ->get();

        return response()->json([
            'status' => 'success',
            'suppliers' => $suppliers
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
            'local_id' => 'nullable|integer',
        ]);

        if ($validator->fails()) {
            return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        }

        $localId = (int) ($request->input('local_id') ?? 0);
        $supplier = Supplier::updateOrCreate(
            ['account_id' => $accountId, 'local_id' => $localId > 0 ? $localId : time()],
            [
                'name' => substr($request->input('name'), 0, 255),
                'phone' => substr((string) ($request->input('phone') ?? ''), 0, 50),
                'timestamp' => time(),
            ]
        );

        return response()->json([
            'status' => 'success',
            'message' => 'Supplier created successfully.',
            'supplier' => $supplier
        ], 201);
    }

    public function update(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $supplier = Supplier::where('account_id', $accountId)
            ->where(function ($q) use ($id) {
                $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
            })
            ->first();

        if (!$supplier) {
            return response()->json(['status' => 'error', 'message' => 'Supplier not found.'], 404);
        }

        if ($request->has('name')) $supplier->name = substr($request->input('name'), 0, 255);
        if ($request->has('phone')) $supplier->phone = substr((string) ($request->input('phone') ?? ''), 0, 50);
        $supplier->save();

        return response()->json([
            'status' => 'success',
            'message' => 'Supplier updated successfully.',
            'supplier' => $supplier
        ]);
    }

    public function destroy(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $supplier = Supplier::where('account_id', $accountId)
            ->where(function ($q) use ($id) {
                $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
            })
            ->first();

        if (!$supplier) {
            return response()->json(['status' => 'error', 'message' => 'Supplier not found.'], 404);
        }

        $supplier->delete();

        return response()->json([
            'status' => 'success',
            'message' => 'Supplier deleted successfully.'
        ]);
    }
}
