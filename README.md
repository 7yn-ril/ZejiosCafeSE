# Zejios Cafe Management System

Android tablet-first cafe POS + back-office, built with Kotlin and Android Views/XML on top of a Supabase (Postgres) backend. Single-credential login (no roles). CIT238 capstone.

> This README is the canonical map of the system. Read it instead of re-scanning the repo.

---

## 1. What the app does

A single `MainActivity` (after `LoginActivity`) renders 7 sections via a collapsible brown sidebar:

| Section    | Purpose                                                                                  |
|------------|------------------------------------------------------------------------------------------|
| Dashboard  | Revenue/orders/profit/stock metrics, top items, low-stock alerts, daily/weekly/monthly chart |
| POS        | Browse menu → size picker → cart → checkout dialog → atomic Supabase RPC                 |
| Orders     | Live order queue (polls every 30s), status transitions, per-item completion, receipts    |
| Inventory  | Ingredient stock + product/variant editor with recipe links                              |
| Reports    | Sales timeline, transaction history, category/product breakdowns                         |
| Staff      | Frontend-only staff cards (no backend writes)                                            |
| Profile    | Local-only user profile state                                                            |

There is **no role system**. Anyone who logs in sees everything.

---

## 2. Tech stack (actually used)

- **Kotlin** + Android Views/XML (`ConstraintLayout`, `RecyclerView`, `Material Components`). Compose deps are present but UI is XML.
- **MVVM**: `ViewModel` + `LiveData`. ViewModels per feature area (`PosViewModel`, `DashboardViewModel`, `InventoryViewModel`, `ReportsViewModel`).
- **Supabase Kotlin SDK** (`Auth` + `Postgrest`) — `SupabaseProvider.client` is the singleton.
- **Coil** for image loading; **MPAndroidChart** for the dashboard line chart.
- **Min SDK 24, target/compile SDK 36**, JVM 11, core library desugaring on.
- Tests: **JUnit 5** + **MockK** + **Robolectric** + `kotlinx-coroutines-test`.

---

## 3. Backend model (Supabase)

All DDL/RPC lives in [`database/`](./database) as separate `.sql` files. **Apply them in order in the Supabase SQL Editor** when bootstrapping a new project. Each file is idempotent and uses the codebase's "best-effort column tagging" pattern (DO blocks that check `information_schema.columns` before altering).

Key surfaces the Android client expects:

- **Tables**: `orders`, `order_items`, `ingredients`, `product_categories`, `products`, `product_variants`, `product_recipe_links`, `discounts`.
- **View**: `product_variant_stock` (per-variant computed stock from recipe + ingredient inventory).
- **RPCs**:
  - `process_checkout_order(...)` — atomic order creation. Allocates `#POS-####` numbers starting at 1024.
  - `peek_next_order_number` — preview next number for the cart header.
  - Partial-completion paths used when ingredient stock can't cover every line.
- **Order numbering**: `#POS-1024`, `#POS-1025`, … resolved server-side in `resolve_pos_order_number`.
- **Orders** carry: `order_status`, `order_type` (`dine_in` | `takeout` | `delivery`), `payment_method` (`cash` | `gcash` | `maya`), `discount_id` + `discount_percent` snapshot, `order_inventory_deducted`, `order_completed_at`, per-item `completed_item_variant_ids` / `deducted_item_variant_ids`.

Repositories that wrap Postgrest live in `*/data/repository/`:
- `OrderRepository`, `ProductRepository`, `CategoryRepository`, `DiscountRepository`
- `InventoryRepository`, `DashboardRepository`, `ReportsRepository`

Every repository call goes through `SupabaseSessionHelper.withJwtRetry { ... }` to refresh the JWT on auth blips.

---

## 4. Key business logic worth knowing

- **Cart → Checkout**: `PosViewModel` holds `orderItems`, `subtotal`, `tax`, `total`. Checkout dialog builds a `CheckoutOrderPayload` (lines reference `product_variant_id` + a source-product/variant snapshot for receipt history) and calls `process_checkout_order` RPC.
- **Order types**: `DINE_IN`, `TAKE_AWAY`, `DELIVERY` exist in the enum, but **Delivery is hidden** in the cart UI — only Dine-In/Take-Away buttons are rendered.
- **Discounts** (3 mutually exclusive radio modes in the checkout dialog):
  1. None
  2. Built-in PWD/Senior (chip-selected, 20%)
  3. Other → spinner of custom `discounts` rows whose date range covers today
  The resolved label + amount + `discount_id` + `discount_percent` are persisted on the order so historical reports stay accurate even if the discount row is later edited.
- **Order statuses**: `PENDING` → `PREPARING` → `COMPLETED` (terminal) or `CANCELLED` (terminal). Cancellation is blocked once any item has been deducted/completed.
- **Per-item completion** (Orders dialog): items split into a read-only "Completed" section (server-locked — already deducted) and editable "In Progress" checkboxes. Marking all complete fires the status transition to `COMPLETED`.
- **Partial completion**: if some lines couldn't be made (insufficient ingredients, missing recipe, low manual stock), the server completes what it can and returns `blockedItems` (`name` + `reason`). The UI shows a warning dialog instead of failing the whole transition.
- **Polling**: `MainActivity` reloads orders every 30 s (`ORDERS_REFRESH_INTERVAL_MS`). De-duped notifications via `notifiedOrderIds` / `notifiedLowStockIds` / `notifiedOutOfStockIds` so the system tray isn't spammed on each tick.
- **Size variants**: products with multiple cup sizes show a size picker dialog (`dialog_size_picker.xml`); single-variant products go straight to the cart.
- **Stock view**: the `product_variant_stock` view drives whether a variant shows as out-of-stock and why.

