package com.motomove.himachal.live

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
import com.motomove.himachal.model.VehicleListing
import com.motomove.himachal.model.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class LiveStateSnapshot(
    val vehicles: List<VehicleListing>,
    val drivers: List<DriverProfile>,
    val properties: List<PropertyListing>,
    val roomTypes: List<RoomType>,
    val diningSpots: List<DiningSpot>,
    val bookings: List<Booking>,
    val notifications: List<AppNotification>
)

data class LiveSyncEvent(
    val type: String,
    val summary: String,
    val targetUserId: String?
)

/**
 * Real-time multi-device sync client using Server-Sent Events (SSE) + REST
 * against MotoMoveServer, with graceful offline fallback.
 */
class LiveSyncClient {

    suspend fun fetchSnapshot(baseUrl: String, userId: String?): LiveStateSnapshot? = withContext(Dispatchers.IO) {
        val clean = baseUrl.trim().trimEnd('/')
        if (clean.isBlank()) return@withContext null
        return@withContext runCatching {
            val url = URL("$clean/api/state?userId=${userId ?: ""}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 5000
            }
            if (conn.responseCode !in 200..299) return@runCatching null
            val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            parseSnapshot(JSONObject(text))
        }.getOrNull()
    }

    fun observeEvents(baseUrl: String): Flow<LiveSyncEvent?> = flow {
        val clean = baseUrl.trim().trimEnd('/')
        while (currentCoroutineContext().isActive && clean.isNotBlank()) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$clean/api/events").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 60_000
                    setRequestProperty("Accept", "text/event-stream")
                }
                if (conn.responseCode in 200..299) {
                    emit(LiveSyncEvent("CONNECTED", "Connected to Live Server", null))
                    val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    var eventName = ""
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("event:")) {
                            eventName = line.removePrefix("event:").trim()
                        } else if (line.startsWith("data:") && eventName == "sync") {
                            val json = JSONObject(line.removePrefix("data:").trim())
                            emit(
                                LiveSyncEvent(
                                    type = json.optString("type", "SYNC"),
                                    summary = json.optString("summary", "Live update received"),
                                    targetUserId = json.optString("targetUserId").takeIf { it.isNotBlank() && it != "null" }
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                emit(null)
            } finally {
                conn?.disconnect()
            }
            delay(3000)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun patchVehicleLive(
        baseUrl: String,
        actorUserId: String,
        vehicleId: String,
        isAvailableNow: Boolean,
        rateInr: Int,
        liveNote: String
    ): Result<Unit> = sendMutation(
        baseUrl = baseUrl,
        method = "PATCH",
        path = "/api/vehicles/$vehicleId/live",
        actorUserId = actorUserId,
        body = JSONObject().apply {
            put("isAvailableNow", isAvailableNow)
            put("rateInr", rateInr)
            put("liveStatusNote", liveNote)
        }
    )

    suspend fun patchDriverLive(
        baseUrl: String,
        actorUserId: String,
        driverId: String,
        isAvailableNow: Boolean,
        ratePerDayInr: Int,
        summary: String
    ): Result<Unit> = sendMutation(
        baseUrl = baseUrl,
        method = "PATCH",
        path = "/api/drivers/$driverId/live",
        actorUserId = actorUserId,
        body = JSONObject().apply {
            put("isAvailableNow", isAvailableNow)
            put("ratePerDayInr", ratePerDayInr)
            put("availabilitySummary", summary)
        }
    )

    suspend fun patchRoomLive(
        baseUrl: String,
        actorUserId: String,
        roomTypeId: String,
        inventoryCount: Int,
        ratePerNightInr: Int
    ): Result<Unit> = sendMutation(
        baseUrl = baseUrl,
        method = "PATCH",
        path = "/api/rooms/$roomTypeId/live",
        actorUserId = actorUserId,
        body = JSONObject().apply {
            put("inventoryCount", inventoryCount)
            put("ratePerNightInr", ratePerNightInr)
        }
    )

    suspend fun patchDiningLive(
        baseUrl: String,
        actorUserId: String,
        spotId: String,
        isOpenNow: Boolean,
        liveNote: String,
        avgCostInr: Int
    ): Result<Unit> = sendMutation(
        baseUrl = baseUrl,
        method = "PATCH",
        path = "/api/dining/$spotId/live",
        actorUserId = actorUserId,
        body = JSONObject().apply {
            put("isOpenNow", isOpenNow)
            put("liveStatusNote", liveNote)
            put("avgCostInr", avgCostInr)
        }
    )

    suspend fun postBooking(baseUrl: String, actorUserId: String, booking: Booking): Result<Unit> =
        sendMutation(
            baseUrl = baseUrl,
            method = "POST",
            path = "/api/bookings",
            actorUserId = actorUserId,
            body = JSONObject().apply {
                put("providerId", booking.providerId)
                put("listingId", booking.listingId)
                put("listingTitle", booking.listingTitle)
                put("bookingType", booking.bookingType.name)
                put("pickupHubCode", booking.pickupHubCode)
                put("destinationCluster", booking.destinationCluster)
                put("trainOrFlightNo", booking.trainOrFlightNo)
                put("pickupNotes", booking.pickupNotes)
                put("startEpochDay", booking.startEpochDay)
                put("endEpochDay", booking.endEpochDay)
                put("slot", booking.slot.name)
                put("guestOrSeatCount", booking.guestOrSeatCount)
                put("quotedPriceInr", booking.quotedPriceInr)
                put("cancellationTerms", booking.cancellationTerms)
            }
        )

    suspend fun postRespondBooking(baseUrl: String, actorUserId: String, bookingId: String, accept: Boolean): Result<Unit> =
        sendMutation(
            baseUrl = baseUrl,
            method = "POST",
            path = "/api/bookings/$bookingId/respond",
            actorUserId = actorUserId,
            body = JSONObject().put("accept", accept)
        )

    private suspend fun sendMutation(
        baseUrl: String,
        method: String,
        path: String,
        actorUserId: String,
        body: JSONObject
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val clean = baseUrl.trim().trimEnd('/')
        if (clean.isBlank()) return@withContext Result.failure(IllegalStateException("Offline"))
        return@withContext runCatching {
            val conn = (URL("$clean$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 3500
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-user-id", actorUserId)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errText = runCatching { BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() } }.getOrDefault("")
                throw IllegalStateException("Server HTTP $code: $errText")
            }
        }
    }

    private fun parseSnapshot(json: JSONObject): LiveStateSnapshot {
        val vehicles = json.optJSONArray("vehicles").toList { o ->
            VehicleListing(
                vehicleId = o.getString("vehicleId"),
                providerId = o.getString("providerId"),
                ownerName = o.getString("ownerName"),
                title = o.getString("title"),
                vehicleType = o.getString("vehicleType"),
                capacity = o.getInt("capacity"),
                transmissionFuel = o.getString("transmissionFuel"),
                serviceArea = o.getString("serviceArea"),
                pickupHubs = o.optJSONArray("pickupHubs").toStringList(),
                rentalMode = RentalMode.valueOf(o.getString("rentalMode")),
                rateInr = o.getInt("rateInr"),
                rateBasis = o.getString("rateBasis"),
                cancellationTerms = o.getString("cancellationTerms"),
                approvalStatus = ListingApprovalStatus.valueOf(o.getString("approvalStatus")),
                providerVerification = VerificationStatus.valueOf(o.getString("providerVerification")),
                lastUpdatedAt = o.getLong("lastUpdatedAt"),
                isAvailableNow = o.optBoolean("isAvailableNow", true),
                liveStatusNote = o.optString("liveStatusNote", "Available now")
            )
        }
        val drivers = json.optJSONArray("drivers").toList { o ->
            DriverProfile(
                driverId = o.getString("driverId"),
                providerId = o.getString("providerId"),
                name = o.getString("name"),
                languages = o.getString("languages"),
                experienceYears = o.getInt("experienceYears"),
                serviceArea = o.getString("serviceArea"),
                pickupHubs = o.optJSONArray("pickupHubs").toStringList(),
                ratePerDayInr = o.getInt("ratePerDayInr"),
                availabilitySummary = o.getString("availabilitySummary"),
                cancellationTerms = o.getString("cancellationTerms"),
                verificationStatus = VerificationStatus.valueOf(o.getString("verificationStatus")),
                approvalStatus = ListingApprovalStatus.valueOf(o.getString("approvalStatus")),
                lastUpdatedAt = o.getLong("lastUpdatedAt"),
                isAvailableNow = o.optBoolean("isAvailableNow", true)
            )
        }
        val properties = json.optJSONArray("properties").toList { o ->
            PropertyListing(
                propertyId = o.getString("propertyId"),
                providerId = o.getString("providerId"),
                hostName = o.getString("hostName"),
                name = o.getString("name"),
                propertyType = o.getString("propertyType"),
                locationCluster = o.getString("locationCluster"),
                nearestHubCode = o.getString("nearestHubCode"),
                amenities = o.getString("amenities"),
                policies = o.getString("policies"),
                registrationNumber = o.getString("registrationNumber"),
                approvalStatus = ListingApprovalStatus.valueOf(o.getString("approvalStatus")),
                providerVerification = VerificationStatus.valueOf(o.getString("providerVerification")),
                lastUpdatedAt = o.getLong("lastUpdatedAt")
            )
        }
        val rooms = json.optJSONArray("roomTypes").toList { o ->
            RoomType(
                roomTypeId = o.getString("roomTypeId"),
                propertyId = o.getString("propertyId"),
                name = o.getString("name"),
                occupancy = o.getInt("occupancy"),
                ratePerNightInr = o.getInt("ratePerNightInr"),
                inventoryCount = o.getInt("inventoryCount")
            )
        }
        val dining = json.optJSONArray("diningSpots").toList { o ->
            DiningSpot(
                spotId = o.getString("spotId"),
                providerId = o.getString("providerId"),
                name = o.getString("name"),
                locationCluster = o.getString("locationCluster"),
                specialty = o.getString("specialty"),
                dishes = o.getString("dishes"),
                ambience = o.getString("ambience"),
                avgCostInr = o.getInt("avgCostInr"),
                isFssaiVerified = o.optBoolean("isFssaiVerified", true),
                rating = o.optDouble("rating", 4.8),
                isOpenNow = o.optBoolean("isOpenNow", true),
                liveStatusNote = o.optString("liveStatusNote", "Tables open now")
            )
        }
        val bookings = json.optJSONArray("bookings").toList { o ->
            Booking(
                bookingId = o.getString("bookingId"),
                touristId = o.getString("touristId"),
                touristName = o.getString("touristName"),
                providerId = o.getString("providerId"),
                listingId = o.getString("listingId"),
                listingTitle = o.getString("listingTitle"),
                bookingType = BookingType.valueOf(o.getString("bookingType")),
                pickupHubCode = o.optString("pickupHubCode").takeIf { it.isNotBlank() && it != "null" },
                destinationCluster = o.getString("destinationCluster"),
                trainOrFlightNo = o.optString("trainOrFlightNo").takeIf { it.isNotBlank() && it != "null" },
                pickupNotes = o.optString("pickupNotes"),
                startEpochDay = o.getLong("startEpochDay"),
                endEpochDay = o.getLong("endEpochDay"),
                slot = TimeSlot.valueOf(o.optString("slot", "MORNING_06_12")),
                guestOrSeatCount = o.optInt("guestOrSeatCount", 1),
                status = BookingStatus.valueOf(o.getString("status")),
                quotedPriceInr = o.optInt("quotedPriceInr", 0),
                cancellationTerms = o.optString("cancellationTerms"),
                createdAt = o.optLong("createdAt"),
                respondedAt = if (o.isNull("respondedAt")) null else o.optLong("respondedAt")
            )
        }
        val notifications = json.optJSONArray("notifications").toList { o ->
            AppNotification(
                notificationId = o.getString("notificationId"),
                userId = o.getString("userId"),
                category = runCatching { NotificationCategory.valueOf(o.optString("category")) }.getOrDefault(NotificationCategory.TRIPS),
                eventType = o.optString("eventType"),
                message = o.optString("message"),
                isRead = o.optBoolean("isRead", false),
                createdAt = o.optLong("createdAt")
            )
        }
        return LiveStateSnapshot(vehicles, drivers, properties, rooms, dining, bookings, notifications)
    }

    private fun <T> JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.let { add(mapper(it)) }
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add(optString(i))
        }
    }
}
