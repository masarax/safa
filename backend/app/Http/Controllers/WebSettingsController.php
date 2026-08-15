<?php

namespace App\Http\Controllers;

use App\Models\AuthSession;
use App\Models\User;
use App\Support\MobileNumber;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;

class WebSettingsController extends Controller
{
    public function updatePersonal(Request $request)
    {
        $validated = $request->validate(['language' => ['required', 'in:en,bn']]);
        $request->session()->put('safa_web_language', $validated['language']);

        return response()->json([
            'status' => 'success',
            'message' => 'Personal web settings updated successfully.',
            'language' => $validated['language'],
        ]);
    }

    /** Update only the authenticated user's identity, never application branding. */
    public function updateProfile(Request $request)
    {
        $user = $request->user();
        if (!$user || !$user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Authenticated user required.'], 401);
        }

        $validated = $request->validate([
            'name' => ['required', 'string', 'max:255'],
            'mobile' => ['required', 'string', 'max:30'],
        ]);
        $name = trim((string) $validated['name']);
        $mobile = MobileNumber::normalize((string) $validated['mobile']);
        if ($name === '' || $mobile === '' || !MobileNumber::isValid($mobile)) {
            return response()->json([
                'status' => 'error',
                'message' => 'Enter a valid name and mobile number.',
                'errors' => ['mobile' => ['The mobile number is not valid.']],
            ], 422);
        }

        if (User::query()->where('mobile', $mobile)->whereKeyNot($user->id)->exists()) {
            return response()->json([
                'status' => 'error',
                'message' => 'That mobile number is already assigned to another user.',
                'errors' => ['mobile' => ['The mobile number has already been taken.']],
            ], 422);
        }
        if (Schema::hasTable('operator_accounts') && DB::table('operator_accounts')
            ->where('mobile', $mobile)
            ->where(function ($query) use ($user) {
                $query->whereNull('user_id')->orWhere('user_id', '!=', $user->id);
            })->exists()) {
            return response()->json([
                'status' => 'error',
                'message' => 'That mobile number is already assigned to another operator.',
                'errors' => ['mobile' => ['The mobile number has already been taken.']],
            ], 422);
        }

        DB::transaction(function () use ($user, $name, $mobile): void {
            $oldMobile = (string) ($user->mobile ?? '');
            $locked = User::query()->whereKey($user->id)->lockForUpdate()->firstOrFail();
            $locked->name = $name;
            $locked->mobile = $mobile;
            $locked->save();

            if (Schema::hasTable('operator_accounts')) {
                DB::table('operator_accounts')
                    ->where(function ($query) use ($locked, $oldMobile) {
                        $query->where('user_id', $locked->id);
                        if ($oldMobile !== '') $query->orWhere('mobile', $oldMobile);
                    })
                    ->update(['user_id' => $locked->id, 'name' => $name, 'mobile' => $mobile, 'updated_at' => now()]);
            }
        });

        $fresh = $user->fresh();
        return response()->json([
            'status' => 'success',
            'message' => 'Your profile was updated successfully.',
            'user' => ['id' => (int) $fresh->id, 'name' => $fresh->name, 'mobile' => $fresh->mobile, 'email' => $fresh->email, 'role' => $fresh->role],
        ]);
    }

    /**
     * Change the authenticated user's six-digit credential from the web app.
     * The current browser session remains valid; API sessions and other
     * database-backed browser sessions are revoked after a credential change.
     */
    public function changePin(Request $request)
    {
        $user = $request->user();
        if (!$user || !$user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Authenticated user required.'], 401);
        }

        $currentPin = $this->normalizeDigits(trim((string) $request->input('current_pin')));
        $newPin = $this->normalizeDigits(trim((string) $request->input('new_pin')));
        $confirmPin = $this->normalizeDigits(trim((string) $request->input('new_pin_confirmation')));
        if (!preg_match('/^\d{6}$/', $currentPin) || !preg_match('/^\d{6}$/', $newPin)) {
            return response()->json(['status' => 'error', 'message' => 'Both current and new PIN must contain exactly six digits.', 'errors' => ['pin' => ['A six-digit current PIN and new PIN are required.']]], 422);
        }
        if (!hash_equals($newPin, $confirmPin)) {
            return response()->json(['status' => 'error', 'message' => 'New PIN confirmation does not match.', 'errors' => ['new_pin_confirmation' => ['The new PIN confirmation must match.']]], 422);
        }
        if (hash_equals($currentPin, $newPin)) {
            return response()->json(['status' => 'error', 'message' => 'The new PIN must be different from the current PIN.', 'errors' => ['new_pin' => ['Choose a different six-digit PIN.']]], 422);
        }

        $currentSessionId = $request->session()->getId();
        $changed = DB::transaction(function () use ($user, $currentPin, $newPin, $currentSessionId): bool {
            $lockedUser = User::query()->whereKey($user->id)->lockForUpdate()->first();
            if (!$lockedUser || !$lockedUser->is_activated) return false;
            $valid = false;
            foreach (array_filter([$lockedUser->pin_hash, $lockedUser->password]) as $hash) {
                try { if (Hash::check($currentPin, (string) $hash)) { $valid = true; break; } } catch (\Throwable) { }
            }
            if (!$valid) return false;

            $newHash = Hash::make($newPin);
            $lockedUser->pin_hash = $newHash;
            $lockedUser->password = $newHash;
            $lockedUser->save();
            if (Schema::hasTable('auth_sessions')) AuthSession::query()->where('user_id', $lockedUser->id)->update(['is_revoked' => true]);
            if (Schema::hasTable('sessions')) DB::table('sessions')->where('user_id', $lockedUser->id)->where('id', '!=', $currentSessionId)->delete();
            if (Schema::hasTable('operator_accounts')) {
                DB::table('operator_accounts')->where(function ($query) use ($lockedUser) {
                    $query->where('user_id', $lockedUser->id);
                    if ($lockedUser->mobile) $query->orWhere('mobile', $lockedUser->mobile);
                })->update(['pin_hash' => $newHash, 'updated_at' => now()]);
            }
            return true;
        });

        if (!$changed) return response()->json(['status' => 'error', 'message' => 'Current PIN is incorrect.'], 401);
        return response()->json(['status' => 'success', 'message' => 'PIN changed successfully.']);
    }

    private function normalizeDigits(string $value): string
    {
        return strtr($value, [
            '٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9',
            '۰'=>'0','۱'=>'1','۲'=>'2','۳'=>'3','۴'=>'4','۵'=>'5','۶'=>'6','۷'=>'7','۸'=>'8','۹'=>'9',
            '০'=>'0','১'=>'1','২'=>'2','৩'=>'3','৪'=>'4','৫'=>'5','৬'=>'6','৭'=>'7','৮'=>'8','৯'=>'9',
        ]);
    }
}
