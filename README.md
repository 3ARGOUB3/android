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

Every push runs the same build on GitHub Actions and publishes the APK as a GitHub release.
Latest APK: https://github.com/3ARGOUB3/android/releases/latest/download/masroufi.apk
(Android 6.0 or newer).

---

# Gestion du personnel (تسيير العمال) – PC

A Windows program (`personnel-desktop/`, with its logic in `personnel-core/`) for keeping the
disciplinary and salary registers of workers:

Suspension de salaire · Rétablissement de salaire · Mise en demeure · Questionnaire ·
Convocation pour entretien · Convocation pour affaire (sans date) · PV d'audition ·
Lettre de radiation · Suspension mise à l'essai · Rétablissement mise à l'essai

## Download

- Installer: https://github.com/3ARGOUB3/android/releases/latest/download/GestionPersonnel.msi
- Without installing: https://github.com/3ARGOUB3/android/releases/latest/download/GestionPersonnel-windows.zip
  (unzip, then run `GestionPersonnel.exe`)

## How it works

- **Import** the Excel file (.xlsx or .xls) already filled for a register. Before importing,
  choose the header row, the first line to import, and untick columns that are not needed.
  A workbook with one sheet per register can be imported at once; each sheet is matched to a
  register by its name and can be reassigned.
- **Registers** show as a table; click a line to edit it, or add new lines after the imported
  ones. The form shows the register's own columns:
  - typing a known *Matricule* fills *Nom prénom* and *Fonction* from any register;
  - name fields suggest matching workers;
  - date columns have a date picker, `N°` columns get the next number,
    other columns suggest values already used.
- **Travailleurs**: search a worker and see all their lines in every register, or start a new
  document for them.
- **Export** a register, or all of them, to .xlsx (one sheet per register).
- Columns can be renamed, added or removed per register.
- French and Arabic (switch in the menu). Escape goes back.

Data is saved in `%APPDATA%\GestionPersonnel` (`~/.gestion-personnel` on Mac/Linux).

```sh
./gradlew :personnel-core:test                # unit tests
./gradlew :personnel-desktop:run              # run on this computer
./gradlew :personnel-desktop:packageMsi       # Windows installer (on Windows)
```
