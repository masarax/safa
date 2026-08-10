<?php

namespace App\Console\Commands;

use App\Models\Account;
use App\Models\User;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

class ProvisionSuperAdmin extends Command
{
    protected $signature = 'safa:provision-admin
        {--name= : SuperAdmin display name}
        {--mobile= : Login mobile number}
        {--email= : Login email address}
        {--pin= : Six-digit login PIN}';

    protected $description = 'Create or update the initial SAFA SuperAdmin without storing personal credentials in migrations or source code.';

    public function handle(): int
    {
        $name = trim((string) ($this->option('name') ?: $this->ask('SuperAdmin name')));
        $mobile = $this->normalizeMobile((string) ($this->option('mobile') ?: $this->ask('Mobile number')));
        $email = trim((string) ($this->option('email') ?: $this->ask('Email address')));
        $pin = trim((string) ($this->option('pin') ?: $this->secret('Six-digit PIN')));

        if ($name === '' || $mobile === '' || !filter_var($email, FILTER_VALIDATE_EMAIL) || !preg_match('/^\d{6}$/', $pin)) {
            $this->error('Invalid administrator data. Name, mobile, valid email and exactly six PIN digits are required.');
            return self::FAILURE;
        }

        $user = User::where('mobile', $mobile)->orWhere('email', $email)->first();
        $now = now();

        if (!$user) {
            $user = new User();
        }

        $user->name = $name;
        $user->mobile = $mobile;
        $user->email = $email;
        $user->password = Hash::make($pin);
        $user->pin_hash = Hash::make($pin);
        $user->role = 'superadmin';
        $user->is_activated = true;
        $user->permissions = User::defaultPermissions(true);
        $user->save();

        $account = Account::firstOrCreate(
            ['name' => 'SAFA Account'],
            ['balance' => 0]
        );
        if (!$account->owner_user_id) {
            $account->owner_user_id = $user->id;
            $account->save();
        }

        $user->ownedAccountShares()->delete();

        $this->info('SAFA SuperAdmin provisioned successfully.');
        $this->line('User ID: ' . $user->id);
        $this->line('Mobile: ' . $user->mobile);
        $this->line('Account ID: ' . $account->id);
        $this->warn('The PIN is intentionally not displayed or stored in application source code.');

        return self::SUCCESS;
    }

    private function normalizeMobile(string $mobile): string
    {
        $digits = preg_replace('/\D+/', '', trim($mobile)) ?? '';
        return Str::limit($digits, 30, '');
    }
}
