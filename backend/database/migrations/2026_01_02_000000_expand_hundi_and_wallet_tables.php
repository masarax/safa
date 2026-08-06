<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void
    {
        Schema::table('transactions', function (Blueprint $table) {
            $table->unsignedBigInteger('customer_id')->default(0)->after('local_id');
            $table->unsignedBigInteger('supplier_id')->default(0)->after('customer_id');
            $table->decimal('amount_sar', 15, 2)->default(0.00)->after('amount');
            $table->decimal('customer_rate', 10, 4)->default(0.0000)->after('amount_sar');
            $table->decimal('supplier_rate', 10, 4)->default(0.0000)->after('customer_rate');
            $table->decimal('amount_bdt', 15, 2)->default(0.00)->after('supplier_rate');
            $table->string('receiver_name')->nullable()->after('amount_bdt');
            $table->string('receiver_phone', 50)->nullable()->after('receiver_name');
            $table->string('receiver_account_type', 50)->nullable()->after('receiver_phone');
            $table->string('receiver_account_no', 100)->nullable()->after('receiver_account_type');
            $table->unsignedBigInteger('wallet_batch_id')->default(0)->after('receiver_account_no');
            $table->text('notes')->nullable()->after('wallet_batch_id');
        });

        Schema::create('wallet_ledgers', function (Blueprint $table) {
            $table->id();
            $table->foreignId('account_id')->constrained('accounts')->onDelete('cascade');
            $table->unsignedBigInteger('local_id')->default(0);
            $table->string('name');
            $table->unsignedBigInteger('timestamp')->nullable();
            $table->timestamps();
        });

        Schema::create('wallet_batches', function (Blueprint $table) {
            $table->id();
            $table->foreignId('account_id')->constrained('accounts')->onDelete('cascade');
            $table->unsignedBigInteger('local_id')->default(0);
            $table->unsignedBigInteger('ledger_id')->default(0);
            $table->decimal('rate', 10, 4);
            $table->decimal('initial_bdt', 15, 2);
            $table->decimal('remaining_bdt', 15, 2);
            $table->unsignedBigInteger('supplier_id')->default(0);
            $table->unsignedBigInteger('supplier_deposit_id')->default(0);
            $table->text('notes')->nullable();
            $table->unsignedBigInteger('timestamp')->nullable();
            $table->timestamps();
        });

        Schema::create('supplier_deposits', function (Blueprint $table) {
            $table->id();
            $table->foreignId('account_id')->constrained('accounts')->onDelete('cascade');
            $table->unsignedBigInteger('local_id')->default(0);
            $table->unsignedBigInteger('supplier_id')->default(0);
            $table->decimal('amount_sar', 15, 2);
            $table->decimal('rate', 10, 4);
            $table->decimal('amount_bdt', 15, 2);
            $table->decimal('paid_bdt', 15, 2)->default(0.00);
            $table->string('transaction_type', 50)->default('SAR_GIVEN');
            $table->text('notes')->nullable();
            $table->unsignedBigInteger('timestamp')->nullable();
            $table->timestamps();
        });

        Schema::create('expenses_incomes', function (Blueprint $table) {
            $table->id();
            $table->foreignId('account_id')->constrained('accounts')->onDelete('cascade');
            $table->unsignedBigInteger('local_id')->default(0);
            $table->string('title');
            $table->decimal('amount', 15, 2);
            $table->string('currency', 10)->default('BDT');
            $table->boolean('is_expense')->default(true);
            $table->string('category', 50)->default('General');
            $table->unsignedBigInteger('timestamp')->nullable();
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('expenses_incomes');
        Schema::dropIfExists('supplier_deposits');
        Schema::dropIfExists('wallet_batches');
        Schema::dropIfExists('wallet_ledgers');

        Schema::table('transactions', function (Blueprint $table) {
            $table->dropColumn([
                'customer_id', 'supplier_id', 'amount_sar', 'customer_rate', 'supplier_rate',
                'amount_bdt', 'receiver_name', 'receiver_phone', 'receiver_account_type',
                'receiver_account_no', 'wallet_batch_id', 'notes'
            ]);
        });
    }
};
