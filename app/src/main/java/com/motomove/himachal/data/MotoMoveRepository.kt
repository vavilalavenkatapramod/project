package com.motomove.himachal.data

import android.content.ContentValues
import android.database.Cursor
import com.motomove.himachal.domain.BookingRulesEngine
import com.motomove.himachal.domain.RuleResult
import com.motomove.himachal.model.AppNotification
import com.motomove.himachal.model.AuditLog
import com.motomove.himachal.model.Booking
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.BookingType
import com.motomove.himachal.model.DiningSpot
import com.motomove.himachal.model.DriverProfile
import com.motomove.himachal.model.ListingApprovalStatus
import com.motomove.himachal.model.NotificationCategory
import com.motomove.himachal.model.PilotMetrics
import com.motomove.himachal.model.PropertyListing
import com.motomove.himachal.model.ProviderProfile
import com.motomove.himachal.model.RentalMode
import com.motomove.himachal.model.Review
import com.motomove.himachal.model.RoomType
import com.motomove.himachal.model.TimeSlot
import com.motomove.himachal.model.TravelReel
import com.motomove.himachal.model.User
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.model.VehicleListing
import com.motomove.himachal.model.VerificationDocument
import com.motomove.himachal.model.VerificationStatus
import java.time.LocalDate
import java.util.UUID

sealed class OpResult<out T> {
    data class Success<T>(val data: T) : OpResult<T>()
    data class Error(val message: String) : OpResult<Nothing>()
}

class MotoMoveRepository(private val dbHelper: MotoMoveDatabaseHelper) {

