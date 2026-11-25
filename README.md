# MotoMove Himachal Pradesh (SIH26204)

An Android (Kotlin + Jetpack Compose + Material 3 + SQLite) smart tourism mobility, **Railway Station & Airport Pickup**, and verified homestay/hotel/dining marketplace built for **underserved destinations in Himachal Pradesh**.

## Key Features
- **Station & Airport Pickups**: 1-tap pickup discovery for Bhuntar Airport (`KUU`), Kalka Railway Station (`KLK`), Shimla (`SML` / `SLV`), Kangra (`DHM`), Joginder Nagar (`JDNX`), and Chandigarh (`IXC`) connecting to Jibhi–Tirthan, Kasol–Manikaran–Tosh, Kaza–Spiti, Sangla–Chitkul–Kalpa, Bir–Barot, and Shimla–Narkanda.
- **Swipeable Navigation**: 5-tab bottom navigation (`Explore`, `Food`, `MotoAI`, `Bookings`/`Manage`, `Alerts`) synced with horizontal swipe (`HorizontalPager`).
- **On-Device MotoAI**: Built-in offline-first assistant for hub-to-valley vehicle recommendations, side-by-side stay comparisons, food recommendations, and mountain Q&A (no external API key required).
- **Strict Ownership & Security**: Salted PBKDF2 (`PBKDF2WithHmacSHA256`) password hashing, role-based access across all 5 SRS roles, and strict ownership checks preventing any user from editing or deleting another user's listings, profile, or bookings.
- **SRS Booking & Inventory Rules**: Prevents overlapping accepted vehicle bookings (`FR-12`), enforces stay check-out dates, room capacity, and date-by-date inventory limits (`FR-19`), restricts reviews to completed bookings (`FR-21`), and records verification/moderation actions in `audit_logs` (`FR-24`).

## Running in Android Studio
1. Open `MotoMoveAndroid` in **Android Studio** (**File → Open**).
2. Let Gradle sync (`compileSdk = 37`, `minSdk = 26`).
3. Run on an Android device or emulator (API 26+), or run unit tests with `./gradlew testDebugUnitTest`.
