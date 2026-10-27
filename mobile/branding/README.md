# Dedicated school branding (one APK per school)

This mobile app is **not** a multi-school picker. Each Play listing / APK is built for **one** school.

This checkout is branded for **Devzard School**.

| Field | Value |
| --- | --- |
| Full name (in-app) | Devzard School |
| Short code (API lookup only) | DZS |
| Android / iOS label | Devzard School Portal |
| Package | `com.devzard.schoolerp` |
| Native logo | `branding/assets/logo-source.jpeg` (authoritative) → `icon.png` / `splash-icon.png` |

## Native vs runtime

| Surface | Source | Changes when DB logo changes? |
| --- | --- | --- |
| Android launcher name | `school.json` `appName` at **prebuild / EAS** | No — rebuild required |
| Launcher / adaptive icon | `branding/assets/icon.png` at **prebuild** | No — rebuild required |
| Native splash | `branding/assets/splash-icon.png` at **prebuild** | No — rebuild required |
| In-app header name/logo/colours | Live `GET /api/v1/public/school?code=DZS` then `GET /tenants/me` after login | Yes, after refresh |

Do not claim that uploading a logo in Settings updates an already-installed launcher icon.

## Another school's APK

1. Put that school's **exact** logo in `branding/assets/` (do not redraw it).
2. Set `school.json` `name` to the full name, `appName` to `{full name} Portal`, `code` to the tenant code used by `/api/v1/public/school?code=`.
3. Change `androidPackage` / `iosBundle` if it is a separate listing.
4. `npx expo prebuild --platform android --clean` then build.

`code` is never the product title. This app shows **Devzard School**, not **DZS Portal**.
