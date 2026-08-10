<?php

namespace App\Http\Controllers;

use App\Models\ExpenseIncome;
use App\Models\SupplierDeposit;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;

/**
 * Server-authoritative CRUD endpoints for business records that were previously
 * only exposed through the batch sync endpoint. These endpoints are the target
 * API surface for the Android remote-first migration.
 */
class RemoteBusinessController extends Controller
{
    use AuthorizeAccountContext;

    private function context(Request $request): array
    {
        return $this->resolveAuthorizedAccountContext($request);
    }

    private function timestamp($value): int
    {
        $ts = (int) ($value ?? time());
        if ($ts > 2000000000) $ts = (int) floor($ts / 1000);
        return ($ts > 0 && $ts <= time() + 86400) ? $ts : time();
    }

    private function findById($model, int $accountId, int $id)
    {
        return $model::withTrashed()
            ->where('account_id', $accountId)
            ->where(function ($q) use ($id) {
                $q->where('id', $id)->orWhere('local_id', $id);
            })
            ->first();
    }

    public function walletLedgers(Request $request)
    {
        $context = $this->context($request);
        if (isset($context['error'])) return $context['error'];
        return response()->json([
            'status' => 'success',
            'wallet_ledgers' => WalletLedger::where('account_id', $context['account_id'])
                ->whereNull('deleted_at')->orderByDesc('timestamp')->get(),
        ]);
    }

    public function storeWalletLedger(Request $request)
    {
        $context = $this->context($request);
        if (isset($context['error'])) return $context['error'];
        $v = Validator::make($request->all(), ['name' => 'required|string|max:255', 'local_id' => 'nullable|integer|min:1', 'timestamp' => 'nullable|integer|min:1']);
        if ($v->fails()) return response()->json(['status' => 'error', 'errors' => $v->errors()], 422);
        $localId = (int) ($request->input('local_id') ?: floor(microtime(true) * 1000));
        $record = WalletLedger::updateOrCreate(
            ['account_id' => $context['account_id'], 'local_id' => $localId],
            ['name' => substr($request->input('name'), 0, 255), 'timestamp' => $this->timestamp($request->input('timestamp')), 'deleted_at' => null]
        );
        if ($record->trashed()) $record->restore();
        return response()->json(['status' => 'success', 'wallet_ledger' => $record], 201);
    }

    public function updateWalletLedger(Request $request, int $id)
    {
        $context = $this->context($request);
        if (isset($context['error'])) return $context['error'];
        $record = $this->findById(WalletLedger::class, $context['account_id'], $id);
        if (!$record) return response()->json(['status' => 'error', 'message' => 'Wallet ledger not found.'], 404);
        if ($request->has('name')) $record->name = substr((string) $request->input('name'), 0, 255);
        if ($request->has('timestamp')) $record->timestamp = $this->timestamp($request->input('timestamp'));
        $record->deleted_at = null;
        $record->save();
        return response()->json(['status' => 'success', 'wallet_ledger' => $record]);
    }

    public function destroyWalletLedger(Request $request, int $id)
    {
        return $this->confirmedDelete($request, WalletLedger::class, $id, 'Wallet ledger');
    }

