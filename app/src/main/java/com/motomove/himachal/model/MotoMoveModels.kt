package com.motomove.himachal.model

enum class UserRole(val displayName: String, val shortLabel: String) {
    TOURIST("Tourist / Traveller", "Tourist"),
    VEHICLE_OWNER("Vehicle Owner", "Owner"),
    LOCAL_DRIVER("Verified Local Driver", "Driver"),
    ACCOMMODATION_PROVIDER("Homestay / Hotel Host", "Stay Host"),
    ADMINISTRATOR("Platform Administrator", "Admin")
}

enum class VerificationStatus(val label: String) {
    PENDING("Pending Review"),
    VERIFIED("Verified"),
    REJECTED("Rejected"),
    EXPIRED("Expired")
}

enum class ListingApprovalStatus(val label: String) {
    PENDING_REVIEW("Pending Approval"),
    APPROVED("Approved & Live"),
    SUSPENDED("Suspended"),
    CORRECTION_REQUESTED("Correction Requested")
}

enum class RentalMode(val label: String) {
    WITH_DRIVER("With Driver"),
    SELF_DRIVE("Self-drive"),
    BOTH("With Driver or Self-drive")
}

enum class BookingType(val label: String) {
    HUB_PICKUP("Station / Airport Pickup"),
    LOCAL_MOBILITY("Vehicle / Driver"),
    STAY("Stay"),
    DINING("Food")
}

enum class BookingStatus(val label: String) {
    PENDING("PENDING"),
    ACCEPTED("ACCEPTED"),
    REJECTED("REJECTED"),
    CANCELLED("CANCELLED"),
    COMPLETED("COMPLETED"),
    EXPIRED("EXPIRED")
}

enum class TimeSlot(val label: String) {
    MORNING_06_12("06:00 – 12:00"),
    AFTERNOON_12_18("12:00 – 18:00"),
    EVENING_18_24("18:00 – 24:00"),
    FULL_DAY("Full Day")
}

enum class HubType(val label: String) {
    RAILWAY_STATION("Railway Station"),
    AIRPORT("Airport")
}

enum class NotificationCategory(val label: String) {
    TRIPS("Trips"),
    DEALS("Deals"),
    AI_TIPS("AI Tips")
}

data class TransitHub(
    val code: String,
    val name: String,
    val type: HubType,
    val city: String,
    val meetingPointHint: String,
    val servedClusters: List<String>
)

data class DestinationCluster(
    val id: String,
    val name: String,
    val shortName: String,
    val district: String,
    val elevationRange: String,
    val pilotStage: String,
    val transportGapNote: String,
    val roadAdvisory: String,
    val nearestHubCodes: List<String>
)

data class User(
    val userId: String,
    val name: String,
    val contact: String,
    val passwordHash: String,
    val role: UserRole,
    val accountStatus: String = "ACTIVE"
)

data class ProviderProfile(
    val providerId: String,
    val userId: String,
    val displayName: String,
    val providerType: UserRole,
    val verificationStatus: VerificationStatus,
    val homeCluster: String,
    val lastUpdatedAt: Long
)

data class VehicleListing(
    val vehicleId: String,
    val providerId: String,
    val ownerName: String,
    val title: String,
    val vehicleType: String,
    val capacity: Int,
    val transmissionFuel: String,
    val serviceArea: String,
    val pickupHubs: List<String>,
    val rentalMode: RentalMode,
    val rateInr: Int,
    val rateBasis: String,
    val cancellationTerms: String,
    val approvalStatus: ListingApprovalStatus,
    val providerVerification: VerificationStatus,
    val lastUpdatedAt: Long,
    val isAvailableNow: Boolean = true,
    val liveStatusNote: String = "Available for pickup"
)

data class DriverProfile(
    val driverId: String,
    val providerId: String,
    val name: String,
    val languages: String,
    val experienceYears: Int,
    val serviceArea: String,
    val pickupHubs: List<String>,
    val ratePerDayInr: Int,
    val availabilitySummary: String,
    val cancellationTerms: String,
    val verificationStatus: VerificationStatus,
    val approvalStatus: ListingApprovalStatus,
    val lastUpdatedAt: Long,
    val isAvailableNow: Boolean = true
)

data class VerificationDocument(
    val documentId: String,
    val providerId: String,
    val providerName: String,
    val documentType: String,
    val fileReference: String,
    val reviewStatus: VerificationStatus,
    val expiryDate: String,
    val reviewerNotes: String
)

data class PropertyListing(
    val propertyId: String,
    val providerId: String,
    val hostName: String,
    val name: String,
    val propertyType: String,
    val locationCluster: String,
    val nearestHubCode: String,
    val amenities: String,
    val policies: String,
    val registrationNumber: String,
    val approvalStatus: ListingApprovalStatus,
    val providerVerification: VerificationStatus,
    val lastUpdatedAt: Long
)

data class RoomType(
    val roomTypeId: String,
    val propertyId: String,
    val name: String,
    val occupancy: Int,
    val ratePerNightInr: Int,
    val inventoryCount: Int
)

data class DiningSpot(
    val spotId: String,
    val providerId: String,
    val name: String,
    val locationCluster: String,
    val specialty: String,
    val dishes: String,
    val ambience: String,
    val avgCostInr: Int,
    val isFssaiVerified: Boolean,
    val rating: Double,
    val isOpenNow: Boolean = true,
    val liveStatusNote: String = "Tables open now"
)

data class TravelReel(
    val reelId: String,
    val authorName: String,
    val authorHandle: String,
    val clusterName: String,
    val hubCode: String,
    val title: String,
    val caption: String,
    val tagLabel: String,
    val linkedCategory: Int, // 0=Vehicles, 1=Drivers, 2=Stays
    val priceHint: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long,
    val likeCount: Int,
    val isLikedByMe: Boolean,
    val isSavedByMe: Boolean
)

data class Booking(
    val bookingId: String,
    val touristId: String,
    val touristName: String,
    val providerId: String,
    val listingId: String,
    val listingTitle: String,
    val bookingType: BookingType,
    val pickupHubCode: String?,
    val destinationCluster: String,
    val trainOrFlightNo: String?,
    val pickupNotes: String,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val slot: TimeSlot,
    val guestOrSeatCount: Int,
    val status: BookingStatus,
    val quotedPriceInr: Int,
    val cancellationTerms: String,
    val createdAt: Long,
    val respondedAt: Long? = null
)

data class Review(
    val reviewId: String,
    val bookingId: String,
    val touristId: String,
    val touristName: String,
    val listingId: String,
    val rating: Int,
    val comment: String,
    val moderationStatus: String = "VISIBLE",
    val createdAt: Long
)

data class AppNotification(
    val notificationId: String,
    val userId: String,
    val category: NotificationCategory,
    val eventType: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: Long
)

data class AuditLog(
    val auditId: String,
    val actorId: String,
    val actorRole: String,
    val action: String,
    val targetId: String,
    val details: String,
    val timestamp: Long
)

data class PilotMetrics(
    val approvedVehicles: Int,
    val approvedDrivers: Int,
    val approvedProperties: Int,
    val freshnessPercent: Int,
    val medianResponseMinutes: Long,
    val bookingCompletionPercent: Int
)
