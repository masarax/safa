<?php

namespace Database\Seeders;

use App\Models\User;
use App\Support\MobileNumber;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use RuntimeException;

class InitialSuperAdminSeeder extends Seeder
{
    public function run(): void
    {
        if (!Schema::hasTable('users')) {
            throw new RuntimeException('Run database migrations before seeding.');
        }

        if (User::query()->where('role', User::ROLE_SUPERADMIN)->where('is_activated', true)->exists()) {
            return;
        }

        if (User::query()->where('role', User::ROLE_SUPERADMIN)->exists()) {
            throw new RuntimeException('An inactive Super Admin already exists and must be recovered manually.');
        }

        $config = (array) config('safa.initial_admin', []);
        $name = trim((string) ($config['name'] ?? ''));
        $mobileInput = trim((string) ($config['mobile'] ?? ''));
        $email = strtolower(trim((string) ($config['email'] ?? '')));
        $pin = trim((string) ($config['pin'] ?? ''));

        // Completely absent bootstrap credentials are intentional: database
        // seeding must still be able to apply non-secret release/reference data.
        // The first Super Admin can then be provisioned explicitly through the
        // supported CLI/web bootstrap flow. Partial configuration remains an
        // operator error and is rejected by the validation below.
        if ($name === '' && $mobileInput === '' && $email === '' && $pin === '') {
            return;
        }

        $mobile = MobileNumber::normalize($mobileInput);

        if ($name === ''
            || $mobile === ''
            || !MobileNumber::isValid($mobile)
            || filter_var($email, FILTER_VALIDATE_EMAIL) === false
            || preg_match('/^\d{6}$/', $pin) !== 1) {
            throw new RuntimeException('Initial Super Admin server configuration is incomplete or invalid.');
        }

        if (User::query()->where('mobile', $mobile)->orWhere('email', $email)->exists()) {
            throw new RuntimeException('Initial Super Admin identity conflicts with an existing user.');
        }

        DB::transaction(function () use ($name, $mobile, $email, $pin): void {
            $hash = Hash::make($pin);
            User::query()->create([
                'name' => $name,
                'mobile' => $mobile,
                'email' => $email,
                'password' => $hash,
                'pin_hash' => $hash,
                'role' => User::ROLE_SUPERADMIN,
                'is_activated' => true,
                'permissions' => User::permissionsForRole(User::ROLE_SUPERADMIN),
            ]);
        });
    }
}
