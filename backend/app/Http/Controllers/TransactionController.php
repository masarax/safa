<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Transaction;
use Illuminate\Support\Facades\Validator;

class TransactionController extends Controller
{
    use AuthorizeAccountContext;

    public function index(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request); if (isset($context['error'])) return $context['error'];
        return response()->json(['status'=>'success','transactions'=>Transaction::where('account_id',$context['account_id'])->whereNull('deleted_at')->orderByDesc('id')->get()]);
    }

    public function store(Request $request)
    {
        $context=$this->resolveAuthorizedAccountContext($request); if(isset($context['error']))return $context['error'];
        $validator=Validator::make($request->all(),['amount_sar'=>'nullable|numeric','amount'=>'nullable|numeric','customer_id'=>'nullable|integer','supplier_id'=>'nullable|integer','local_id'=>'nullable|integer']);
        if($validator->fails())return response()->json(['status'=>'error','errors'=>$validator->errors()],422);
        $amountSar=(float)($request->input('amount_sar')??$request->input('amount')??0); $localId=(int)($request->input('local_id')?:time());
        $transaction=Transaction::withTrashed()->updateOrCreate(['account_id'=>$context['account_id'],'local_id'=>$localId],[
            'type'=>substr((string)$request->input('type','Pending'),0,20),'amount'=>$amountSar,'amount_sar'=>$amountSar,'customer_id'=>(int)($request->input('customer_id')?:0),'supplier_id'=>(int)($request->input('supplier_id')?:0),
            'customer_rate'=>(float)($request->input('customer_rate')?:0),'supplier_rate'=>(float)($request->input('supplier_rate')?:0),'amount_bdt'=>(float)($request->input('amount_bdt')?:0),
            'receiver_name'=>substr((string)$request->input('receiver_name',''),0,255),'receiver_phone'=>substr((string)$request->input('receiver_phone',''),0,50),'receiver_account_type'=>substr((string)$request->input('receiver_account_type',''),0,50),'receiver_account_no'=>substr((string)$request->input('receiver_account_no',''),0,100),'wallet_batch_id'=>(int)($request->input('wallet_batch_id')?:0),'notes'=>$request->input('notes'),'timestamp'=>time(),'deleted_at'=>null,
        ]); if($transaction->trashed())$transaction->restore();
        return response()->json(['status'=>'success','message'=>'Transaction recorded successfully.','transaction'=>$transaction],201);
    }

    public function update(Request $request,$id)
    {
        $context=$this->resolveAuthorizedAccountContext($request); if(isset($context['error']))return $context['error']; $transaction=Transaction::withTrashed()->where('account_id',$context['account_id'])->where(function($q)use($id){$q->where('id',(int)$id)->orWhere('local_id',(int)$id);})->first();
        if(!$transaction)return response()->json(['status'=>'error','message'=>'Transaction not found.'],404);
        foreach(['type','notes','amount_sar','customer_rate','supplier_rate','amount_bdt'] as $field)if($request->has($field))$transaction->{$field}=in_array($field,['amount_sar','customer_rate','supplier_rate','amount_bdt'],true)?(float)$request->input($field):substr((string)$request->input($field),0,($field==='type'?20:1000));
        if($request->has('amount_sar'))$transaction->amount=(float)$request->input('amount_sar'); $transaction->deleted_at=null; $transaction->save();
        return response()->json(['status'=>'success','message'=>'Transaction updated successfully.','transaction'=>$transaction]);
    }

    public function destroy(Request $request,$id)
    {
        $context=$this->resolveAuthorizedAccountContext($request); if(isset($context['error']))return $context['error'];
        if(!$request->boolean('confirmed'))return response()->json(['status'=>'confirmation_required','message'=>'Confirmation required before deleting transaction.','requires_confirmation'=>true],409);
        $transaction=Transaction::where('account_id',$context['account_id'])->where(function($q)use($id){$q->where('id',(int)$id)->orWhere('local_id',(int)$id);})->first();
        if(!$transaction)return response()->json(['status'=>'error','message'=>'Transaction not found.'],404); $transaction->delete();
        return response()->json(['status'=>'success','message'=>'Transaction deleted successfully.','id'=>(int)$transaction->id]);
    }
}