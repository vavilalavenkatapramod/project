package com.motomove.himachal.domain

import com.motomove.himachal.model.Booking
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.BookingType
import com.motomove.himachal.model.ListingApprovalStatus
import com.motomove.himachal.model.PilotMetrics
import com.motomove.himachal.model.RoomType
import com.motomove.himachal.model.TimeSlot
import com.motomove.himachal.model.User
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.model.VerificationStatus

sealed class RuleResult {
    data object Allowed : RuleResult()
    data class Denied(val reason: String) : RuleResult()
}

/**
 * Enforces MotoMove SRS Section A5 Booking & Inventory Rules, Section 6 Pilot Metrics,
 * and strict per-user Ownership & Access Isolation (one person cannot edit/delete/alter
 * another person's details, listings, or bookings).
 */
object BookingRulesEngine {

    const val PENDING_EXPIRY_MILLIS: Long = 24L * 60L * 60L * 1000L
    const val FRESHNESS_WINDOW_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L

    fun verifyResourceOwnership(
        actor: User,
        actorProviderId: String?,
        ownerProviderId: String,
        resourceLabel: String
    ): RuleResult {
        if (actor.accountStatus != "ACTIVE") {
            return RuleResult.Denied("Account is not active.")
        }
        if (actorProviderId.isNullOrBlank() || actorProviderId != ownerProviderId) {
            return RuleResult.Denied("Access Denied: You cannot edit or remove another person's $resourceLabel.")
        }
        return RuleResult.Allowed
    }

    fun verifyTouristBookingOwner(actor: User, bookingTouristId: String): RuleResult {
        if (actor.userId != bookingTouristId) {
            return RuleResult.Denied("Access Denied: You can only manage or cancel your own bookings.")
        }
        return RuleResult.Allowed
    }

    fun verifyProfileSelfEdit(actor: User, targetUserId: String): RuleResult {
        if (actor.userId != targetUserId) {
            return RuleResult.Denied("Access Denied: You cannot edit another user's profile details.")
        }
        return RuleResult.Allowed
    }

    fun verifyAdminOnly(actor: User, actionLabel: String): RuleResult {
        if (actor.role != UserRole.ADMINISTRATOR) {
            return RuleResult.Denied("Access Denied: Only Administrators can perform $actionLabel.")
        }
        return RuleResult.Allowed
    }

    fun validateMobilityRequest(
        approvalStatus: ListingApprovalStatus,
        providerVerification: VerificationStatus,
        startDay: Long,
        endDay: Long,
        seatsRequested: Int,
        maxCapacity: Int,
        bookingType: BookingType,
        pickupHubCode: String?
    ): RuleResult {
        if (approvalStatus != ListingApprovalStatus.APPROVED) {
            return RuleResult.Denied("Only approved active listings can receive booking requests (SRS A5).")
        }
        if (providerVerification != VerificationStatus.VERIFIED) {
            return RuleResult.Denied("Provider verification must be Verified before accepting bookings.")
        }
        if (endDay < startDay) {
            return RuleResult.Denied("Return date cannot be earlier than pickup date.")
        }
        if (seatsRequested !in 1..maxCapacity) {
            return RuleResult.Denied("Passenger count ($seatsRequested) must be between 1 and $maxCapacity.")
        }
        if (bookingType == BookingType.HUB_PICKUP && pickupHubCode.isNullOrBlank()) {
            return RuleResult.Denied("Please select a Railway Station or Airport for hub pickup.")
        }
        return RuleResult.Allowed
    }

    fun hasMobilityConflict(
        candidate: Booking,
        existingBookings: List<Booking>
    ): Boolean {
        if (candidate.bookingType == BookingType.STAY || candidate.bookingType == BookingType.DINING) return false
        return existingBookings.any { other ->
            other.bookingId != candidate.bookingId &&
                other.listingId == candidate.listingId &&
                other.bookingType != BookingType.STAY &&
                other.bookingType != BookingType.DINING &&
                other.status == BookingStatus.ACCEPTED &&
                datesOverlapInclusive(candidate.startEpochDay, candidate.endEpochDay, other.startEpochDay, other.endEpochDay) &&
                slotsOverlap(candidate.slot, other.slot)
        }
    }

