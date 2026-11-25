package com.motomove.himachal.live

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.motomove.himachal.model.AppNotification
import com.motomove.himachal.model.Booking
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.BookingType
import com.motomove.himachal.model.DiningSpot
import com.motomove.himachal.model.DriverProfile
import com.motomove.himachal.model.ListingApprovalStatus
import com.motomove.himachal.model.NotificationCategory
import com.motomove.himachal.model.PropertyListing
import com.motomove.himachal.model.RentalMode
import com.motomove.himachal.model.RoomType
import com.motomove.himachal.model.TimeSlot
import com.motomove.himachal.model.User
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.model.VehicleListing
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
        rawPassword: String,
        onReady: (uid: String) -> Unit,
        onError: (String) -> Unit
    ) {
        val email = toFirebaseEmail(user.contact)
        val pass = if (rawPassword.length >= 6) rawPassword else "${rawPassword}_motomove"

        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: return@addOnSuccessListener
                ensureUserDoc(uid, user) { onReady(uid) }
            }
            .addOnFailureListener {
                // Create Firebase Auth account if first login for this email/demo user
                auth.createUserWithEmailAndPassword(email, pass)
                    .addOnSuccessListener { created ->
                        val uid = created.user?.uid ?: return@addOnSuccessListener
                        ensureUserDoc(uid, user) { onReady(uid) }
                    }
                    .addOnFailureListener {
                        // Fallback to anonymous auth if email provider isn't enabled yet
                        auth.signInAnonymously()
                            .addOnSuccessListener { anon ->
                                val uid = anon.user?.uid ?: return@addOnSuccessListener
                                ensureUserDoc(uid, user) { onReady(uid) }
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

    private fun ensureUserDoc(uid: String, user: User, onDone: () -> Unit) {
        val roleToStore = if (user.role == UserRole.ADMINISTRATOR) UserRole.TOURIST.name else user.role.name
        val payload = hashMapOf(
            "userId" to uid,
            "name" to user.name,
            "contact" to user.contact,
            "role" to roleToStore,
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("users").document(uid)
            .set(payload, SetOptions.merge())
            .addOnCompleteListener { onDone() }
    }

    fun seedProviderListingsIfOwned(
        uid: String,
        vehicles: List<VehicleListing>,
        drivers: List<DriverProfile>,
        properties: List<PropertyListing>,
        rooms: List<RoomType>,
        diningSpots: List<DiningSpot>
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
    }

    fun startListeners(
        uid: String,
        role: UserRole,
        onVehicles: (List<VehicleListing>) -> Unit,
        onDrivers: (List<DriverProfile>) -> Unit,
        onProperties: (List<PropertyListing>) -> Unit,
        onRooms: (List<RoomType>) -> Unit,
        onDining: (List<DiningSpot>) -> Unit,
        onBookings: (List<Booking>) -> Unit,
        onNotifications: (List<AppNotification>) -> Unit,
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

        val bookingQuery = when (role) {
            UserRole.TOURIST -> db.collection("bookings").whereEqualTo("touristUid", uid)
            UserRole.ADMINISTRATOR -> db.collection("bookings")
            else -> db.collection("bookings").whereEqualTo("ownerUid", uid)
        }
        registrations += bookingQuery.addSnapshotListener { snap, _ ->
            val list = snap?.documents?.mapNotNull { it.toBooking() }.orEmpty()
            onBookings(list)
        }

        registrations += db.collection("notifications")
            .whereEqualTo("userUid", uid)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toNotification() }.orEmpty()
                onNotifications(list)
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
        val owner = targetOwnerUid.ifBlank { uid }
        val updated = booking.copy(touristUid = uid, ownerUid = owner)
        db.collection("bookings").document(booking.bookingId)
            .set(updated.toMap())
            .addOnSuccessListener {
                if (owner != uid) {
                    pushNotification(owner, NotificationCategory.TRIPS, "NEW_BOOKING", "LIVE: ${booking.touristName} requested ${booking.listingTitle}.")
                }
                onDone(true, null)
            }
            .addOnFailureListener { onDone(false, it.message) }
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
                    pushNotification(booking.touristUid, NotificationCategory.TRIPS, "BOOKING_${newStatus.name}", "LIVE: ${booking.listingTitle} is now ${newStatus.label}!")
                }
                onDone(true, null)
            }
            .addOnFailureListener { onDone(false, it.message) }
    }

    private fun pushNotification(userUid: String, category: NotificationCategory, eventType: String, message: String) {
        val doc = db.collection("notifications").document()
        doc.set(
            hashMapOf(
                "notificationId" to doc.id,
                "userUid" to userUid,
                "category" to category.name,
                "eventType" to eventType,
                "message" to message.take(450),
                "isRead" to false,
                "createdAt" to System.currentTimeMillis()
            )
        )
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
