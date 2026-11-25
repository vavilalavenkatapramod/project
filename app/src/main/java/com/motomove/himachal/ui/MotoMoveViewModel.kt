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
    // Live Multi-Device Sync state
    val liveServerUrl: String = "http://10.0.2.2:8080",
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
    private val liveClient = LiveSyncClient()
    private var sseJob: Job? = null

    var uiState by mutableStateOf(MotoMoveUiState())
        private set

    init {
        val savedUrl = prefs.getString("live_server_url", "http://10.0.2.2:8080") ?: "http://10.0.2.2:8080"
        uiState = uiState.copy(liveServerUrl = savedUrl)
        refreshAll()
        startLiveStream(savedUrl)
    }

    fun setLiveServerUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        prefs.edit().putString("live_server_url", clean).apply()
        uiState = uiState.copy(liveServerUrl = clean, isLiveConnected = false)
        startLiveStream(clean)
        showMessage("Connecting to Live Server: $clean")
    }

    private fun startLiveStream(url: String) {
        sseJob?.cancel()
        if (url.isBlank()) return
        sseJob = viewModelScope.launch {
            pullLiveSnapshot()
            liveClient.observeEvents(url).collect { event ->
                if (event == null) {
                    uiState = uiState.copy(isLiveConnected = false)
                } else {
                    uiState = uiState.copy(isLiveConnected = true)
                    pullLiveSnapshot()
                    if (event.type != "CONNECTED" && event.summary.isNotBlank()) {
                        val mine = event.targetUserId == null || event.targetUserId == uiState.currentUser?.userId
                        if (mine) {
                            showMessage("LIVE: ${event.summary}")
                        }
                    }
                }
            }
        }
    }

    private suspend fun pullLiveSnapshot() {
        val snap = liveClient.fetchSnapshot(uiState.liveServerUrl, uiState.currentUser?.userId) ?: return
        val roomsMap = snap.roomTypes.groupBy { it.propertyId }
        uiState = uiState.copy(
            isLiveConnected = true,
            vehicles = snap.vehicles,
            drivers = snap.drivers,
            properties = snap.properties,
            roomsByProperty = roomsMap,
            diningSpots = snap.diningSpots,
            bookings = if (uiState.currentUser != null) snap.bookings else uiState.bookings,
            notifications = if (uiState.currentUser != null) snap.notifications else uiState.notifications
        )
    }

    fun refreshAll() {
        val user = uiState.currentUser
        val isAdmin = user?.role == UserRole.ADMINISTRATOR
        val isProvider = user?.role in setOf(UserRole.VEHICLE_OWNER, UserRole.LOCAL_DRIVER, UserRole.ACCOMMODATION_PROVIDER)
        val includeUnapproved = isAdmin || isProvider
        val props = repo.getProperties(includeUnapproved = includeUnapproved)
        val roomsMap = props.associate { it.propertyId to repo.getRoomsForProperty(it.propertyId) }

        uiState = uiState.copy(
            demoUsers = repo.getAllDemoUsers(),
            currentProvider = user?.let { repo.getProviderForUser(it.userId) },
            vehicles = repo.getVehicles(includeUnapproved = includeUnapproved),
            drivers = repo.getDrivers(includeUnapproved = includeUnapproved),
            properties = props,
            diningSpots = repo.getDiningSpots(),
            travelReels = repo.getReelsForUser(user?.userId),
            roomsByProperty = roomsMap,
            bookings = user?.let { repo.getBookingsForActor(it) } ?: emptyList(),
            verificationDocs = user?.let { repo.getVerificationDocumentsForActor(it) } ?: emptyList(),
            reviews = repo.getReviewsForListing(null),
            notifications = user?.let { repo.getNotificationsForUser(it.userId) } ?: emptyList(),
            auditLogs = user?.let { repo.getAuditLogsForActor(it) } ?: emptyList(),
            pilotMetrics = user?.let { repo.getPilotMetrics(it) }
        )

        viewModelScope.launch { pullLiveSnapshot() }
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
                showMessage("Signed in securely as ${res.data.name}")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun quickSignInDemo(user: User) {
        uiState = uiState.copy(currentUser = user)
        refreshAll()
        showMessage("Switched to ${user.name} (${user.role.displayName})")
    }

    fun registerAccount(name: String, contact: String, password: String, role: UserRole, homeCluster: String) {
        when (val res = repo.registerUser(name, contact, password, role, homeCluster)) {
            is OpResult.Success -> {
                uiState = uiState.copy(currentUser = res.data)
                refreshAll()
                showMessage("Account created securely for ${res.data.name}")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun logout() {
        uiState = uiState.copy(currentUser = null, statusBanner = null, aiChatMessages = emptyList())
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

    // --- Live Real-Time Broadcast Controls for Providers ---

    fun toggleVehicleLive(vehicle: VehicleListing, availableNow: Boolean, rateInr: Int, note: String) {
        val user = uiState.currentUser ?: return
        if (uiState.isLiveConnected) {
            viewModelScope.launch {
                val r = liveClient.patchVehicleLive(uiState.liveServerUrl, user.userId, vehicle.vehicleId, availableNow, rateInr, note)
                r.onSuccess {
                    pullLiveSnapshot()
                    showMessage("Broadcasted LIVE update for ${vehicle.title}")
                }.onFailure {
                    showMessage(it.message ?: "Live update denied", isError = true)
                }
            }
        } else {
            when (val res = repo.toggleVehicleLive(user, vehicle.vehicleId, availableNow, rateInr, note)) {
                is OpResult.Success -> {
                    refreshAll()
                    showMessage("Updated ${vehicle.title} locally (connect Live Server to sync across phones).")
                }
                is OpResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    fun toggleDriverLive(driver: DriverProfile, availableNow: Boolean, ratePerDay: Int, summary: String) {
        val user = uiState.currentUser ?: return
        if (uiState.isLiveConnected) {
            viewModelScope.launch {
                val r = liveClient.patchDriverLive(uiState.liveServerUrl, user.userId, driver.driverId, availableNow, ratePerDay, summary)
                r.onSuccess {
                    pullLiveSnapshot()
                    showMessage("Broadcasted LIVE driver status!")
                }.onFailure {
                    showMessage(it.message ?: "Live update denied", isError = true)
                }
            }
        } else {
            when (val res = repo.toggleDriverLive(user, driver.driverId, availableNow, ratePerDay, summary)) {
                is OpResult.Success -> {
                    refreshAll()
                    showMessage("Driver status updated locally.")
                }
                is OpResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    fun updateRoomLiveInventory(room: RoomType, newInventory: Int, newRate: Int) {
        val user = uiState.currentUser ?: return
        if (uiState.isLiveConnected) {
            viewModelScope.launch {
                val r = liveClient.patchRoomLive(uiState.liveServerUrl, user.userId, room.roomTypeId, newInventory.coerceAtLeast(0), newRate)
                r.onSuccess {
                    pullLiveSnapshot()
                    showMessage("Broadcasted LIVE room count (${newInventory.coerceAtLeast(0)})!")
                }.onFailure {
                    showMessage(it.message ?: "Room update denied", isError = true)
                }
            }
        } else {
            when (val res = repo.updateRoomLiveInventory(user, room.roomTypeId, newInventory, newRate)) {
                is OpResult.Success -> {
                    refreshAll()
                    showMessage("Room inventory updated locally.")
                }
                is OpResult.Error -> showMessage(res.message, isError = true)
            }
        }
    }

    fun toggleDiningLive(spot: DiningSpot, isOpenNow: Boolean, note: String, avgCost: Int) {
        val user = uiState.currentUser ?: return
        if (uiState.isLiveConnected) {
            viewModelScope.launch {
                val r = liveClient.patchDiningLive(uiState.liveServerUrl, user.userId, spot.spotId, isOpenNow, note, avgCost)
                r.onSuccess {
                    pullLiveSnapshot()
                    showMessage("Broadcasted LIVE dining status for ${spot.name}!")
                }.onFailure {
                    showMessage(it.message ?: "Dining update denied", isError = true)
                }
            }
        } else {
            when (val res = repo.toggleDiningLive(user, spot.spotId, isOpenNow, note, avgCost)) {
                is OpResult.Success -> {
                    refreshAll()
                    showMessage("Dining availability updated locally.")
                }
                is OpResult.Error -> showMessage(res.message, isError = true)
            }
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
                if (uiState.isLiveConnected) {
                    viewModelScope.launch {
                        liveClient.postBooking(uiState.liveServerUrl, user.userId, res.data)
                        pullLiveSnapshot()
                    }
                } else {
                    refreshAll()
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
                if (uiState.isLiveConnected) {
                    viewModelScope.launch {
                        liveClient.postBooking(uiState.liveServerUrl, user.userId, res.data)
                        pullLiveSnapshot()
                    }
                } else {
                    refreshAll()
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
                if (uiState.isLiveConnected) {
                    viewModelScope.launch {
                        liveClient.postBooking(uiState.liveServerUrl, user.userId, res.data)
                        pullLiveSnapshot()
                    }
                } else {
                    refreshAll()
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
                if (uiState.isLiveConnected) {
                    viewModelScope.launch {
                        liveClient.postBooking(uiState.liveServerUrl, user.userId, res.data)
                        pullLiveSnapshot()
                    }
                } else {
                    refreshAll()
                }
                showMessage("Table reserved at ${spot.name} for $guests guests!")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun respondToBooking(bookingId: String, accept: Boolean) {
        val user = uiState.currentUser ?: return
        if (uiState.isLiveConnected) {
            viewModelScope.launch {
                liveClient.postRespondBooking(uiState.liveServerUrl, user.userId, bookingId, accept)
                    .onSuccess {
                        pullLiveSnapshot()
                        showMessage("Live response broadcasted!")
                    }
                    .onFailure { showMessage(it.message ?: "Failed", isError = true) }
            }
            return
        }
        when (val res = repo.respondToBooking(user, bookingId, accept)) {
            is OpResult.Success -> {
                refreshAll()
                showMessage("Booking updated to ${res.data.label}.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun cancelBooking(bookingId: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.updateBookingLifecycle(user, bookingId, BookingStatus.CANCELLED)) {
            is OpResult.Success -> {
                refreshAll()
                showMessage("Booking cancelled.")
            }
            is OpResult.Error -> showMessage(res.message, isError = true)
        }
    }

    fun completeBooking(bookingId: String) {
        val user = uiState.currentUser ?: return
        when (val res = repo.updateBookingLifecycle(user, bookingId, BookingStatus.COMPLETED)) {
            is OpResult.Success -> {
                refreshAll()
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
}
