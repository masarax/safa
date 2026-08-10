<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Customer;
use App\Models\SafaApiKey;
use App\Models\Account;
use Illuminate\Support\Facades\Validator;

class CustomerController extends Controller
{
    use AuthorizeAccountContext;

    public function index(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $customers = Customer::where('account_id', $accountId)
            ->whereNull('deleted_at')
            ->orderBy('name', 'asc')
            ->get();

        return response()->json([
            'status' => 'success',
            'customers' => $customers
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
        $customer = Customer::updateOrCreate(
            ['account_id' => $accountId, 'local_id' => $localId > 0 ? $localId : time()],
            [
                'name' => substr($request->input('name'), 0, 255),
                'phone' => substr((string) ($request->input('phone') ?? ''), 0, 50),
                'timestamp' => time(),
            ]
        );

        return response()->json([
            'status' => 'success',
            'message' => 'Customer created successfully.',
            'customer' => $customer
        ], 201);
    }

    public function update(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $customer = Customer::where('account_id', $accountId)
            ->where(function ($q) use ($id) {
                $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
            })
            ->first();

        if (!$customer) {
            return response()->json(['status' => 'error', 'message' => 'Customer not found.'], 404);
        }

        if ($request->has('name')) $customer->name = substr($request->input('name'), 0, 255);
        if ($request->has('phone')) $customer->phone = substr((string) ($request->input('phone') ?? ''), 0, 50);
        $customer->save();

        return response()->json([
            'status' => 'success',
            'message' => 'Customer updated successfully.',
            'customer' => $customer
        ]);
    }

    public function destroy(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $customer = Customer::where('account_id', $accountId)
            ->where(function ($q) use ($id) {
                $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
            })
            ->first();

        if (!$customer) {
            return response()->json(['status' => 'error', 'message' => 'Customer not found.'], 404);
        }

        $customer->delete();

        return response()->json([
            'status' => 'success',
            'message' => 'Customer deleted successfully.'
        ]);
    }
}
