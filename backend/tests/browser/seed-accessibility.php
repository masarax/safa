<?php

declare(strict_types=1);

use App\Models\Account;
use App\Models\User;
use Illuminate\Contracts\Console\Kernel;
use Illuminate\Support\Facades\Hash;

require __DIR__ . '/../../vendor/autoload.php';
$app = require __DIR__ . '/../../bootstrap/app.php';
$app->make(Kernel::class)->bootstrap();

$user = User::query()->updateOrCreate(
    ['mobile' => '0500000000'],
    [
        'name' => 'Accessibility Admin',
        'email' => 'accessibility@safa.local',
        'pin_hash' => Hash::make('123456'),
        'password' => Hash::make('123456'),
        'role' => 'superadmin',
        'is_activated' => true,
        'permissions' => User::defaultPermissions(true),
    ]
);

Account::query()->updateOrCreate(
    ['owner_user_id' => $user->id],
    ['name' => 'Accessibility Workspace', 'balance' => 0]
);
