# School ERP — React Native app (`mobile/`)

Native Expo / React Native client for the same Spring Boot `/api/v1` used by `web/`. Not a WebView shell. No second database, no mock users, no mock “success” notifications.

Last GitHub commit this app was written against: inspect `HEAD` on `arena/01a0ae42-erp-school`. Web and backend were not replaced.

## What it is

| Item | Value |
| --- | --- |
| Path | `mobile/` |
| Stack | Expo SDK 58, React Native 0.88, React 19.3, Expo Router, Reanimated 4.7.1 / Worklets 0.13.0 |
| API | Same origin as the web ERP: `{EXPO_PUBLIC_API_URL}/api/v1` |
| Auth | JWT access + refresh in `expo-secure-store` (`erp.access`, `erp.refresh`) |
| Tenancy | Bearer JWT claims; campus isolation stays on the server |
| Notifications | Poll `GET /api/v1/notifications` + local OS banner when unread count increases. **No remote Expo push token API exists on the backend — do not claim FCM/APNs delivery.** |

## Inventory (screens)

| Route | Roles | Live endpoints |
| --- | --- | --- |
| `/login` | public | `POST /auth/login` |
| `/(tabs)` Home | all signed-in | dashboards below |
| `/(tabs)/hub` | all | feature-filtered `SCHOOL_NAV` modules |
| `/(tabs)/alerts` | all | `GET /notifications`, `POST /notifications/{id}/read` (mark-all = loop) |
| `/(tabs)/me` | all | `GET /auth/me`, children, logout `POST /auth/logout` |
| `/password` | all | `POST /auth/change-password` (username-only, password-only, or both) |
| `/students` | SCHOOL_ADMIN, PRINCIPAL, TEACHER, ACCOUNT_OFFICER | `GET /students` paged |
| `/users` | SCHOOL_ADMIN, PRINCIPAL | `GET /users`, activate/deactivate |
| `/campuses` | SCHOOL_ADMIN | `GET /campuses` |
| `/academics` | SCHOOL_ADMIN, PRINCIPAL | `GET /classes`, `GET /subjects` |
| `/timetable` | school roles | `/timetable/me`, `/teacher/timetable`, `/timetable?classId&sectionId` |
| `/attendance` | school roles | lecture roster + `POST /attendance/lectures/{slotId}`; family history |
| `/teacher-attendance` | SCHOOL_ADMIN, PRINCIPAL | `GET /attendance/teachers/day` |
| `/homework` | school roles | `GET /homework`, `POST /homework/{id}/submissions` + `POST /files` |
| `/exams` | school roles | sessions, `/mine`, `/students/{id}`, publish |
| `/fees` | ACCOUNT_OFFICER, PRINCIPAL, PARENT, STUDENT — **not SCHOOL_ADMIN, not TEACHER** | challans, generate, proofs, review |
| `/salaries` | SCHOOL_ADMIN, ACCOUNT_OFFICER, PRINCIPAL, TEACHER | `/salaries`, generate, pay |
| `/announcements` | school roles | `GET/POST /announcements` |
| `/calendar` | school roles | `GET /calendar/events` |
| `/reports` | SCHOOL_ADMIN, PRINCIPAL, ACCOUNT_OFFICER | `GET /dashboard/school` |
| `/admissions` | SCHOOL_ADMIN, PRINCIPAL | applications + status |
| `/settings` | SCHOOL_ADMIN (read hint), ACCOUNT_OFFICER (bank) | `GET/PUT /school-settings` |
| `/billing` | SCHOOL_ADMIN | `GET /subscriptions/current`, payment slip upload |
| `/schools`, `/school/[id]`, `/subscriptions` | ERP_OWNER | tenants + subscription review |

### Role dashboards (Home)

| Role | First-screen APIs |
| --- | --- |
| ERP_OWNER | `GET /dashboard/platform`, `GET /tenants` |
| SCHOOL_ADMIN / PRINCIPAL | `GET /dashboard/school` |
| TEACHER | `GET /teacher/timetable/today`, `GET /reports/progress/teacher`, homework if on |
| ACCOUNT_OFFICER | school dashboard + `GET /fees/proofs/pending` |
| PARENT | `GET /parents/me/children`, `GET /reports/progress/children`, child challans |
| STUDENT | `GET /reports/progress/me`, then homework/fees |

