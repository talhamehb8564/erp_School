# Dedicated school branding (one APK per school)

This mobile app is **not** a multi-school picker. Each Play listing / APK is built for **one** school.

## What is native vs runtime

| Surface | Source | Changes when DB logo changes? |
| --- | --- | --- |
| Android launcher name | `school.json` `appName` at **prebuild / EAS** | No — rebuild required |
| Launcher / adaptive icon | `branding/assets/icon.png` (and adaptive) at **prebuild** | No — rebuild required |
| Native splash | `branding/assets/splash-icon.png` + `primaryColor` at **prebuild** | No — rebuild required |
| In-app header name/logo/colours | Live `GET /api/v1/public/school?code=` then `GET /tenants/me` after login | Yes, after refresh |

Do not claim that uploading a logo in Settings updates an already-installed launcher icon.

## Produce another school's APK

1. Replace `branding/school.json`:
   - `name`: full school name (never the short code as the title)
   - `appName`: `{full name} Portal`
   - `code`: tenant code used only to call `/api/v1/public/school?code=`
   - `androidPackage` / `iosBundle`: that school's unique application id
   - colours
2. Replace `branding/assets/icon.png` and `splash-icon.png` with **that school's** logo (1024×1024 PNG). Copy them over `assets/images/icon.png` and `splash-icon.png`.
3. Set `EXPO_PUBLIC_API_URL` to that school's API origin.
4. `npx expo prebuild --platform android --clean` then build. Confirm `AndroidManifest` `android:label` is the portal name and `mipmap` is the new crest — not the Expo caret.

`code` is an API lookup key. It must never be shown as the product name (`GVS Portal` is wrong; `Green Valley School Portal` is right).
