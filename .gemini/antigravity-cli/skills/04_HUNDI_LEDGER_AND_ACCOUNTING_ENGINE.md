# SAFA System Skill 04: Hundi & Multi-Currency Ledger Accounting Engine

## 1. Accounting Domain Concepts
SAFA handles Hundi (Hawala) financial operations across multiple foreign currencies (AED, BDT, USD, SAR, MYR, INR, OMR, QAR, KWD).

### Key Entities:
1. **Customer Ledger**: Tracks money owed by/to remittance senders and receivers.
2. **Supplier Ledger**: Tracks settlement accounts with foreign Hundi agents and liquidity providers.
3. **Multi-Currency Wallets**: Cash, Bank, and Mobile Financial Services (Bkash, Nagad) holding balances in specified currencies.
4. **Exchange Rates**: Dynamic buying and selling rate pairs defining cross-currency conversion ratios.

---

## 2. Dynamic Transaction Calculation Rules
Every transaction calculation in `com.safa.account.ui.screens.TransactionScreen` and `backend/app/Services/HundiCalculationService.php` must obey:

```
[Send Amount (e.g. 1000 AED)] * [Exchange Rate (e.g. 32.50 BDT/AED)] = [Payout Amount (e.g. 32,500 BDT)]

Profit Margin Calculation:
[Selling Rate - Buying Rate] * [Send Amount] = [Net Hundi Profit]
```

### Double-Entry Ledger Posting Rules:
- **Customer Payment Received**: Debit `Wallet/Cash`, Credit `Customer Account`.
- **Hundi Remittance Sent**: Debit `Customer Account`, Credit `Supplier/Agent Account`.
- **Expense Incurred**: Debit `Expense Account`, Credit `Wallet/Cash`.

---

## 3. Data Integrity & Precision Rules
- Use `BigDecimal` in Kotlin and `DECIMAL(16, 4)` in Laravel for all currency calculations to prevent floating point inaccuracies.
- Ensure all transactions record UTC timestamps alongside local device time zone offsets.
