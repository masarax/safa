<?php

namespace App\Http\Controllers;

use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;

/** Server-authoritative CRUD for remote business records. */
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

    private function page(Request $request): array
    {
        $perPage = min(max((int) $request->input('per_page', 100), 1), 500);
        return [$perPage, max((int) $request->input('page', 1), 1)];
    }

    private function findById(string $model, int $accountId, int $id)
    {
        return $model::withTrashed()->where('account_id', $accountId)
            ->where(fn ($q) => $q->where('id', $id)->orWhere('local_id', $id))->first();
    }

    private function relationInAccount(string $model, int $accountId, ?int $id): bool
    {
        return !$id || $model::query()->where('account_id', $accountId)->whereKey($id)->exists();
    }

    private function validation(Request $request, array $rules): \Illuminate\Contracts\Validation\Validator
    {
        return Validator::make($request->all(), $rules);
    }

    public function walletLedgers(Request $request)
    {
        $context = $this->context($request); if (isset($context['error'])) return $context['error'];
        [$perPage, $page] = $this->page($request);
        return response()->json(['status' => 'success', 'wallet_ledgers' => WalletLedger::where('account_id', $context['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->forPage($page, $perPage)->get()]);
    }

    public function storeWalletLedger(Request $request)
    {
        $context = $this->context($request); if (isset($context['error'])) return $context['error'];
        $v = $this->validation($request, ['name' => 'required|string|max:255', 'local_id' => 'nullable|integer|min:1', 'timestamp' => 'nullable|integer|min:1']);
        if ($v->fails()) return response()->json(['status' => 'error', 'errors' => $v->errors()], 422);
        $localId = (int) ($request->input('local_id') ?: floor(microtime(true) * 1000));
        $record = DB::transaction(fn () => WalletLedger::updateOrCreate(
            ['account_id' => $context['account_id'], 'local_id' => $localId],
            ['name' => trim((string) $request->input('name')), 'timestamp' => $this->timestamp($request->input('timestamp')), 'deleted_at' => null]
        ));
        if ($record->trashed()) $record->restore();
        return response()->json(['status' => 'success', 'wallet_ledger' => $record], 201);
    }

    public function updateWalletLedger(Request $request, int $id)
    {
        $context = $this->context($request); if (isset($context['error'])) return $context['error'];
        $record = $this->findById(WalletLedger::class, $context['account_id'], $id);
        if (!$record) return response()->json(['status' => 'error', 'message' => 'Wallet ledger not found.'], 404);
        $v = $this->validation($request, ['name' => 'sometimes|required|string|max:255', 'timestamp' => 'sometimes|integer|min:1']);
        if ($v->fails()) return response()->json(['status' => 'error', 'errors' => $v->errors()], 422);
        DB::transaction(function () use ($record, $request) {
            if ($request->has('name')) $record->name = trim((string) $request->input('name'));
            if ($request->has('timestamp')) $record->timestamp = $this->timestamp($request->input('timestamp'));
            $record->deleted_at = null; $record->save();
        });
        return response()->json(['status' => 'success', 'wallet_ledger' => $record]);
    }

    public function destroyWalletLedger(Request $request, int $id) { return $this->confirmedDelete($request, WalletLedger::class, $id, 'Wallet ledger'); }

    public function supplierDeposits(Request $request)
    {
        $context = $this->context($request); if (isset($context['error'])) return $context['error'];
        [$perPage, $page] = $this->page($request);
        return response()->json(['status' => 'success', 'supplier_deposits' => SupplierDeposit::where('account_id', $context['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->forPage($page, $perPage)->get()]);
    }

    public function storeSupplierDeposit(Request $request)
    {
        $context = $this->context($request); if (isset($context['error'])) return $context['error'];
        $v = $this->validation($request, [
            'supplier_id' => 'nullable|integer|min:1', 'amount_sar' => 'required|numeric|min:0|max:999999999999.99',
            'rate' => 'required|numeric|min:0|max:999999.9999', 'amount_bdt' => 'required|numeric|min:0|max:999999999999.99',
            'paid_bdt' => 'nullable|numeric|min:0|max:999999999999.99', 'transaction_type' => 'nullable|string|max:50',
            'notes' => 'nullable|string|max:10000', 'local_id' => 'nullable|integer|min:1', 'timestamp' => 'nullable|integer|min:1',
        ]);
        $v->after(function ($validator) use ($request) {
            if ((string) $request->input('paid_bdt', '0') > (string) $request->input('amount_bdt', '0')) $validator->errors()->add('paid_bdt', 'Paid amount cannot exceed the deposit amount.');
        });
        if ($v->fails()) return response()->json(['status' => 'error', 'errors' => $v->errors()], 422);
        $supplierId = $request->filled('supplier_id') ? (int) $request->input('supplier_id') : null;
        if (!$this->relationInAccount(Supplier::class, $context['account_id'], $supplierId)) return response()->json(['status' => 'error', 'code' => 'DEPENDENCY', 'message' => 'Supplier does not belong to the active account.'], 422);
        $localId = (int) ($request->input('local_id') ?: floor(microtime(true) * 1000));
        $record = DB::transaction(fn () => SupplierDeposit::updateOrCreate(
            ['account_id' => $context['account_id'], 'local_id' => $localId],
            ['supplier_id' => $supplierId, 'amount_sar' => (string) $request->input('amount_sar'), 'rate' => (string) $request->input('rate'), 'amount_bdt' => (string) $request->input('amount_bdt'), 'paid_bdt' => (string) ($request->input('paid_bdt', '0')), 'transaction_type' => $request->input('transaction_type', 'SAR_GIVEN'), 'notes' => $request->input('notes'), 'timestamp' => $this->timestamp($request->input('timestamp')), 'deleted_at' => null]
        ));
        if ($record->trashed()) $record->restore();
        return response()->json(['status' => 'success', 'supplier_deposit' => $record], 201);
    }

    public function updateSupplierDeposit(Request $request, int $id)
    {
        $context = $this->context($request); if (isset($context['error'])) return $context['error'];
        $record = $this->findById(SupplierDeposit::class, $context['account_id'], $id); if (!$record) return response()->json(['status' => 'error', 'message' => 'Supplier deposit not found.'], 404);
        $v = $this->validation($request, ['supplier_id'=>'sometimes|nullable|integer|min:1','amount_sar'=>'sometimes|numeric|min:0|max:999999999999.99','rate'=>'sometimes|numeric|min:0|max:999999.9999','amount_bdt'=>'sometimes|numeric|min:0|max:999999999999.99','paid_bdt'=>'sometimes|numeric|min:0|max:999999999999.99','transaction_type'=>'sometimes|string|max:50','notes'=>'sometimes|nullable|string|max:10000','timestamp'=>'sometimes|integer|min:1']);
        if ($v->fails()) return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        if ($request->has('supplier_id')) {
            $supplierId = $request->input('supplier_id') ? (int) $request->input('supplier_id') : null;
            if (!$this->relationInAccount(Supplier::class, $context['account_id'], $supplierId)) return response()->json(['status'=>'error','code'=>'DEPENDENCY','message'=>'Supplier does not belong to the active account.'],422);
            $record->supplier_id = $supplierId;
        }
        foreach (['amount_sar','rate','amount_bdt','paid_bdt','transaction_type','notes'] as $field) if ($request->has($field)) $record->{$field} = $request->input($field);
        if ((string) $record->paid_bdt > (string) $record->amount_bdt) return response()->json(['status'=>'error','errors'=>['paid_bdt'=>['Paid amount cannot exceed the deposit amount.']]],422);
        if ($request->has('timestamp')) $record->timestamp = $this->timestamp($request->input('timestamp'));
        DB::transaction(function () use ($record) { $record->deleted_at = null; $record->save(); });
        return response()->json(['status'=>'success','supplier_deposit'=>$record]);
    }

    public function destroySupplierDeposit(Request $request, int $id) { return $this->confirmedDelete($request, SupplierDeposit::class, $id, 'Supplier deposit'); }

    public function walletBatches(Request $request)
    {
        $context=$this->context($request); if(isset($context['error']))return $context['error']; [$perPage,$page]=$this->page($request);
        return response()->json(['status'=>'success','wallet_batches'=>WalletBatch::where('account_id',$context['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->forPage($page,$perPage)->get()]);
    }

    public function storeWalletBatch(Request $request)
    {
        $context=$this->context($request); if(isset($context['error']))return $context['error'];
        $v=$this->validation($request,['ledger_id'=>'nullable|integer|min:1','rate'=>'required|numeric|min:0|max:999999.9999','initial_bdt'=>'required|numeric|min:0|max:999999999999.99','remaining_bdt'=>'required|numeric|min:0|max:999999999999.99','supplier_id'=>'nullable|integer|min:1','supplier_deposit_id'=>'nullable|integer|min:1','notes'=>'nullable|string|max:10000','local_id'=>'nullable|integer|min:1','timestamp'=>'nullable|integer|min:1']);
        $v->after(function($validator)use($request){if((string)$request->input('remaining_bdt','0')>(string)$request->input('initial_bdt','0'))$validator->errors()->add('remaining_bdt','Remaining amount cannot exceed the initial amount.');});
        if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        $supplierId=$request->filled('supplier_id')?(int)$request->input('supplier_id'):null; if(!$this->relationInAccount(Supplier::class,$context['account_id'],$supplierId))return response()->json(['status'=>'error','code'=>'DEPENDENCY','message'=>'Supplier does not belong to the active account.'],422);
        $depositId=$request->filled('supplier_deposit_id')?(int)$request->input('supplier_deposit_id'):null; if(!$this->relationInAccount(SupplierDeposit::class,$context['account_id'],$depositId))return response()->json(['status'=>'error','code'=>'DEPENDENCY','message'=>'Supplier deposit does not belong to the active account.'],422);
        $ledgerId=$request->filled('ledger_id')?(int)$request->input('ledger_id'):null; if(!$this->relationInAccount(WalletLedger::class,$context['account_id'],$ledgerId))return response()->json(['status'=>'error','code'=>'DEPENDENCY','message'=>'Wallet ledger does not belong to the active account.'],422);
        $localId=(int)($request->input('local_id')?:floor(microtime(true)*1000));
        $record=DB::transaction(fn()=>WalletBatch::updateOrCreate(['account_id'=>$context['account_id'],'local_id'=>$localId],['ledger_id'=>$ledgerId,'rate'=>(string)$request->input('rate'),'initial_bdt'=>(string)$request->input('initial_bdt'),'remaining_bdt'=>(string)$request->input('remaining_bdt'),'supplier_id'=>$supplierId,'supplier_deposit_id'=>$depositId,'notes'=>$request->input('notes'),'timestamp'=>$this->timestamp($request->input('timestamp')),'deleted_at'=>null]));
        if($record->trashed())$record->restore(); return response()->json(['status'=>'success','wallet_batch'=>$record],201);
    }

    public function updateWalletBatch(Request $request,int $id)
    {
        $context=$this->context($request);if(isset($context['error']))return $context['error'];$record=$this->findById(WalletBatch::class,$context['account_id'],$id);if(!$record)return response()->json(['status'=>'error','message'=>'Wallet batch not found.'],404);
        $v=$this->validation($request,['ledger_id'=>'sometimes|nullable|integer|min:1','rate'=>'sometimes|numeric|min:0|max:999999.9999','initial_bdt'=>'sometimes|numeric|min:0|max:999999999999.99','remaining_bdt'=>'sometimes|numeric|min:0|max:999999999999.99','supplier_id'=>'sometimes|nullable|integer|min:1','supplier_deposit_id'=>'sometimes|nullable|integer|min:1','notes'=>'sometimes|nullable|string|max:10000','timestamp'=>'sometimes|integer|min:1']);if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        foreach([['supplier_id',Supplier::class],['supplier_deposit_id',SupplierDeposit::class],['ledger_id',WalletLedger::class]] as [$field,$model])if($request->has($field)){ $value=$request->input($field)?(int)$request->input($field):null;if(!$this->relationInAccount($model,$context['account_id'],$value))return response()->json(['status'=>'error','code'=>'DEPENDENCY','message'=>ucwords(str_replace('_',' ',$field)).' does not belong to the active account.'],422);$record->{$field}=$value; }
        foreach(['rate','initial_bdt','remaining_bdt','notes'] as $field)if($request->has($field))$record->{$field}=$request->input($field);if($request->has('timestamp'))$record->timestamp=$this->timestamp($request->input('timestamp'));if((string)$record->remaining_bdt>(string)$record->initial_bdt)return response()->json(['status'=>'error','errors'=>['remaining_bdt'=>['Remaining amount cannot exceed the initial amount.']]],422);
        DB::transaction(function()use($record){$record->deleted_at=null;$record->save();});return response()->json(['status'=>'success','wallet_batch'=>$record]);
    }

    public function destroyWalletBatch(Request $request,int $id){return $this->confirmedDelete($request,WalletBatch::class,$id,'Wallet batch');}

    public function expensesIncomes(Request $request)
    {
        $context=$this->context($request);if(isset($context['error']))return $context['error'];[$perPage,$page]=$this->page($request);return response()->json(['status'=>'success','expenses_incomes'=>ExpenseIncome::where('account_id',$context['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->forPage($page,$perPage)->get()]);
    }

    public function storeExpenseIncome(Request $request)
    {
        $context=$this->context($request);if(isset($context['error']))return $context['error'];$v=$this->validation($request,['title'=>'required|string|max:255','amount'=>'required|numeric|min:0|max:999999999999.99','currency'=>'required|string|max:16','is_expense'=>'required|boolean','category'=>'nullable|string|max:255','local_id'=>'nullable|integer|min:1','timestamp'=>'nullable|integer|min:1']);if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);$localId=(int)($request->input('local_id')?:floor(microtime(true)*1000);$record=DB::transaction(fn()=>ExpenseIncome::updateOrCreate(['account_id'=>$context['account_id'],'local_id'=>$localId],['title'=>trim((string)$request->input('title')),'amount'=>(string)$request->input('amount'),'currency'=>strtoupper(trim((string)$request->input('currency'))),'is_expense'=>(bool)$request->input('is_expense'),'category'=>$request->input('category'),'timestamp'=>$this->timestamp($request->input('timestamp')),'deleted_at'=>null]));if($record->trashed())$record->restore();return response()->json(['status'=>'success','expense_income'=>$record],201);
    }

    public function updateExpenseIncome(Request $request,int $id)
    {
        $context=$this->context($request);if(isset($context['error']))return $context['error'];$record=$this->findById(ExpenseIncome::class,$context['account_id'],$id);if(!$record)return response()->json(['status'=>'error','message'=>'Expense/income not found.'],404);$v=$this->validation($request,['title'=>'sometimes|required|string|max:255','amount'=>'sometimes|numeric|min:0|max:999999999999.99','currency'=>'sometimes|string|max:16','is_expense'=>'sometimes|boolean','category'=>'sometimes|nullable|string|max:255','timestamp'=>'sometimes|integer|min:1']);if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);foreach(['title','amount','currency','is_expense','category'] as $field)if($request->has($field))$record->{$field}=$field==='title'?trim((string)$request->input($field)):($field==='currency'?strtoupper(trim((string)$request->input($field))):$request->input($field));if($request->has('timestamp'))$record->timestamp=$this->timestamp($request->input('timestamp'));DB::transaction(function()use($record){$record->deleted_at=null;$record->save();});return response()->json(['status'=>'success','expense_income'=>$record]);
    }

    public function destroyExpenseIncome(Request $request,int $id){return $this->confirmedDelete($request,ExpenseIncome::class,$id,'Expense/income');}

    private function confirmedDelete(Request $request,string $model,int $id,string $label)
    {
        $context=$this->context($request);if(isset($context['error']))return $context['error'];if(!$request->boolean('confirmed'))return response()->json(['status'=>'confirmation_required','message'=>"Confirmation required before deleting {$label}.",'requires_confirmation'=>true],409);$record=$this->findById($model,$context['account_id'],$id);if(!$record)return response()->json(['status'=>'error','message'=>"{$label} not found."],404);DB::transaction(fn()=> $record->delete());return response()->json(['status'=>'success','message'=>"{$label} deleted successfully.",'id'=>(int)$record->id]);
    }
}
