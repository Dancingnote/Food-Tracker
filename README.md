# FoodTracker

An Android app for managing household food inventory: it tracks expiry dates, sends reminders before food goes off, and lets family members share a fridge.

Built as the final project for **COMP9003**.

---

## Features

### Inventory management
- Add items with name, quantity, price per unit, added date, production date, expiry date and category
- Attach a product photo from the camera or gallery
- Record storage condition (**Room temp / Chilled / Frozen**) and whether the item is opened
- Edit or delete items at any time; swipe left to archive or delete, swipe right to edit
- Partially archive an item when only part of the stock has been used

### Scan label to autofill (OCR)
- Tap **Scan label to autofill** on the Add Product page, then take a photo of the package or pick one from the gallery
- Text is recognised **on-device** with Google ML Kit (Chinese + Latin script), so no network or API key is needed
- The parser extracts:
  - **Product name** — the largest text on the label, skipping ingredient/nutrition lines
  - **Production and expiry dates** — `2026-11-21`, `2026年11月21日`, `21/11/2026`, `20261121`, `21 NOV 2026`, labelled by keywords such as `生产日期`, `MFG`, `EXP`, `Best before`, `到期`
  - **Shelf life** — e.g. `保质期 6个月` / `shelf life 180 days`, added to the production date when there is no expiry date
  - **Price** — `¥ $ € £` amounts or `售价` / `Price`
  - **Category** and **storage condition** from keywords (`牛奶` → Dairy, `冷藏` / `0-4℃` → Chilled, …)
- A review dialog lists everything that was found; untick anything misread before tapping **Fill in**

### Expiry tracking
- Each item shows the time remaining with a colour-coded progress bar (green → yellow → red)
- Status badges: **Fresh**, **Near Expiry**, **Expired**
- **Expiry Settings**: recommended shelf life per category and storage condition; the expiry date picker highlights the suggested date
- **Near-Expiry Settings**: configurable near-expiry threshold per category (e.g. Meat 1 day, Fruit 3 days, Snack 30 days)
- A reminder banner appears on launch when items are expiring soon

### Search, filter & sort
- Real-time search by product name
- Filter by category, storage, opened status, expiry status, days to expiry, and shared / from-others
- Sort by name, expiry date, days remaining or quantity, ascending or descending
- Switch between the active inventory and archived items

### Family sharing
- Create a family group and share its invite code; other users join with the code
- Shared items appear in every member's inventory with a "from [user]" tag and an accent strip
- In-app group chat for family members

### Statistics
- Counters for active, archived, near-expiry and expired items
- Breakdown by food category
- Monthly timeline of items added vs. consumed

### Notifications
- Daily notification summarising items that are expiring
- Optional persistent notification
- Rules: on expiry day, or 2 / 3 / 15 / 30 days before expiry, with separate near-expiry rules

### Display preferences
- Primary display: remaining time or remaining stock
- Remaining time as dynamic (d/m/y) or days only, counted to the expiry or near-expiry date
- Date format (`10.05.2026`, `10/05/2026`, `10-05-2026`, `10 May 2026`)
- Whether the production day counts towards shelf life

### User accounts
- Local multi-user support: sign up, log in, sign out
- Custom avatar and display name
- Passwords are hashed with SHA-256 before storage

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 11 |
| Min / target SDK | 31 (Android 12) / 34 (Android 14) |
| UI | Single activity + 4 fragments, ConstraintLayout, Material Components |
| Database | Room 2.8.4 (primary) + SharedPreferences (fallback) |
| Text recognition | Google ML Kit Text Recognition (Chinese model, bundled) 16.0.1 |
| Image loading | Glide 4.16.0 |
| Flexible layouts | Google FlexboxLayout 3.0.0 |
| Notifications | AlarmManager + BroadcastReceiver + NotificationCompat |
| Build | Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin DSL + version catalog |

---

## Project structure

```
app/src/main/java/com/comp9003/foodtracker/
├── MainActivity.java               # Single activity host, bottom navigation
├── HomeFragment.java               # Inventory list, search / filter / sort / swipe
├── StatsFragment.java              # Statistics overview and monthly chart
├── FamilyFragment.java             # Family group management and group chat
├── ProfileFragment.java            # Settings menu
├── ProductRepository.java          # Central data layer — Room + SharedPreferences
├── SharedViewModel.java            # Filter / sort state shared across fragments
├── AddProductActivity.java         # Add product form, incl. label scanning
├── EditProductActivity.java        # Edit product form
├── ExpirySettingsActivity.java     # Recommended shelf life per category / storage
├── NearExpiryConfigActivity.java   # Near-expiry thresholds per category
├── ReminderSettingsActivity.java   # Notification rules
├── DisplayPreferencesActivity.java # List display options
├── LoginActivity.java / SignupActivity.java
├── EditProfileActivity.java / AvatarEditActivity.java
├── HelpFeedbackActivity.java
├── FilterBottomSheetFragment.java / SortBottomSheetFragment.java / StatsBottomSheetFragment.java
├── adapter/
│   └── ProductAdapter.java         # RecyclerView adapter with swipe gestures
├── db/                             # Room database, entities and DAOs
├── model/
│   └── Product.java
├── ocr/
│   └── PackageInfoParser.java      # Turns OCR text into form values (pure Java)
└── reminder/
    ├── ReminderReceiver.java       # Builds and posts notifications
    └── ReminderScheduler.java      # Schedules daily alarms via AlarmManager
```

---

## Getting started

### Prerequisites
- Android Studio Hedgehog (2023.1) or later
- **JDK 17 or 21** for Gradle. Gradle 8.7 cannot run on JDK 25, so on the command line point `JAVA_HOME` at Android Studio's bundled JDK.
- A device or emulator running API 31+

### Build & run
1. Open the project in Android Studio and let Gradle sync.
2. Run on a device or emulator with **Run → Run 'app'**.

From the command line:

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

No API keys or external services are required. All data, including OCR, stays on the device.

### Tests

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```

`PackageInfoParserTest` covers Chinese and English labels, keyword-labelled and unlabelled dates, shelf-life calculation and storage detection.

---

## Permissions

| Permission | Purpose |
|---|---|
| `CAMERA` | Take product photos and photos of package labels for scanning |
| `POST_NOTIFICATIONS` | Expiry reminder notifications (Android 13+) |

---

## Architecture notes

- **Single source of truth**: `ProductRepository.products` is the in-memory store; Room is the persistent backing store, with a SharedPreferences fallback for migration compatibility.
- **Shared thresholds**: `ProductRepository.thresholdDaysByCategory` is the one definition of the default near-expiry days, used by the UI and the reminder receiver.
- **Bind-cycle cache**: `ProductAdapter.refreshBindCache()` reads display preferences once before `notifyDataSetChanged()`, so `onBindViewHolder` does no SharedPreferences or database reads.
- **Single-pass filter**: `HomeFragment.applyFilter()` checks every active filter in one `removeIf` pass, with preference values read once before the loop.
- **Fragment reuse**: bottom navigation skips replacing a fragment that is already showing, avoiding needless reloads.
- **User cache**: `ProductRepository.getCurrentUser()` is cached after the first lookup and cleared on login and logout.
- **Testable OCR parsing**: ML Kit only produces lines of text with their heights; `PackageInfoParser` has no Android dependencies, so the extraction rules are covered by plain JUnit tests.