    fun getAllDemoUsers(): List<User> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<User>()
        db.rawQuery("SELECT * FROM users ORDER BY role, name", null).use { c ->
            while (c.moveToNext()) list.add(c.toUser().copy(passwordHash = ""))
        }
        return list
    }

    fun authenticate(contact: String, password: String): OpResult<User> {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT * FROM users WHERE LOWER(contact)=LOWER(?)",
            arrayOf(contact.trim())
        ).use { c ->
            if (c.moveToFirst()) {
                val user = c.toUser()
                if (user.accountStatus != "ACTIVE") {
                    return OpResult.Error("This account is suspended.")
                }
                if (MotoMoveDatabaseHelper.verifyPassword(password, user.passwordHash)) {
                    return OpResult.Success(user.copy(passwordHash = ""))
                }
            }
            return OpResult.Error("Invalid email/phone or password.")
        }
    }

    fun registerUser(
        name: String,
        contact: String,
        password: String,
        role: UserRole,
        homeCluster: String
    ): OpResult<User> {
        if (role == UserRole.ADMINISTRATOR) {
            return OpResult.Error("Access Denied: Administrator accounts cannot be self-registered.")
        }
        if (name.isBlank() || contact.isBlank() || password.length < 4) {
            return OpResult.Error("Provide name, contact, and password (4+ chars).")
        }
        val db = dbHelper.writableDatabase
        db.rawQuery("SELECT user_id FROM users WHERE LOWER(contact)=LOWER(?)", arrayOf(contact.trim())).use {
            if (it.moveToFirst()) return OpResult.Error("An account with this contact already exists.")
        }
        val userId = "u_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()
        db.insert("users", null, ContentValues().apply {
            put("user_id", userId)
            put("name", name.trim())
            put("contact", contact.trim())
            put("password_hash", MotoMoveDatabaseHelper.hashPassword(password))
            put("role", role.name)
            put("account_status", "ACTIVE")
        })
        if (role in setOf(UserRole.VEHICLE_OWNER, UserRole.LOCAL_DRIVER, UserRole.ACCOMMODATION_PROVIDER)) {
            val providerId = "p_${UUID.randomUUID().toString().take(8)}"
            db.insert("provider_profiles", null, ContentValues().apply {
                put("provider_id", providerId)
                put("user_id", userId)
                put("display_name", name.trim())
                put("provider_type", role.name)
                put("verification_status", VerificationStatus.PENDING.name)
                put("home_cluster", homeCluster)
                put("last_updated_at", now)
            })
        }
        return OpResult.Success(User(userId, name.trim(), contact.trim(), "", role))
    }

    fun updateOwnProfileName(actor: User, targetUserId: String, newName: String): OpResult<User> {
        when (val guard = BookingRulesEngine.verifyProfileSelfEdit(actor, targetUserId)) {
            is RuleResult.Denied -> {
                writeAudit(actor.userId, actor.role.name, "SECURITY_DENIED_PROFILE", targetUserId, guard.reason)
                return OpResult.Error(guard.reason)
            }
            RuleResult.Allowed -> Unit
        }
        if (newName.isBlank()) return OpResult.Error("Name cannot be empty.")
        val cleanName = newName.trim()
        val db = dbHelper.writableDatabase
        db.update("users", ContentValues().apply { put("name", cleanName) }, "user_id=?", arrayOf(actor.userId))
        db.update("provider_profiles", ContentValues().apply { put("display_name", cleanName) }, "user_id=?", arrayOf(actor.userId))
        writeAudit(actor.userId, actor.role.name, "PROFILE_UPDATED", actor.userId, "Updated own display name.")
        return OpResult.Success(actor.copy(name = cleanName))
    }

    fun getProviderForUser(userId: String): ProviderProfile? {
        dbHelper.readableDatabase.rawQuery("SELECT * FROM provider_profiles WHERE user_id=?", arrayOf(userId)).use { c ->
            return if (c.moveToFirst()) c.toProvider() else null
        }
    }

    fun getVehicles(includeUnapproved: Boolean = false): List<VehicleListing> {
        val where = if (includeUnapproved) "" else "WHERE v.approval_status='APPROVED' AND p.verification_status='VERIFIED'"
        val sql = """
            SELECT v.*, p.verification_status FROM vehicles v
            JOIN provider_profiles p ON v.provider_id = p.provider_id
            $where
            ORDER BY v.last_updated_at DESC
        """.trimIndent()
        val list = mutableListOf<VehicleListing>()
        dbHelper.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) list.add(c.toVehicle())
        }
        return list
    }

    fun getDrivers(includeUnapproved: Boolean = false): List<DriverProfile> {
        val where = if (includeUnapproved) "" else "WHERE d.approval_status='APPROVED' AND p.verification_status='VERIFIED'"
        val sql = """
            SELECT d.*, p.verification_status FROM drivers d
            JOIN provider_profiles p ON d.provider_id = p.provider_id
            $where
            ORDER BY d.last_updated_at DESC
        """.trimIndent()
        val list = mutableListOf<DriverProfile>()
        dbHelper.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) list.add(c.toDriver())
        }
        return list
    }

    fun getProperties(includeUnapproved: Boolean = false): List<PropertyListing> {
        val where = if (includeUnapproved) "" else "WHERE prop.approval_status='APPROVED' AND p.verification_status='VERIFIED'"
        val sql = """
            SELECT prop.*, p.verification_status FROM properties prop
            JOIN provider_profiles p ON prop.provider_id = p.provider_id
            $where
            ORDER BY prop.last_updated_at DESC
        """.trimIndent()
        val list = mutableListOf<PropertyListing>()
        dbHelper.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) list.add(c.toProperty())
        }
        return list
    }

    fun getDiningSpots(): List<DiningSpot> {
        val list = mutableListOf<DiningSpot>()
        dbHelper.readableDatabase.rawQuery("SELECT * FROM dining_spots ORDER BY rating DESC", null).use { c ->
            while (c.moveToNext()) list.add(c.toDiningSpot())
        }
        return list
    }

    fun getReelsForUser(userId: String?): List<TravelReel> {
        val db = dbHelper.readableDatabase
        val userPrefs = mutableMapOf<String, Pair<Boolean, Boolean>>()
        if (userId != null) {
            db.rawQuery("SELECT reel_id, is_liked, is_saved FROM user_reel_interactions WHERE user_id=?", arrayOf(userId)).use { c ->
                while (c.moveToNext()) {
                    userPrefs[c.getString(0)] = (c.getInt(1) == 1) to (c.getInt(2) == 1)
                }
            }
        }
        val totalLikesByReel = mutableMapOf<String, Int>()
        db.rawQuery("SELECT reel_id, SUM(is_liked) FROM user_reel_interactions GROUP BY reel_id", null).use { c ->
            while (c.moveToNext()) {
                totalLikesByReel[c.getString(0)] = c.getInt(1)
            }
        }

        val list = mutableListOf<TravelReel>()
        db.rawQuery("SELECT * FROM travel_reels", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(c.getColumnIndexOrThrow("reel_id"))
                val baseLikes = c.getInt(c.getColumnIndexOrThrow("base_likes"))
                val extraLikes = totalLikesByReel[id] ?: 0
                val (liked, saved) = userPrefs[id] ?: (false to false)
                list.add(
                    TravelReel(
                        reelId = id,
                        authorName = c.getString(c.getColumnIndexOrThrow("author_name")),
                        authorHandle = c.getString(c.getColumnIndexOrThrow("author_handle")),
                        clusterName = c.getString(c.getColumnIndexOrThrow("cluster_name")),
                        hubCode = c.getString(c.getColumnIndexOrThrow("hub_code")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        caption = c.getString(c.getColumnIndexOrThrow("caption")),
                        tagLabel = c.getString(c.getColumnIndexOrThrow("tag_label")),
                        linkedCategory = c.getInt(c.getColumnIndexOrThrow("linked_category")),
                        priceHint = c.getString(c.getColumnIndexOrThrow("price_hint")),
                        gradientStartHex = c.getLong(c.getColumnIndexOrThrow("gradient_start")),
                        gradientEndHex = c.getLong(c.getColumnIndexOrThrow("gradient_end")),
                        likeCount = baseLikes + extraLikes,
                        isLikedByMe = liked,
                        isSavedByMe = saved
                    )
                )
            }
        }
        return list
    }

    fun toggleReelLike(actor: User, reelId: String) {
        toggleReelFlag(actor.userId, reelId, toggleLike = true)
    }

    fun toggleReelSave(actor: User, reelId: String) {
        toggleReelFlag(actor.userId, reelId, toggleLike = false)
    }

    private fun toggleReelFlag(userId: String, reelId: String, toggleLike: Boolean) {
        val db = dbHelper.writableDatabase
        var liked = false
        var saved = false
        db.rawQuery(
            "SELECT is_liked, is_saved FROM user_reel_interactions WHERE user_id=? AND reel_id=?",
            arrayOf(userId, reelId)
        ).use { c ->
            if (c.moveToFirst()) {
                liked = c.getInt(0) == 1
                saved = c.getInt(1) == 1
            }
        }
        if (toggleLike) liked = !liked else saved = !saved
        db.insertWithOnConflict(
            "user_reel_interactions",
            null,
            ContentValues().apply {
                put("user_id", userId)
                put("reel_id", reelId)
                put("is_liked", if (liked) 1 else 0)
                put("is_saved", if (saved) 1 else 0)
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getRoomsForProperty(propertyId: String): List<RoomType> {
        val list = mutableListOf<RoomType>()
        dbHelper.readableDatabase.rawQuery("SELECT * FROM room_types WHERE property_id=?", arrayOf(propertyId)).use { c ->
            while (c.moveToNext()) list.add(c.toRoomType())
        }
        return list
    }

    fun getAllRooms(): List<RoomType> {
        val list = mutableListOf<RoomType>()
        dbHelper.readableDatabase.rawQuery("SELECT * FROM room_types", null).use { c ->
            while (c.moveToNext()) list.add(c.toRoomType())
        }
        return list
    }

    fun getBookingsForActor(actor: User): List<Booking> {
        expireStalePendingBookings()
        val provider = getProviderForUser(actor.userId)
        val all = mutableListOf<Booking>()
        dbHelper.readableDatabase.rawQuery("SELECT * FROM bookings ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) all.add(c.toBooking())
        }
        return when (actor.role) {
            UserRole.TOURIST -> all.filter { it.touristId == actor.userId }
            UserRole.ADMINISTRATOR -> all
            else -> all.filter { it.providerId == provider?.providerId }
        }
    }

    fun getVerificationDocumentsForActor(actor: User): List<VerificationDocument> {
        val list = mutableListOf<VerificationDocument>()
        val sqlAndArgs: Pair<String, Array<String>?> = when (actor.role) {
            UserRole.ADMINISTRATOR -> "SELECT * FROM verification_documents" to null
            UserRole.TOURIST -> return emptyList()
            else -> {
                val provId = getProviderForUser(actor.userId)?.providerId ?: return emptyList()
                "SELECT * FROM verification_documents WHERE provider_id=?" to arrayOf(provId)
            }
        }
        dbHelper.readableDatabase.rawQuery(sqlAndArgs.first, sqlAndArgs.second).use { c ->
            while (c.moveToNext()) list.add(c.toVerificationDoc())
        }
        return list
    }

    fun getReviewsForListing(listingId: String? = null): List<Review> {
        val sql = if (listingId == null) {
            "SELECT * FROM reviews WHERE moderation_status='VISIBLE' ORDER BY created_at DESC"
        } else {
            "SELECT * FROM reviews WHERE listing_id=? AND moderation_status='VISIBLE' ORDER BY created_at DESC"
        }
        val args = if (listingId == null) null else arrayOf(listingId)
        val list = mutableListOf<Review>()
        dbHelper.readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) list.add(c.toReview())
        }
        return list
    }

    fun getNotificationsForUser(userId: String): List<AppNotification> {
        val list = mutableListOf<AppNotification>()
        dbHelper.readableDatabase.rawQuery(
            "SELECT * FROM notifications WHERE user_id=? ORDER BY created_at DESC",
            arrayOf(userId)
        ).use { c ->
            while (c.moveToNext()) list.add(c.toNotification())
        }
        return list
    }

    fun markAllNotificationsRead(actor: User) {
        dbHelper.writableDatabase.update(
            "notifications",
            ContentValues().apply { put("is_read", 1) },
            "user_id=?",
            arrayOf(actor.userId)
        )
    }

    fun sendTestAlert(actor: User) {
        insertNotification(
            userId = actor.userId,
            category = NotificationCategory.TRIPS,
            eventType = "HUB_STATUS",
            message = "Test Alert: Bhuntar Airport (KUU) & Kalka Railway Station (KLK) verified drivers are ready."
        )
    }

    fun getAuditLogsForActor(actor: User): List<AuditLog> {
        if (actor.role != UserRole.ADMINISTRATOR) return emptyList()
        val list = mutableListOf<AuditLog>()
        dbHelper.readableDatabase.rawQuery("SELECT * FROM audit_logs ORDER BY timestamp DESC", null).use { c ->
            while (c.moveToNext()) list.add(c.toAuditLog())
        }
        return list
    }

    fun createMobilityBooking(
        tourist: User,
        providerId: String,
        listingId: String,
        listingTitle: String,
        approvalStatus: ListingApprovalStatus,
        providerVerification: VerificationStatus,
        maxCapacity: Int,
        bookingType: BookingType,
        pickupHubCode: String?,
        destinationCluster: String,
        trainOrFlightNo: String,
        pickupNotes: String,
        startEpochDay: Long,
        endEpochDay: Long,
        slot: TimeSlot,
        seatsRequested: Int,
        ratePerUnitInr: Int,
        cancellationTerms: String
    ): OpResult<Booking> {
        if (tourist.role != UserRole.TOURIST) {
            return OpResult.Error("Only Tourist accounts can submit booking requests (FR-02).")
        }
        when (val rule = BookingRulesEngine.validateMobilityRequest(
            approvalStatus = approvalStatus,
            providerVerification = providerVerification,
            startDay = startEpochDay,
            endDay = endEpochDay,
            seatsRequested = seatsRequested,
            maxCapacity = maxCapacity,
            bookingType = bookingType,
            pickupHubCode = pickupHubCode
        )) {
            is RuleResult.Denied -> return OpResult.Error(rule.reason)
            RuleResult.Allowed -> Unit
        }
        val days = (endEpochDay - startEpochDay + 1).coerceAtLeast(1L).toInt()
        val booking = Booking(
            bookingId = "bk_${UUID.randomUUID().toString().take(8)}",
            touristId = tourist.userId,
            touristName = tourist.name,
            providerId = providerId,
            listingId = listingId,
            listingTitle = listingTitle,
            bookingType = bookingType,
            pickupHubCode = pickupHubCode,
            destinationCluster = destinationCluster,
            trainOrFlightNo = trainOrFlightNo.ifBlank { null },
            pickupNotes = pickupNotes.ifBlank { "Standard hub/cluster pickup" },
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            slot = slot,
            guestOrSeatCount = seatsRequested,
            status = BookingStatus.PENDING,
            quotedPriceInr = ratePerUnitInr * days,
            cancellationTerms = cancellationTerms,
            createdAt = System.currentTimeMillis()
        )
        insertBooking(booking)
        notifyProviderOwner(
            providerId = providerId,
            category = NotificationCategory.TRIPS,
            eventType = "BOOKING_REQUESTED",
            message = "New ${bookingType.label} request from ${tourist.name} for $listingTitle ($destinationCluster)."
        )
        writeAudit(tourist.userId, tourist.role.name, "BOOKING_CREATED", booking.bookingId, "Mobility request submitted.")
        return OpResult.Success(booking)
    }

    fun createStayBooking(
        tourist: User,
        property: PropertyListing,
        roomType: RoomType,
        checkInDay: Long,
        checkOutDay: Long,
        guests: Int,
        notes: String
    ): OpResult<Booking> {
        if (tourist.role != UserRole.TOURIST) {
            return OpResult.Error("Only Tourist accounts can submit stay requests (FR-02).")
        }
        val allBookings = readAllBookingsRaw()
        when (val rule = BookingRulesEngine.validateStayRequest(
            approvalStatus = property.approvalStatus,
            providerVerification = property.providerVerification,
            roomType = roomType,
            checkInDay = checkInDay,
            checkOutDay = checkOutDay,
            guests = guests,
            existingBookings = allBookings
        )) {
            is RuleResult.Denied -> return OpResult.Error(rule.reason)
            RuleResult.Allowed -> Unit
        }
        val nights = (checkOutDay - checkInDay).toInt()
        val booking = Booking(
            bookingId = "bk_${UUID.randomUUID().toString().take(8)}",
            touristId = tourist.userId,
            touristName = tourist.name,
            providerId = property.providerId,
            listingId = roomType.roomTypeId,
            listingTitle = "${property.name} — ${roomType.name}",
            bookingType = BookingType.STAY,
            pickupHubCode = property.nearestHubCode,
            destinationCluster = property.locationCluster,
            trainOrFlightNo = null,
            pickupNotes = notes.ifBlank { "Standard homestay check-in" },
            startEpochDay = checkInDay,
            endEpochDay = checkOutDay,
            slot = TimeSlot.FULL_DAY,
            guestOrSeatCount = guests,
            status = BookingStatus.PENDING,
            quotedPriceInr = roomType.ratePerNightInr * nights,
            cancellationTerms = property.policies,
            createdAt = System.currentTimeMillis()
        )
        insertBooking(booking)
        notifyProviderOwner(
            providerId = property.providerId,
            category = NotificationCategory.TRIPS,
            eventType = "STAY_REQUESTED",
            message = "New stay request from ${tourist.name} at ${property.name} ($nights nights)."
        )
        writeAudit(tourist.userId, tourist.role.name, "STAY_BOOKING_CREATED", booking.bookingId, "Stay request submitted.")
        return OpResult.Success(booking)
    }

    fun createDiningReservation(
        tourist: User,
        spot: DiningSpot,
        guests: Int,
        notes: String
    ): OpResult<Booking> {
        if (tourist.role != UserRole.TOURIST) {
            return OpResult.Error("Only Tourist accounts can reserve tables.")
        }
        val today = LocalDate.now().toEpochDay()
        val booking = Booking(
            bookingId = "bk_${UUID.randomUUID().toString().take(8)}",
            touristId = tourist.userId,
            touristName = tourist.name,
            providerId = spot.providerId,
            listingId = spot.spotId,
            listingTitle = spot.name,
            bookingType = BookingType.DINING,
            pickupHubCode = null,
            destinationCluster = spot.locationCluster,
            trainOrFlightNo = null,
            pickupNotes = notes.ifBlank { "Table reservation (${spot.specialty})" },
            startEpochDay = today + 1,
            endEpochDay = today + 1,
            slot = TimeSlot.EVENING_18_24,
            guestOrSeatCount = guests.coerceIn(1, 10),
            status = BookingStatus.PENDING,
            quotedPriceInr = spot.avgCostInr * guests.coerceIn(1, 10),
            cancellationTerms = "Free table cancellation up to 2h prior.",
            createdAt = System.currentTimeMillis(),
            respondedAt = null
        )
        insertBooking(booking)
        insertNotification(
            userId = tourist.userId,
            category = NotificationCategory.DEALS,
            eventType = "TABLE_REQUESTED",
            message = "Table reservation request sent to ${spot.name} for $guests guests."
        )
        notifyProviderOwner(
            providerId = spot.providerId,
            category = NotificationCategory.TRIPS,
            eventType = "NEW_TABLE_BOOKING",
            message = "New table request from ${tourist.name} at ${spot.name} ($guests guests)."
        )
        return OpResult.Success(booking)
    }

    fun respondToBooking(actor: User, bookingId: String, accept: Boolean): OpResult<BookingStatus> {
        val allBookings = readAllBookingsRaw()
        val booking = allBookings.firstOrNull { it.bookingId == bookingId }
            ?: return OpResult.Error("Booking not found.")
        val actorProviderId = getProviderForUser(actor.userId)?.providerId
        when (val guard = BookingRulesEngine.verifyResourceOwnership(actor, actorProviderId, booking.providerId, "booking request")) {
            is RuleResult.Denied -> {
                writeAudit(actor.userId, actor.role.name, "SECURITY_DENIED_BOOKING_RESPONSE", bookingId, guard.reason)
                return OpResult.Error(guard.reason)
            }
            RuleResult.Allowed -> Unit
        }
        if (booking.status != BookingStatus.PENDING) {
            return OpResult.Error("Only Pending requests can be responded to.")
        }
        if (accept) {
            if (booking.bookingType == BookingType.STAY) {
                val room = getAllRooms().firstOrNull { it.roomTypeId == booking.listingId }
                    ?: return OpResult.Error("Room type not found.")
                when (val check = BookingRulesEngine.canAcceptStayBooking(booking, room, allBookings)) {
                    is RuleResult.Denied -> return OpResult.Error(check.reason)
                    RuleResult.Allowed -> Unit
                }
            } else if (BookingRulesEngine.hasMobilityConflict(booking, allBookings)) {
                return OpResult.Error("Cannot accept: overlapping accepted booking already exists for this vehicle/time slot (FR-12).")
            }
        }

        val newStatus = if (accept) BookingStatus.ACCEPTED else BookingStatus.REJECTED
        val now = System.currentTimeMillis()
        dbHelper.writableDatabase.update(
            "bookings",
            ContentValues().apply {
                put("status", newStatus.name)
                put("responded_at", now)
            },
            "booking_id=?",
            arrayOf(bookingId)
        )
        insertNotification(
            userId = booking.touristId,
            category = NotificationCategory.TRIPS,
            eventType = "BOOKING_${newStatus.name}",
            message = "Your request for ${booking.listingTitle} was ${newStatus.label}."
        )
        writeAudit(actor.userId, actor.role.name, "BOOKING_${newStatus.name}", bookingId, "Provider responded: ${newStatus.name}")
        return OpResult.Success(newStatus)
    }

    fun updateBookingLifecycle(actor: User, bookingId: String, targetStatus: BookingStatus): OpResult<Unit> {
        val booking = readAllBookingsRaw().firstOrNull { it.bookingId == bookingId }
            ?: return OpResult.Error("Booking not found.")
        val actorProviderId = getProviderForUser(actor.userId)?.providerId
        when (targetStatus) {
            BookingStatus.CANCELLED -> {
                when (val guard = BookingRulesEngine.verifyTouristBookingOwner(actor, booking.touristId)) {
                    is RuleResult.Denied -> {
                        writeAudit(actor.userId, actor.role.name, "SECURITY_DENIED_CANCEL", bookingId, guard.reason)
                        return OpResult.Error(guard.reason)
                    }
                    RuleResult.Allowed -> Unit
                }
                if (booking.status !in setOf(BookingStatus.PENDING, BookingStatus.ACCEPTED)) {
                    return OpResult.Error("Only Pending or Accepted bookings can be cancelled.")
                }
            }
            BookingStatus.COMPLETED -> {
                when (val guard = BookingRulesEngine.verifyResourceOwnership(actor, actorProviderId, booking.providerId, "booking")) {
                    is RuleResult.Denied -> {
                        writeAudit(actor.userId, actor.role.name, "SECURITY_DENIED_COMPLETE", bookingId, guard.reason)
                        return OpResult.Error(guard.reason)
                    }
                    RuleResult.Allowed -> Unit
                }
                if (booking.status != BookingStatus.ACCEPTED) {
                    return OpResult.Error("Only Accepted bookings can be marked Completed.")
                }
            }
            else -> return OpResult.Error("Unsupported lifecycle state transition.")
        }

        dbHelper.writableDatabase.update(
            "bookings",
            ContentValues().apply { put("status", targetStatus.name) },
            "booking_id=?",
            arrayOf(bookingId)
        )
        insertNotification(
            userId = booking.touristId,
            category = NotificationCategory.TRIPS,
            eventType = "BOOKING_${targetStatus.name}",
            message = "Booking ${booking.listingTitle} marked ${targetStatus.label}."
        )
        writeAudit(actor.userId, actor.role.name, "BOOKING_${targetStatus.name}", bookingId, "Lifecycle transition.")
        return OpResult.Success(Unit)
    }

    fun submitReview(tourist: User, bookingId: String, rating: Int, comment: String): OpResult<Review> {
        if (rating !in 1..5 || comment.isBlank()) {
            return OpResult.Error("Please select 1–5 stars and enter a review comment.")
        }
        val booking = readAllBookingsRaw().firstOrNull { it.bookingId == bookingId }
            ?: return OpResult.Error("Booking not found.")
        val existingReviews = getReviewsForListing(null).any { it.bookingId == bookingId }
        when (val rule = BookingRulesEngine.isEligibleForReview(booking, tourist.userId, existingReviews)) {
            is RuleResult.Denied -> return OpResult.Error(rule.reason)
            RuleResult.Allowed -> Unit
        }
        val review = Review(
            reviewId = "rev_${UUID.randomUUID().toString().take(8)}",
            bookingId = bookingId,
            touristId = tourist.userId,
            touristName = tourist.name,
            listingId = booking.listingId,
            rating = rating,
            comment = comment.trim(),
            createdAt = System.currentTimeMillis()
        )
        dbHelper.writableDatabase.insert("reviews", null, ContentValues().apply {
            put("review_id", review.reviewId)
            put("booking_id", review.bookingId)
            put("tourist_id", review.touristId)
            put("tourist_name", review.touristName)
            put("listing_id", review.listingId)
            put("rating", review.rating)
            put("comment", review.comment)
            put("moderation_status", "VISIBLE")
            put("created_at", review.createdAt)
        })
        writeAudit(tourist.userId, tourist.role.name, "REVIEW_SUBMITTED", review.reviewId, "Rating $rating/5 on $bookingId")
        return OpResult.Success(review)
    }

    fun addVehicleListing(
        actor: User,
        title: String,
        vehicleType: String,
        capacity: Int,
        transmissionFuel: String,
        serviceArea: String,
        pickupHubs: List<String>,
        rentalMode: RentalMode,
        rateInr: Int,
        cancellationTerms: String
    ): OpResult<Unit> {
        if (actor.role != UserRole.VEHICLE_OWNER) {
            return OpResult.Error("Only Vehicle Owners can add vehicle listings (FR-02, FR-03).")
        }
        val provider = getProviderForUser(actor.userId) ?: return OpResult.Error("Provider profile missing.")
        if (title.isBlank() || serviceArea.isBlank() || pickupHubs.isEmpty()) {
            return OpResult.Error("Title, service area, and at least 1 Railway/Airport pickup hub are required.")
        }
        val id = "veh_${UUID.randomUUID().toString().take(8)}"
        dbHelper.writableDatabase.insert("vehicles", null, ContentValues().apply {
            put("vehicle_id", id)
            put("provider_id", provider.providerId)
            put("owner_name", provider.displayName)
            put("title", title.trim())
            put("vehicle_type", vehicleType.trim())
            put("capacity", capacity)
            put("transmission_fuel", transmissionFuel.trim())
            put("service_area", serviceArea.trim())
            put("pickup_hubs", pickupHubs.joinToString(","))
            put("rental_mode", rentalMode.name)
            put("rate_inr", rateInr)
            put("rate_basis", "per day")
            put("cancellation_terms", cancellationTerms.ifBlank { "Free cancellation 12h prior" })
            put("approval_status", ListingApprovalStatus.PENDING_REVIEW.name)
            put("last_updated_at", System.currentTimeMillis())
        })
        writeAudit(actor.userId, actor.role.name, "VEHICLE_CREATED", id, "New vehicle submitted for Admin review.")
        return OpResult.Success(Unit)
    }

    fun updateVehicleListing(
        actor: User,
        vehicleId: String,
        newTitle: String,
        newRateInr: Int,
        newServiceArea: String
    ): OpResult<Unit> {
        val vehicle = getVehicles(includeUnapproved = true).firstOrNull { it.vehicleId == vehicleId }
            ?: return OpResult.Error("Vehicle not found.")
        val actorProvId = getProviderForUser(actor.userId)?.providerId
        when (val guard = BookingRulesEngine.verifyResourceOwnership(actor, actorProvId, vehicle.providerId, "vehicle")) {
            is RuleResult.Denied -> {
                writeAudit(actor.userId, actor.role.name, "SECURITY_DENIED_EDIT_VEHICLE", vehicleId, guard.reason)
                return OpResult.Error(guard.reason)
            }
            RuleResult.Allowed -> Unit
        }
        if (newTitle.isBlank() || newRateInr <= 0) return OpResult.Error("Valid title and rate required.")
        dbHelper.writableDatabase.update(
            "vehicles",
            ContentValues().apply {
                put("title", newTitle.trim())
                put("rate_inr", newRateInr)
                put("service_area", newServiceArea.trim())
                put("last_updated_at", System.currentTimeMillis())
            },
            "vehicle_id=? AND provider_id=?",
            arrayOf(vehicleId, actorProvId)
        )
        writeAudit(actor.userId, actor.role.name, "VEHICLE_UPDATED", vehicleId, "Owner updated own vehicle.")
        return OpResult.Success(Unit)
    }

    fun deleteVehicleListing(actor: User, vehicleId: String): OpResult<Unit> {
        val vehicle = getVehicles(includeUnapproved = true).firstOrNull { it.vehicleId == vehicleId }
            ?: return OpResult.Error("Vehicle not found.")
        val actorProvId = getProviderForUser(actor.userId)?.providerId
        when (val guard = BookingRulesEngine.verifyResourceOwnership(actor, actorProvId, vehicle.providerId, "vehicle")) {
            is RuleResult.Denied -> {
                writeAudit(actor.userId, actor.role.name, "SECURITY_DENIED_DELETE_VEHICLE", vehicleId, guard.reason)
                return OpResult.Error(guard.reason)
            }
            RuleResult.Allowed -> Unit
        }
        dbHelper.writableDatabase.delete("vehicles", "vehicle_id=? AND provider_id=?", arrayOf(vehicleId, actorProvId))
        writeAudit(actor.userId, actor.role.name, "VEHICLE_DELETED", vehicleId, "Owner deleted own vehicle.")
        return OpResult.Success(Unit)
    }

    fun toggleVehicleLive(
        actor: User,
        vehicleId: String,
        isAvailableNow: Boolean,
        rateInr: Int,
        liveNote: String
    ): OpResult<Unit> {
        val vehicle = getVehicles(includeUnapproved = true).firstOrNull { it.vehicleId == vehicleId }
            ?: return OpResult.Error("Vehicle not found.")
        val actorProvId = getProviderForUser(actor.userId)?.providerId
        when (val guard = BookingRulesEngine.verifyResourceOwnership(actor, actorProvId, vehicle.providerId, "vehicle")) {
            is RuleResult.Denied -> return OpResult.Error(guard.reason)
            RuleResult.Allowed -> Unit
        }
        dbHelper.writableDatabase.update(
            "vehicles",
            ContentValues().apply {
                put("is_available_now", if (isAvailableNow) 1 else 0)
                put("rate_inr", rateInr.coerceAtLeast(500))
                put("live_status_note", liveNote.ifBlank { if (isAvailableNow) "Available now" else "On trip" })
                put("last_updated_at", System.currentTimeMillis())
            },
            "vehicle_id=? AND provider_id=?",
            arrayOf(vehicleId, actorProvId)
        )
        return OpResult.Success(Unit)
    }

    fun toggleDriverLive(
        actor: User,
        driverId: String,
        isAvailableNow: Boolean,
        ratePerDayInr: Int,
        summary: String
    ): OpResult<Unit> {
        val driver = getDrivers(includeUnapproved = true).firstOrNull { it.driverId == driverId }
            ?: return OpResult.Error("Driver not found.")
        val actorProvId = getProviderForUser(actor.userId)?.providerId
        when (val guard = BookingRulesEngine.verifyResourceOwnership(actor, actorProvId, driver.providerId, "driver profile")) {
            is RuleResult.Denied -> return OpResult.Error(guard.reason)
            RuleResult.Allowed -> Unit
        }
        dbHelper.writableDatabase.update(
            "drivers",
            ContentValues().apply {
                put("is_available_now", if (isAvailableNow) 1 else 0)
                put("rate_per_day_inr", ratePerDayInr.coerceAtLeast(500))
                put("availability_summary", summary.ifBlank { if (isAvailableNow) "Online & ready" else "Currently on trip" })
                put("last_updated_at", System.currentTimeMillis())
            },
            "driver_id=? AND provider_id=?",
            arrayOf(driverId, actorProvId)
        )
        return OpResult.Success(Unit)
    }

    fun updateRoomLiveInventory(
        actor: User,
        roomTypeId: String,
        newInventoryCount: Int,
        newRateInr: Int
    ): OpResult<Unit> {
        val room = getAllRooms().firstOrNull { it.roomTypeId == roomTypeId }
            ?: return OpResult.Error("Room not found.")
        val prop = getProperties(includeUnapproved = true).firstOrNull { it.propertyId == room.propertyId }
            ?: return OpResult.Error("Property not found.")
        val actorProvId = getProviderForUser(actor.userId)?.providerId
        when (val guard = BookingRulesEngine.verifyResourceOwnership(actor, actorProvId, prop.providerId, "room inventory")) {
            is RuleResult.Denied -> return OpResult.Error(guard.reason)
            RuleResult.Allowed -> Unit
        }
        dbHelper.writableDatabase.update(
            "room_types",
            ContentValues().apply {
                put("inventory_count", newInventoryCount.coerceAtLeast(0))
                put("rate_per_night_inr", newRateInr.coerceAtLeast(500))
            },
            "room_type_id=?",
            arrayOf(roomTypeId)
        )
        return OpResult.Success(Unit)
    }

    fun toggleDiningLive(
        actor: User,
        spotId: String,
        isOpenNow: Boolean,
        liveNote: String,
        avgCostInr: Int
    ): OpResult<Unit> {
        val spot = getDiningSpots().firstOrNull { it.spotId == spotId }
            ?: return OpResult.Error("Dining spot not found.")
        val actorProvId = getProviderForUser(actor.userId)?.providerId
        when (val guard = BookingRulesEngine.verifyResourceOwnership(actor, actorProvId, spot.providerId, "dining spot")) {
            is RuleResult.Denied -> return OpResult.Error(guard.reason)
            RuleResult.Allowed -> Unit
        }
        dbHelper.writableDatabase.update(
            "dining_spots",
            ContentValues().apply {
                put("is_open_now", if (isOpenNow) 1 else 0)
                put("live_status_note", liveNote.ifBlank { if (isOpenNow) "Tables open now" else "Currently full" })
                put("avg_cost_inr", avgCostInr.coerceAtLeast(100))
            },
            "spot_id=? AND provider_id=?",
            arrayOf(spotId, actorProvId)
        )
        return OpResult.Success(Unit)
    }

    fun submitDriverProfileAndDocument(
        actor: User,
        languages: String,
        experienceYears: Int,
        serviceArea: String,
        pickupHubs: List<String>,
        ratePerDayInr: Int,
        docType: String,
        docRef: String,
        expiryDate: String
    ): OpResult<Unit> {
        if (actor.role != UserRole.LOCAL_DRIVER) {
            return OpResult.Error("Only Local Drivers can submit driver profiles & licences (FR-05, FR-06).")
        }
        val provider = getProviderForUser(actor.userId) ?: return OpResult.Error("Provider profile missing.")
        val now = System.currentTimeMillis()
        val drvId = "drv_${UUID.randomUUID().toString().take(8)}"
        dbHelper.writableDatabase.insert("drivers", null, ContentValues().apply {
            put("driver_id", drvId)
            put("provider_id", provider.providerId)
            put("name", provider.displayName)
            put("languages", languages.ifBlank { "Hindi, English" })
            put("experience_years", experienceYears)
            put("service_area", serviceArea)
            put("pickup_hubs", pickupHubs.joinToString(","))
            put("rate_per_day_inr", ratePerDayInr)
            put("availability_summary", "Active for hub pickups & valley routes")
            put("cancellation_terms", "Free reschedule for train/flight delays.")
            put("approval_status", ListingApprovalStatus.PENDING_REVIEW.name)
            put("last_updated_at", now)
        })
        val docId = "doc_${UUID.randomUUID().toString().take(8)}"
        dbHelper.writableDatabase.insert("verification_documents", null, ContentValues().apply {
            put("document_id", docId)
            put("provider_id", provider.providerId)
            put("provider_name", provider.displayName)
            put("document_type", docType.ifBlank { "HP Commercial Driving Licence" })
            put("file_reference", docRef.ifBlank { "private://docs/${drvId}_dl.pdf" })
            put("review_status", VerificationStatus.PENDING.name)
            put("expiry_date", expiryDate.ifBlank { "2030-12-31" })
            put("reviewer_notes", "Submitted by driver for restricted admin review.")
        })
        writeAudit(actor.userId, actor.role.name, "DRIVER_DOC_SUBMITTED", docId, "Driver profile + verification doc submitted.")
        return OpResult.Success(Unit)
    }

    fun addPropertyWithRoom(
        actor: User,
        name: String,
        propertyType: String,
        locationCluster: String,
        nearestHubCode: String,
        amenities: String,
        registrationNumber: String,
        roomName: String,
        occupancy: Int,
        ratePerNight: Int,
        inventoryCount: Int
    ): OpResult<Unit> {
        if (actor.role != UserRole.ACCOMMODATION_PROVIDER) {
            return OpResult.Error("Only Accommodation Providers can create property listings (FR-14).")
        }
        val provider = getProviderForUser(actor.userId) ?: return OpResult.Error("Provider profile missing.")
        if (name.isBlank() || registrationNumber.isBlank()) {
            return OpResult.Error("Property name and HP Tourism registration number are required (SRS Section 2).")
        }
        val propId = "prop_${UUID.randomUUID().toString().take(8)}"
        val roomId = "room_${UUID.randomUUID().toString().take(8)}"
        val now = System.currentTimeMillis()
        dbHelper.writableDatabase.insert("properties", null, ContentValues().apply {
            put("property_id", propId)
            put("provider_id", provider.providerId)
            put("host_name", provider.displayName)
            put("name", name.trim())
            put("property_type", propertyType)
            put("location_cluster", locationCluster)
            put("nearest_hub_code", nearestHubCode)
            put("amenities", amenities)
            put("policies", "Check-in 12:00 / Check-out 10:00 • Free cancellation 24h prior")
            put("registration_number", registrationNumber.trim())
            put("approval_status", ListingApprovalStatus.PENDING_REVIEW.name)
            put("last_updated_at", now)
        })
        dbHelper.writableDatabase.insert("room_types", null, ContentValues().apply {
            put("room_type_id", roomId)
            put("property_id", propId)
            put("name", roomName.ifBlank { "Standard Valley Room" })
            put("occupancy", occupancy.coerceIn(1, 8))
            put("rate_per_night_inr", ratePerNight.coerceAtLeast(500))
            put("inventory_count", inventoryCount.coerceAtLeast(1))
        })
        writeAudit(actor.userId, actor.role.name, "PROPERTY_CREATED", propId, "Property + room inventory created.")
        return OpResult.Success(Unit)
    }

    fun updatePropertyListing(
        actor: User,
        propertyId: String,
        newName: String,
        newAmenities: String
    ): OpResult<Unit> {
        val prop = getProperties(includeUnapproved = true).firstOrNull { it.propertyId == propertyId }
            ?: return OpResult.Error("Property not found.")
        val actorProvId = getProviderForUser(actor.userId)?.providerId
        when (val guard = BookingRulesEngine.verifyResourceOwnership(actor, actorProvId, prop.providerId, "property")) {
            is RuleResult.Denied -> {
                writeAudit(actor.userId, actor.role.name, "SECURITY_DENIED_EDIT_PROP", propertyId, guard.reason)
                return OpResult.Error(guard.reason)
            }
            RuleResult.Allowed -> Unit
        }
        if (newName.isBlank()) return OpResult.Error("Property name cannot be empty.")
        dbHelper.writableDatabase.update(
            "properties",
            ContentValues().apply {
                put("name", newName.trim())
                put("amenities", newAmenities.trim())
                put("last_updated_at", System.currentTimeMillis())
            },
            "property_id=? AND provider_id=?",
            arrayOf(propertyId, actorProvId)
        )
        writeAudit(actor.userId, actor.role.name, "PROPERTY_UPDATED", propertyId, "Host updated own property.")
        return OpResult.Success(Unit)
    }

    fun adminReviewDocument(
        admin: User,
        documentId: String,
        newStatus: VerificationStatus,
        notes: String
    ): OpResult<Unit> {
        when (val guard = BookingRulesEngine.verifyAdminOnly(admin, "document verification")) {
            is RuleResult.Denied -> {
                writeAudit(admin.userId, admin.role.name, "SECURITY_DENIED_ADMIN_DOC", documentId, guard.reason)
                return OpResult.Error(guard.reason)
            }
            RuleResult.Allowed -> Unit
        }
        val doc = getVerificationDocumentsForActor(admin).firstOrNull { it.documentId == documentId }
            ?: return OpResult.Error("Document not found.")
        val db = dbHelper.writableDatabase
        db.update(
            "verification_documents",
            ContentValues().apply {
                put("review_status", newStatus.name)
                put("reviewer_notes", notes.ifBlank { newStatus.label })
            },
            "document_id=?",
            arrayOf(documentId)
        )
        db.update(
            "provider_profiles",
            ContentValues().apply {
                put("verification_status", newStatus.name)
                put("last_updated_at", System.currentTimeMillis())
            },
            "provider_id=?",
            arrayOf(doc.providerId)
        )
        notifyProviderOwner(
            providerId = doc.providerId,
            category = NotificationCategory.TRIPS,
            eventType = "VERIFICATION_${newStatus.name}",
            message = "Admin updated your verification status to ${newStatus.label}: $notes"
        )
        writeAudit(admin.userId, admin.role.name, "DOC_${newStatus.name}", documentId, notes)
        return OpResult.Success(Unit)
    }

    fun adminUpdateListingApproval(
        admin: User,
        table: String,
        idColumn: String,
        listingId: String,
        newStatus: ListingApprovalStatus
    ): OpResult<Unit> {
        when (val guard = BookingRulesEngine.verifyAdminOnly(admin, "listing moderation")) {
            is RuleResult.Denied -> {
                writeAudit(admin.userId, admin.role.name, "SECURITY_DENIED_ADMIN_MOD", listingId, guard.reason)
                return OpResult.Error(guard.reason)
            }
            RuleResult.Allowed -> Unit
        }
        val allowedTables = mapOf(
            "vehicles" to "vehicle_id",
            "drivers" to "driver_id",
            "properties" to "property_id"
        )
        if (allowedTables[table] != idColumn) {
            return OpResult.Error("Invalid moderation target.")
        }
        dbHelper.writableDatabase.update(
            table,
            ContentValues().apply {
                put("approval_status", newStatus.name)
                put("last_updated_at", System.currentTimeMillis())
            },
            "$idColumn=?",
            arrayOf(listingId)
        )
        writeAudit(admin.userId, admin.role.name, "LISTING_${newStatus.name}", listingId, "Table=$table")
        return OpResult.Success(Unit)
    }

    fun getAllUsersForAdmin(actor: User): List<User> {
        if (actor.role != UserRole.ADMINISTRATOR) return emptyList()
        return getAllDemoUsers()
    }

    fun adminSetUserAccountStatus(
        admin: User,
        targetUserId: String,
        newAccountStatus: String
    ): OpResult<Unit> {
        when (val guard = BookingRulesEngine.verifyAdminOnly(admin, "user account moderation")) {
            is RuleResult.Denied -> {
                writeAudit(admin.userId, admin.role.name, "SECURITY_DENIED_ADMIN_USER", targetUserId, guard.reason)
                return OpResult.Error(guard.reason)
            }
            RuleResult.Allowed -> Unit
        }
        if (targetUserId == admin.userId) {
            return OpResult.Error("Administrators cannot suspend their own active account.")
        }
        if (newAccountStatus !in setOf("ACTIVE", "SUSPENDED")) {
            return OpResult.Error("Invalid account status: $newAccountStatus")
        }
        val rows = dbHelper.writableDatabase.update(
            "users",
            ContentValues().apply { put("account_status", newAccountStatus) },
            "user_id=?",
            arrayOf(targetUserId)
        )
        if (rows <= 0) return OpResult.Error("User not found.")
        insertNotification(
            userId = targetUserId,
            category = NotificationCategory.TRIPS,
            eventType = "ACCOUNT_$newAccountStatus",
            message = if (newAccountStatus == "SUSPENDED") {
                "Your account was suspended by Platform Admin. Contact support@motomove.in."
            } else {
                "Your account has been reactivated by Platform Admin."
            }
        )
        writeAudit(admin.userId, admin.role.name, "USER_$newAccountStatus", targetUserId, "Account status set to $newAccountStatus")
        return OpResult.Success(Unit)
    }

    fun adminModerateReview(
        admin: User,
        reviewId: String,
        moderationStatus: String
    ): OpResult<Unit> {
        when (val guard = BookingRulesEngine.verifyAdminOnly(admin, "review moderation")) {
            is RuleResult.Denied -> {
                writeAudit(admin.userId, admin.role.name, "SECURITY_DENIED_ADMIN_REVIEW", reviewId, guard.reason)
                return OpResult.Error(guard.reason)
            }
            RuleResult.Allowed -> Unit
        }
        if (moderationStatus !in setOf("VISIBLE", "HIDDEN", "FLAGGED")) {
            return OpResult.Error("Invalid moderation status.")
        }
        dbHelper.writableDatabase.update(
            "reviews",
            ContentValues().apply { put("moderation_status", moderationStatus) },
            "review_id=?",
            arrayOf(reviewId)
        )
        writeAudit(admin.userId, admin.role.name, "REVIEW_$moderationStatus", reviewId, "Review marked $moderationStatus")
        return OpResult.Success(Unit)
    }

    fun getPilotMetrics(actor: User): PilotMetrics? {
        if (actor.role != UserRole.ADMINISTRATOR) return null
        val vehicles = getVehicles(includeUnapproved = true)
        val drivers = getDrivers(includeUnapproved = true)
        val props = getProperties(includeUnapproved = true)
        val bookings = readAllBookingsRaw()
        val timestamps = vehicles.map { it.lastUpdatedAt } + drivers.map { it.lastUpdatedAt } + props.map { it.lastUpdatedAt }
        return BookingRulesEngine.computePilotMetrics(
            approvedVehicles = vehicles.count { it.approvalStatus == ListingApprovalStatus.APPROVED },
            approvedDrivers = drivers.count { it.approvalStatus == ListingApprovalStatus.APPROVED },
            approvedProperties = props.count { it.approvalStatus == ListingApprovalStatus.APPROVED },
            listingUpdateTimestamps = timestamps,
            bookings = bookings,
            nowMillis = System.currentTimeMillis()
        )
    }

    private fun readAllBookingsRaw(): List<Booking> {
        expireStalePendingBookings()
        val list = mutableListOf<Booking>()
        dbHelper.readableDatabase.rawQuery("SELECT * FROM bookings ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) list.add(c.toBooking())
        }
        return list
    }

    private fun expireStalePendingBookings() {
        val cutoff = System.currentTimeMillis() - BookingRulesEngine.PENDING_EXPIRY_MILLIS
        dbHelper.writableDatabase.execSQL(
            "UPDATE bookings SET status=? WHERE status=? AND created_at < ?",
            arrayOf(BookingStatus.EXPIRED.name, BookingStatus.PENDING.name, cutoff)
        )
    }

    private fun insertBooking(b: Booking) {
        dbHelper.writableDatabase.insert("bookings", null, ContentValues().apply {
            put("booking_id", b.bookingId)
            put("tourist_id", b.touristId)
            put("tourist_name", b.touristName)
            put("provider_id", b.providerId)
            put("listing_id", b.listingId)
            put("listing_title", b.listingTitle)
            put("booking_type", b.bookingType.name)
            put("pickup_hub_code", b.pickupHubCode)
            put("destination_cluster", b.destinationCluster)
            put("train_or_flight_no", b.trainOrFlightNo)
            put("pickup_notes", b.pickupNotes)
            put("start_epoch_day", b.startEpochDay)
            put("end_epoch_day", b.endEpochDay)
            put("slot", b.slot.name)
            put("guest_or_seat_count", b.guestOrSeatCount)
            put("status", b.status.name)
            put("quoted_price_inr", b.quotedPriceInr)
            put("cancellation_terms", b.cancellationTerms)
            put("created_at", b.createdAt)
            putNull("responded_at")
        })
    }

    private fun notifyProviderOwner(providerId: String, category: NotificationCategory, eventType: String, message: String) {
        dbHelper.readableDatabase.rawQuery(
            "SELECT user_id FROM provider_profiles WHERE provider_id=?",
            arrayOf(providerId)
        ).use { c ->
            if (c.moveToFirst()) {
                insertNotification(c.getString(0), category, eventType, message)
            }
        }
    }

    private fun insertNotification(userId: String, category: NotificationCategory, eventType: String, message: String) {
        dbHelper.writableDatabase.insert("notifications", null, ContentValues().apply {
            put("notification_id", "not_${UUID.randomUUID().toString().take(8)}")
            put("user_id", userId)
            put("category", category.name)
            put("event_type", eventType)
            put("message", message)
            put("is_read", 0)
            put("created_at", System.currentTimeMillis())
        })
    }

    private fun writeAudit(actorId: String, actorRole: String, action: String, targetId: String, details: String) {
        dbHelper.writableDatabase.insert("audit_logs", null, ContentValues().apply {
            put("audit_id", "aud_${UUID.randomUUID().toString().take(8)}")
            put("actor_id", actorId)
            put("actor_role", actorRole)
            put("action", action)
            put("target_id", targetId)
            put("details", details)
            put("timestamp", System.currentTimeMillis())
        })
    }

    private fun Cursor.toUser() = User(
        userId = getString(getColumnIndexOrThrow("user_id")),
        name = getString(getColumnIndexOrThrow("name")),
        contact = getString(getColumnIndexOrThrow("contact")),
        passwordHash = getString(getColumnIndexOrThrow("password_hash")),
        role = UserRole.valueOf(getString(getColumnIndexOrThrow("role"))),
        accountStatus = getString(getColumnIndexOrThrow("account_status"))
    )

    private fun Cursor.toProvider() = ProviderProfile(
        providerId = getString(getColumnIndexOrThrow("provider_id")),
        userId = getString(getColumnIndexOrThrow("user_id")),
        displayName = getString(getColumnIndexOrThrow("display_name")),
        providerType = UserRole.valueOf(getString(getColumnIndexOrThrow("provider_type"))),
        verificationStatus = VerificationStatus.valueOf(getString(getColumnIndexOrThrow("verification_status"))),
        homeCluster = getString(getColumnIndexOrThrow("home_cluster")),
        lastUpdatedAt = getLong(getColumnIndexOrThrow("last_updated_at"))
    )

    private fun Cursor.toVehicle() = VehicleListing(
        vehicleId = getString(getColumnIndexOrThrow("vehicle_id")),
        providerId = getString(getColumnIndexOrThrow("provider_id")),
        ownerName = getString(getColumnIndexOrThrow("owner_name")),
        title = getString(getColumnIndexOrThrow("title")),
        vehicleType = getString(getColumnIndexOrThrow("vehicle_type")),
        capacity = getInt(getColumnIndexOrThrow("capacity")),
        transmissionFuel = getString(getColumnIndexOrThrow("transmission_fuel")),
        serviceArea = getString(getColumnIndexOrThrow("service_area")),
        pickupHubs = getString(getColumnIndexOrThrow("pickup_hubs")).split(",").filter { it.isNotBlank() },
        rentalMode = RentalMode.valueOf(getString(getColumnIndexOrThrow("rental_mode"))),
        rateInr = getInt(getColumnIndexOrThrow("rate_inr")),
        rateBasis = getString(getColumnIndexOrThrow("rate_basis")),
        cancellationTerms = getString(getColumnIndexOrThrow("cancellation_terms")),
        approvalStatus = ListingApprovalStatus.valueOf(getString(getColumnIndexOrThrow("approval_status"))),
        providerVerification = VerificationStatus.valueOf(getString(getColumnIndexOrThrow("verification_status"))),
        lastUpdatedAt = getLong(getColumnIndexOrThrow("last_updated_at")),
        isAvailableNow = getInt(getColumnIndexOrThrow("is_available_now")) == 1,
        liveStatusNote = getString(getColumnIndexOrThrow("live_status_note"))
    )

    private fun Cursor.toDriver() = DriverProfile(
        driverId = getString(getColumnIndexOrThrow("driver_id")),
        providerId = getString(getColumnIndexOrThrow("provider_id")),
        name = getString(getColumnIndexOrThrow("name")),
        languages = getString(getColumnIndexOrThrow("languages")),
        experienceYears = getInt(getColumnIndexOrThrow("experience_years")),
        serviceArea = getString(getColumnIndexOrThrow("service_area")),
        pickupHubs = getString(getColumnIndexOrThrow("pickup_hubs")).split(",").filter { it.isNotBlank() },
        ratePerDayInr = getInt(getColumnIndexOrThrow("rate_per_day_inr")),
        availabilitySummary = getString(getColumnIndexOrThrow("availability_summary")),
        cancellationTerms = getString(getColumnIndexOrThrow("cancellation_terms")),
        verificationStatus = VerificationStatus.valueOf(getString(getColumnIndexOrThrow("verification_status"))),
        approvalStatus = ListingApprovalStatus.valueOf(getString(getColumnIndexOrThrow("approval_status"))),
        lastUpdatedAt = getLong(getColumnIndexOrThrow("last_updated_at")),
        isAvailableNow = getInt(getColumnIndexOrThrow("is_available_now")) == 1
    )

    private fun Cursor.toProperty() = PropertyListing(
        propertyId = getString(getColumnIndexOrThrow("property_id")),
        providerId = getString(getColumnIndexOrThrow("provider_id")),
        hostName = getString(getColumnIndexOrThrow("host_name")),
        name = getString(getColumnIndexOrThrow("name")),
        propertyType = getString(getColumnIndexOrThrow("property_type")),
        locationCluster = getString(getColumnIndexOrThrow("location_cluster")),
        nearestHubCode = getString(getColumnIndexOrThrow("nearest_hub_code")),
        amenities = getString(getColumnIndexOrThrow("amenities")),
        policies = getString(getColumnIndexOrThrow("policies")),
        registrationNumber = getString(getColumnIndexOrThrow("registration_number")),
        approvalStatus = ListingApprovalStatus.valueOf(getString(getColumnIndexOrThrow("approval_status"))),
        providerVerification = VerificationStatus.valueOf(getString(getColumnIndexOrThrow("verification_status"))),
        lastUpdatedAt = getLong(getColumnIndexOrThrow("last_updated_at"))
    )

    private fun Cursor.toDiningSpot() = DiningSpot(
        spotId = getString(getColumnIndexOrThrow("spot_id")),
        providerId = getString(getColumnIndexOrThrow("provider_id")),
        name = getString(getColumnIndexOrThrow("name")),
        locationCluster = getString(getColumnIndexOrThrow("location_cluster")),
        specialty = getString(getColumnIndexOrThrow("specialty")),
        dishes = getString(getColumnIndexOrThrow("dishes")),
        ambience = getString(getColumnIndexOrThrow("ambience")),
        avgCostInr = getInt(getColumnIndexOrThrow("avg_cost_inr")),
        isFssaiVerified = getInt(getColumnIndexOrThrow("is_fssai_verified")) == 1,
        rating = getDouble(getColumnIndexOrThrow("rating")),
        isOpenNow = getInt(getColumnIndexOrThrow("is_open_now")) == 1,
        liveStatusNote = getString(getColumnIndexOrThrow("live_status_note"))
    )

    private fun Cursor.toRoomType() = RoomType(
        roomTypeId = getString(getColumnIndexOrThrow("room_type_id")),
        propertyId = getString(getColumnIndexOrThrow("property_id")),
        name = getString(getColumnIndexOrThrow("name")),
        occupancy = getInt(getColumnIndexOrThrow("occupancy")),
        ratePerNightInr = getInt(getColumnIndexOrThrow("rate_per_night_inr")),
        inventoryCount = getInt(getColumnIndexOrThrow("inventory_count"))
    )

    private fun Cursor.toBooking(): Booking {
        val respondedIdx = getColumnIndexOrThrow("responded_at")
        return Booking(
            bookingId = getString(getColumnIndexOrThrow("booking_id")),
            touristId = getString(getColumnIndexOrThrow("tourist_id")),
            touristName = getString(getColumnIndexOrThrow("tourist_name")),
            providerId = getString(getColumnIndexOrThrow("provider_id")),
            listingId = getString(getColumnIndexOrThrow("listing_id")),
            listingTitle = getString(getColumnIndexOrThrow("listing_title")),
            bookingType = BookingType.valueOf(getString(getColumnIndexOrThrow("booking_type"))),
            pickupHubCode = getString(getColumnIndexOrThrow("pickup_hub_code")),
            destinationCluster = getString(getColumnIndexOrThrow("destination_cluster")),
            trainOrFlightNo = getString(getColumnIndexOrThrow("train_or_flight_no")),
            pickupNotes = getString(getColumnIndexOrThrow("pickup_notes")),
            startEpochDay = getLong(getColumnIndexOrThrow("start_epoch_day")),
            endEpochDay = getLong(getColumnIndexOrThrow("end_epoch_day")),
            slot = TimeSlot.valueOf(getString(getColumnIndexOrThrow("slot"))),
            guestOrSeatCount = getInt(getColumnIndexOrThrow("guest_or_seat_count")),
            status = BookingStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
            quotedPriceInr = getInt(getColumnIndexOrThrow("quoted_price_inr")),
            cancellationTerms = getString(getColumnIndexOrThrow("cancellation_terms")),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            respondedAt = if (isNull(respondedIdx)) null else getLong(respondedIdx)
        )
    }

    private fun Cursor.toVerificationDoc() = VerificationDocument(
        documentId = getString(getColumnIndexOrThrow("document_id")),
        providerId = getString(getColumnIndexOrThrow("provider_id")),
        providerName = getString(getColumnIndexOrThrow("provider_name")),
        documentType = getString(getColumnIndexOrThrow("document_type")),
        fileReference = getString(getColumnIndexOrThrow("file_reference")),
        reviewStatus = VerificationStatus.valueOf(getString(getColumnIndexOrThrow("review_status"))),
        expiryDate = getString(getColumnIndexOrThrow("expiry_date")),
        reviewerNotes = getString(getColumnIndexOrThrow("reviewer_notes"))
    )

    private fun Cursor.toReview() = Review(
        reviewId = getString(getColumnIndexOrThrow("review_id")),
        bookingId = getString(getColumnIndexOrThrow("booking_id")),
        touristId = getString(getColumnIndexOrThrow("tourist_id")),
        touristName = getString(getColumnIndexOrThrow("tourist_name")),
        listingId = getString(getColumnIndexOrThrow("listing_id")),
        rating = getInt(getColumnIndexOrThrow("rating")),
        comment = getString(getColumnIndexOrThrow("comment")),
        moderationStatus = getString(getColumnIndexOrThrow("moderation_status")),
        createdAt = getLong(getColumnIndexOrThrow("created_at"))
    )

    private fun Cursor.toNotification() = AppNotification(
        notificationId = getString(getColumnIndexOrThrow("notification_id")),
        userId = getString(getColumnIndexOrThrow("user_id")),
        category = runCatching { NotificationCategory.valueOf(getString(getColumnIndexOrThrow("category"))) }
            .getOrDefault(NotificationCategory.TRIPS),
        eventType = getString(getColumnIndexOrThrow("event_type")),
        message = getString(getColumnIndexOrThrow("message")),
        isRead = getInt(getColumnIndexOrThrow("is_read")) == 1,
        createdAt = getLong(getColumnIndexOrThrow("created_at"))
    )

    private fun Cursor.toAuditLog() = AuditLog(
        auditId = getString(getColumnIndexOrThrow("audit_id")),
        actorId = getString(getColumnIndexOrThrow("actor_id")),
        actorRole = getString(getColumnIndexOrThrow("actor_role")),
        action = getString(getColumnIndexOrThrow("action")),
        targetId = getString(getColumnIndexOrThrow("target_id")),
        details = getString(getColumnIndexOrThrow("details")),
        timestamp = getLong(getColumnIndexOrThrow("timestamp"))
    )
}
