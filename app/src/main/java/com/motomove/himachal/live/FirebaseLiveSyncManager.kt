package com.motomove.himachal.live

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.motomove.himachal.model.AppNotification
import com.motomove.himachal.model.AuditLog
import com.motomove.himachal.model.Booking
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.BookingType
import com.motomove.himachal.model.DiningSpot
import com.motomove.himachal.model.DriverProfile
import com.motomove.himachal.model.ListingApprovalStatus
import com.motomove.himachal.model.NotificationCategory
import com.motomove.himachal.model.PropertyListing
import com.motomove.himachal.model.RentalMode
import com.motomove.himachal.model.Review
import com.motomove.himachal.model.RoomType
import com.motomove.himachal.model.TimeSlot
import com.motomove.himachal.model.User
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.model.VehicleListing
import com.motomove.himachal.model.VerificationDocument
import com.motomove.himachal.model.VerificationStatus

/**
 * Cloud Firestore + Firebase Auth real-time sync engine for MotoMove.
 * Every update from any Driver, Vehicle Owner, Hotel Host, Dining Spot, or Tourist
 * propagates across all connected devices over 4G/5G/Wi-Fi via addSnapshotListener(),
 * while firestore.rules strictly enforces per-user ownership at the database server.
 */
class FirebaseLiveSyncManager {

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val registrations = mutableListOf<ListenerRegistration>()

    fun currentAuthUid(): String? = auth.currentUser?.uid

    fun authenticateAndBind(
        user: User,
        providerId: String?,
        rawPassword: String,
        onReady: (uid: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val email = toFirebaseEmail(user.contact)
        val pass = if (rawPassword.length >= 6) rawPassword else "${rawPassword}_motomove"

        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                ensureUserDoc(uid, user, providerId) { onReady(uid) }
            }
            .addOnFailureListener {
                // Create Firebase Auth account if first login for this email/demo user
                auth.createUserWithEmailAndPassword(email, pass)
                    .addOnSuccessListener { created ->
                        val uid = created.user?.uid ?: return@addOnSuccessListener
                        ensureUserDoc(uid, user, providerId) { onReady(uid) }
                    }
                    .addOnFailureListener {
                        // Fallback to anonymous auth if email provider isn't enabled yet
                        auth.signInAnonymously()
                            .addOnSuccessListener { anon ->
                                val uid = anon.user?.uid ?: return@addOnSuccessListener
                                ensureUserDoc(uid, user, providerId) { onReady(uid) }
                            }
                            .addOnFailureListener { err ->
                                onError(err.message ?: "Firebase Auth unavailable")
                            }
                    }
            }
    }

    fun signOut() {
        stopListeners()
        runCatching { auth.signOut() }
    }

    private fun ensureUserDoc(uid: String, user: User, providerId: String?, onDone: () -> Unit) {
        val roleToStore = user.role.name
        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "name" to user.name,
            "contact" to user.contact,
            "role" to roleToStore,
            "accountStatus" to user.accountStatus,
            "updatedAt" to System.currentTimeMillis()
        )
        if (!providerId.isNullOrBlank() && user.role in setOf(UserRole.VEHICLE_OWNER, UserRole.LOCAL_DRIVER, UserRole.ACCOMMODATION_PROVIDER)) {
            payload["providerId"] = providerId
        }
        db.collection("users").document(uid)
            .set(payload, SetOptions.merge())
            .addOnCompleteListener {
                if (!providerId.isNullOrBlank() && user.role in setOf(UserRole.VEHICLE_OWNER, UserRole.LOCAL_DRIVER, UserRole.ACCOMMODATION_PROVIDER)) {
                    val provPayload = hashMapOf<String, Any>(
                        "providerId" to providerId,
                        "ownerUid" to uid,
                        "userId" to user.userId,
                        "displayName" to user.name,
                        "providerType" to user.role.name,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    db.collection("providers").document(providerId)
                        .set(provPayload, SetOptions.merge())
                        .addOnCompleteListener { onDone() }
                } else {
                    onDone()
                }
            }
    }