    fun validateStayRequest(
        approvalStatus: ListingApprovalStatus,
        providerVerification: VerificationStatus,
        roomType: RoomType,
        checkInDay: Long,
        checkOutDay: Long,
        guests: Int,
        existingBookings: List<Booking>
    ): RuleResult {
        if (approvalStatus != ListingApprovalStatus.APPROVED) {
            return RuleResult.Denied("Only approved active properties can receive stay requests (SRS A5).")
        }
        if (providerVerification != VerificationStatus.VERIFIED) {
            return RuleResult.Denied("Accommodation host must be Verified before accepting stay requests.")
        }
        if (checkOutDay <= checkInDay) {
            return RuleResult.Denied("Check-out date must be later than check-in date (SRS A5).")
        }
        if (guests !in 1..roomType.occupancy) {
            return RuleResult.Denied("Guest count ($guests) exceeds room capacity (${roomType.occupancy}) (SRS A5).")
        }
        val maxOccupied = maxRoomsBookedAcrossStay(
            roomTypeId = roomType.roomTypeId,
            checkInDay = checkInDay,
            checkOutDay = checkOutDay,
            existingAccepted = existingBookings.filter { it.status == BookingStatus.ACCEPTED }
        )
        if (maxOccupied >= roomType.inventoryCount) {
            return RuleResult.Denied("No rooms left in '${roomType.name}' for the selected dates (FR-19).")
        }
        return RuleResult.Allowed
    }

    fun canAcceptStayBooking(
        candidate: Booking,
        roomType: RoomType,
        existingBookings: List<Booking>
    ): RuleResult {
        val others = existingBookings.filter {
            it.bookingId != candidate.bookingId && it.status == BookingStatus.ACCEPTED
        }
        val maxOccupied = maxRoomsBookedAcrossStay(
            roomTypeId = roomType.roomTypeId,
            checkInDay = candidate.startEpochDay,
            checkOutDay = candidate.endEpochDay,
            existingAccepted = others
        )
        return if (maxOccupied + 1 > roomType.inventoryCount) {
            RuleResult.Denied("Cannot accept: room inventory (${roomType.inventoryCount}) would be exceeded on overlapping dates.")
        } else {
            RuleResult.Allowed
        }
    }

    fun maxRoomsBookedAcrossStay(
        roomTypeId: String,
        checkInDay: Long,
        checkOutDay: Long,
        existingAccepted: List<Booking>
    ): Int {
        var worstNightCount = 0
        var day = checkInDay
        while (day < checkOutDay) {
            val countOnNight = existingAccepted.count { b ->
                b.bookingType == BookingType.STAY &&
                    b.listingId == roomTypeId &&
                    day >= b.startEpochDay &&
                    day < b.endEpochDay
            }
            if (countOnNight > worstNightCount) worstNightCount = countOnNight
            day++
        }
        return worstNightCount
    }

    fun isEligibleForReview(
        booking: Booking,
        touristId: String,
        hasExistingReview: Boolean
    ): RuleResult {
        if (booking.touristId != touristId) {
            return RuleResult.Denied("You can only review your own bookings.")
        }
        if (booking.status != BookingStatus.COMPLETED) {
            return RuleResult.Denied("Reviews can only be submitted after a Completed booking (FR-21).")
        }
        if (hasExistingReview) {
            return RuleResult.Denied("You have already reviewed this booking.")
        }
        return RuleResult.Allowed
    }

    fun isPendingExpired(booking: Booking, nowMillis: Long, ttlMillis: Long = PENDING_EXPIRY_MILLIS): Boolean {
        return booking.status == BookingStatus.PENDING && (nowMillis - booking.createdAt) > ttlMillis
    }

    fun computePilotMetrics(
        approvedVehicles: Int,
        approvedDrivers: Int,
        approvedProperties: Int,
        listingUpdateTimestamps: List<Long>,
        bookings: List<Booking>,
        nowMillis: Long
    ): PilotMetrics {
        val freshCount = listingUpdateTimestamps.count { (nowMillis - it) <= FRESHNESS_WINDOW_MILLIS }
        val freshnessPercent = if (listingUpdateTimestamps.isEmpty()) 0 else (freshCount * 100) / listingUpdateTimestamps.size

        val responseDurationsMins = bookings.mapNotNull { b ->
            b.respondedAt?.let { resp -> ((resp - b.createdAt) / 60_000L).coerceAtLeast(1L) }
        }.sorted()
        val medianResp = if (responseDurationsMins.isEmpty()) 0L else responseDurationsMins[responseDurationsMins.size / 2]

        val acceptedOrCompleted = bookings.count { it.status == BookingStatus.ACCEPTED || it.status == BookingStatus.COMPLETED }
        val completed = bookings.count { it.status == BookingStatus.COMPLETED }
        val completionPercent = if (acceptedOrCompleted == 0) 0 else (completed * 100) / acceptedOrCompleted

        return PilotMetrics(
            approvedVehicles = approvedVehicles,
            approvedDrivers = approvedDrivers,
            approvedProperties = approvedProperties,
            freshnessPercent = freshnessPercent,
            medianResponseMinutes = medianResp,
            bookingCompletionPercent = completionPercent
        )
    }

    private fun datesOverlapInclusive(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean {
        return aStart <= bEnd && bStart <= aEnd
    }

    private fun slotsOverlap(a: TimeSlot, b: TimeSlot): Boolean {
        return a == TimeSlot.FULL_DAY || b == TimeSlot.FULL_DAY || a == b
    }
}
