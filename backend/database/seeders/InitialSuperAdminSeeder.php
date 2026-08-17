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

        if (!$this->canPromptInteractively()) {
            $this->command?->warn('No Super Admin exists. Run php artisan db:seed interactively to create the first Super Admin.');
            return;
        }

        $name = trim((string) $this->command->ask('Super Admin name'));
        $mobile = MobileNumber::normalize((string) $this->command->ask('Mobile number'));
        $email = strtolower(trim((string) $this->command->ask('Email address')));
        $pin = trim((string) $this->command->secret('6-digit PIN'));
        $pinConfirmation = trim((string) $this->command->secret('Confirm 6-digit PIN'));

        if ($name === ''
            || $mobile === ''
            || !MobileNumber::isValid($mobile)
            || filter_var($email, FILTER_VALIDATE_EMAIL) === false
            || preg_match('/^\d{6}$/', $pin) !== 1
            || !hash_equals($pin, $pinConfirmation)) {
            throw new RuntimeException('Initial Super Admin input is invalid. Name, valid mobile/email and matching 6-digit PIN values are required.');
        }

        if (User::query()->where('mobile', $mobile)->orWhereRaw('LOWER(email) = ?', [$email])->exists()) {
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
        }, 3);

        $this->command->info('Initial Super Admin created successfully.');
    }

    private function canPromptInteractively(): bool
    {
        if ($this->command === null) {
            return false;
        }

        $arguments = array_values(array_map('strval', $_SERVER['argv'] ?? []));
        if (in_array('--no-interaction', $arguments, true) || in_array('-n', $arguments, true)) {
            return false;
        }

        $artisanCommand = $arguments[1] ?? '';
        if ($artisanCommand === 'db:seed') {
            return true;
        }

        return in_array($artisanCommand, ['migrate:fresh', 'migrate:refresh'], true)
            && in_array('--seed', $arguments, true);
    }
}