    public function supplierDeposits(Request $request)
    {
        $context = $this->context($request);
        if (isset($context['error'])) return $context['error'];
        return response()->json(['status' => 'success', 'supplier_deposits' => SupplierDeposit::where('account_id', $context['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->get()]);
    }

    public function storeSupplierDeposit(Request $request)
    {
        $context = $this->context($request);
        if (isset($context['error'])) return $context['error'];
        $v = Validator::make($request->all(), [
            'supplier_id' => 'nullable|integer|min:1', 'amount_sar' => 'required|numeric|min:0', 'rate' => 'required|numeric|min:0',
            'amount_bdt' => 'required|numeric|min:0', 'paid_bdt' => 'nullable|numeric|min:0', 'transaction_type' => 'nullable|string|max:50',
            'notes' => 'nullable|string', 'local_id' => 'nullable|integer|min:1', 'timestamp' => 'nullable|integer|min:1',
        ]);
        if ($v->fails()) return response()->json(['status' => 'error', 'errors' => $v->errors()], 422);
        $localId = (int) ($request->input('local_id') ?: floor(microtime(true) * 1000));
        $record = SupplierDeposit::updateOrCreate(
            ['account_id' => $context['account_id'], 'local_id' => $localId],
            [
                'supplier_id' => (int) ($request->input('supplier_id') ?: 0), 'amount_sar' => (float) $request->input('amount_sar'),
                'rate' => (float) $request->input('rate'), 'amount_bdt' => (float) $request->input('amount_bdt'),
                'paid_bdt' => (float) ($request->input('paid_bdt') ?: 0), 'transaction_type' => $request->input('transaction_type', 'SAR_GIVEN'),
                'notes' => $request->input('notes'), 'timestamp' => $this->timestamp($request->input('timestamp')), 'deleted_at' => null,
            ]
        );
        if ($record->trashed()) $record->restore();
        return response()->json(['status' => 'success', 'supplier_deposit' => $record], 201);
    }

    public function updateSupplierDeposit(Request $request, int $id)
    {
        $context = $this->context($request);
        if (isset($context['error'])) return $context['error'];
        $record = $this->findById(SupplierDeposit::class, $context['account_id'], $id);
        if (!$record) return response()->json(['status' => 'error', 'message' => 'Supplier deposit not found.'], 404);
        foreach (['supplier_id','amount_sar','rate','amount_bdt','paid_bdt','transaction_type','notes'] as $field) if ($request->has($field)) $record->{$field} = $request->input($field);
        if ($request->has('timestamp')) $record->timestamp = $this->timestamp($request->input('timestamp'));
        $record->deleted_at = null; $record->save();
        return response()->json(['status' => 'success', 'supplier_deposit' => $record]);
    }

    public function destroySupplierDeposit(Request $request, int $id)
    {
        return $this->confirmedDelete($request, SupplierDeposit::class, $id, 'Supplier deposit');
    }

    public function walletBatches(Request $request)
    {
        $context = $this->context($request);
        if (isset($context['error'])) return $context['error'];
        return response()->json(['status' => 'success', 'wallet_batches' => WalletBatch::where('account_id', $context['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->get()]);
    }

    public function storeWalletBatch(Request $request)
    {
        $context = $this->context($request);
        if (isset($context['error'])) return $context['error'];
        $v = Validator::make($request->all(), ['rate'=>'required|numeric|min:0','initial_bdt'=>'required|numeric|min:0','remaining_bdt'=>'required|numeric|min:0','local_id'=>'nullable|integer|min:1','timestamp'=>'nullable|integer|min:1']);
        if ($v->fails()) return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        $localId=(int)($request->input('local_id')?:floor(microtime(true)*1000));
        $record=WalletBatch::updateOrCreate(['account_id'=>$context['account_id'],'local_id'=>$localId],[
            'ledger_id'=>(int)($request->input('ledger_id')?:0),'rate'=>(float)$request->input('rate'),'initial_bdt'=>(float)$request->input('initial_bdt'),'remaining_bdt'=>(float)$request->input('remaining_bdt'),
            'supplier_id'=>(int)($request->input('supplier_id')?:0),'supplier_deposit_id'=>(int)($request->input('supplier_deposit_id')?:0),'notes'=>$request->input('notes'),'timestamp'=>$this->timestamp($request->input('timestamp')),'deleted_at'=>null,
        ]);
        if($record->trashed())$record->restore();
        return response()->json(['status'=>'success','wallet_batch'=>$record],201);
    }

    public function updateWalletBatch(Request $request,int $id)
    {
        $context=$this->context($request); if(isset($context['error']))return $context['error'];
        $record=$this->findById(WalletBatch::class,$context['account_id'],$id); if(!$record)return response()->json(['status'=>'error','message'=>'Wallet batch not found.'],404);
        foreach(['ledger_id','rate','initial_bdt','remaining_bdt','supplier_id','supplier_deposit_id','notes'] as $field)if($request->has($field))$record->{$field}=$request->input($field);
        if($request->has('timestamp'))$record->timestamp=$this->timestamp($request->input('timestamp')); $record->deleted_at=null; $record->save();
        return response()->json(['status'=>'success','wallet_batch'=>$record]);
    }

    public function destroyWalletBatch(Request $request,int $id){return $this->confirmedDelete($request,WalletBatch::class,$id,'Wallet batch');}

    public function expensesIncomes(Request $request)
    {
        $context=$this->context($request); if(isset($context['error']))return $context['error'];
        return response()->json(['status'=>'success','expenses_incomes'=>ExpenseIncome::where('account_id',$context['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->get()]);
    }

    public function storeExpenseIncome(Request $request)
    {
        $context=$this->context($request); if(isset($context['error']))return $context['error'];
        $v=Validator::make($request->all(),['title'=>'required|string|max:255','amount'=>'required|numeric|min:0','currency'=>'required|string|max:16','is_expense'=>'required|boolean','category'=>'nullable|string|max:255','local_id'=>'nullable|integer|min:1','timestamp'=>'nullable|integer|min:1']);
        if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        $localId=(int)($request->input('local_id')?:floor(microtime(true)*1000));
        $record=ExpenseIncome::updateOrCreate(['account_id'=>$context['account_id'],'local_id'=>$localId],[
            'title'=>$request->input('title'),'amount'=>(float)$request->input('amount'),'currency'=>$request->input('currency'),'is_expense'=>(bool)$request->input('is_expense'),'category'=>$request->input('category'),'timestamp'=>$this->timestamp($request->input('timestamp')),'deleted_at'=>null,
        ]);
        if($record->trashed())$record->restore();
        return response()->json(['status'=>'success','expense_income'=>$record],201);
    }

    public function updateExpenseIncome(Request $request,int $id)
    {
        $context=$this->context($request); if(isset($context['error']))return $context['error']; $record=$this->findById(ExpenseIncome::class,$context['account_id'],$id); if(!$record)return response()->json(['status'=>'error','message'=>'Expense/income not found.'],404);
        foreach(['title','amount','currency','is_expense','category'] as $field)if($request->has($field))$record->{$field}=$request->input($field); if($request->has('timestamp'))$record->timestamp=$this->timestamp($request->input('timestamp')); $record->deleted_at=null; $record->save();
        return response()->json(['status'=>'success','expense_income'=>$record]);
    }

    public function destroyExpenseIncome(Request $request,int $id){return $this->confirmedDelete($request,ExpenseIncome::class,$id,'Expense/income');}

    private function confirmedDelete(Request $request,string $model,int $id,string $label)
    {
        $context=$this->context($request); if(isset($context['error']))return $context['error'];
        if(!$request->boolean('confirmed'))return response()->json(['status'=>'confirmation_required','message'=>"Confirmation required before deleting {$label}.",'requires_confirmation'=>true],409);
        $record=$this->findById($model,$context['account_id'],$id); if(!$record)return response()->json(['status'=>'error','message'=>"{$label} not found."],404);
        $record->delete(); return response()->json(['status'=>'success','message'=>"{$label} deleted successfully.",'id'=>(int)$record->id]);
    }
}
