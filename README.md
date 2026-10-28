# Masroufi (مصروفي)

An Android app for browsing personal spending exported from an expense-tracker app as an `.xls` file
(sheet "المعاملات" with columns الملاحظات، المجموعة، جهة الإتصال، الى (المحفظة)، المحفظة، العملة، التكلفة، القسم، القسم الرئيسي، التاريخ).

## Features

- **Import** the `.xls` export from the phone's files (the upload icon in the top bar). Importing replaces the saved data.
- **Overview**: total balance, wallet balances, income/spending for the latest month and its top categories.
- **Transactions**: every transaction grouped by day, with search.
- **Reports**: income/spending per month or year, spending by category (tap a category to see its sub-categories) and a 12-month chart.
- English and Arabic UI (follows the phone language, right-to-left in Arabic).

Wallet balances are computed only from the transactions in the file, so a wallet whose opening
balance was never recorded can show a negative number.

## Project layout

- `core/` – plain Kotlin: `.xls` parsing (JExcelApi), Arabic date parsing, totals and balances, JSON storage. Unit-tested.
- `app/` – the Android app (Jetpack Compose, Material 3).

## Build

Requires JDK 17 and the Android SDK.

```sh
./gradlew :core:test          # unit tests
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug/
```

Every push runs the same build on GitHub Actions; the debug APK is attached to the run as the
`masroufi-debug-apk` artifact, so you can download it and install it on the phone.