Secondary widgets are not required before the first numbers render. Final Term countdown uses `announceAt` from `GET /exams/sessions` and hides when the clock has elapsed (published marksheet path).

## Role × fee matrix (must match backend)

| Role | Fee UI |
| --- | --- |
| ACCOUNT_OFFICER | generate challans, review proofs |
| PRINCIPAL | view pending proofs (no generate) |
| PARENT / STUDENT | own/child challans + slip upload |
| SCHOOL_ADMIN | **blocked in the app** (empty state). Backend already rejects challan generate (`ACCOUNT_OFFICER` / `ERP_OWNER` only). |
| TEACHER | no Fees module in nav |

## Auth & session

1. `POST /api/v1/auth/login` `{ username, password }` → access + refresh + user + tenant + features.
2. Tokens in SecureStore. Native `Authorization: Bearer`. CORS on the server is `*` with `allowCredentials: false`, which matches this (no cookies).
3. 401 → `POST /api/v1/auth/refresh`. Failure → clear session → login.
4. Portal chip is **expected role**. Wrong role signs out and shows `WRONG_PORTAL` — it does not keep the other role’s session.
5. Logout: local tokens cleared first, then `POST /auth/logout`.

## API base URL (never hardcode production localhost)

Resolution order in `mobile/src/lib/config.ts`:

1. `EXPO_PUBLIC_API_URL` (no trailing slash), e.g. `https://api.example.com`
2. `expo.extra.apiUrl` in `app.json` / EAS
3. Dev: Metro LAN host + `:8080` (phone talking to your PC)
4. Dev fallback: `http://127.0.0.1:8080` (simulator / emulator on the same machine)
5. Production with none of the above: **empty string** — login shows “set EXPO_PUBLIC_API_URL”

Physical Android phone: **never** use `localhost` / `127.0.0.1` — that is the phone itself. The app uses, in order: `EXPO_PUBLIC_API_URL`, `extra.apiUrl`, the Metro LAN host + `:8080`, Android emulator `http://10.0.2.2:8080`, iOS simulator `http://127.0.0.1:8080`, otherwise empty (login tells you to set the URL). Spring already listens on `0.0.0.0`. Same Wi‑Fi, PC firewall must allow port 8080.

## Windows Android native build (260-character Ninja path)

`npx expo run:android` on Windows fails with Gesture Handler codegen if Android SDK CMake **3.22.1** is used. That package ships **Ninja 1.10.2**, which rejects object paths over 260 characters even when Windows long paths are on.

This repo pins **CMake 3.31.6** (Ninja 1.12+, long-path aware) in two places:

- `expo-build-properties` `android.cmakeVersion` writes `android.cmakeVersion=3.31.6` to `gradle.properties` (AGP does **not** use that property to pick the CMake binary).
- `mobile/plugins/withCmakeObjectPathMax.js` writes `android.externalNativeBuild.cmake.version "3.31.6"` into `android/app/build.gradle` (this is what AGP actually reads) and keeps `-DCMAKE_OBJECT_PATH_MAX=1024`.

You still must:

1. Enable Windows long paths (Administrator PowerShell, then reboot) if not already on:
   `New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force`
2. In Android Studio → SDK Manager → SDK Tools, install **CMake 3.31.6** (already done on the machine that reported this).
3. Delete the **generated** `mobile\android` folder (it is gitignored). A leftover prebuild from before this plugin still has no `cmake { version "3.31.6" }` and Gradle will keep using 3.22.1.
4. From `mobile`, run `npx expo prebuild --platform android --clean` then `npx expo run:android`. Confirm `android\app\build.gradle` contains `version "3.31.6"` and the first CMake log line is **not** `CMake 3.22.1`.

Do not copy the whole project to a shorter path unless CMake 3.31.6 cannot be installed. The original folder `D:\School\erp_School\mobile` is the source of truth.

## Setup

```bash
cd mobile
npm install
npx expo start
```

