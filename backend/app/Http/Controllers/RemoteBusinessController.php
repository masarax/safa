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

class RemoteBusinessController extends Controller
{
    use AuthorizeAccountContext;

    private function context(Request $request): array { return $this->resolveAuthorizedAccountContext($request); }

    private function timestamp($value): int
    {
        $ts = (int) ($value ?? time());
        if ($ts > 2000000000) $ts = (int) floor($ts / 1000);
        return ($ts > 0 && $ts <= time() + 86400) ? $ts : time();
    }

    private function page(Request $request): array
    {
        return [min(max((int) $request->input('per_page', 100), 1), 500), max((int) $request->input('page', 1), 1)];
    }

    private function findById(string $model, int $accountId, int $id)
    {
        return $model::withTrashed()->where('account_id', $accountId)->where(fn ($q) => $q->where('id', $id)->orWhere('local_id', $id))->first();
    }

    private function relationInAccount(string $model, int $accountId, ?int $id): bool
    {
        return !$id || $model::query()->where('account_id', $accountId)->whereKey($id)->exists();
    }

    private function decimalGreater($left, $right): bool
    {
        if (function_exists('bccomp')) return bccomp((string) $left, (string) $right, 8) === 1;
        return (float) $left > (float) $right;
    }

    public function walletLedgers(Request $request)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];[$per,$page]=$this->page($request);
        return response()->json(['status'=>'success','wallet_ledgers'=>WalletLedger::where('account_id',$c['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->forPage($page,$per)->get()]);
    }

    public function storeWalletLedger(Request $request)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];$v=Validator::make($request->all(),['name'=>'required|string|max:255','local_id'=>'nullable|integer|min:1','timestamp'=>'nullable|integer|min:1']);if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        $id=(int)($request->input('local_id')?:floor(microtime(true)*1000));$r=DB::transaction(fn()=>WalletLedger::updateOrCreate(['account_id'=>$c['account_id'],'local_id'=>$id],['name'=>trim((string)$request->input('name')),'timestamp'=>$this->timestamp($request->input('timestamp')),'deleted_at'=>null]));if($r->trashed())$r->restore();return response()->json(['status'=>'success','wallet_ledger'=>$r],201);
    }

    public function updateWalletLedger(Request $request,int $id)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];$r=$this->findById(WalletLedger::class,$c['account_id'],$id);if(!$r)return response()->json(['status'=>'error','message'=>'Wallet ledger not found.'],404);$v=Validator::make($request->all(),['name'=>'sometimes|required|string|max:255','timestamp'=>'sometimes|integer|min:1']);if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);if($request->has('name'))$r->name=trim((string)$request->input('name'));if($request->has('timestamp'))$r->timestamp=$this->timestamp($request->input('timestamp'));DB::transaction(function()use($r){$r->deleted_at=null;$r->save();});return response()->json(['status'=>'success','wallet_ledger'=>$r]);
    }

    public function destroyWalletLedger(Request $request,int $id){return $this->confirmedDelete($request,WalletLedger::class,$id,'Wallet ledger');}

    public function supplierDeposits(Request $request)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];[$per,$page]=$this->page($request);return response()->json(['status'=>'success','supplier_deposits'=>SupplierDeposit::where('account_id',$c['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->forPage($page,$per)->get()]);
    }

    private function validateDeposit(Request $request,$existing=null): \Illuminate\Contracts\Validation\Validator
    {
        $v=Validator::make($request->all(),['supplier_id'=>'sometimes|nullable|integer|min:1','amount_sar'=>'sometimes|numeric|min:0|max:999999999999.99','rate'=>'sometimes|numeric|min:0|max:999999.9999','amount_bdt'=>'sometimes|numeric|min:0|max:999999999999.99','paid_bdt'=>'sometimes|numeric|min:0|max:999999999999.99','transaction_type'=>'sometimes|string|max:50','notes'=>'sometimes|nullable|string|max:10000','local_id'=>'nullable|integer|min:1','timestamp'=>'nullable|integer|min:1']);
        $v->after(function($validator)use($request,$existing){$amount=$request->has('amount_bdt')?$request->input('amount_bdt'):($existing?->amount_bdt??'0');$paid=$request->has('paid_bdt')?$request->input('paid_bdt'):($existing?->paid_bdt??'0');if($this->decimalGreater($paid,$amount))$validator->errors()->add('paid_bdt','Paid amount cannot exceed the deposit amount.');});return $v;
    }

    public function storeSupplierDeposit(Request $request)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];$v=$this->validateDeposit($request);foreach(['amount_sar','rate','amount_bdt'] as $f)if(!$request->has($f))$v->errors()->add($f,'This field is required.');if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        $sid=$request->filled('supplier_id')?(int)$request->input('supplier_id'):null;if(!$this->relationInAccount(Supplier::class,$c['account_id'],$sid))return response()->json(['status'=>'error','code'=>'DEPENDENCY','message'=>'Supplier does not belong to the active account.'],422);$id=(int)($request->input('local_id')?:floor(microtime(true)*1000));
        $r=DB::transaction(fn()=>SupplierDeposit::updateOrCreate(['account_id'=>$c['account_id'],'local_id'=>$id],['supplier_id'=>$sid,'amount_sar'=>(string)$request->input('amount_sar'),'rate'=>(string)$request->input('rate'),'amount_bdt'=>(string)$request->input('amount_bdt'),'paid_bdt'=>(string)$request->input('paid_bdt','0'),'transaction_type'=>$request->input('transaction_type','SAR_GIVEN'),'notes'=>$request->input('notes'),'timestamp'=>$this->timestamp($request->input('timestamp')),'deleted_at'=>null]));if($r->trashed())$r->restore();return response()->json(['status'=>'success','supplier_deposit'=>$r],201);
    }

    public function updateSupplierDeposit(Request $request,int $id)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];$r=$this->findById(SupplierDeposit::class,$c['account_id'],$id);if(!$r)return response()->json(['status'=>'error','message'=>'Supplier deposit not found.'],404);$v=$this->validateDeposit($request,$r);if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        if($request->has('supplier_id')){$sid=$request->input('supplier_id')?(int)$request->input('supplier_id'):null;if(!$this->relationInAccount(Supplier::class,$c['account_id'],$sid))return response()->json(['status'=>'error','code'=>'DEPENDENCY','message'=>'Supplier does not belong to the active account.'],422);$r->supplier_id=$sid;}foreach(['amount_sar','rate','amount_bdt','paid_bdt','transaction_type','notes'] as $f)if($request->has($f))$r->{$f}=$request->input($f);if($request->has('timestamp'))$r->timestamp=$this->timestamp($request->input('timestamp'));DB::transaction(function()use($r){$r->deleted_at=null;$r->save();});return response()->json(['status'=>'success','supplier_deposit'=>$r]);
    }

    public function destroySupplierDeposit(Request $request,int $id){return $this->confirmedDelete($request,SupplierDeposit::class,$id,'Supplier deposit');}

    public function walletBatches(Request $request)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];[$per,$page]=$this->page($request);return response()->json(['status'=>'success','wallet_batches'=>WalletBatch::where('account_id',$c['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->forPage($page,$per)->get()]);
    }

    private function validateBatch(Request $request,$existing=null): \Illuminate\Contracts\Validation\Validator
    {
        $v=Validator::make($request->all(),['ledger_id'=>'sometimes|nullable|integer|min:1','rate'=>'sometimes|numeric|min:0|max:999999.9999','initial_bdt'=>'sometimes|numeric|min:0|max:999999999999.99','remaining_bdt'=>'sometimes|numeric|min:0|max:999999999999.99','supplier_id'=>'sometimes|nullable|integer|min:1','supplier_deposit_id'=>'sometimes|nullable|integer|min:1','notes'=>'sometimes|nullable|string|max:10000','local_id'=>'nullable|integer|min:1','timestamp'=>'nullable|integer|min:1']);
        $v->after(function($validator)use($request,$existing){$initial=$request->has('initial_bdt')?$request->input('initial_bdt'):($existing?->initial_bdt??'0');$remaining=$request->has('remaining_bdt')?$request->input('remaining_bdt'):($existing?->remaining_bdt??'0');if($this->decimalGreater($remaining,$initial))$validator->errors()->add('remaining_bdt','Remaining amount cannot exceed the initial amount.');});return $v;
    }

    public function storeWalletBatch(Request $request)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];$v=$this->validateBatch($request);foreach(['rate','initial_bdt','remaining_bdt'] as $f)if(!$request->has($f))$v->errors()->add($f,'This field is required.');if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        $sid=$request->filled('supplier_id')?(int)$request->input('supplier_id'):null;$did=$request->filled('supplier_deposit_id')?(int)$request->input('supplier_deposit_id'):null;$lid=$request->filled('ledger_id')?(int)$request->input('ledger_id'):null;foreach([[$sid,Supplier::class,'Supplier'],[$did,SupplierDeposit::class,'Supplier deposit'],[$lid,WalletLedger::class,'Wallet ledger']] as [$id,$model,$label])if(!$this->relationInAccount($model,$c['account_id'],$id))return response()->json(['status'=>'error','code'=>'DEPENDENCY','message'=>"{$label} does not belong to the active account."],422);
        $local=(int)($request->input('local_id')?:floor(microtime(true)*1000));$r=DB::transaction(fn()=>WalletBatch::updateOrCreate(['account_id'=>$c['account_id'],'local_id'=>$local],['ledger_id'=>$lid,'rate'=>(string)$request->input('rate'),'initial_bdt'=>(string)$request->input('initial_bdt'),'remaining_bdt'=>(string)$request->input('remaining_bdt'),'supplier_id'=>$sid,'supplier_deposit_id'=>$did,'notes'=>$request->input('notes'),'timestamp'=>$this->timestamp($request->input('timestamp')),'deleted_at'=>null]));if($r->trashed())$r->restore();return response()->json(['status'=>'success','wallet_batch'=>$r],201);
    }

    public function updateWalletBatch(Request $request,int $id)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];$r=$this->findById(WalletBatch::class,$c['account_id'],$id);if(!$r)return response()->json(['status'=>'error','message'=>'Wallet batch not found.'],404);$v=$this->validateBatch($request,$r);if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);
        foreach([['supplier_id',Supplier::class,'Supplier'],['supplier_deposit_id',SupplierDeposit::class,'Supplier deposit'],['ledger_id',WalletLedger::class,'Wallet ledger']] as [$f,$model,$label])if($request->has($f)){$value=$request->input($f)?(int)$request->input($f):null;if(!$this->relationInAccount($model,$c['account_id'],$value))return response()->json(['status'=>'error','code'=>'DEPENDENCY','message'=>"{$label} does not belong to the active account."],422);$r->{$f}=$value;}foreach(['rate','initial_bdt','remaining_bdt','notes'] as $f)if($request->has($f))$r->{$f}=$request->input($f);if($request->has('timestamp'))$r->timestamp=$this->timestamp($request->input('timestamp'));DB::transaction(function()use($r){$r->deleted_at=null;$r->save();});return response()->json(['status'=>'success','wallet_batch'=>$r]);
    }

    public function destroyWalletBatch(Request $request,int $id){return $this->confirmedDelete($request,WalletBatch::class,$id,'Wallet batch');}

    public function expensesIncomes(Request $request)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];[$per,$page]=$this->page($request);return response()->json(['status'=>'success','expenses_incomes'=>ExpenseIncome::where('account_id',$c['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->forPage($page,$per)->get()]);
    }

    public function storeExpenseIncome(Request $request)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];$v=Validator::make($request->all(),['title'=>'required|string|max:255','amount'=>'required|numeric|min:0|max:999999999999.99','currency'=>'required|string|max:16','is_expense'=>'required|boolean','category'=>'nullable|string|max:255','local_id'=>'nullable|integer|min:1','timestamp'=>'nullable|integer|min:1']);if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);$id=(int)($request->input('local_id')?:floor(microtime(true)*1000));$r=DB::transaction(fn()=>ExpenseIncome::updateOrCreate(['account_id'=>$c['account_id'],'local_id'=>$id],['title'=>trim((string)$request->input('title')),'amount'=>(string)$request->input('amount'),'currency'=>strtoupper(trim((string)$request->input('currency'))),'is_expense'=>(bool)$request->input('is_expense'),'category'=>$request->input('category'),'timestamp'=>$this->timestamp($request->input('timestamp')),'deleted_at'=>null]));if($r->trashed())$r->restore();return response()->json(['status'=>'success','expense_income'=>$r],201);
    }

    public function updateExpenseIncome(Request $request,int $id)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];$r=$this->findById(ExpenseIncome::class,$c['account_id'],$id);if(!$r)return response()->json(['status'=>'error','message'=>'Expense/income not found.'],404);$v=Validator::make($request->all(),['title'=>'sometimes|required|string|max:255','amount'=>'sometimes|numeric|min:0|max:999999999999.99','currency'=>'sometimes|string|max:16','is_expense'=>'sometimes|boolean','category'=>'sometimes|nullable|string|max:255','timestamp'=>'sometimes|integer|min:1']);if($v->fails())return response()->json(['status'=>'error','errors'=>$v->errors()],422);foreach(['title','amount','currency','is_expense','category'] as $f)if($request->has($f))$r->{$f}=$f==='title'?trim((string)$request->input($f)):($f==='currency'?strtoupper(trim((string)$request->input($f))):$request->input($f));if($request->has('timestamp'))$r->timestamp=$this->timestamp($request->input('timestamp'));DB::transaction(function()use($r){$r->deleted_at=null;$r->save();});return response()->json(['status'=>'success','expense_income'=>$r]);
    }

    public function destroyExpenseIncome(Request $request,int $id){return $this->confirmedDelete($request,ExpenseIncome::class,$id,'Expense/income');}

    private function confirmedDelete(Request $request,string $model,int $id,string $label)
    {
        $c=$this->context($request);if(isset($c['error']))return $c['error'];if(!$request->boolean('confirmed'))return response()->json(['status'=>'confirmation_required','message'=>"Confirmation required before deleting {$label}.",'requires_confirmation'=>true],409);$r=$this->findById($model,$c['account_id'],$id);if(!$r)return response()->json(['status'=>'error','message'=>"{$label} not found."],404);DB::transaction(fn()=> $r->delete());return response()->json(['status'=>'success','message'=>"{$label} deleted successfully.",'id'=>(int)$r->id]);
    }
}
