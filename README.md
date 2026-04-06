# Zejios Cafe Management System

Android tablet-first POS admin interface built with Kotlin + XML in Android Studio.

## Implemented POS Screen

- Brown cafe-styled collapsible sidebar with active POS state
- Tablet-first split layout (landscape 70/30) and portrait stacked layout
- Search/filter row, icon category pills, responsive product grid, and order panel
- Payment method selection, clear/checkout actions, and floating cart FAB
- MVVM flow with `PosViewModel`, `RecyclerView` adapters, and sample product data
- Product cards load realistic food/drink images via Coil

## Tech Stack

- Kotlin
- Android Views + XML (`ConstraintLayout`, `RecyclerView`, Material Components)
- MVVM (`ViewModel`, `LiveData`)
- Coil image loading
- Supabase (`Auth`, `PostgREST`)

## Team Setup

Each teammate needs their own local app config because `local.properties` is intentionally ignored by Git.

1. Copy [local.properties.example](./local.properties.example) to `local.properties`
2. Update `sdk.dir` for your own machine
3. Add the shared Supabase values:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_OR_PUBLISHABLE_KEY
```

You can also provide `SUPABASE_URL` and `SUPABASE_ANON_KEY` through project `gradle.properties` or environment variables if preferred.

## Run

```powershell
./gradlew.bat :app:assembleDebug
```

Install the generated APK from `app/build/outputs/apk/debug/` on an emulator or device.

## Common Team Issues

- If the app opens but menu/inventory screens fail, check that `SUPABASE_URL` and `SUPABASE_ANON_KEY` are set locally.
- If the project does not build, make sure Android SDK Platform 36 is installed in Android Studio SDK Manager.
- If checkout fails, make sure the shared Supabase project has the SQL migrations and RPC functions already applied.