iOS: `npx expo start --ios` (macOS + Xcode). Android: `npx expo start --android` (SDK + emulator or USB).

EAS production:

```bash
cd mobile
EXPO_PUBLIC_API_URL=https://YOUR-API-HOST npx eas build --platform android
```

Set the same extra in EAS secrets / `app.json` `expo.extra.apiUrl`. Store builds must use **HTTPS**. Android cleartext HTTP for LAN Boot is set via the `expo-build-properties` plugin (`android.usesCleartextTraffic` is **not** a valid `app.json` schema field on SDK 58). That plugin applies to prebuild / development / production APKs, not Expo Go (Expo Go already allows HTTP to Metro/LAN in development).

### JDK / API in this sandbox

This environment has **no JDK** and **Maven Central is blocked**. `mvn clean test` and `spring-boot:run` were **not** run for this mobile commit. Port **8080** was down. Do not invent timings. Device/simulator E2E was **not** run here.

## Notifications

| Kind | Status |
| --- | --- |
| In-app list | `GET /api/v1/notifications` |
| Mark one / mark visible | `POST /api/v1/notifications/{id}/read` only (no mark-all endpoint) |
| Local OS banner | when unread count **increases** on a 45s poll / AppState resume, using local scheduler APIs (not the `expo-notifications` barrel, which crashes Expo Go Android via `ExpoTopicSubscriptionModule`) |
| Remote push (Expo token → FCM/APNs) | **not implemented** — backend has no device-token register API. Expo Go Android has not shipped remote push since SDK 53. Do not fake it. |

### Expo Go vs development build

- **Expo Go (SDK 58) is enough** for login, ERP screens, JWT, file picks, and **local** banners, as long as `src/lib/notifications.ts` never imports the `expo-notifications` package index.
- **A development build is required** only for remote FCM/APNs push (not in this backend) or any native module Expo Go does not ship.
- Phone Expo Go must be the **SDK 58** client. An older Expo Go will fail native modules even after this fix.

## Tests run vs blocked

| Check | Result |
| --- | --- |
| `npx tsc --noEmit` in `mobile/` | **PASS** (after pinning Expo 58 native modules) |
| Login / dashboards against live Boot + Neon | **blocked** — no JDK, API 8080 down, no demo credentials in this sandbox |
| iOS Simulator / Android emulator | **blocked** — no Xcode/Android SDK in this sandbox |
| `mvn clean test` | **blocked** — no JDK |
| True push | **blocked** — no backend token API |

## Limitations (honest)

- Bulk promote, bulk inactivate, website builder, and logo/theme file branding stay on **web** (large forms + file UX).
- Teacher marks entry for a full class is easier on web; mobile exams for staff are session publish + list.
- Appointment slot editor stays on web.
- `package-reanimated-backup/` is **not** part of the app and must not be committed.
- Do not nest a git repo inside `mobile/`.
- Do not force-push `main`.
- Do not `import * as Notifications from "expo-notifications"` in a layout or any always-loaded module — Expo Go Android throws `ExpoTopicSubscriptionModule` and Expo Router then reports `ErrorBoundary` of undefined.

## File map (mobile ERP)

```
mobile/app.json
mobile/package.json
mobile/babel.config.js
mobile/tsconfig.json
mobile/src/app/_layout.tsx          # providers + stack
mobile/src/app/index.tsx            # session gate
mobile/src/app/login.tsx
mobile/src/app/(tabs)/{index,hub,alerts,me}.tsx
mobile/src/app/{students,users,campuses,academics,timetable,attendance,
  teacher-attendance,homework,exams,fees,salaries,announcements,calendar,
  reports,admissions,settings,billing,schools,subscriptions,password}.tsx
mobile/src/app/school/[id].tsx
mobile/src/api/{client,services}.ts
mobile/src/lib/{types,format,config,theme,session,features,appearance,
  nav,notifications,storage,picker}.ts(x)
mobile/src/ui/{kit,countdown,gate}.tsx
MOBILE.md
```

Web (`web/`) and Spring (`src/main/java`) are unchanged by the mobile app aside from CORS already allowing `*` + Bearer.
