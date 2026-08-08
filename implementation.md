তোমার audit findings আমি review করেছি। Findings গুরুত্বপূর্ণ এবং implementation এগিয়ে নেওয়া যাবে, কিন্তু সরাসরি code modification শুরু করার আগে নিচের verification phase সম্পূর্ণ করো।

## IMPORTANT — DO NOT MODIFY CODE YET

প্রথমে একটি final technical verification report তৈরি করো।

### 1. Entity Identity Matrix

প্রতিটি syncable entity-এর জন্য exact table দাও:

* Android Room Entity
* Room primary key (`id`)
* local_id
* serverId
* Laravel Model
* server primary key
* foreign keys
* parent entity
* current sync fields

Entities:

* Customer
* Supplier
* RemittanceTransaction
* SupplierDeposit
* ExpenseIncome
* WalletLedger
* WalletBatch

বিশেষভাবে নিশ্চিত করো Android-এর `id` বর্তমানে local ID নাকি server ID হিসেবে কোথাও ব্যবহৃত হচ্ছে।

### 2. Full Dependency Graph

Code-এর actual foreign-key relationships দেখে dependency graph তৈরি করো।

শুধু অনুমান করে sync order নির্ধারণ করবে না।

প্রতিটি entity-এর ক্ষেত্রে লিখবে:

```text
Entity
↓
Depends on
↓
Why
↓
Required server ID
```

এরপর সেই graph থেকে actual sync order নির্ধারণ করবে।

### 3. Existing Sync Implementation

পুরো Android code থেকে verify করো:

```text
Create
Update
Delete
↓
DAO
↓
Repository
↓
Sync state
↓
SyncManager
↓
Worker
↓
Retrofit
↓
/sync/up
```

প্রতিটি entity-এর জন্য কোথায় sync queue/state তৈরি হচ্ছে সেটা file + function name সহ দেখাও।

### 4. DO NOT Assume Missing Sync State Means No Sync

তুমি বলেছ entities-এ `syncStatus/serverId` নেই।

কিন্তু verify করো বর্তমান code অন্য কোনো mechanism দিয়ে pending records track করছে কিনা।

যেমন:

* timestamps
* dirty flags
* local/server ID comparison
* pending tables
* sync metadata
* queue tables
* separate sync state table

যদি থাকে, নতুন syncStatus যোগ করার আগে existing mechanism-এর সঙ্গে conflict করবে কিনা নির্ধারণ করো।

### 5. Server ID Strategy

Final strategy explicitly define করো:

```text
Room id
local_id
serverId
```

কোনটি কোন purpose-এ ব্যবহৃত হবে।

বিশেষ করে নিশ্চিত করো:

```text
Android local Customer ID
        ↓
account_id + local_id
        ↓
server customers.id
        ↓
server transaction.customer_id
```

একইভাবে:

```text
Supplier
Wallet
Ledger
Deposit
Transaction
```

সব relationship-এর জন্য mapping verify করো।

### 6. Sync Status State Machine

Final state machine code-এর existing behaviour দেখে তৈরি করো।

Minimum expected states:

```text
PENDING_CREATE
PENDING_UPDATE
PENDING_DELETE
SYNCING
SYNCED
SYNC_FAILED
```

যদি existing project-এর জন্য অন্য naming better হয়, সেটা ব্যবহার করতে পারো।

### 7. Sync Down Conflict Policy

Explicitly define:

* server newer হলে কী হবে
* local newer হলে কী হবে
* local pending হলে server data overwrite হবে কিনা
* delete conflict কীভাবে handle হবে
* timestamps কোন timezone/format-এ থাকবে

### 8. Silent Failure Audit

পুরো Android + Laravel code search করে identify করো:

```text
catch {}
continue
return success
HTTP 200 with rejected data
ignored validation errors
ignored Retrofit errors
```

যে জায়গায় sync failure silently disappear করতে পারে সেগুলো list করো।

### 9. Production URL Audit

পুরো repository search করে verify করো:

```text
localhost
127.0.0.1
10.0.2.2
192.168.x.x
development API URL
production API URL
```

Release build কোন URL ব্যবহার করছে সেটা exact file/config থেকে prove করো।

### 10. API Route Consistency

Android-এর প্রতিটি Retrofit endpoint-এর বিপরীতে Laravel route verify করো।

একটি table দাও:

```text
Android endpoint | Laravel route | Used? | Correct?
```

বিশেষ করে:

```text
/customers
/suppliers
/deposits
/transactions
/sync/up
/sync/down
```

Direct CRUD endpoint এবং central sync endpoint-এর মধ্যে কোনো conflicting architecture আছে কিনা দেখো।

### 11. Database Migration Safety

Room current schema version এবং actual schema verify করো।

তারপর:

```text
Current version
↓
Required version
↓
Migration SQL
↓
Existing data preservation
```

প্রমাণ করো।

`fallbackToDestructiveMigration()` সরানোর পর existing installations safely upgrade করতে পারবে কিনা verify করো।

### 12. Financial Data Safety

Ensure:

* no local data deletion on sync failure
* no production data deletion
* no destructive migration
* no silent rejection
* no duplicate records
* no cross-account data
* no incorrect foreign keys

### 13. FINAL VERIFICATION REPORT

এই verification phase শেষে আমাকে শুধু report দাও।

এই phase-এ code modify করবে না।

Report-এর শেষে লিখবে:

```text
VERIFICATION STATUS:
READY FOR IMPLEMENTATION
```

শুধুমাত্র তখনই implementation শুরু করবে যখন সব above points code থেকে verify করা হবে।

তারপর implementation হবে ছোট ছোট logically isolated steps-এ এবং প্রতিটি step-এর পরে tests run করতে হবে।
