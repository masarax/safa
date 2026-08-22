<?php

declare(strict_types=1);

use App\Models\Account;
use App\Models\User;
use App\Support\ReleaseUpdateState;
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

// The browser accessibility suite intentionally exercises the normal login and
// authenticated workspace, not the release-update gate. migrate:fresh creates
// the release-state table but does not mark the current application fingerprint
// as applied, so make that fixture boundary explicit after all migrations and
// deterministic seed data are in place.
ReleaseUpdateState::markApplied();
