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

## Run

```powershell
./gradlew.bat :app:assembleDebug
```

Install the generated APK from `app/build/outputs/apk/debug/` on an emulator or device.
