package com.motomove.himachal.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.BookingType
import com.motomove.himachal.model.ListingApprovalStatus
import com.motomove.himachal.model.NotificationCategory
import com.motomove.himachal.model.RentalMode
import com.motomove.himachal.model.TimeSlot
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.model.VerificationStatus
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MotoMoveDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                user_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                contact TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL,
                account_status TEXT NOT NULL DEFAULT 'ACTIVE'
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE provider_profiles (
                provider_id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                display_name TEXT NOT NULL,
                provider_type TEXT NOT NULL,
                verification_status TEXT NOT NULL,
                home_cluster TEXT NOT NULL,
                last_updated_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(user_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE vehicles (
                vehicle_id TEXT PRIMARY KEY,
                provider_id TEXT NOT NULL,
                owner_name TEXT NOT NULL,
                title TEXT NOT NULL,
                vehicle_type TEXT NOT NULL,
                capacity INTEGER NOT NULL,
                transmission_fuel TEXT NOT NULL,
                service_area TEXT NOT NULL,
                pickup_hubs TEXT NOT NULL,
                rental_mode TEXT NOT NULL,
                rate_inr INTEGER NOT NULL,
                rate_basis TEXT NOT NULL,
                cancellation_terms TEXT NOT NULL,
                approval_status TEXT NOT NULL,
                last_updated_at INTEGER NOT NULL,
                FOREIGN KEY(provider_id) REFERENCES provider_profiles(provider_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE drivers (
                driver_id TEXT PRIMARY KEY,
                provider_id TEXT NOT NULL,
                name TEXT NOT NULL,
                languages TEXT NOT NULL,
                experience_years INTEGER NOT NULL,
                service_area TEXT NOT NULL,
                pickup_hubs TEXT NOT NULL,
                rate_per_day_inr INTEGER NOT NULL,
                availability_summary TEXT NOT NULL,
                cancellation_terms TEXT NOT NULL,
                approval_status TEXT NOT NULL,
                last_updated_at INTEGER NOT NULL,
                FOREIGN KEY(provider_id) REFERENCES provider_profiles(provider_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE verification_documents (
                document_id TEXT PRIMARY KEY,
                provider_id TEXT NOT NULL,
                provider_name TEXT NOT NULL,
                document_type TEXT NOT NULL,
                file_reference TEXT NOT NULL,
                review_status TEXT NOT NULL,
                expiry_date TEXT NOT NULL,
                reviewer_notes TEXT NOT NULL,
                FOREIGN KEY(provider_id) REFERENCES provider_profiles(provider_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE properties (
                property_id TEXT PRIMARY KEY,
                provider_id TEXT NOT NULL,
                host_name TEXT NOT NULL,
                name TEXT NOT NULL,
                property_type TEXT NOT NULL,
                location_cluster TEXT NOT NULL,
                nearest_hub_code TEXT NOT NULL,
                amenities TEXT NOT NULL,
                policies TEXT NOT NULL,
                registration_number TEXT NOT NULL,
                approval_status TEXT NOT NULL,
                last_updated_at INTEGER NOT NULL,
                FOREIGN KEY(provider_id) REFERENCES provider_profiles(provider_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE room_types (
                room_type_id TEXT PRIMARY KEY,
                property_id TEXT NOT NULL,
                name TEXT NOT NULL,
                occupancy INTEGER NOT NULL,
                rate_per_night_inr INTEGER NOT NULL,
                inventory_count INTEGER NOT NULL,
                FOREIGN KEY(property_id) REFERENCES properties(property_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE dining_spots (
                spot_id TEXT PRIMARY KEY,
                provider_id TEXT NOT NULL,
                name TEXT NOT NULL,
                location_cluster TEXT NOT NULL,
                specialty TEXT NOT NULL,
                dishes TEXT NOT NULL,
                ambience TEXT NOT NULL,
                avg_cost_inr INTEGER NOT NULL,
                is_fssai_verified INTEGER NOT NULL DEFAULT 1,
                rating REAL NOT NULL DEFAULT 4.8
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE travel_reels (
                reel_id TEXT PRIMARY KEY,
                author_name TEXT NOT NULL,
                author_handle TEXT NOT NULL,
                cluster_name TEXT NOT NULL,
                hub_code TEXT NOT NULL,
                title TEXT NOT NULL,
                caption TEXT NOT NULL,
                tag_label TEXT NOT NULL,
                linked_category INTEGER NOT NULL,
                price_hint TEXT NOT NULL,
                gradient_start INTEGER NOT NULL,
                gradient_end INTEGER NOT NULL,
                base_likes INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE user_reel_interactions (
                user_id TEXT NOT NULL,
                reel_id TEXT NOT NULL,
                is_liked INTEGER NOT NULL DEFAULT 0,
                is_saved INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(user_id, reel_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE bookings (
                booking_id TEXT PRIMARY KEY,
                tourist_id TEXT NOT NULL,
                tourist_name TEXT NOT NULL,
                provider_id TEXT NOT NULL,
                listing_id TEXT NOT NULL,
                listing_title TEXT NOT NULL,
                booking_type TEXT NOT NULL,
                pickup_hub_code TEXT,
                destination_cluster TEXT NOT NULL,
                train_or_flight_no TEXT,
                pickup_notes TEXT NOT NULL,
                start_epoch_day INTEGER NOT NULL,
                end_epoch_day INTEGER NOT NULL,
                slot TEXT NOT NULL,
                guest_or_seat_count INTEGER NOT NULL,
                status TEXT NOT NULL,
                quoted_price_inr INTEGER NOT NULL,
                cancellation_terms TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                responded_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE reviews (
                review_id TEXT PRIMARY KEY,
                booking_id TEXT UNIQUE NOT NULL,
                tourist_id TEXT NOT NULL,
                tourist_name TEXT NOT NULL,
                listing_id TEXT NOT NULL,
                rating INTEGER NOT NULL,
                comment TEXT NOT NULL,
                moderation_status TEXT NOT NULL DEFAULT 'VISIBLE',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE notifications (
                notification_id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'TRIPS',
                event_type TEXT NOT NULL,
                message TEXT NOT NULL,
                is_read INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE audit_logs (
                audit_id TEXT PRIMARY KEY,
                actor_id TEXT NOT NULL,
                actor_role TEXT NOT NULL,
                action TEXT NOT NULL,
                target_id TEXT NOT NULL,
                details TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        seedPilotData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        val tables = listOf(
            "audit_logs", "notifications", "reviews", "bookings",
            "user_reel_interactions", "travel_reels", "dining_spots",
            "room_types", "properties", "verification_documents",
            "drivers", "vehicles", "provider_profiles", "users"
        )
        tables.forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    private fun seedPilotData(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val twoDaysAgo = now - 2L * 86_400_000L
        val todayEpoch = LocalDate.now().toEpochDay()
        val demoHash = hashPassword("demo1234")

        fun insertUser(id: String, name: String, contact: String, role: UserRole) {
            db.insert("users", null, ContentValues().apply {
                put("user_id", id)
                put("name", name)
                put("contact", contact)
                put("password_hash", demoHash)
                put("role", role.name)
                put("account_status", "ACTIVE")
            })
        }

        insertUser("u_tourist_1", "Aarav Sharma", "tourist@motomove.in", UserRole.TOURIST)
        insertUser("u_tourist_2", "Neha Verma", "neha@motomove.in", UserRole.TOURIST)
        insertUser("u_owner_1", "Parvati Wheels Rental", "owner@motomove.in", UserRole.VEHICLE_OWNER)
        insertUser("u_owner_2", "Tirthan 4x4 Fleet", "tirthan@motomove.in", UserRole.VEHICLE_OWNER)
        insertUser("u_owner_3", "Kinnaur Mountain Rides", "kinnaur@motomove.in", UserRole.VEHICLE_OWNER)
        insertUser("u_driver_1", "Vikram Negi", "driver@motomove.in", UserRole.LOCAL_DRIVER)
        insertUser("u_driver_2", "Sonam Dorje", "spiti.driver@motomove.in", UserRole.LOCAL_DRIVER)
        insertUser("u_driver_3", "Pankaj Thakur", "pankaj.driver@motomove.in", UserRole.LOCAL_DRIVER)
        insertUser("u_host_1", "Meera Chauhan", "host@motomove.in", UserRole.ACCOMMODATION_PROVIDER)
        insertUser("u_host_2", "Tashi Bodh (Spiti & Kinnaur)", "tashi.stays@motomove.in", UserRole.ACCOMMODATION_PROVIDER)
        insertUser("u_admin_1", "SIH Pilot Admin", "admin@motomove.in", UserRole.ADMINISTRATOR)

        fun insertProvider(id: String, userId: String, name: String, type: UserRole, ver: VerificationStatus, cluster: String) {
            db.insert("provider_profiles", null, ContentValues().apply {
                put("provider_id", id)
                put("user_id", userId)
                put("display_name", name)
                put("provider_type", type.name)
                put("verification_status", ver.name)
                put("home_cluster", cluster)
                put("last_updated_at", twoDaysAgo)
            })
        }

        insertProvider("p_owner_1", "u_owner_1", "Parvati Wheels Rental", UserRole.VEHICLE_OWNER, VerificationStatus.VERIFIED, "Kasol–Manikaran–Tosh")
        insertProvider("p_owner_2", "u_owner_2", "Tirthan 4x4 Fleet", UserRole.VEHICLE_OWNER, VerificationStatus.VERIFIED, "Jibhi–Tirthan Valley")
        insertProvider("p_owner_3", "u_owner_3", "Kinnaur Mountain Rides", UserRole.VEHICLE_OWNER, VerificationStatus.VERIFIED, "Sangla–Chitkul–Kalpa")
        insertProvider("p_driver_1", "u_driver_1", "Vikram Negi", UserRole.LOCAL_DRIVER, VerificationStatus.VERIFIED, "Kasol–Manikaran–Tosh")
        insertProvider("p_driver_2", "u_driver_2", "Sonam Dorje", UserRole.LOCAL_DRIVER, VerificationStatus.PENDING, "Kaza–Kibber–Langza–Hikkim")
        insertProvider("p_driver_3", "u_driver_3", "Pankaj Thakur", UserRole.LOCAL_DRIVER, VerificationStatus.VERIFIED, "Jibhi–Tirthan Valley")
        insertProvider("p_host_1", "u_host_1", "Meera Chauhan", UserRole.ACCOMMODATION_PROVIDER, VerificationStatus.VERIFIED, "Jibhi–Tirthan Valley")
        insertProvider("p_host_2", "u_host_2", "Tashi Bodh", UserRole.ACCOMMODATION_PROVIDER, VerificationStatus.VERIFIED, "Kaza–Kibber–Langza–Hikkim")

        // 12 Vehicles across all Himachal hubs & clusters
        fun addVehicle(
            id: String, provId: String, owner: String, title: String, type: String, cap: Int,
            fuel: String, area: String, hubs: String, mode: RentalMode, rate: Int, terms: String, ageMs: Long
        ) {
            db.insert("vehicles", null, ContentValues().apply {
                put("vehicle_id", id)
                put("provider_id", provId)
                put("owner_name", owner)
                put("title", title)
                put("vehicle_type", type)
                put("capacity", cap)
                put("transmission_fuel", fuel)
                put("service_area", area)
                put("pickup_hubs", hubs)
                put("rental_mode", mode.name)
                put("rate_inr", rate)
                put("rate_basis", "per day")
                put("cancellation_terms", terms)
                put("approval_status", ListingApprovalStatus.APPROVED.name)
                put("last_updated_at", now - ageMs)
            })
        }

        addVehicle("veh_1", "p_owner_1", "Parvati Wheels Rental", "Royal Enfield Classic 350 Stealth", "Cruiser Bike", 2, "5-Speed • Petrol", "Kasol–Manikaran–Tosh, Jibhi–Tirthan Valley", "KUU,DHM,JDNX", RentalMode.SELF_DRIVE, 1500, "Valid DL required at Bhuntar/Joginder Nagar handover.", 3_600_000L)
        addVehicle("veh_2", "p_owner_2", "Tirthan 4x4 Fleet", "Mahindra Scorpio-N 4x4 (Hill Permit)", "4x4 SUV", 6, "Manual • Diesel 4WD", "Jibhi–Tirthan Valley, Kasol–Manikaran–Tosh, Kaza–Kibber–Langza–Hikkim", "KUU,IXC,KLK", RentalMode.WITH_DRIVER, 3800, "Free cancellation 12h before train/flight ETA.", 7_200_000L)
        addVehicle("veh_3", "p_owner_2", "Tirthan 4x4 Fleet", "Maruti Jimny 4x4 (Snow & Narrow Roads)", "Compact 4x4 SUV", 4, "Manual • Petrol 4x4", "Jibhi–Tirthan Valley, Bir–Billing & Barot Valley, Shimla–Narkanda", "KUU,KLK,SML,SLV,JDNX", RentalMode.BOTH, 3200, "Free reschedule for train/flight delays up to 3h.", 86_400_000L)
        addVehicle("veh_4", "p_owner_1", "Parvati Wheels Rental", "Royal Enfield Himalayan 450", "Adventure Bike", 2, "6-Speed • Petrol", "Kasol–Manikaran–Tosh, Sangla–Chitkul–Kalpa", "KUU,KLK,IXC", RentalMode.SELF_DRIVE, 1600, "Free cancellation 24h prior; 2 helmets included.", 5_000_000L)
        addVehicle("veh_5", "p_owner_3", "Kinnaur Mountain Rides", "Toyota Innova Crysta (Airport VIP Pickup)", "MPV / Family Cab", 7, "Diesel • Hill Cruise", "Shimla–Narkanda, Sangla–Chitkul–Kalpa, Jibhi–Tirthan Valley", "KLK,SML,SLV,IXC", RentalMode.WITH_DRIVER, 4200, "Includes luggage carrier & 90m train/flight delay wait.", 9_000_000L)
        addVehicle("veh_6", "p_owner_3", "Kinnaur Mountain Rides", "Force Gurkha 4x4 (Spiti & Chitkul Edition)", "Extreme 4x4 SUV", 5, "Diesel 4x4 • Snorkel", "Kaza–Kibber–Langza–Hikkim, Sangla–Chitkul–Kalpa", "KLK,SML,KUU,IXC", RentalMode.WITH_DRIVER, 4600, "Snow chains & oxygen cylinder included.", 10_000_000L)
        addVehicle("veh_7", "p_owner_2", "Tirthan 4x4 Fleet", "Mahindra Thar 4x4 Hardtop", "4x4 Off-Road SUV", 4, "Automatic • Diesel 4x4", "Jibhi–Tirthan Valley, Kasol–Manikaran–Tosh, Bir–Billing & Barot Valley", "KUU,DHM,JDNX,IXC", RentalMode.BOTH, 3900, "Free cancellation 24h prior.", 12_000_000L)
        addVehicle("veh_8", "p_owner_1", "Parvati Wheels Rental", "Maruti Swift Dzire (Budget Hub Transfer)", "Hill Sedan", 4, "Manual • Petrol/CNG", "Shimla–Narkanda, Bir–Billing & Barot Valley, Jibhi–Tirthan Valley", "KLK,SML,SLV,DHM,JDNX", RentalMode.WITH_DRIVER, 2200, "Best value railway station/airport feeder cab.", 14_000_000L)
        addVehicle("veh_9", "p_owner_2", "Tirthan 4x4 Fleet", "Force Tempo Traveller 12-Seater (Group)", "Group Minibus", 12, "Diesel • High Roof", "Jibhi–Tirthan Valley, Kasol–Manikaran–Tosh, Shimla–Narkanda", "IXC,KLK,KUU,DHM", RentalMode.WITH_DRIVER, 6500, "Ideal for college/family groups arriving at Chandigarh/Kalka.", 16_000_000L)
        addVehicle("veh_10", "p_owner_3", "Kinnaur Mountain Rides", "Bolero Neo Plus 9-Seater (Village Link)", "Rugged SUV", 9, "Diesel • High Clearance", "Sangla–Chitkul–Kalpa, Kaza–Kibber–Langza–Hikkim", "KLK,SML,KUU", RentalMode.WITH_DRIVER, 3600, "Built for steep link roads to Chitkul & Kibber.", 18_000_000L)
        addVehicle("veh_11", "p_owner_1", "Parvati Wheels Rental", "Hero XPulse 200 4V Rally", "Trail Bike", 2, "5-Speed • Petrol", "Bir–Billing & Barot Valley, Jibhi–Tirthan Valley", "DHM,JDNX,KUU", RentalMode.SELF_DRIVE, 1200, "Lightweight trail bike for Barot & Rajgundha tracks.", 20_000_000L)
        addVehicle("veh_12", "p_owner_2", "Tirthan 4x4 Fleet", "Hyundai Creta SX (Comfortable Hill SUV)", "Crossover SUV", 5, "Automatic • Petrol", "Shimla–Narkanda, Bir–Billing & Barot Valley, Jibhi–Tirthan Valley", "IXC,SLV,DHM,KUU", RentalMode.WITH_DRIVER, 3100, "Smooth highway + hill airport transfer.", 22_000_000L)

        // 10 Local Drivers (9 Approved + 1 Pending Admin Check)
        fun addDriver(
            id: String, provId: String, name: String, langs: String, exp: Int,
            area: String, hubs: String, rate: Int, summary: String, approval: ListingApprovalStatus
        ) {
            db.insert("drivers", null, ContentValues().apply {
                put("driver_id", id)
                put("provider_id", provId)
                put("name", name)
                put("languages", langs)
                put("experience_years", exp)
                put("service_area", area)
                put("pickup_hubs", hubs)
                put("rate_per_day_inr", rate)
                put("availability_summary", summary)
                put("cancellation_terms", "Free cancellation 6h prior; waits 90m for train/flight delays.")
                put("approval_status", approval.name)
                put("last_updated_at", now - 4_000_000L)
            })
        }

        addDriver("drv_1", "p_driver_1", "Vikram Negi", "Hindi, English, Pahari, Kinnauri", 11, "Kasol–Manikaran–Tosh, Sangla–Chitkul–Kalpa, Jibhi–Tirthan Valley", "KUU,KLK,SML,SLV,IXC", 1400, "Bhuntar Airport & Kalka Railway Station specialist", ListingApprovalStatus.APPROVED)
        addDriver("drv_2", "p_driver_2", "Sonam Dorje", "Hindi, Spiti Bhoti, English", 9, "Kaza–Kibber–Langza–Hikkim, Sangla–Chitkul–Kalpa", "KUU,KLK,IXC", 1800, "High-altitude snow road specialist (pending admin check)", ListingApprovalStatus.PENDING_REVIEW)
        addDriver("drv_3", "p_driver_3", "Pankaj Thakur", "Hindi, English, Kulluvi", 8, "Jibhi–Tirthan Valley, Kasol–Manikaran–Tosh", "KUU,JDNX,IXC", 1300, "Aut Tunnel & Jalori Pass expert • Morning/Evening shifts", ListingApprovalStatus.APPROVED)
        addDriver("drv_4", "p_driver_3", "Karanveer Singh", "Hindi, Punjabi, English", 14, "Shimla–Narkanda, Sangla–Chitkul–Kalpa", "KLK,SML,SLV,IXC", 1500, "Kalka Toy Train & Shimla Station pickup captain", ListingApprovalStatus.APPROVED)
        addDriver("drv_5", "p_driver_1", "Devender Chauhan", "Hindi, Pahari, English", 10, "Shimla–Narkanda, Jibhi–Tirthan Valley", "KLK,SML,SLV", 1350, "Snow-chain certified winter hill driver", ListingApprovalStatus.APPROVED)
        addDriver("drv_6", "p_driver_3", "Ramesh Katoch", "Hindi, Kangri, English", 12, "Bir–Billing & Barot Valley, Jibhi–Tirthan Valley", "DHM,JDNX,IXC", 1250, "Kangra Gaggal Airport & Joginder Nagar Station pickup", ListingApprovalStatus.APPROVED)
        addDriver("drv_7", "p_driver_1", "Tenzin Norbu", "Hindi, Tibetan, English", 7, "Kaza–Kibber–Langza–Hikkim, Kasol–Manikaran–Tosh", "KUU,IXC", 1700, "Atal Tunnel & Spiti circuit verified guide-driver", ListingApprovalStatus.APPROVED)
        addDriver("drv_8", "p_driver_3", "Suresh Kumar", "Hindi, English", 6, "Bir–Billing & Barot Valley", "DHM,JDNX", 1200, "Paragliding take-off & Barot Valley last-mile driver", ListingApprovalStatus.APPROVED)
        addDriver("drv_9", "p_driver_1", "Mahesh Sharma", "Hindi, English, Pahari", 15, "Sangla–Chitkul–Kalpa, Shimla–Narkanda", "KLK,SML,IXC", 1600, "Veteran Kinnaur NH-5 cliff road specialist", ListingApprovalStatus.APPROVED)
        addDriver("drv_10", "p_driver_3", "Anil Verma", "Hindi, English", 9, "Jibhi–Tirthan Valley, Shimla–Narkanda", "KUU,KLK,SML,IXC", 1450, "Family-friendly English-speaking chauffeur", ListingApprovalStatus.APPROVED)

        // Verification Docs
        db.insert("verification_documents", null, ContentValues().apply {
            put("document_id", "doc_1")
            put("provider_id", "p_driver_1")
            put("provider_name", "Vikram Negi")
            put("document_type", "HP Commercial Hill Driving Licence")
            put("file_reference", "private://docs/hp01_dl_vikram.pdf")
            put("review_status", VerificationStatus.VERIFIED.name)
            put("expiry_date", "2031-08-15")
            put("reviewer_notes", "Verified hill endorsement & badge.")
        })
        db.insert("verification_documents", null, ContentValues().apply {
            put("document_id", "doc_2")
            put("provider_id", "p_driver_2")
            put("provider_name", "Sonam Dorje")
            put("document_type", "HP Commercial Hill Driving Licence + Spiti Permit")
            put("file_reference", "private://docs/hp42_dl_sonam.pdf")
            put("review_status", VerificationStatus.PENDING.name)
            put("expiry_date", "2030-11-20")
            put("reviewer_notes", "Awaiting admin verification.")
        })
        db.insert("verification_documents", null, ContentValues().apply {
            put("document_id", "doc_3")
            put("provider_id", "p_host_1")
            put("provider_name", "Meera Chauhan")
            put("document_type", "HP Tourism Homestay Registration (#HP-KUL-2025-418)")
            put("file_reference", "private://docs/hp_homestay_meera.pdf")
            put("review_status", VerificationStatus.VERIFIED.name)
            put("expiry_date", "2028-03-31")
            put("reviewer_notes", "Valid HP Tourism homestay certificate.")
        })
        db.insert("verification_documents", null, ContentValues().apply {
            put("document_id", "doc_4")
            put("provider_id", "p_driver_3")
            put("provider_name", "Pankaj Thakur")
            put("document_type", "HP Commercial Hill Licence & Police Clearance")
            put("file_reference", "private://docs/hp34_dl_pankaj.pdf")
            put("review_status", VerificationStatus.VERIFIED.name)
            put("expiry_date", "2032-02-10")
            put("reviewer_notes", "Verified for Tirthan/Aut night pickups.")
        })

        // 10 Stays & Hotels across all Himachal valleys (with 18 RoomTypes!)
        fun addPropertyAndRooms(
            propId: String, provId: String, host: String, name: String, pType: String,
            cluster: String, hub: String, amenities: String, reg: String,
            rooms: List<Triple<String, String, Pair<Int, Int>>> // (roomId, roomName, occupancy to rate)
        ) {
            db.insert("properties", null, ContentValues().apply {
                put("property_id", propId)
                put("provider_id", provId)
                put("host_name", host)
                put("name", name)
                put("property_type", pType)
                put("location_cluster", cluster)
                put("nearest_hub_code", hub)
                put("amenities", amenities)
                put("policies", "Check-in 12:00 / Check-out 10:00 • Free cancellation 24h prior")
                put("registration_number", reg)
                put("approval_status", ListingApprovalStatus.APPROVED.name)
                put("last_updated_at", now - 24_000_000L)
            })
            rooms.forEach { (rId, rName, occRate) ->
                db.insert("room_types", null, ContentValues().apply {
                    put("room_type_id", rId)
                    put("property_id", propId)
                    put("name", rName)
                    put("occupancy", occRate.first)
                    put("rate_per_night_inr", occRate.second)
                    put("inventory_count", 3)
                })
            }
        }

        addPropertyAndRooms("prop_1", "p_host_1", "Meera Chauhan", "Tirthan Cedar Creek Homestay", "Homestay", "Jibhi–Tirthan Valley", "KUU", "Bukhari wood heater, Home-cooked Himachali meals, Solar backup, Parking", "HP-KUL-HS-2025-418", listOf(Triple("room_1", "Deodar Valley Attic Room", 3 to 2200), Triple("room_2", "Family Riverfront Cottage", 5 to 3900)))
        addPropertyAndRooms("prop_2", "p_host_1", "Meera Chauhan", "Tosh Orchard Wooden Stay", "Guesthouse", "Kasol–Manikaran–Tosh", "KUU", "Mountain balcony, Hot water, Wi-Fi backup, Trail guide", "HP-KUL-GH-2025-902", listOf(Triple("room_3", "Parvati View Timber Room", 2 to 1800), Triple("room_4", "Glacier Loft Suite", 4 to 2900)))
        addPropertyAndRooms("prop_3", "p_host_2", "Tashi Bodh", "Spiti Starlight Mud-House & Hotel", "Boutique Hotel", "Kaza–Kibber–Langza–Hikkim", "KUU", "Bukhari heating, Oxygen backup, Hot water bottles, Stargazing deck", "HP-LS-HT-2025-114", listOf(Triple("room_5", "Heated Monastery View Room", 2 to 2800), Triple("room_6", "Kibber Family Suite", 4 to 4500)))
        addPropertyAndRooms("prop_4", "p_host_2", "Tashi Bodh", "Chitkul Last-Village Wooden Inn", "Hotel", "Sangla–Chitkul–Kalpa", "KLK", "Baspa river view, Electric blankets, Kinnauri dinner, Driver stay", "HP-KIN-HT-2025-307", listOf(Triple("room_7", "Baspa River Deluxe", 2 to 2600), Triple("room_8", "Snow Peak Family Room", 4 to 4100)))
        addPropertyAndRooms("prop_5", "p_host_1", "Meera Chauhan", "Jibhi Waterfall Treehouse & Cabins", "Boutique Hotel", "Jibhi–Tirthan Valley", "KUU", "Private pine deck, Bonfire pit, High-speed fiber, Cafe", "HP-KUL-HT-2025-529", listOf(Triple("room_9", "Pine Canopy Treehouse", 2 to 3400), Triple("room_10", "Cedar Glass Cabin", 3 to 4200)))
        addPropertyAndRooms("prop_6", "p_host_1", "Meera Chauhan", "Barot Trout Valley Eco-Lodge", "Homestay", "Bir–Billing & Barot Valley", "JDNX", "Uhl riverside lawn, Fresh trout dinner, Bike parking, Wi-Fi", "HP-MND-HS-2025-210", listOf(Triple("room_11", "Uhl River Stone Cottage", 3 to 2100)))
        addPropertyAndRooms("prop_7", "p_host_1", "Meera Chauhan", "Bir Paragliding Landing Hotel", "Boutique Hotel", "Bir–Billing & Barot Valley", "DHM", "Landing-site view, Rooftop cafe, Airport shuttle desk", "HP-KNG-HT-2025-642", listOf(Triple("room_12", "Dhauladhar Sunrise Room", 2 to 2500), Triple("room_13", "Group Dormitory (4 Beds)", 4 to 3200)))
        addPropertyAndRooms("prop_8", "p_host_2", "Tashi Bodh", "Narkanda Apple Blossom Resort", "Hotel", "Shimla–Narkanda", "KLK", "Hatu Peak trail access, Central heating, Orchard dining, Parking", "HP-SML-HT-2025-775", listOf(Triple("room_14", "Orchard Executive Suite", 3 to 3800), Triple("room_15", "Himalayan Panorama Room", 2 to 2900)))
        addPropertyAndRooms("prop_9", "p_host_2", "Tashi Bodh", "Kalpa Kinner Kailash Heritage Hotel", "Hotel", "Sangla–Chitkul–Kalpa", "SML", "Direct Kinner Kailash view, Room heater, Apple orchard walk", "HP-KIN-HT-2025-881", listOf(Triple("room_16", "Kailash View Deluxe", 2 to 3300)))
        addPropertyAndRooms("prop_10", "p_host_1", "Meera Chauhan", "Kasol Riverside Retreat & Camp", "Guesthouse", "Kasol–Manikaran–Tosh", "KUU", "Parvati riverside lawn, Acoustic evenings, Hot showers", "HP-KUL-GH-2025-994", listOf(Triple("room_17", "Riverside Swiss Cottage", 2 to 1950), Triple("room_18", "Family Pine Chalet", 4 to 3500)))

        // 6 Mountain Dining Spots
        fun insertDining(id: String, provId: String, name: String, cluster: String, spec: String, dishes: String, amb: String, cost: Int, rating: Double) {
            db.insert("dining_spots", null, ContentValues().apply {
                put("spot_id", id)
                put("provider_id", provId)
                put("name", name)
                put("location_cluster", cluster)
                put("specialty", spec)
                put("dishes", dishes)
                put("ambience", amb)
                put("avg_cost_inr", cost)
                put("is_fssai_verified", 1)
                put("rating", rating)
            })
        }
        insertDining("food_1", "p_host_1", "The Himalayan Trout House & Cafe", "Jibhi–Tirthan Valley", "Fresh Trout", "Fresh Rainbow Trout, Herb Butter, Smoked Trout", "Riverside Deck • Top Rated", 750, 4.9)
        insertDining("food_2", "p_host_1", "Mama's Mountain Siddu Kitchen", "Kasol–Manikaran–Tosh", "Pahadi Dham & Siddu", "Steamed Walnut Siddu, Chana Madra, Ghee", "Cozy Stone Hearth • Local Favorite", 250, 4.8)
        insertDining("food_3", "p_host_2", "Sol Monastery Bakery & Tea", "Kaza–Kibber–Langza–Hikkim", "Woodfired Cafe", "Yak Cheese Pizza, Sea Buckthorn Tea, Apple Tart", "Warm Bukhari Hall • 3,800m", 480, 4.7)
        insertDining("food_4", "p_host_2", "Baspa Valley Apple Orchard Cafe", "Sangla–Chitkul–Kalpa", "Pahadi Dham & Siddu", "Kinnauri Rajma Chawal, Buckwheat Chilta, Apricot Jam", "Snow-Peak Patio • Family Friendly", 400, 4.8)
        insertDining("food_5", "p_host_1", "Uhl River Fresh Trout Dhaba", "Bir–Billing & Barot Valley", "Fresh Trout", "Crispy River Trout, Pahadi Dal, Tandoori Roti", "Wooden Bridge View • Barot", 550, 4.8)
        insertDining("food_6", "p_host_2", "Hatu Ridge Woodfired Bistro", "Shimla–Narkanda", "Woodfired Cafe", "Sourdough Garlic Bread, Himachali Thali, Hot Chocolate", "Pine Ridge Terrace • Narkanda", 620, 4.7)

        // 8 Instagram-style Vertical Reels & Stories
        fun insertReel(
            id: String, author: String, handle: String, cluster: String, hub: String,
            title: String, caption: String, tag: String, cat: Int, price: String,
            gStart: Long, gEnd: Long, likes: Int
        ) {
            db.insert("travel_reels", null, ContentValues().apply {
                put("reel_id", id)
                put("author_name", author)
                put("author_handle", handle)
                put("cluster_name", cluster)
                put("hub_code", hub)
                put("title", title)
                put("caption", caption)
                put("tag_label", tag)
                put("linked_category", cat)
                put("price_hint", price)
                put("gradient_start", gStart)
                put("gradient_end", gEnd)
                put("base_likes", likes)
            })
        }

        insertReel("reel_1", "Tirthan 4x4 Fleet", "@tirthan_4x4", "Jibhi–Tirthan Valley", "KUU", "Sunrise Pickup: Bhuntar Airport → Jibhi Treehouses", "Land at KUU at 9 AM, meet our verified Scorpio-N at Gate 1, and cross Aut Tunnel straight into pine waterfalls!", "AIRPORT PICKUP", 0, "From ₹3,800/day", 0xFF0F5132, 0xFF15803D, 342)
        insertReel("reel_2", "Meera Chauhan", "@tirthan_cedar", "Jibhi–Tirthan Valley", "KUU", "Cedar Creek Homestay & Riverfront Morning Chai", "Wake up beside Tirthan river with wood-fired Bukhari warmth and home-cooked Siddu. Zero city traffic, pure peace.", "VERIFIED HOMESTAY", 2, "From ₹2,200/night", 0xFF1565C0, 0xFF0284C7, 489)
        insertReel("reel_3", "Vikram Negi", "@vikram_hilldrive", "Kasol–Manikaran–Tosh", "KUU", "Last-Mile Climb to Tosh Village (Snow & Hairpins)", "Public buses stop at Barshaini—our hill-permit 4x4 pickups take you safely all the way to Tosh orchard stays.", "LOCAL DRIVER", 1, "₹1,400/day driver", 0xFFD84315, 0xFFF59E0B, 275)
        insertReel("reel_4", "Tashi Bodh", "@spiti_starlight", "Kaza–Kibber–Langza–Hikkim", "KUU", "Stargazing at 3,800m • Spiti Starlight Mud-House", "Heated rooms, oxygen backup, and zero-signal offline booking IDs so you stay worry-free in Lahaul & Spiti.", "BOUTIQUE HOTEL", 2, "From ₹2,800/night", 0xFF1E1B4B, 0xFF4F46E5, 612)
        insertReel("reel_5", "Kinnaur Rides", "@kinnaur_4x4", "Sangla–Chitkul–Kalpa", "KLK", "Kalka Railway Station → Chitkul Last Village Drive", "Step off the Kalka Shatabdi onto Platform 1 and ride our Force Gurkha along the cliff-hugging Baspa river.", "RAILWAY PICKUP", 0, "From ₹4,600/day", 0xFF0F766E, 0xFF0EA5E9, 398)
        insertReel("reel_6", "Parvati Wheels", "@parvati_riders", "Kasol–Manikaran–Tosh", "KUU", "Himalayan 450 & Classic 350 Self-Drive Trails", "Pick up your bike right after Bhuntar arrival—helmets, bungee cords, and offline trail maps included.", "SELF-DRIVE BIKE", 0, "From ₹1,500/day", 0xFF7C2D12, 0xFFEA580C, 530)
        insertReel("reel_7", "Ramesh Katoch", "@bir_barot_cabs", "Bir–Billing & Barot Valley", "DHM", "Kangra Airport → Bir Landing Site & Barot Trout", "Fly into Gaggal (DHM) or arrive on the narrow-gauge train at Joginder Nagar—seamless transfer to Barot valley.", "VALLEY TRANSFER", 1, "₹1,250/day driver", 0xFF065F46, 0xFF10B981, 214)
        insertReel("reel_8", "Narkanda Resorts", "@narkanda_apple", "Shimla–Narkanda", "KLK", "Apple Blossom Resort & Hatu Peak Snow Walk", "3.5 hours from Kalka Station—warm cedar suites surrounded by snowy apple orchards above Shimla.", "HILL HOTEL", 2, "From ₹2,900/night", 0xFF881337, 0xFFE11D48, 367)

        // Seed Bookings & Notifications
        db.insert("bookings", null, ContentValues().apply {
            put("booking_id", "bk_seed_1")
            put("tourist_id", "u_tourist_1")
            put("tourist_name", "Aarav Sharma")
            put("provider_id", "p_owner_2")
            put("listing_id", "veh_2")
            put("listing_title", "Mahindra Scorpio-N 4x4 (Hill Permit)")
            put("booking_type", BookingType.HUB_PICKUP.name)
            put("pickup_hub_code", "KUU")
            put("destination_cluster", "Jibhi–Tirthan Valley")
            put("train_or_flight_no", "9I-805 (Bhuntar Arrival)")
            put("pickup_notes", "2 adults + 2 rucksacks, Arrival Gate 1")
            put("start_epoch_day", todayEpoch - 4)
            put("end_epoch_day", todayEpoch - 4)
            put("slot", TimeSlot.MORNING_06_12.name)
            put("guest_or_seat_count", 2)
            put("status", BookingStatus.COMPLETED.name)
            put("quoted_price_inr", 3800)
            put("cancellation_terms", "Free cancellation up to 12h before arrival.")
            put("created_at", now - 5L * 86_400_000L)
            put("responded_at", now - 5L * 86_400_000L + 900_000L)
        })

        db.insert("bookings", null, ContentValues().apply {
            put("booking_id", "bk_seed_2")
            put("tourist_id", "u_tourist_1")
            put("tourist_name", "Aarav Sharma")
            put("provider_id", "p_owner_2")
            put("listing_id", "veh_3")
            put("listing_title", "Maruti Jimny 4x4 (Snow & Narrow Roads)")
            put("booking_type", BookingType.HUB_PICKUP.name)
            put("pickup_hub_code", "KLK")
            put("destination_cluster", "Shimla–Narkanda")
            put("train_or_flight_no", "12011 Kalka Shatabdi")
            put("pickup_notes", "Pickup from Kalka Platform 1 Exit Bay")
            put("start_epoch_day", todayEpoch + 2)
            put("end_epoch_day", todayEpoch + 3)
            put("slot", TimeSlot.AFTERNOON_12_18.name)
            put("guest_or_seat_count", 3)
            put("status", BookingStatus.PENDING.name)
            put("quoted_price_inr", 6400)
            put("cancellation_terms", "Free reschedule for train/flight delays up to 3 hours.")
            put("created_at", now - 1_800_000L)
            putNull("responded_at")
        })

        fun insertNotif(id: String, userId: String, cat: NotificationCategory, type: String, msg: String, ageMs: Long) {
            db.insert("notifications", null, ContentValues().apply {
                put("notification_id", id)
                put("user_id", userId)
                put("category", cat.name)
                put("event_type", type)
                put("message", msg)
                put("is_read", 0)
                put("created_at", now - ageMs)
            })
        }

        insertNotif("not_1", "u_tourist_1", NotificationCategory.TRIPS, "BOOKING_STATUS", "Your Kalka Railway Station (KLK) pickup request for Maruti Jimny 4x4 is Pending confirmation.", 1_800_000L)
        insertNotif("not_2", "u_tourist_1", NotificationCategory.DEALS, "FOODIE_ALERT", "Foodie Alert: Fresh Himalayan Rainbow Trout is in peak season at Tirthan Valley & Sangla. Reserve your table now!", 3_600_000L)
        insertNotif("not_3", "u_tourist_1", NotificationCategory.TRIPS, "ROAD_UPDATE", "Road Status Update: Jalori Pass is clear and open for 4x4 vehicles. Drive safely through Shoja curves!", 7_200_000L)
        insertNotif("not_4", "u_tourist_1", NotificationCategory.AI_TIPS, "MOTOAI_TIP", "MotoAI Tip: Kaza temperatures drop to -5°C tonight. Choose stays with Bukhari heating or oxygen support.", 10_800_000L)

        insertNotif("not_owner2", "u_owner_2", NotificationCategory.TRIPS, "BOOKING_REQUESTED", "New Kalka Railway Station (KLK) pickup request from Aarav Sharma for Maruti Jimny 4x4.", 1_800_000L)

        db.insert("audit_logs", null, ContentValues().apply {
            put("audit_id", "aud_1")
            put("actor_id", "u_admin_1")
            put("actor_role", UserRole.ADMINISTRATOR.name)
            put("action", "PROVIDER_VERIFIED")
            put("target_id", "p_driver_1")
            put("details", "Verified HP Commercial Hill Driving Licence for Vikram Negi.")
            put("timestamp", twoDaysAgo)
        })
    }

    companion object {
        private const val DB_NAME = "motomove_himachal.db"
        private const val DB_VERSION = 3

        private const val PBKDF2_ITERATIONS = 10_000
        private const val KEY_LENGTH_BITS = 256

        fun hashPassword(raw: String): String {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val derived = pbkdf2(raw.toCharArray(), salt)
            return "${salt.toHex()}:${derived.toHex()}"
        }

        fun verifyPassword(raw: String, stored: String): Boolean {
            val parts = stored.split(":")
            if (parts.size != 2) return false
            val salt = parts[0].hexToBytes() ?: return false
            val expected = parts[1].hexToBytes() ?: return false
            val actual = pbkdf2(raw.toCharArray(), salt)
            return MessageDigest.isEqual(expected, actual)
        }

        private fun pbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
            val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun String.hexToBytes(): ByteArray? {
            if (length % 2 != 0) return null
            return ByteArray(length / 2) { i ->
                val idx = i * 2
                substring(idx, idx + 2).toIntOrNull(16)?.toByte() ?: return null
            }
        }
    }
}
