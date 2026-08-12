<?php

namespace App\Http\Controllers;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Validator;

class UserManagementController extends Controller
{
    private function manageableRoles(): array { return array_values(array_unique([User::ROLE_ADMIN, User::ROLE_USER, 'staff', 'manager'])); }
    public function index(Request $request): JsonResponse { if (!$this->isSuperAdmin($request)) return $this->forbidden(); return response()->json(['status'=>'success','users'=>User::query()->whereIn('role',$this->manageableRoles())->orderBy('id')->get()->map(fn(User $u)=>$this->serializeUser($u))]); }
    public function store(Request $request): JsonResponse { if (!$this->isSuperAdmin($request)) return $this->forbidden(); $validator=Validator::make($request->all(),['name'=>['required','string','max:255'],'mobile'=>['required','string','max:30','unique:users,mobile'],'email'=>['nullable','email','max:255'],'role'=>['required','in:'.implode(',',$this->manageableRoles())],'pin'=>['required','digits:6'],'permissions'=>['nullable','array'],'is_activated'=>['nullable','boolean']]); if($validator->fails())return response()->json(['status'=>'error','message'=>'Validation failed.','errors'=>$validator->errors()],422);$hash=Hash::make((string)$request->input('pin'));$role=(string)$request->input('role');$permissions=$this->sanitizePermissions($request->input('permissions',[]));$user=DB::transaction(function()use($request,$hash,$role,$permissions){$u=User::create(['name'=>$request->string('name')->toString(),'email'=>$request->input('email')?:($request->input('mobile').'@safa.local'),'mobile'=>$request->string('mobile')->toString(),'role'=>$role,'pin_hash'=>$hash,'password'=>$hash,'is_activated'=>$request->has('is_activated')?(bool)$request->boolean('is_activated'):true,'permissions'=>$permissions]);$this->syncOperatorAccount($u);return $u;});return response()->json(['status'=>'success','message'=>'User account created successfully.','user'=>$this->serializeUser($user)],201); }
    public function update(Request $request,int $id): JsonResponse
    {
        if (!in_array(strtoupper($request->method()), ['PUT','PATCH'], true)) return response()->json(['status'=>'error','message'=>'Method not allowed.'],405);
        if (!$this->getSuperAdmin($request)) return $this->forbidden();
        $user=User::find($id); if(!$user||$user->isSuperAdmin()) return response()->json(['status'=>'error','message'=>'User not found.'],404);
        $validator=Validator::make($request->all(),['name'=>['sometimes','string','max:255'],'mobile'=>['sometimes','string','max:30','unique:users,mobile,'.$user->id],'email'=>['sometimes','nullable','email','max:255'],'role'=>['sometimes','in:'.implode(',',$this->manageableRoles())],'pin'=>['sometimes','digits:6'],'password'=>['sometimes','string','min:6','max:255'],'permissions'=>['sometimes','array'],'is_activated'=>['sometimes','boolean']]);
        if($validator->fails())return response()->json(['status'=>'error','message'=>'Validation failed.','errors'=>$validator->errors()],422);
        DB::transaction(function()use($request,$user){foreach(['name','email','mobile','role']as$f)if($request->has($f))$user->{$f}=$request->input($f);if($request->has('is_activated'))$user->is_activated=(bool)$request->boolean('is_activated');$secret=$request->input('pin')??$request->input('password');if($secret!==null&&$secret!==''){$hash=Hash::make((string)$secret);$user->pin_hash=$hash;$user->password=$hash;AuthSession::where('user_id',$user->id)->update(['is_revoked'=>true]);DeviceBinding::where('user_id',$user->id)->update(['is_active'=>false]);}if($request->has('permissions'))$user->permissions=$this->sanitizePermissions($request->input('permissions',[]));$user->save();$this->syncOperatorAccount($user);});
        return response()->json(['status'=>'success','message'=>'User updated successfully.','user'=>$this->serializeUser($user->fresh())]);
    }
    public function destroy(Request $request,int $id):JsonResponse { $superAdmin=$this->getSuperAdmin($request);if(!$superAdmin)return $this->forbidden();$user=User::find($id);if(!$user)return response()->json(['status'=>'error','message'=>'User not found.'],404);if($user->isSuperAdmin()){if((int)$user->id===(int)$superAdmin->id)return response()->json(['status'=>'error','message'=>'A SuperAdmin cannot delete their own account.'],400);return response()->json(['status'=>'error','message'=>'User not found.'],404);}DB::transaction(function()use($user){AuthSession::where('user_id',$user->id)->delete();DeviceBinding::where('user_id',$user->id)->delete();DB::table('operator_accounts')->where('user_id',$user->id)->delete();$user->delete();});return response()->json(['status'=>'success','message'=>'User deleted successfully.']); }
    private function getSuperAdmin(Request $request):?User{$user=$request->user();if(!$user){$token=$request->bearerToken()??$request->header('X-SAFA-ACCESS-TOKEN')??$request->input('access_token');if($token){$payload=AuthJWTController::verifyJwt($token);if($payload&&isset($payload['user_id']))$user=User::find($payload['user_id']);}}return $user&&$user->isSuperAdmin()?$user:null;}
    private function isSuperAdmin(Request $request):bool{return $this->getSuperAdmin($request)!==null;}
    private function sanitizePermissions(mixed $permissions):array{if(!is_array($permissions))return User::defaultPermissions(false);$allowed=User::defaultPermissions(false);foreach($allowed as$key=>$default)if(array_key_exists($key,$permissions))$allowed[$key]=(bool)$permissions[$key];return $allowed;}
    private function syncOperatorAccount(User $user):void{if(!DB::getSchemaBuilder()->hasTable('operator_accounts'))return;DB::table('operator_accounts')->updateOrInsert(['user_id'=>$user->id],['name'=>$user->name,'email'=>$user->email,'mobile'=>$user->mobile,'role'=>$user->role,'pin_hash'=>$user->pin_hash,'is_activated'=>$user->is_activated,'permissions'=>json_encode($user->getFormattedPermissions()),'updated_at'=>now(),'created_at'=>$user->created_at??now()]);}
    private function serializeUser(User $user):array{return ['id'=>$user->id,'name'=>$user->name,'email'=>$user->email,'mobile'=>$user->mobile,'role'=>$user->role,'is_activated'=>(bool)$user->is_activated,'permissions'=>$user->getFormattedPermissions(),'created_at'=>$user->created_at?->toIso8601String(),'updated_at'=>$user->updated_at?->toIso8601String()];}
    private function forbidden():JsonResponse{return response()->json(['status'=>'error','message'=>'Forbidden. SuperAdmin access required.'],403);}
}
