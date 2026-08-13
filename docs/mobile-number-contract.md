# SAFA Mobile Number Contract

The Android client and Laravel API use the same canonical mobile-number contract.

## Normalization

Before validation or lookup:

1. Trim surrounding whitespace.
2. Convert supported Unicode decimal digits to ASCII digits:
   - Arabic-Indic `٠١٢٣٤٥٦٧٨٩`
   - Persian `۰۱۲۳۴۵۶۷۸۹`
   - Bengali `০১২৩৪৫৬৭৮৯`
3. Remove formatting characters such as spaces, hyphens and parentheses.
4. Convert supported international forms to local form:
   - `+966` / `966` + Saudi mobile digits -> `05xxxxxxxx`
   - `+880` / `880` + Bangladesh mobile digits -> `01xxxxxxxxx`
5. Validate the canonical local representation.

## Canonical representation

The stored and lookup value contains ASCII digits only and starts with `0`.

Examples:

| Input | Canonical |
| --- | --- |
| `0536-308-965` | `0536308965` |
| `০৫৩৬ ৩০৮ ৯৬৫` | `0536308965` |
| `٠٥٣٦ ٣٠٨ ٩٦٥` | `0536308965` |
| `+966 50 123 4567` | `0501234567` |
| `+880 1712-345-678` | `01712345678` |

## Authentication

Normalization occurs before credential lookup. Invalid or ambiguous identities must be rejected; normalization must never select an arbitrary account.

The Laravel implementation is `App\\Support\\MobileNumber`. Android uses `MobileNumberNormalizer` for client-side input preparation. The backend remains authoritative for validation and account lookup.
