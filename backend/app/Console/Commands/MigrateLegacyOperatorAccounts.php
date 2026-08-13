<?php

namespace App\Console\Commands;

use App\Models\OperatorAccount;
use App\Models\User;
use App\Support\MobileNumber;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\DB;

class MigrateLegacyOperatorAccounts extends Command
{
    protected $signature = 'safa:migrate-legacy-operators {--dry-run : Report changes without writing data}';
    protected $description = 'Idempotently link legacy operator_accounts to canonical users without exposing PINs';

    public function handle(): int
    {
        $dryRun = (bool) $this->option('dry-run');
        $migrated = 0;
        $linked = 0;
        $ambiguous = 0;

        foreach (OperatorAccount::query()->orderBy('id')->cursor() as $operator) {
            $mobile = MobileNumber::normalize((string) $operator->mobile);
            if ($mobile === '') {
                $this->warn("operator #{$operator->id}: invalid mobile; skipped");
                continue;
            }

            $users = User::query()->where('mobile', $mobile)->get();
            if ($users->count() > 1) {
                $this->error("operator #{$operator->id}: duplicate canonical mobile {$mobile}; skipped");
                $ambiguous++;
                continue;
            }

            if ($operator->user_id) {
                $user = User::find($operator->user_id);
                if (!$user || MobileNumber::normalize((string) $user->mobile) !== $mobile) {
                    $this->error("operator #{$operator->id}: invalid user linkage; skipped");
                    $ambiguous++;
                    continue;
                }
                $linked++;
                continue;
            }

            if ($users->count() === 1) {
                if (!$dryRun) {
                    DB::transaction(function () use ($operator, $users) {
                        $operator->user_id = $users->first()->id;
                        $operator->save();
                    });
                }
                $linked++;
                continue;
            }

            if (!$operator->pin_hash || !is_string($operator->pin_hash)) {
                $this->error("operator #{$operator->id}: missing PIN hash; skipped");
                $ambiguous++;
                continue;
            }

            if (!$dryRun) {
                DB::transaction(function () use ($operator, $mobile) {
                    $user = User::create([
                        'name' => $operator->name,
                        'email' => $operator->email ?: ($mobile . '@safa.local'),
                        'mobile' => $mobile,
                        'role' => $operator->role,
                        'pin_hash' => $operator->pin_hash,
                        'password' => $operator->pin_hash,
                        'is_activated' => (bool) $operator->is_activated,
                        'permissions' => is_array($operator->permissions) ? $operator->permissions : [],
                    ]);
                    $operator->user_id = $user->id;
                    $operator->save();
                });
            }
            $migrated++;
        }

        $this->info("linked={$linked} migrated={$migrated} ambiguous={$ambiguous}" . ($dryRun ? ' (dry-run)' : ''));
        return $ambiguous > 0 ? self::FAILURE : self::SUCCESS;
    }
}
