# מועדון השחמט פתח תקווה — Petach Tikva Chess Club (Android)

An Android app (Java) for the Petach Tikva Chess Club. Blue‑and‑black theme with a
warm gold accent, **Hebrew by default** with a one‑tap switch to English (full RTL/LTR),
role‑based access for four user levels, an interactive puzzle board, a club library,
homework/assignments, tournament results, and an admin center.

> **Status: working foundation.** This project compiles and runs today against a
> local, seeded database so every screen is usable immediately. A few requirements
> are inherently multi‑device (emailing the admin on registration, password‑reset
> emails, real cross‑phone sync). Those need a backend — the app is structured so it
> can be added without reworking the UI. See **[What needs a backend](#what-needs-a-backend)**.

---

## Open & run

1. Open the project folder in **Android Studio** (Giraffe/Koala or newer). Let it sync
   Gradle — Android Studio will generate the Gradle wrapper if it is missing.
2. Pick an emulator or device (**Android 8.0 / API 26+**) and press **Run**.

Command line (once the SDK is configured and a `local.properties` with `sdk.dir` exists):

```bash
./gradlew assembleDebug
```

## Demo accounts (⚠️ change before publishing)

Seeded on first launch (`DbHelper.seed`). Passwords are stored **hashed** (PBKDF2).

| Role    | Email                   | Password      |
|---------|-------------------------|---------------|
| Admin   | `admin@ptchess.co.il`   | `Admin#2026`  |
| Tutor   | `tutor@ptchess.co.il`   | `Tutor#2026`  |
| Student | `child@ptchess.co.il`   | `Child#2026`  |
| Parent  | `parent@ptchess.co.il`  | `Parent#2026` |

There is also a **pending** registration (`pending@ptchess.co.il`) so the admin
approvals screen is not empty. Replace the seeded admin with your real credentials
before shipping.

## Roles & permissions (4 levels)

- **Student (חניך)** — base level on registration. Sees their groups, chess puzzles,
  their own tournament results, assignments, and the library.
- **Tutor (מאמן)** — sees/manages only their own groups, uploads puzzles (PGN) and
  library books, posts assignments, sees their students' results.
- **Parent (הורה)** — sees everything their linked children see, can add more children,
  and can contact the tutor/admin by phone or WhatsApp.
- **Admin (מנהל)** — full access: approves/rejects registrations, promotes users to any
  role, posts club news, and manages all content.

Registration creates a **Student pending admin approval**. If the new user supplies a
valid child's email+password during registration, they are linked and granted the
**Parent** role automatically (identity verified by the child's own credentials).

## Security

- **Password hashing:** salted **PBKDF2‑HMAC‑SHA256**, 120k iterations, constant‑time
  compare (`security/PasswordHasher.java`). No plaintext passwords anywhere.
- **SQL‑injection safe:** every query in `data/ClubRepository.java` uses `?` placeholders
  with bound arguments — no user input is ever concatenated into SQL.
- **Brute‑force lockout:** repeated failed logins for an email trigger an escalating
  temporary lock (5 → 10 → 20 → 40 → 60 min).
- **Encrypted session:** the logged‑in user id is kept in `EncryptedSharedPreferences`
  (AES‑256, key in the Android Keystore).
- **Input validation/sanitization:** email/password/name checks, length caps, control‑char
  stripping (`security/InputValidator.java`).
- **Transport & backup:** cleartext HTTP is blocked (`network_security_config.xml`); the
  credential DB and session prefs are excluded from cloud/device backups.
- **Release hardening:** R8/ProGuard shrinking + obfuscation, log stripping.
- Neutral “reset link sent” / “wrong credentials” messages avoid leaking which emails exist.

## Chess puzzles

- Custom `chess/ChessBoardView` renders an interactive board; tap a piece to see legal
  targets, tap a target to move. Solved by matching the stored solution line.
- `chess/ChessBoard` parses FEN and applies UCI moves (incl. castling, promotion,
  en passant) and generates pseudo‑legal moves.
- **PGN import** (tutor/admin): the **Import PGN** button on the Puzzles screen reads a
  `.pgn` file and converts each game's main line to a puzzle (`chess/PgnImporter`).
  Puzzles exported from Lichess/ChessBase import this way.

## Library

Everyone can browse. Tutors/admins add a book with **category** (opening/attack/defense/
endgame/psychology/strategy/tactics), **side** (white/black/both), **rating range**,
**language**, and a **PDF** (via the system file picker). Each book has **Open** and
**Print** actions.

## Localization

Hebrew is the default (`res/values`), English is in `res/values-en`. Change it any time
in **Settings**; the app re‑applies the locale and layout direction. Weekday names come
from a localized `string-array`.

## Replace the placeholder logo

No logo image was attached, so a vector placeholder ships in
`res/drawable/logo_emblem.xml` (a gold chess king on a blue shield), reused for the
launcher icon. To use your real logo:

- **Simplest:** drop `logo_emblem.png` into `res/drawable/` (overriding the vector), or
- Replace `logo_emblem.xml` with your vector, and regenerate launcher icons via
  **Android Studio → New → Image Asset**.

## What needs a backend

These parts are stubbed locally and marked in code; wire them to a server (a REST API,
or Firebase Auth + Firestore + Storage + Cloud Functions, map cleanly onto
`ClubRepository`):

1. **Email to admin on new registration** and **password‑reset emails**
   (`requestPasswordReset` is a safe stub today).
2. **True multi‑device data** — the local SQLite store is per‑device; a backend makes
   groups/puzzles/results/library shared across users.
3. **Assignment push notifications** — students/parents get the assignment in‑app now;
   real push needs FCM.
4. **Server‑side rate limiting** to complement the on‑device brute‑force lock.
5. **In‑app PDF print rendering** — currently hands off to a PDF viewer/print service.

## Project layout

```
app/src/main/java/com/ptchess/club/
  ChessApp.java                 Application (locale + DB warm‑up)
  chess/                        ChessBoard, ChessBoardView, PgnImporter
  data/                         DbHelper (schema+seed), ClubRepository, model/
  security/                     PasswordHasher, InputValidator, SessionManager
  ui/                           auth/ admin/ tutor/ parent/ child/ common/
  util/                         LocaleHelper, Async, UiUtils
app/src/main/res/               themes, colors, strings (he + en), drawables, layouts
```
