# NephroCare 1.0 – رفيق مريض الكلى

An offline Android app for people with kidney failure and on dialysis: health education,
personal tracking (dry weight, blood pressure, labs, fluids, medicines, sessions, symptoms),
reminders, prayer times and adhkar, and a PDF summary for the doctor.
Arabic (RTL), French, English and Spanish.

**Download the APK:** https://github.com/3ARGOUB3/android/releases/download/nephrocare-latest/nephrocare.apk
(Android 6.0 or newer; open the file on the phone and allow installation from this source).

User guides: [Arabic](docs/user-guide-ar.md) · [French](docs/user-guide-fr.md) · [English](docs/user-guide-en.md).
Database: [docs/database.md](docs/database.md).

## Medical safety

The app is for education and organising the patient's own information. It never diagnoses,
never proposes or changes a treatment or a dose, never sets the dry weight or the fluid to
remove, and its diet advice is general. Reference ranges are indicative. **All medical content
(`content-src/`, `tools/build_foods.py`, `core/.../Labs.kt`) must be reviewed by a nephrologist
before clinical use.**

## What is inside

| Section | Features |
|---|---|
| Dashboard | name, current and dry weight with difference, next session, last BP, today's doses with "Taken", fluids today, tip of the day, tiles to every section |
| Dry weight | dry/pre/post/home weights, gain between sessions (kg and % of dry weight), week/month/3-month charts, history |
| Blood pressure | systolic/diastolic/pulse with context (before/after dialysis, home), charts, pre/post averages, warnings for very high/low readings, awareness articles |
| Labs | Urée, Créatinine, K, P, Ca, Hb, Ferritine, Albumine, PTH, CRP, TSAT: results with date and unit (automatic conversion), chart, explanation, indicative general and dialysis ranges |
| Nutrition | guide (K, P, Na, protein, fluids, soaking method, labels, meal plan), 78-food table with filters, 17 Algerian dishes with ratings and tips, recipes |
| Fistula | illustration, daily thrill/redness/swelling/pain check with history, 8 care articles |
| Dialysis schedule | days and time, reminder before the session, evening "prepare your bag" reminder, sessions log (weights and BP feed the other charts), bag checklist |
| Medicines | name, prescribed dose (free text), times, reminders with a "Taken" action, today/history, information articles; never suggests doses |
| Prayer & adhkar | offline prayer times (58 cities or coordinates, 9 methods, Hanafi/standard Asr, per-prayer adjustment, reminders), morning/evening/after-prayer adhkar with counters, duas for the sick, full Quran with translations |
| Encyclopedia, support, emergency | 70 articles × 4 languages; emergency numbers and call buttons |
| Search | offline search across articles, labs, foods and dishes (Arabic diacritics and letter variants ignored) |
| Media | drawn illustrations (kidneys, fistula, dialysis circuit); video library (phone file or link, .srt/.vtt subtitles) |
| Diary, fluids, centers | symptoms/fatigue/appetite/sleep, questions for the doctor; fluid intake vs team allowance, 7-day chart; personal directory of centers searchable by city with call/map |
| Reports | PDF (A4, RTL in Arabic) with charts and tables for a chosen period, share/save |
| Privacy | data only on the phone, Android cloud backup disabled, AES-256 encrypted backup file, PIN lock with screenshot blocking, generic lock-screen notifications, "delete all my data" |
| Display | light/dark theme, three text sizes, large touch targets |

## Project layout

- `core/` – plain Kotlin, unit-tested: prayer-time calculation, lab definitions and unit
  conversions, weight-gain and schedule calculations, search and Arabic normalisation,
  backup encryption, content models.
- `app/` – Android app (Kotlin, Jetpack Compose, Material 3, Room, AlarmManager, Media3).
  `app/schemas/` receives the Room schema at build time.
- `content-src/<lang>.md` – educational content, compiled by `tools/build_content.py` to
  `app/src/main/assets/content/<lang>.json`.
- `tools/strings_src.py` – all UI strings in the four languages → `tools/build_strings.py`.
- `tools/build_foods.py` – foods and Algerian dishes → `assets/foods.json`.
- `tools/build_quran.py` – splits the quran-json package (CC BY-SA 4.0) into `assets/quran/`.
- `app/src/main/assets/adhkar.json` – adhkar and duas; Quranic passages are referenced, not retyped.

## Build

Requires JDK 17 and the Android SDK.

```sh
python3 tools/build_strings.py && python3 tools/build_content.py && python3 tools/build_foods.py
./gradlew :core:test :app:testDebugUnitTest   # logic tests + every screen rendered under Robolectric
./gradlew :app:assembleRelease                 # app/build/outputs/apk/release/app-release.apk
```

Every push that touches `nephrocare/` runs `.github/workflows/nephrocare.yml`, which checks the
generated files, runs the tests, builds the release APK and publishes it as the
`nephrocare-latest` release.

The APK is signed with `app/nephrocare.keystore` (password `nephrocare`) so each build can
update the previous install. That key is public: create a private key before any store release.