    fun seedProviderListingsIfOwned(
        uid: String,
        vehicles: List<VehicleListing>,
        drivers: List<DriverProfile>,
        properties: List<PropertyListing>,
        rooms: List<RoomType>,
        diningSpots: List<DiningSpot>,
        verificationDocs: List<VerificationDocument> = emptyList()
    ) {
        vehicles.forEach { v ->
            val ref = db.collection("vehicles").document(v.vehicleId)
            ref.get().addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    ref.set(v.copy(ownerUid = uid).toOwnerMap(includeStatus = false))
                }
            }
        }
        drivers.forEach { d ->
            val ref = db.collection("drivers").document(d.driverId)
            ref.get().addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    ref.set(d.copy(ownerUid = uid).toOwnerMap(includeStatus = false))
                }
            }
        }
        properties.forEach { p ->
            val ref = db.collection("properties").document(p.propertyId)
            ref.get().addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    ref.set(p.copy(ownerUid = uid).toOwnerMap(includeStatus = false))
                }
            }
        }
        rooms.forEach { r ->
            val ref = db.collection("rooms").document(r.roomTypeId)
            ref.get().addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    ref.set(r.copy(ownerUid = uid).toMap())
                }
            }
        }
        diningSpots.forEach { s ->
            val ref = db.collection("diningSpots").document(s.spotId)
            ref.get().addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    ref.set(s.copy(ownerUid = uid).toMap())
                }
            }
        }
        verificationDocs.forEach { doc ->
            val ref = db.collection("verificationDocs").document(doc.documentId)
            ref.get().addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    ref.set(doc.toMap(ownerUid = uid))
                }
            }
        }
    }

    fun startListeners(
        uid: String,
        role: UserRole,
        providerId: String? = null,
        onVehicles: (List<VehicleListing>) -> Unit,
        onDrivers: (List<DriverProfile>) -> Unit,
        onProperties: (List<PropertyListing>) -> Unit,
        onRooms: (List<RoomType>) -> Unit,
        onDining: (List<DiningSpot>) -> Unit,
        onBookings: (List<Booking>) -> Unit,
        onNotifications: (List<AppNotification>) -> Unit,
        onUsers: ((List<User>) -> Unit)? = null,
        onVerificationDocs: ((List<VerificationDocument>) -> Unit)? = null,
        onReviews: ((List<Review>) -> Unit)? = null,
        onAuditLogs: ((List<AuditLog>) -> Unit)? = null,
        onStatus: (connected: Boolean, liveBanner: String?) -> Unit
    ) {
        stopListeners()

        registrations += db.collection("vehicles").addSnapshotListener { snap, err ->
            if (err != null) {
                onStatus(false, null)
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { it.toVehicle() }.orEmpty()
            onVehicles(list)
            onStatus(true, null)
        }

        registrations += db.collection("drivers").addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { it.toDriver() }.orEmpty()
            onDrivers(list)
        }

        registrations += db.collection("properties").addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { it.toProperty() }.orEmpty()
            onProperties(list)
        }

        registrations += db.collection("rooms").addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { it.toRoom() }.orEmpty()
            onRooms(list)
        }

        registrations += db.collection("diningSpots").addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { it.toDining() }.orEmpty()
            onDining(list)
        }

        if (role == UserRole.TOURIST || role == UserRole.ADMINISTRATOR || providerId.isNullOrBlank()) {
            val bookingQuery = when (role) {
                UserRole.TOURIST -> db.collection("bookings").whereEqualTo("touristUid", uid)
                UserRole.ADMINISTRATOR -> db.collection("bookings")
                else -> db.collection("bookings").whereEqualTo("ownerUid", uid)
            }
            registrations += bookingQuery.addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toBooking() }.orEmpty()
                onBookings(list)
            }
        } else {
            var byProvider: List<Booking> = emptyList()
            var byOwner: List<Booking> = emptyList()
            fun emitMergedBookings() {
                val merged = LinkedHashMap<String, Booking>()
                byOwner.forEach { merged[it.bookingId] = it }
                byProvider.forEach { merged[it.bookingId] = it }
                onBookings(merged.values.toList())
            }
            registrations += db.collection("bookings")
                .whereEqualTo("providerId", providerId)
                .addSnapshotListener { snap, _ ->
                    byProvider = snap?.documents?.mapNotNull { it.toBooking() }.orEmpty()
                    emitMergedBookings()
                }
            registrations += db.collection("bookings")
                .whereEqualTo("ownerUid", uid)
                .addSnapshotListener { snap, _ ->
                    byOwner = snap?.documents?.mapNotNull { it.toBooking() }.orEmpty()
                    emitMergedBookings()
                }
        }

        if (providerId.isNullOrBlank()) {
            registrations += db.collection("notifications")
                .whereEqualTo("userUid", uid)
                .addSnapshotListener { snap, _ ->
                    val list = snap?.documents?.mapNotNull { it.toNotification() }.orEmpty()
                    onNotifications(list)
                }
        } else {
            var byUser: List<AppNotification> = emptyList()
            var byProviderNotifs: List<AppNotification> = emptyList()
            fun emitMergedNotifs() {
                val merged = LinkedHashMap<String, AppNotification>()
                byUser.forEach { merged[it.notificationId] = it }
                byProviderNotifs.forEach { merged[it.notificationId] = it }
                onNotifications(merged.values.toList())
            }
            registrations += db.collection("notifications")
                .whereEqualTo("userUid", uid)
                .addSnapshotListener { snap, _ ->
                    byUser = snap?.documents?.mapNotNull { it.toNotification() }.orEmpty()
                    emitMergedNotifs()
                }
            registrations += db.collection("notifications")
                .whereEqualTo("targetProviderId", providerId)
                .addSnapshotListener { snap, _ ->
                    byProviderNotifs = snap?.documents?.mapNotNull { it.toNotification() }.orEmpty()
                    emitMergedNotifs()
                }
        }

        if (onReviews != null) {
            registrations += db.collection("reviews").addSnapshotListener { snap, _ ->
                onReviews(snap?.documents?.mapNotNull { it.toReviewModel() }.orEmpty())
            }
        }

        if (role == UserRole.ADMINISTRATOR) {
            if (onUsers != null) {
                registrations += db.collection("users").addSnapshotListener { snap, _ ->
                    onUsers(snap?.documents?.mapNotNull { it.toUserModel() }.orEmpty())
                }
            }
            if (onVerificationDocs != null) {
                registrations += db.collection("verificationDocs").addSnapshotListener { snap, _ ->
                    onVerificationDocs(snap?.documents?.mapNotNull { it.toVerificationDoc() }.orEmpty())
                }
            }
            if (onAuditLogs != null) {
                registrations += db.collection("auditLogs").addSnapshotListener { snap, _ ->
                    onAuditLogs(snap?.documents?.mapNotNull { it.toAuditLogModel() }.orEmpty())
                }
            }
        } else if (!providerId.isNullOrBlank() && onVerificationDocs != null) {
            registrations += db.collection("verificationDocs")
                .whereEqualTo("ownerUid", uid)
                .addSnapshotListener { snap, _ ->
                    onVerificationDocs(snap?.documents?.mapNotNull { it.toVerificationDoc() }.orEmpty())
                }
        }
    }

    fun stopListeners() {
        registrations.forEach { runCatching { it.remove() } }
        registrations.clear()
    }

    fun upsertVehicle(vehicle: VehicleListing, onDone: (Boolean, String?) -> Unit) {
        val uid = currentAuthUid() ?: return onDone(false, "Not signed into Firebase")
        db.collection("vehicles").document(vehicle.vehicleId)
            .set(vehicle.copy(ownerUid = uid, lastUpdatedAt = System.currentTimeMillis()).toOwnerMap(includeStatus = false), SetOptions.merge())
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun deleteVehicle(vehicleId: String, onDone: (Boolean, String?) -> Unit) {
        db.collection("vehicles").document(vehicleId)
            .delete()
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun upsertDriver(driver: DriverProfile, onDone: (Boolean, String?) -> Unit) {
        val uid = currentAuthUid() ?: return onDone(false, "Not signed into Firebase")
        db.collection("drivers").document(driver.driverId)
            .set(driver.copy(ownerUid = uid, lastUpdatedAt = System.currentTimeMillis()).toOwnerMap(includeStatus = false), SetOptions.merge())
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun upsertRoom(room: RoomType, onDone: (Boolean, String?) -> Unit) {
        val uid = currentAuthUid() ?: return onDone(false, "Not signed into Firebase")
        db.collection("rooms").document(room.roomTypeId)
            .set(room.copy(ownerUid = uid).toMap(), SetOptions.merge())
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun upsertDining(spot: DiningSpot, onDone: (Boolean, String?) -> Unit) {
        val uid = currentAuthUid() ?: return onDone(false, "Not signed into Firebase")
        db.collection("diningSpots").document(spot.spotId)
            .set(spot.copy(ownerUid = uid).toMap(), SetOptions.merge())
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun upsertProperty(property: PropertyListing, onDone: (Boolean, String?) -> Unit) {
        val uid = currentAuthUid() ?: return onDone(false, "Not signed into Firebase")
        db.collection("properties").document(property.propertyId)
            .set(property.copy(ownerUid = uid, lastUpdatedAt = System.currentTimeMillis()).toOwnerMap(includeStatus = false), SetOptions.merge())
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun createBooking(booking: Booking, targetOwnerUid: String, onDone: (Boolean, String?) -> Unit) {
        val uid = currentAuthUid() ?: return onDone(false, "Not signed into Firebase")
        fun writeBookingDoc(resolvedOwnerUid: String) {
            val updated = booking.copy(touristUid = uid, ownerUid = resolvedOwnerUid)
            db.collection("bookings").document(booking.bookingId)
                .set(updated.toMap())
                .addOnSuccessListener {
                    if (resolvedOwnerUid != uid) {
                        pushNotification(
                            userUid = resolvedOwnerUid,
                            targetProviderId = booking.providerId,
                            category = NotificationCategory.TRIPS,
                            eventType = "NEW_BOOKING",
                            message = "LIVE: ${booking.touristName} requested ${booking.listingTitle}."
                        )
                    }
                    onDone(true, null)
                }
                .addOnFailureListener { onDone(false, it.message) }
        }
        if (targetOwnerUid.isNotBlank()) {
            writeBookingDoc(targetOwnerUid)
        } else {
            db.collection("providers").document(booking.providerId).get()
                .addOnSuccessListener { snap ->
                    val resolvedOwner = snap.getString("ownerUid").orEmpty()
                    writeBookingDoc(resolvedOwner)
                }
                .addOnFailureListener {
                    writeBookingDoc("")
                }
        }
    }

    fun updateBookingStatus(booking: Booking, newStatus: BookingStatus, onDone: (Boolean, String?) -> Unit) {
        db.collection("bookings").document(booking.bookingId)
            .update(
                mapOf(
                    "status" to newStatus.name,
                    "respondedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                if (booking.touristUid.isNotBlank()) {
                    pushNotification(
                        userUid = booking.touristUid,
                        targetProviderId = null,
                        category = NotificationCategory.TRIPS,
                        eventType = "BOOKING_${newStatus.name}",
                        message = "LIVE: ${booking.listingTitle} is now ${newStatus.label}!"
                    )
                }
                onDone(true, null)
            }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun adminUpdateListingApproval(
        collection: String,
        docId: String,
        status: ListingApprovalStatus,
        onDone: (Boolean, String?) -> Unit
    ) {
        db.collection(collection).document(docId)
            .update(
                mapOf(
                    "approvalStatus" to status.name,
                    "lastUpdatedAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun adminUpdateVerificationDoc(
        doc: VerificationDocument,
        status: VerificationStatus,
        notes: String,
        onDone: (Boolean, String?) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val ref = db.collection("verificationDocs").document(doc.documentId)
        ref.get()
            .addOnSuccessListener { snap ->
                val task = if (snap.exists()) {
                    ref.update(
                        mapOf(
                            "reviewStatus" to status.name,
                            "reviewerNotes" to notes.ifBlank { status.label },
                            "updatedAt" to now
                        )
                    )
                } else {
                    ref.set(doc.copy(reviewStatus = status, reviewerNotes = notes.ifBlank { status.label }).toMap(ownerUid = ""))
                }
                task.addOnSuccessListener { onDone(true, null) }
                    .addOnFailureListener { onDone(false, it.message) }
            }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun adminSetUserAccountStatus(
        targetUserUid: String,
        accountStatus: String,
        onDone: (Boolean, String?) -> Unit
    ) {
        db.collection("users").document(targetUserUid)
            .update(
                mapOf(
                    "accountStatus" to accountStatus,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun adminModerateReview(
        reviewId: String,
        moderationStatus: String,
        onDone: (Boolean, String?) -> Unit
    ) {
        db.collection("reviews").document(reviewId)
            .update("moderationStatus", moderationStatus)
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun upsertReview(review: Review, onDone: (Boolean, String?) -> Unit = { _, _ -> }) {
        val uid = currentAuthUid() ?: return onDone(false, "Not signed in")
        db.collection("reviews").document(review.reviewId)
            .set(
                hashMapOf(
                    "reviewId" to review.reviewId,
                    "bookingId" to review.bookingId,
                    "touristUid" to uid,
                    "touristName" to review.touristName,
                    "listingId" to review.listingId,
                    "rating" to review.rating,
                    "comment" to review.comment,
                    "moderationStatus" to review.moderationStatus,
                    "createdAt" to review.createdAt
                )
            )
            .addOnSuccessListener { onDone(true, null) }
            .addOnFailureListener { onDone(false, it.message) }
    }

    fun pushAuditLog(actorRole: String, action: String, targetId: String, details: String) {
        val uid = currentAuthUid() ?: return
        val doc = db.collection("auditLogs").document()
        doc.set(
            hashMapOf(
                "auditId" to doc.id,
                "actorUid" to uid,
                "actorRole" to actorRole,
                "action" to action,
                "targetId" to targetId,
                "details" to details.take(900),
                "timestamp" to System.currentTimeMillis()
            )
        )
    }

    private fun pushNotification(
        userUid: String,
        targetProviderId: String?,
        category: NotificationCategory,
        eventType: String,
        message: String
    ) {
        val doc = db.collection("notifications").document()
        val payload = hashMapOf<String, Any>(
            "notificationId" to doc.id,
            "userUid" to userUid,
            "category" to category.name,
            "eventType" to eventType,
            "message" to message.take(450),
            "isRead" to false,
            "createdAt" to System.currentTimeMillis()
        )
        if (!targetProviderId.isNullOrBlank()) {
            payload["targetProviderId"] = targetProviderId
        }
        doc.set(payload)
    }

    private fun toFirebaseEmail(contact: String): String {
        val clean = contact.trim().lowercase()
        return if ("@" in clean) clean else "${clean.replace(Regex("[^a-z0-9]"), "")}@motomove.in"
    }

    private fun VehicleListing.toOwnerMap(includeStatus: Boolean): HashMap<String, Any> = hashMapOf<String, Any>(
        "vehicleId" to vehicleId,
        "providerId" to providerId,
        "ownerUid" to ownerUid,
        "ownerName" to ownerName,
        "title" to title,
        "vehicleType" to vehicleType,
        "capacity" to capacity,
        "transmissionFuel" to transmissionFuel,
        "serviceArea" to serviceArea,
        "pickupHubs" to pickupHubs,
        "rentalMode" to rentalMode.name,
        "rateInr" to rateInr,
        "rateBasis" to rateBasis,
        "cancellationTerms" to cancellationTerms,
        "isAvailableNow" to isAvailableNow,
        "liveStatusNote" to liveStatusNote,
        "lastUpdatedAt" to lastUpdatedAt
    ).apply {
        if (includeStatus) {
            put("approvalStatus", approvalStatus.name)
            put("providerVerification", providerVerification.name)
        }
    }

    private fun DriverProfile.toOwnerMap(includeStatus: Boolean): HashMap<String, Any> = hashMapOf<String, Any>(
        "driverId" to driverId,
        "providerId" to providerId,
        "ownerUid" to ownerUid,
        "name" to name,
        "languages" to languages,
        "experienceYears" to experienceYears,
        "serviceArea" to serviceArea,
        "pickupHubs" to pickupHubs,
        "ratePerDayInr" to ratePerDayInr,
        "availabilitySummary" to availabilitySummary,
        "cancellationTerms" to cancellationTerms,
        "isAvailableNow" to isAvailableNow,
        "lastUpdatedAt" to lastUpdatedAt
    ).apply {
        if (includeStatus) {
            put("verificationStatus", verificationStatus.name)
            put("approvalStatus", approvalStatus.name)
        }
    }

    private fun PropertyListing.toOwnerMap(includeStatus: Boolean): HashMap<String, Any> = hashMapOf<String, Any>(
        "propertyId" to propertyId,
        "providerId" to providerId,
        "ownerUid" to ownerUid,
        "hostName" to hostName,
        "name" to name,
        "propertyType" to propertyType,
        "locationCluster" to locationCluster,
        "nearestHubCode" to nearestHubCode,
        "amenities" to amenities,
        "policies" to policies,
        "registrationNumber" to registrationNumber,
        "lastUpdatedAt" to lastUpdatedAt
    ).apply {
        if (includeStatus) {
            put("approvalStatus", approvalStatus.name)
            put("providerVerification", providerVerification.name)
        }
    }

    private fun RoomType.toMap() = hashMapOf(
        "roomTypeId" to roomTypeId,
        "propertyId" to propertyId,
        "ownerUid" to ownerUid,
        "name" to name,
        "occupancy" to occupancy,
        "ratePerNightInr" to ratePerNightInr,
        "inventoryCount" to inventoryCount
    )

    private fun DiningSpot.toMap() = hashMapOf(
        "spotId" to spotId,
        "providerId" to providerId,
        "ownerUid" to ownerUid,
        "name" to name,
        "locationCluster" to locationCluster,
        "specialty" to specialty,
        "dishes" to dishes,
        "ambience" to ambience,
        "avgCostInr" to avgCostInr,
        "isFssaiVerified" to isFssaiVerified,
        "rating" to rating,
        "isOpenNow" to isOpenNow,
        "liveStatusNote" to liveStatusNote
    )

    private fun Booking.toMap() = hashMapOf(
        "bookingId" to bookingId,
        "touristId" to touristId,
        "touristUid" to touristUid,
        "touristName" to touristName,
        "providerId" to providerId,
        "ownerUid" to ownerUid,
        "listingId" to listingId,
        "listingTitle" to listingTitle,
        "bookingType" to bookingType.name,
        "pickupHubCode" to (pickupHubCode ?: ""),
        "destinationCluster" to destinationCluster,
        "trainOrFlightNo" to (trainOrFlightNo ?: ""),
        "pickupNotes" to pickupNotes,
        "startEpochDay" to startEpochDay,
        "endEpochDay" to endEpochDay,
        "slot" to slot.name,
        "guestOrSeatCount" to guestOrSeatCount,
        "status" to status.name,
        "quotedPriceInr" to quotedPriceInr,
        "cancellationTerms" to cancellationTerms,
        "createdAt" to createdAt,
        "respondedAt" to respondedAt
    )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toVehicle(): VehicleListing? = runCatching {
        VehicleListing(
            vehicleId = getString("vehicleId") ?: id,
            providerId = getString("providerId") ?: "",
            ownerName = getString("ownerName") ?: "",
            title = getString("title") ?: "",
            vehicleType = getString("vehicleType") ?: "",
            capacity = (getLong("capacity") ?: 4L).toInt(),
            transmissionFuel = getString("transmissionFuel") ?: "",
            serviceArea = getString("serviceArea") ?: "",
            pickupHubs = (get("pickupHubs") as? List<String>) ?: emptyList(),
            rentalMode = RentalMode.valueOf(getString("rentalMode") ?: RentalMode.WITH_DRIVER.name),
            rateInr = (getLong("rateInr") ?: 2500L).toInt(),
            rateBasis = getString("rateBasis") ?: "per day",
            cancellationTerms = getString("cancellationTerms") ?: "",
            approvalStatus = ListingApprovalStatus.valueOf(getString("approvalStatus") ?: ListingApprovalStatus.APPROVED.name),
            providerVerification = VerificationStatus.valueOf(getString("providerVerification") ?: VerificationStatus.VERIFIED.name),
            lastUpdatedAt = getLong("lastUpdatedAt") ?: System.currentTimeMillis(),
            isAvailableNow = getBoolean("isAvailableNow") ?: true,
            liveStatusNote = getString("liveStatusNote") ?: "Available now",
            ownerUid = getString("ownerUid") ?: ""
        )
    }.getOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toDriver(): DriverProfile? = runCatching {
        DriverProfile(
            driverId = getString("driverId") ?: id,
            providerId = getString("providerId") ?: "",
            name = getString("name") ?: "",
            languages = getString("languages") ?: "",
            experienceYears = (getLong("experienceYears") ?: 5L).toInt(),
            serviceArea = getString("serviceArea") ?: "",
            pickupHubs = (get("pickupHubs") as? List<String>) ?: emptyList(),
            ratePerDayInr = (getLong("ratePerDayInr") ?: 1400L).toInt(),
            availabilitySummary = getString("availabilitySummary") ?: "",
            cancellationTerms = getString("cancellationTerms") ?: "",
            verificationStatus = VerificationStatus.valueOf(getString("verificationStatus") ?: VerificationStatus.VERIFIED.name),
            approvalStatus = ListingApprovalStatus.valueOf(getString("approvalStatus") ?: ListingApprovalStatus.APPROVED.name),
            lastUpdatedAt = getLong("lastUpdatedAt") ?: System.currentTimeMillis(),
            isAvailableNow = getBoolean("isAvailableNow") ?: true,
            ownerUid = getString("ownerUid") ?: ""
        )
    }.getOrNull()

    private fun DocumentSnapshot.toProperty(): PropertyListing? = runCatching {
        PropertyListing(
            propertyId = getString("propertyId") ?: id,
            providerId = getString("providerId") ?: "",
            hostName = getString("hostName") ?: "",
            name = getString("name") ?: "",
            propertyType = getString("propertyType") ?: "Homestay",
            locationCluster = getString("locationCluster") ?: "",
            nearestHubCode = getString("nearestHubCode") ?: "KUU",
            amenities = getString("amenities") ?: "",
            policies = getString("policies") ?: "",
            registrationNumber = getString("registrationNumber") ?: "",
            approvalStatus = ListingApprovalStatus.valueOf(getString("approvalStatus") ?: ListingApprovalStatus.APPROVED.name),
            providerVerification = VerificationStatus.valueOf(getString("providerVerification") ?: VerificationStatus.VERIFIED.name),
            lastUpdatedAt = getLong("lastUpdatedAt") ?: System.currentTimeMillis(),
            ownerUid = getString("ownerUid") ?: ""
        )
    }.getOrNull()

    private fun DocumentSnapshot.toRoom(): RoomType? = runCatching {
        RoomType(
            roomTypeId = getString("roomTypeId") ?: id,
            propertyId = getString("propertyId") ?: "",
            name = getString("name") ?: "",
            occupancy = (getLong("occupancy") ?: 2L).toInt(),
            ratePerNightInr = (getLong("ratePerNightInr") ?: 2000L).toInt(),
            inventoryCount = (getLong("inventoryCount") ?: 1L).toInt(),
            ownerUid = getString("ownerUid") ?: ""
        )
    }.getOrNull()

    private fun DocumentSnapshot.toDining(): DiningSpot? = runCatching {
        DiningSpot(
            spotId = getString("spotId") ?: id,
            providerId = getString("providerId") ?: "",
            name = getString("name") ?: "",
            locationCluster = getString("locationCluster") ?: "",
            specialty = getString("specialty") ?: "",
            dishes = getString("dishes") ?: "",
            ambience = getString("ambience") ?: "",
            avgCostInr = (getLong("avgCostInr") ?: 500L).toInt(),
            isFssaiVerified = getBoolean("isFssaiVerified") ?: true,
            rating = getDouble("rating") ?: 4.8,
            isOpenNow = getBoolean("isOpenNow") ?: true,
            liveStatusNote = getString("liveStatusNote") ?: "Tables open now",
            ownerUid = getString("ownerUid") ?: ""
        )
    }.getOrNull()

    private fun DocumentSnapshot.toBooking(): Booking? = runCatching {
        Booking(
            bookingId = getString("bookingId") ?: id,
            touristId = getString("touristId") ?: "",
            touristName = getString("touristName") ?: "",
            providerId = getString("providerId") ?: "",
            listingId = getString("listingId") ?: "",
            listingTitle = getString("listingTitle") ?: "",
            bookingType = BookingType.valueOf(getString("bookingType") ?: BookingType.HUB_PICKUP.name),
            pickupHubCode = getString("pickupHubCode")?.takeIf { it.isNotBlank() },
            destinationCluster = getString("destinationCluster") ?: "",
            trainOrFlightNo = getString("trainOrFlightNo")?.takeIf { it.isNotBlank() },
            pickupNotes = getString("pickupNotes") ?: "",
            startEpochDay = getLong("startEpochDay") ?: 0L,
            endEpochDay = getLong("endEpochDay") ?: 0L,
            slot = TimeSlot.valueOf(getString("slot") ?: TimeSlot.MORNING_06_12.name),
            guestOrSeatCount = (getLong("guestOrSeatCount") ?: 1L).toInt(),
            status = BookingStatus.valueOf(getString("status") ?: BookingStatus.PENDING.name),
            quotedPriceInr = (getLong("quotedPriceInr") ?: 0L).toInt(),
            cancellationTerms = getString("cancellationTerms") ?: "",
            createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
            respondedAt = getLong("respondedAt"),
            touristUid = getString("touristUid") ?: "",
            ownerUid = getString("ownerUid") ?: ""
        )
    }.getOrNull()

    private fun VerificationDocument.toMap(ownerUid: String) = hashMapOf(
        "documentId" to documentId,
        "providerId" to providerId,
        "ownerUid" to ownerUid,
        "providerName" to providerName,
        "documentType" to documentType,
        "fileReference" to fileReference,
        "reviewStatus" to reviewStatus.name,
        "expiryDate" to expiryDate,
        "reviewerNotes" to reviewerNotes,
        "updatedAt" to System.currentTimeMillis()
    )

    private fun DocumentSnapshot.toUserModel(): User? = runCatching {
        User(
            userId = getString("userId") ?: id,
            name = getString("name") ?: "",
            contact = getString("contact") ?: "",
            passwordHash = "",
            role = UserRole.valueOf(getString("role") ?: UserRole.TOURIST.name),
            accountStatus = getString("accountStatus") ?: "ACTIVE"
        )
    }.getOrNull()

    private fun DocumentSnapshot.toVerificationDoc(): VerificationDocument? = runCatching {
        VerificationDocument(
            documentId = getString("documentId") ?: id,
            providerId = getString("providerId") ?: "",
            providerName = getString("providerName") ?: "",
            documentType = getString("documentType") ?: "",
            fileReference = getString("fileReference") ?: "",
            reviewStatus = VerificationStatus.valueOf(getString("reviewStatus") ?: VerificationStatus.PENDING.name),
            expiryDate = getString("expiryDate") ?: "",
            reviewerNotes = getString("reviewerNotes") ?: ""
        )
    }.getOrNull()

    private fun DocumentSnapshot.toReviewModel(): Review? = runCatching {
        Review(
            reviewId = getString("reviewId") ?: id,
            bookingId = getString("bookingId") ?: "",
            touristId = getString("touristUid") ?: "",
            touristName = getString("touristName") ?: "",
            listingId = getString("listingId") ?: "",
            rating = (getLong("rating") ?: 5L).toInt(),
            comment = getString("comment") ?: "",
            moderationStatus = getString("moderationStatus") ?: "VISIBLE",
            createdAt = getLong("createdAt") ?: System.currentTimeMillis()
        )
    }.getOrNull()

    private fun DocumentSnapshot.toAuditLogModel(): AuditLog? = runCatching {
        AuditLog(
            auditId = getString("auditId") ?: id,
            actorId = getString("actorUid") ?: "",
            actorRole = getString("actorRole") ?: "",
            action = getString("action") ?: "",
            targetId = getString("targetId") ?: "",
            details = getString("details") ?: "",
            timestamp = getLong("timestamp") ?: System.currentTimeMillis()
        )
    }.getOrNull()

    private fun DocumentSnapshot.toNotification(): AppNotification? = runCatching {
        AppNotification(
            notificationId = getString("notificationId") ?: id,
            userId = getString("userUid") ?: "",
            category = runCatching { NotificationCategory.valueOf(getString("category") ?: "TRIPS") }.getOrDefault(NotificationCategory.TRIPS),
            eventType = getString("eventType") ?: "ALERT",
            message = getString("message") ?: "",
            isRead = getBoolean("isRead") ?: false,
            createdAt = getLong("createdAt") ?: System.currentTimeMillis()
        )
    }.getOrNull()
}
