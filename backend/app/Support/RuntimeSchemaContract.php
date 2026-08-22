<?php

namespace App\Support;

final class RuntimeSchemaContract
{
    /**
     * Tables and columns whose absence makes the current API release unsafe to
     * serve. Keep this contract release-owned so health, deployment and tests do
     * not maintain separate partial schema inventories.
     *
     * @return array<string, list<string>>
     */
    public static function requiredColumns(): array
    {
        return [
            'migrations' => ['id', 'migration', 'batch'],
            'users' => ['id', 'mobile', 'pin_hash', 'role', 'permissions', 'is_activated'],
            'operator_accounts' => ['id', 'user_id', 'mobile', 'role', 'permissions', 'is_activated'],
            'device_bindings' => ['id', 'user_id', 'device_uuid', 'fingerprint_hash', 'is_active'],
            'auth_sessions' => ['id', 'user_id', 'device_uuid', 'access_token', 'refresh_token', 'session_token', 'is_revoked', 'expires_at'],
            'accounts' => ['id', 'owner_user_id', 'balance'],
            'user_account_shares' => ['id', 'owner_user_id', 'account_id', 'shared_with_user_id', 'permissions_override'],
            'customers' => ['id', 'account_id', 'local_id', 'name', 'phone', 'address', 'timestamp', 'deleted_at', 'sync_version', 'last_mutation_id'],
            'suppliers' => ['id', 'account_id', 'local_id', 'name', 'phone', 'timestamp', 'deleted_at', 'sync_version', 'last_mutation_id'],
            'rates' => ['id', 'account_id', 'currency_pair', 'rate'],
            'wallet_ledgers' => ['id', 'account_id', 'local_id', 'name', 'timestamp', 'deleted_at', 'sync_version', 'last_mutation_id'],
            'supplier_deposits' => ['id', 'account_id', 'local_id', 'supplier_id', 'amount_sar', 'rate', 'amount_bdt', 'paid_bdt', 'timestamp', 'deleted_at', 'sync_version', 'last_mutation_id'],
            'wallet_batches' => ['id', 'account_id', 'local_id', 'ledger_id', 'rate', 'initial_bdt', 'remaining_bdt', 'supplier_id', 'supplier_deposit_id', 'timestamp', 'deleted_at', 'sync_version', 'last_mutation_id'],
            'transactions' => [
                'id', 'account_id', 'local_id', 'customer_id', 'supplier_id', 'wallet_batch_id',
                'amount_sar', 'customer_rate', 'supplier_rate', 'amount_bdt', 'sar_collected',
                'bdt_disbursed', 'receiver_name', 'receiver_phone', 'receiver_account_type',
                'receiver_account_no', 'timestamp', 'deleted_at', 'sync_version', 'last_mutation_id',
            ],
            'expenses_incomes' => ['id', 'account_id', 'local_id', 'title', 'amount', 'currency', 'is_expense', 'category', 'timestamp', 'deleted_at', 'sync_version', 'last_mutation_id'],
            'sync_mutations' => ['id', 'account_id', 'mutation_id', 'entity', 'local_id', 'server_id', 'operation', 'sync_version', 'response'],
            'system_settings' => ['id', 'account_id', 'app_name', 'app_logo_url', 'app_version', 'local_currency', 'foreign_currency', 'rate_based_mode', 'supplier_rate_enabled', 'wallet_rate_enabled'],
            'app_versions' => ['id', 'platform', 'min_version_code', 'latest_version_code', 'force_update', 'update_url'],
            'audit_logs' => ['id', 'user_id', 'action', 'endpoint', 'payload', 'ip_address'],
        ];
    }

    /**
     * Unique-key capabilities required for tenant isolation/idempotency. Index
     * names are intentionally not part of the contract because MySQL and SQLite
     * can name equivalent indexes differently.
     *
     * @return array<string, list<list<string>>>
     */
    public static function requiredUniqueIndexes(): array
    {
        return [
            'device_bindings' => [['user_id', 'device_uuid']],
            'user_account_shares' => [['owner_user_id', 'shared_with_user_id', 'account_id']],
            'customers' => [['account_id', 'local_id']],
            'suppliers' => [['account_id', 'local_id']],
            'wallet_ledgers' => [['account_id', 'local_id']],
            'supplier_deposits' => [['account_id', 'local_id']],
            'wallet_batches' => [['account_id', 'local_id']],
            'transactions' => [['account_id', 'local_id']],
            'expenses_incomes' => [['account_id', 'local_id']],
            'sync_mutations' => [['account_id', 'mutation_id']],
            'system_settings' => [['account_id']],
        ];
    }
}