---

## 5. Where things live

```
app/src/main/java/com/example/zejioscafese/
├── LoginActivity.kt              — SHA-256 single-credential login, "Remember me" via SharedPreferences
├── MainActivity.kt               — sidebar host, sections, orders polling, checkout review, dialogs (large)
├── core/
│   ├── supabase/                 — SupabaseProvider, SupabaseSessionHelper (JWT retry)
│   ├── local/LocalAppPrefs.kt    — local profile persistence
│   ├── network/                  — NetworkErrorFormatter
│   └── notifications/            — AppNotifications channels
├── pos/         (cart, products, discounts, size picker)
│   ├── data/{model,remote/dto,repository,local}
│   ├── presentation/PosViewModel.kt
│   └── ui/      (adapters + MenuBrowseDialogFragment)
├── orders/      (queue, status flow, receipts)
│   ├── data/repository/OrderRepository.kt
│   ├── model/OrderModels.kt
│   └── ui/OrderManagementAdapter.kt
├── inventory/   (ingredients, product editor, recipes)
│   ├── data/{model,remote/dto,repository}
│   └── (Fragment + ViewModel live under ui/)
├── dashboard/   (metrics, alerts, top items, chart)
│   ├── data/{repository,DashboardSampleData}
│   ├── model/DashboardModels.kt
│   ├── presentation/DashboardViewModel.kt
│   └── ui/      (adapters)
├── reports/     (timeline, transactions)
│   └── data/{model,repository}
└── ui/          (shared: NavigationHost, *Fragment, NoticeDialog, RevenueBarChartView, DialogButtonStyling)

app/src/main/res/layout/         — all XML (activity_main, activity_login, content_*, dialog_*, item_*, view_*)
database/                        — *.sql migrations, apply in order in Supabase SQL Editor
assets/other_assets/             — ZejiosCafeLogo.jpg etc.
run_pixel_tablet.ps1             — build + launch on Pixel_Tablet emulator
run_tablet.ps1                   — generic emulator helper (supports -ColdBoot)
pull_latest_report_to_laptop.ps1 — companion script for export reports
```

---

## 6. Per-machine setup

`local.properties` is gitignored. Each teammate creates their own:

1. Copy [local.properties.example](./local.properties.example) → `local.properties`.
2. Set `sdk.dir` to your Android SDK path.
3. Add the shared Supabase creds:
   ```properties
   SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
   SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_OR_PUBLISHABLE_KEY
   ```
4. Add login creds (**required** — app errors out if missing):
   ```properties
   LOGIN_USERNAME=admin
   LOGIN_PASSWORD_SHA256=<lowercase-hex-sha256-of-plain-password>
   ```

`SUPABASE_URL`, `SUPABASE_ANON_KEY`, `LOGIN_USERNAME`, `LOGIN_PASSWORD_SHA256` can alternatively be provided via project `gradle.properties` or environment variables — `app/build.gradle.kts` checks all three sources in that order and exposes them as `BuildConfig` fields.

When pointing at a fresh Supabase project, run every `.sql` in [`database/`](./database) in filename order in the SQL Editor.

---

## 7. Build & run

```powershell
./gradlew.bat :app:assembleDebug
```

Install the APK from `app/build/outputs/apk/debug/`.

One-shot: build, recover `adb`, boot `Pixel_Tablet`, install, launch:

```powershell
.\run_pixel_tablet.ps1
```

Cold-boot emulator with the project's DNS defaults (use when Supabase calls time out):

```powershell
.\run_tablet.ps1 -ColdBoot
```

---

## 8. Tests

```powershell
./gradlew.bat :app:testDebugUnitTest
```

Unit tests cover `OrderItem`, `ProductVariantStockDto`, `PosViewModel`, `InventoryViewModel`, `ReportsViewModel`. Robolectric is available for view-bound tests.

---

## 9. Common team issues

- **Menu/inventory screens fail to load** → `SUPABASE_URL` / `SUPABASE_ANON_KEY` aren't set on this machine.
- **Supabase requests time out on the emulator** → emulator lost DNS. Cold-boot: `.\run_tablet.ps1 -ColdBoot`.
- **Project won't build** → install Android SDK Platform 36 in Android Studio SDK Manager.
- **Checkout fails on a new Supabase project** → SQL migrations + RPCs not applied. Run every `database/*.sql` in order.
- **"adb.exe: device offline"** → only deploy failed, not the build. Run `.\run_pixel_tablet.ps1` to restart adb and reinstall.
- **Login shows "credentials not configured"** → `LOGIN_USERNAME` / `LOGIN_PASSWORD_SHA256` missing in `local.properties`.
- **Order completion succeeds with a warning** → that's partial completion. The dialog message lists which items were blocked and why; restock the named ingredient and re-complete.
