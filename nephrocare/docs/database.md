# NephroCare database

Local SQLite database `nephrocare.db` managed by Room (`app/src/main/java/com/nephrocare/data/`),
stored in the app's private storage and excluded from Android cloud backup and device transfer.
The exact schema is exported at build time to `app/schemas/com.nephrocare.data.AppDatabase/1.json`
(included in the CI artifact `nephrocare-apk`). Times are epoch milliseconds.

| Table | Columns | Notes |
|---|---|---|
| `weights` | id, time, kind, kg, sessionId, note | kind = DRY (prescribed), PRE, POST, HOME; sessionId links weights created from a session |
| `blood_pressure` | id, time, systolic, diastolic, pulse, context, note | context = PRE, POST, HOME, OTHER |
| `lab_results` | id, code, value, unit, date, note | code = UREA, CREAT, K, PHOS, CA, HB, FERRITIN, ALB, PTH, CRP, TSAT; value in the lab's unit |
| `dialysis_sessions` | id, date, durationMin, preWeight, postWeight, pre/postSystolic, pre/postDiastolic, note | |
| `medications` | id, name, dose, form, times, instructions, active, reminders, createdAt | times = "08:00;20:00"; dose is free text as prescribed |
| `medication_intakes` | id, medId, medName, dose, scheduledAt, takenAt, status | status = TAKEN or SKIPPED |
| `diary` | id, date, fatigue, appetite, sleepHours, sleepQuality, symptoms, note | symptoms = comma-separated codes |
| `doctor_questions` | id, created, text, answered, answer | |
| `fluids` | id, time, ml, kind | kind = WATER, HOT, SOUP, MILK, OTHER |
| `fistula_checks` | id, time, thrill, redness, swelling, pain, note | |
| `centers` | id, name, type, city, address, phone, notes | type = CENTER, HOSPITAL, NEPHROLOGIST |
| `videos` | id, title, uri, subtitleUri, language, category, description | |

Settings (profile, dialysis schedule, prayer settings, display, PIN hash) are JSON in the private
SharedPreferences file `nephrocare_settings`.

## Backup file (`.ncb`)

`"NCB1"` · iterations (int32) · salt (16 bytes) · IV (12 bytes) · AES-256-GCM ciphertext of a JSON
`BackupData` document (all tables and settings except the PIN). The key is derived from the user's
password with PBKDF2-HMAC-SHA256 (60 000 iterations). See `core/.../BackupCrypto.kt`.

## Offline content (assets)

- `content/<lang>.json` – articles, lab explanations and tips (from `content-src/`).
- `foods.json` – foods (K, P, Na mg and protein g per 100 g) and Algerian dishes.
- `adhkar.json` – adhkar and duas (Quranic passages referenced by surah:verse).
- `quran/` – index, Arabic text and fr/en/es translations per surah.
