package com.motomove.himachal.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.motomove.himachal.ai.FoodMatchResult
import com.motomove.himachal.ai.MotoAiEngine
import com.motomove.himachal.ai.MotoAiPickupPlan
import com.motomove.himachal.ai.StayComparisonResult
import com.motomove.himachal.data.HimachalCatalog
import com.motomove.himachal.data.MotoMoveDatabaseHelper
import com.motomove.himachal.data.MotoMoveRepository
import com.motomove.himachal.data.OpResult
import com.motomove.himachal.live.FirebaseLiveSyncManager
import com.motomove.himachal.live.LiveSyncClient
import com.motomove.himachal.model.AppNotification
import com.motomove.himachal.model.AuditLog
import com.motomove.himachal.model.Booking
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.BookingType
import com.motomove.himachal.model.DiningSpot
import com.motomove.himachal.model.DriverProfile
import com.motomove.himachal.model.ListingApprovalStatus
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ChatBubble(val isUser: Boolean, val text: String)

data class MotoMoveUiState(
    val currentUser: User? = null,
    val firebaseUid: String? = null,
    val demoUsers: List<User> = emptyList(),
    val currentProvider: ProviderProfile? = null,
    val vehicles: List<VehicleListing> = emptyList(),
    val drivers: List<DriverProfile> = emptyList(),
    val properties: List<PropertyListing> = emptyList(),
    val diningSpots: List<DiningSpot> = emptyList(),
    val travelReels: List<TravelReel> = emptyList(),
    val roomsByProperty: Map<String, List<RoomType>> = emptyMap(),
    val bookings: List<Booking> = emptyList(),
    val verificationDocs: List<VerificationDocument> = emptyList(),
    val reviews: List<Review> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val pilotMetrics: PilotMetrics? = null,
    // Cloud Firestore + optional SSE Live Sync state
    val isFirebaseConnected: Boolean = false,
    val liveServerUrl: String = "",
    val isLiveConnected: Boolean = false,
    // Explore Refine Search state
    val selectedCategory: Int = 0,
    val selectedHubCode: String = "ALL",
    val selectedClusterName: String = "ALL",
    val searchStartDay: Long = LocalDate.now().plusDays(1).toEpochDay(),
    val searchEndDay: Long = LocalDate.now().plusDays(2).toEpochDay(),
    val searchPassengers: Int = 2,
    val sortLowestPrice: Boolean = false,
    // Food tab filters
    val foodClusterFilter: String = "ALL",
    val foodSpecialtyFilter: String = "ALL",
    // MotoAI state
    val aiHubCode: String = "KUU",
    val aiClusterId: String = "jibhi_tirthan",
    val aiPassengers: Int = 2,
    val aiLuggageNote: String = "2 backpacks + cabin bag",
    val aiPlan: MotoAiPickupPlan? = null,
    val aiStayComparison: StayComparisonResult? = null,
    val aiFoodMatches: List<FoodMatchResult> = emptyList(),
    val aiChatMessages: List<ChatBubble> = emptyList(),
    // Feedback banner
    val statusBanner: String? = null,
    val isErrorBanner: Boolean = false
)

class MotoMoveViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("motomove_prefs", Context.MODE_PRIVATE)
    private val dbHelper = MotoMoveDatabaseHelper(application)
    private val repo = MotoMoveRepository(dbHelper)
    private val firebaseSync = FirebaseLiveSyncManager()
    private val liveClient = LiveSyncClient()
    private var sseJob: Job? = null

    private var cloudVehicles: List<VehicleListing> = emptyList()
    private var cloudDrivers: List<DriverProfile> = emptyList()
    private var cloudProperties: List<PropertyListing> = emptyList()
    private var cloudRooms: List<RoomType> = emptyList()
    private var cloudDining: List<DiningSpot> = emptyList()
    private var cloudBookings: List<Booking> = emptyList()
    private var cloudNotifications: List<AppNotification> = emptyList()

    var uiState by mutableStateOf(MotoMoveUiState())
        private set

    init {
        val savedUrl = prefs.getString("live_server_url", "") ?: ""
        uiState = uiState.copy(liveServerUrl = savedUrl)
        refreshAll()
        if (savedUrl.isNotBlank()) startLiveStream(savedUrl)
    }

    fun setLiveServerUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        prefs.edit().putString("live_server_url", clean).apply()
        uiState = uiState.copy(liveServerUrl = clean, isLiveConnected = false)
        if (clean.isNotBlank()) {
            startLiveStream(clean)
            showMessage("Connecting to optional SSE Server: $clean")
        } else {
            sseJob?.cancel()
            showMessage("Using Cloud Firestore Live Sync.")
        }
    }

    private fun bindFirebaseSession(user: User, rawPassword: String) {
        firebaseSync.authenticateAndBind(
            user = user,
            rawPassword = rawPassword,
            onReady = { uid ->
                uiState = uiState.copy(firebaseUid = uid, isFirebaseConnected = true)
                val provId = repo.getProviderForUser(user.userId)?.providerId
                if (provId != null) {
                    firebaseSync.seedProviderListingsIfOwned(
                        uid = uid,
                        vehicles = repo.getVehicles(includeUnapproved = true).filter { it.providerId == provId },
                        drivers = repo.getDrivers(includeUnapproved = true).filter { it.providerId == provId },
                        properties = repo.getProperties(includeUnapproved = true).filter { it.providerId == provId },
                        rooms = repo.getAllRooms().filter { r ->
                            repo.getProperties(includeUnapproved = true).any { p -> p.propertyId == r.propertyId && p.providerId == provId }
                        },
                        diningSpots = repo.getDiningSpots().filter { it.providerId == provId }
                    )
                }
                firebaseSync.startListeners(
                    uid = uid,
                    role = user.role,
                    onVehicles = { list -> cloudVehicles = list; mergeAndEmitState() },
                    onDrivers = { list -> cloudDrivers = list; mergeAndEmitState() },
                    onProperties = { list -> cloudProperties = list; mergeAndEmitState() },
                    onRooms = { list -> cloudRooms = list; mergeAndEmitState() },
                    onDining = { list -> cloudDining = list; mergeAndEmitState() },
                    onBookings = { list -> cloudBookings = list; mergeAndEmitState() },
                    onNotifications = { list -> cloudNotifications = list; mergeAndEmitState() },
                    onStatus = { connected, _ ->
                        uiState = uiState.copy(isFirebaseConnected = connected)
                    }
                )
            },
            onError = {
                uiState = uiState.copy(isFirebaseConnected = false)
            }
        )
    }

    private fun mergeAndEmitState() {
        val user = uiState.currentUser
        val isAdmin = user?.role == UserRole.ADMINISTRATOR
        val isProvider = user?.role in setOf(UserRole.VEHICLE_OWNER, UserRole.LOCAL_DRIVER, UserRole.ACCOMMODATION_PROVIDER)
        val includeUnapproved = isAdmin || isProvider

        val localVehicles = repo.getVehicles(includeUnapproved)
        val mergedVehicles = mergeByKey(localVehicles, cloudVehicles) { it.vehicleId }

        val localDrivers = repo.getDrivers(includeUnapproved)
        val mergedDrivers = mergeByKey(localDrivers, cloudDrivers) { it.driverId }

        val localProps = repo.getProperties(includeUnapproved)
        val mergedProps = mergeByKey(localProps, cloudProperties) { it.propertyId }

        val localRooms = repo.getAllRooms()
        val mergedRooms = mergeByKey(localRooms, cloudRooms) { it.roomTypeId }
        val roomsMap = mergedRooms.groupBy { it.propertyId }

        val localDining = repo.getDiningSpots()
        val mergedDining = mergeByKey(localDining, cloudDining) { it.spotId }

        val localBookings = user?.let { repo.getBookingsForActor(it) } ?: emptyList()
        val mergedBookings = mergeByKey(localBookings, cloudBookings) { it.bookingId }
            .sortedByDescending { it.createdAt }

        val localNotifs = user?.let { repo.getNotificationsForUser(it.userId) } ?: emptyList()
        val mergedNotifs = (cloudNotifications + localNotifs)
            .distinctBy { it.notificationId }
            .sortedByDescending { it.createdAt }

        uiState = uiState.copy(
            vehicles = mergedVehicles,
            drivers = mergedDrivers,
            properties = mergedProps,
            roomsByProperty = roomsMap,
            diningSpots = mergedDining,
            bookings = mergedBookings,
            notifications = mergedNotifs
        )
    }

    private fun <T> mergeByKey(base: List<T>, overlay: List<T>, keyFn: (T) -> String): List<T> {
        if (overlay.isEmpty()) return base
        val map = LinkedHashMap<String, T>()
        base.forEach { map[keyFn(it)] = it }
        overlay.forEach { map[keyFn(it)] = it }
        return map.values.toList()
    }

    private fun startLiveStream(url: String) {
        sseJob?.cancel()
        if (url.isBlank()) return
        sseJob = viewModelScope.launch {
            liveClient.observeEvents(url).collect { event ->
                if (event == null) {
                    uiState = uiState.copy(isLiveConnected = false)
                } else {
                    uiState = uiState.copy(isLiveConnected = true)
                    val snap = liveClient.fetchSnapshot(url, uiState.currentUser?.userId)
                    if (snap != null) {
                        cloudVehicles = snap.vehicles
                        cloudDrivers = snap.drivers
                        cloudProperties = snap.properties
                        cloudRooms = snap.roomTypes
                        cloudDining = snap.diningSpots
                        cloudBookings = snap.bookings
                        cloudNotifications = snap.notifications
                        mergeAndEmitState()
                    }
                    if (event.type != "CONNECTED" && event.summary.isNotBlank()) {
                        showMessage("LIVE: ${event.summary}")
                    }
                }
            }
        }
    }

    fun refreshAll() {
        val user = uiState.currentUser
        uiState = uiState.copy(
            demoUsers = repo.getAllDemoUsers(),
            currentProvider = user?.let { repo.getProviderForUser(it.userId) },
            travelReels = repo.getReelsForUser(user?.userId),
            verificationDocs = user?.let { repo.getVerificationDocumentsForActor(it) } ?: emptyList(),
            reviews = repo.getReviewsForListing(null),
            auditLogs = user?.let { repo.getAuditLogsForActor(it) } ?: emptyList(),
            pilotMetrics = user?.let { repo.getPilotMetrics(it) }
        )
        mergeAndEmitState()
    }

    fun clearBanner() {
        uiState = uiState.copy(statusBanner = null, isErrorBanner = false)
    }

    private fun showMessage(msg: String, isError: Boolean = false) {
        uiState = uiState.copy(statusBanner = msg, isErrorBanner = isError)
    }

    fun loginWithCredentials(contact: String, password: String) {
        when (val res = repo.authenticate(contact, password)) {
            is OpResult.Success -> {
                uiState = uiState.copy(currentUser = res.data)
                refreshAll()
                bindFirebaseSession(res.data, password)
                showMessage("Signed in as ${res.data.name} (Cloud Live Sync connecting...)")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun quickSignInDemo(user: User) {
        uiState = uiState.copy(currentUser = user)
        refreshAll()
        bindFirebaseSession(user, "demo1234")
        showMessage("Switched to ${user.name} (${user.role.displayName})")
    }

    fun registerAccount(name: String, contact: String, password: String, role: UserRole, homeCluster: String) {
        when (val res = repo.registerUser(name, contact, password, role, homeCluster)) {
            is OpResult.Success -> {
                uiState = uiState.copy(currentUser = res.data)
                refreshAll()
                bindFirebaseSession(res.data, password)
                showMessage("Account created securely for ${res.data.name}")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun logout() {
        firebaseSync.signOut()
        cloudBookings = emptyList()
        cloudNotifications = emptyList()
        uiState = uiState.copy(
            currentUser = null,
            firebaseUid = null,
            isFirebaseConnected = false,
            statusBanner = null,
            aiChatMessages = emptyList()
        )
        refreshAll()
    }

    fun updateOwnName(newName: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.updateOwnProfileName(user, user.userId, newName)) {
            is OpResult.Success -> {
                uiState = uiState.copy(currentUser = res.data)
                refreshAll()
                showMessage("Your profile name has been updated.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun updateExploreFilters(
        category: Int = uiState.selectedCategory,
        hubCode: String = uiState.selectedHubCode,
        clusterName: String = uiState.selectedClusterName,
        startDay: Long = uiState.searchStartDay,
        endDay: Long = uiState.searchEndDay,
        passengers: Int = uiState.searchPassengers,
        sortLowestPrice: Boolean = uiState.sortLowestPrice
    ) {
        uiState = uiState.copy(
            selectedCategory = category,
            selectedHubCode = hubCode,
            selectedClusterName = clusterName,
            searchStartDay = startDay,
            searchEndDay = endDay.coerceAtLeast(startDay),
            searchPassengers = passengers.coerceIn(1, 12),
            sortLowestPrice = sortLowestPrice
        )
    }

    fun updateFoodFilters(cluster: String = uiState.foodClusterFilter, specialty: String = uiState.foodSpecialtyFilter) {
        uiState = uiState.copy(foodClusterFilter = cluster, foodSpecialtyFilter = specialty)
    }

    // --- Real-Time Cloud Firestore + Local SQLite Broadcast Controls ---

    fun toggleVehicleLive(vehicle: VehicleListing, availableNow: Boolean, rateInr: Int, note: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.toggleVehicleLive(user, vehicle.vehicleId, availableNow, rateInr, note)) {
            is OpResult.Success -> {
                val updated = vehicle.copy(
                    isAvailableNow = availableNow,
                    rateInr = rateInr.coerceAtLeast(500),
                    liveStatusNote = note.ifBlank { if (availableNow) "Available now" else "On trip" },
                    lastUpdatedAt = System.currentTimeMillis()
                )
                refreshAll()
                if (uiState.isFirebaseConnected) {
                    firebaseSync.upsertVehicle(updated) { ok, err ->
                        showMessage(if (ok) "LIVE on Firebase: ${vehicle.title} updated!" else "Cloud error: $err", !ok)
                    }
                } else {
                    showMessage("Updated ${vehicle.title} locally.")
                }
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun toggleDriverLive(driver: DriverProfile, availableNow: Boolean, ratePerDay: Int, summary: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.toggleDriverLive(user, driver.driverId, availableNow, ratePerDay, summary)) {
            is OpResult.Success -> {
                val updated = driver.copy(
                    isAvailableNow = availableNow,
                    ratePerDayInr = ratePerDay.coerceAtLeast(500),
                    availabilitySummary = summary.ifBlank { if (availableNow) "Online & ready" else "On trip" },
                    lastUpdatedAt = System.currentTimeMillis()
                )
                refreshAll()
                if (uiState.isFirebaseConnected) {
                    firebaseSync.upsertDriver(updated) { ok, err ->
                        showMessage(if (ok) "LIVE on Firebase: Driver status broadcasted!" else "Cloud error: $err", !ok)
                    }
                } else {
                    showMessage("Driver status updated locally.")
                }
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun updateRoomLiveInventory(room: RoomType, newInventory: Int, newRate: Int) {
        val user = uiState.currentUser ?: return
        val count = newInventory.coerceAtLeast(0)
        when (val res = repo.updateRoomLiveInventory(user, room.roomTypeId, count, newRate)) {
            is OpResult.Success -> {
                val updated = room.copy(inventoryCount = count, ratePerNightInr = newRate.coerceAtLeast(500))
                refreshAll()
                if (uiState.isFirebaseConnected) {
                    firebaseSync.upsertRoom(updated) { ok, err ->
                        showMessage(if (ok) "LIVE on Firebase: ${room.name} ($count rooms left)!" else "Cloud error: $err", !ok)
                    }
                } else {
                    showMessage("Room inventory updated locally.")
                }
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun toggleDiningLive(spot: DiningSpot, isOpenNow: Boolean, note: String, avgCost: Int) {
        val user = uiState.currentUser ?: return
        when (val res = repo.toggleDiningLive(user, spot.spotId, isOpenNow, note, avgCost)) {
            is OpResult.Success -> {
                val updated = spot.copy(
                    isOpenNow = isOpenNow,
                    liveStatusNote = note.ifBlank { if (isOpenNow) "Tables open now" else "Currently full" },
                    avgCostInr = avgCost.coerceAtLeast(100)
                )
                refreshAll()
                if (uiState.isFirebaseConnected) {
                    firebaseSync.upsertDining(updated) { ok, err ->
                        showMessage(if (ok) "LIVE on Firebase: ${spot.name} updated!" else "Cloud error: $err", !ok)
                    }
                } else {
                    showMessage("Dining status updated locally.")
                }
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun submitVehicleBooking(
        vehicle: VehicleListing,
        bookingType: BookingType,
        pickupHubCode: String?,
        destinationCluster: String,
        trainOrFlightNo: String,
        pickupNotes: String,
        startEpochDay: Long,
        endEpochDay: Long,
        slot: TimeSlot,
        seats: Int
    ) {
        val user = uiState.currentUser ?: return
        when (val res = repo.createMobilityBooking(
            tourist = user,
            providerId = vehicle.providerId,
            listingId = vehicle.vehicleId,
            listingTitle = vehicle.title,
            approvalStatus = vehicle.approvalStatus,
            providerVerification = vehicle.providerVerification,
            maxCapacity = vehicle.capacity,
            bookingType = bookingType,
            pickupHubCode = pickupHubCode,
            destinationCluster = destinationCluster,
            trainOrFlightNo = trainOrFlightNo,
            pickupNotes = pickupNotes,
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            slot = slot,
            seatsRequested = seats,
            ratePerUnitInr = vehicle.rateInr,
            cancellationTerms = vehicle.cancellationTerms
        )) {
            is OpResult.Success -> {
                refreshAll()
                if (uiState.isFirebaseConnected) {
                    firebaseSync.createBooking(res.data, vehicle.ownerUid) { _, _ -> }
                }
                showMessage("Pickup/Ride request sent! ID: ${res.data.bookingId} (₹${res.data.quotedPriceInr})")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun submitDriverBooking(
        driver: DriverProfile,
        bookingType: BookingType,
        pickupHubCode: String?,
        destinationCluster: String,
        trainOrFlightNo: String,
        pickupNotes: String,
        startEpochDay: Long,
        endEpochDay: Long,
        slot: TimeSlot
    ) {
        val user = uiState.currentUser ?: return
        when (val res = repo.createMobilityBooking(
            tourist = user,
            providerId = driver.providerId,
            listingId = driver.driverId,
            listingTitle = "Driver: ${driver.name}",
            approvalStatus = driver.approvalStatus,
            providerVerification = driver.verificationStatus,
            maxCapacity = 6,
            bookingType = bookingType,
            pickupHubCode = pickupHubCode,
            destinationCluster = destinationCluster,
            trainOrFlightNo = trainOrFlightNo,
            pickupNotes = pickupNotes,
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            slot = slot,
            seatsRequested = 1,
            ratePerUnitInr = driver.ratePerDayInr,
            cancellationTerms = driver.cancellationTerms
        )) {
            is OpResult.Success -> {
                refreshAll()
                if (uiState.isFirebaseConnected) {
                    firebaseSync.createBooking(res.data, driver.ownerUid) { _, _ -> }
                }
                showMessage("Driver request sent to ${driver.name}!")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun submitStayBooking(
        property: PropertyListing,
        roomType: RoomType,
        checkInDay: Long,
        checkOutDay: Long,
        guests: Int,
        notes: String
    ) {
        val user = uiState.currentUser ?: return
        when (val res = repo.createStayBooking(user, property, roomType, checkInDay, checkOutDay, guests, notes)) {
            is OpResult.Success -> {
                refreshAll()
                if (uiState.isFirebaseConnected) {
                    firebaseSync.createBooking(res.data, property.ownerUid) { _, _ -> }
                }
                showMessage("Stay request sent to ${property.name} (₹${res.data.quotedPriceInr})!")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun reserveDiningTable(spot: DiningSpot, guests: Int, notes: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.createDiningReservation(user, spot, guests, notes)) {
            is OpResult.Success -> {
                refreshAll()
                if (uiState.isFirebaseConnected) {
                    firebaseSync.createBooking(res.data, spot.ownerUid) { _, _ -> }
                }
                showMessage("Table reserved at ${spot.name} for $guests guests!")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun respondToBooking(bookingId: String, accept: Boolean) {
        val user = uiState.currentUser ?: return
        val cloudTarget = uiState.bookings.firstOrNull { it.bookingId == bookingId }
        val localRes = repo.respondToBooking(user, bookingId, accept)
        if (localRes is OpResult.Success) {
            refreshAll()
            if (uiState.isFirebaseConnected && cloudTarget != null) {
                firebaseSync.updateBookingStatus(
                    cloudTarget,
                    if (accept) BookingStatus.ACCEPTED else BookingStatus.REJECTED
                ) { _, _ -> }
            }
            showMessage("Booking updated to ${localRes.data.label}.")
        } else if (uiState.isFirebaseConnected && cloudTarget != null) {
            val newStatus = if (accept) BookingStatus.ACCEPTED else BookingStatus.REJECTED
            firebaseSync.updateBookingStatus(cloudTarget, newStatus) { ok, err ->
                showMessage(if (ok) "Live booking ${newStatus.label}!" else "Access denied: $err", !ok)
            }
        } else if (localRes is OpResult.Error) {
            showMessage(localRes.message, isError = true)
        }
    }

    fun cancelBooking(bookingId: String) {
        val user = uiState.currentUser ?: return
        val cloudTarget = uiState.bookings.firstOrNull { it.bookingId == bookingId }
        when (val res = repo.updateBookingLifecycle(user, bookingId, BookingStatus.CANCELLED)) {
            is OpResult.Success -> {
                refreshAll()
                if (uiState.isFirebaseConnected && cloudTarget != null) {
                    firebaseSync.updateBookingStatus(cloudTarget, BookingStatus.CANCELLED) { _, _ -> }
                }
                showMessage("Booking cancelled.")
            }
            is OpResult.Error -> {
                if (uiState.isFirebaseConnected && cloudTarget != null) {
                    firebaseSync.updateBookingStatus(cloudTarget, BookingStatus.CANCELLED) { ok, err ->
                        showMessage(if (ok) "Booking cancelled live." else "Denied: $err", !ok)
                    }
                } else {
                    showMessage(res.message, isError = true)
                }
            }
        }
    }

    fun completeBooking(bookingId: String) {
        val user = uiState.currentUser ?: return
        val cloudTarget = uiState.bookings.firstOrNull { it.bookingId == bookingId }
        when (val res = repo.updateBookingLifecycle(user, bookingId, BookingStatus.COMPLETED)) {
            is OpResult.Success -> {
                refreshAll()
                if (uiState.isFirebaseConnected && cloudTarget != null) {
                    firebaseSync.updateBookingStatus(cloudTarget, BookingStatus.COMPLETED) { _, _ -> }
                }
                showMessage("Trip/Stay marked Completed. Tourist can now submit a review!")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun submitReview(bookingId: String, rating: Int, comment: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.submitReview(user, bookingId, rating, comment)) {
            is OpResult.Success -> {
                refreshAll()
                showMessage("Thank you! Your ${res.data.rating}★ review is live.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun addVehicleListing(
        title: String,
        vehicleType: String,
        capacity: Int,
        transmissionFuel: String,
        serviceArea: String,
        pickupHubs: List<String>,
        rentalMode: RentalMode,
        rateInr: Int,
        cancellationTerms: String
    ) {
        val user = uiState.currentUser ?: return
        when (val res = repo.addVehicleListing(
            user, title, vehicleType, capacity, transmissionFuel,
            serviceArea, pickupHubs, rentalMode, rateInr, cancellationTerms
        )) {
            is OpResult.Success -> {
                refreshAll()
                val newest = repo.getVehicles(includeUnapproved = true).firstOrNull { it.title == title.trim() }
                if (uiState.isFirebaseConnected && newest != null) {
                    firebaseSync.upsertVehicle(newest) { _, _ -> }
                }
                showMessage("Vehicle listing submitted for Admin approval.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun updateOwnVehicle(vehicleId: String, newTitle: String, newRate: Int, newArea: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.updateVehicleListing(user, vehicleId, newTitle, newRate, newArea)) {
            is OpResult.Success -> {
                refreshAll()
                val updated = repo.getVehicles(includeUnapproved = true).firstOrNull { it.vehicleId == vehicleId }
                if (uiState.isFirebaseConnected && updated != null) {
                    firebaseSync.upsertVehicle(updated) { _, _ -> }
                }
                showMessage("Your vehicle listing was updated.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun deleteOwnVehicle(vehicleId: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.deleteVehicleListing(user, vehicleId)) {
            is OpResult.Success -> {
                refreshAll()
                if (uiState.isFirebaseConnected) {
                    firebaseSync.deleteVehicle(vehicleId) { _, _ -> }
                }
                showMessage("Your vehicle listing was deleted.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun submitDriverDoc(
        languages: String,
        experienceYears: Int,
        serviceArea: String,
        pickupHubs: List<String>,
        ratePerDayInr: Int,
        docType: String,
        docRef: String,
        expiryDate: String
    ) {
        val user = uiState.currentUser ?: return
        when (val res = repo.submitDriverProfileAndDocument(
            user, languages, experienceYears, serviceArea, pickupHubs,
            ratePerDayInr, docType, docRef, expiryDate
        )) {
            is OpResult.Success -> {
                refreshAll()
                showMessage("Driver profile & licence submitted for restricted Admin review.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun addPropertyWithRoom(
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
    ) {
        val user = uiState.currentUser ?: return
        when (val res = repo.addPropertyWithRoom(
            user, name, propertyType, locationCluster, nearestHubCode,
            amenities, registrationNumber, roomName, occupancy, ratePerNight, inventoryCount
        )) {
            is OpResult.Success -> {
                refreshAll()
                val newest = repo.getProperties(includeUnapproved = true).firstOrNull { it.name == name.trim() }
                if (uiState.isFirebaseConnected && newest != null) {
                    firebaseSync.upsertProperty(newest) { _, _ -> }
                }
                showMessage("Property & room inventory submitted for Admin approval.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun updateOwnProperty(propertyId: String, newName: String, newAmenities: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.updatePropertyListing(user, propertyId, newName, newAmenities)) {
            is OpResult.Success -> {
                refreshAll()
                val updated = repo.getProperties(includeUnapproved = true).firstOrNull { it.propertyId == propertyId }
                if (uiState.isFirebaseConnected && updated != null) {
                    firebaseSync.upsertProperty(updated) { _, _ -> }
                }
                showMessage("Your property listing was updated.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun adminVerifyDocument(documentId: String, status: VerificationStatus, notes: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.adminReviewDocument(user, documentId, status, notes)) {
            is OpResult.Success -> {
                refreshAll()
                showMessage("Verification updated to ${status.label} & logged.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun adminApproveVehicle(vehicleId: String, status: ListingApprovalStatus) {
        val user = uiState.currentUser ?: return
        when (val res = repo.adminUpdateListingApproval(user, "vehicles", "vehicle_id", vehicleId, status)) {
            is OpResult.Success -> {
                refreshAll()
                showMessage("Vehicle marked ${status.label}.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun adminApproveDriver(driverId: String, status: ListingApprovalStatus) {
        val user = uiState.currentUser ?: return
        when (val res = repo.adminUpdateListingApproval(user, "drivers", "driver_id", driverId, status)) {
            is OpResult.Success -> {
                refreshAll()
                showMessage("Driver marked ${status.label}.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun adminApproveProperty(propertyId: String, status: ListingApprovalStatus) {
        val user = uiState.currentUser ?: return
        when (val res = repo.adminUpdateListingApproval(user, "properties", "property_id", propertyId, status)) {
            is OpResult.Success -> {
                refreshAll()
                showMessage("Property marked ${status.label}.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun markAllAlertsRead() {
        val user = uiState.currentUser ?: return
        repo.markAllNotificationsRead(user)
        refreshAll()
        showMessage("All alerts marked as read.")
    }

    fun sendTestAlert() {
        val user = uiState.currentUser ?: return
        repo.sendTestAlert(user)
        refreshAll()
    }

    fun updateMotoAiInputs(hubCode: String, clusterId: String, passengers: Int, luggage: String) {
        uiState = uiState.copy(
            aiHubCode = hubCode,
            aiClusterId = clusterId,
            aiPassengers = passengers.coerceIn(1, 8),
            aiLuggageNote = luggage
        )
    }

    fun runMotoAiPickupPlan() {
        val hub = HimachalCatalog.hubByCode(uiState.aiHubCode) ?: HimachalCatalog.transitHubs.first()
        val cluster = HimachalCatalog.destinationClusters.firstOrNull { it.id == uiState.aiClusterId }
            ?: HimachalCatalog.destinationClusters.first()
        val plan = MotoAiEngine.planHubPickup(
            hub = hub,
            cluster = cluster,
            passengerCount = uiState.aiPassengers,
            luggageNote = uiState.aiLuggageNote,
            availableVehicles = uiState.vehicles
        )
        uiState = uiState.copy(aiPlan = plan)
    }

    fun runMotoAiCompareStays() {
        if (uiState.properties.size >= 2) {
            val res = MotoAiEngine.compareStaysSideBySide(
                uiState.properties[0],
                uiState.properties[1],
                uiState.roomsByProperty
            )
            uiState = uiState.copy(aiStayComparison = res)
        }
    }

    fun runMotoAiFoodFaceOff(clusterFilter: String) {
        val matches = MotoAiEngine.recommendFoodFaceOff(clusterFilter, uiState.diningSpots)
        uiState = uiState.copy(aiFoodMatches = matches)
    }

    fun askMotoAiQuestion(question: String) {
        if (question.isBlank()) return
        val hub = HimachalCatalog.hubByCode(uiState.aiHubCode) ?: HimachalCatalog.transitHubs.first()
        val cluster = HimachalCatalog.destinationClusters.firstOrNull { it.id == uiState.aiClusterId }
            ?: HimachalCatalog.destinationClusters.first()
        val answer = MotoAiEngine.answerTravelQuestion(question, hub, cluster)
        uiState = uiState.copy(
            aiChatMessages = uiState.aiChatMessages +
                ChatBubble(isUser = true, text = question.trim()) +
                ChatBubble(isUser = false, text = answer)
        )
    }

    override fun onCleared() {
        firebaseSync.stopListeners()
        super.onCleared()
    }
}
