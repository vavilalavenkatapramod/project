package com.motomove.himachal

import com.motomove.himachal.ai.MotoAiEngine
import com.motomove.himachal.data.HimachalCatalog
import com.motomove.himachal.domain.BookingRulesEngine
import com.motomove.himachal.domain.RuleResult
import com.motomove.himachal.model.Booking
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.BookingType
import com.motomove.himachal.model.ListingApprovalStatus
import com.motomove.himachal.model.RoomType
import com.motomove.himachal.model.TimeSlot
import com.motomove.himachal.model.User
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.model.VerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingRulesEngineTest {

    @Test
    fun unapprovedListingCannotReceiveBookings() {
        val result = BookingRulesEngine.validateMobilityRequest(
            approvalStatus = ListingApprovalStatus.PENDING_REVIEW,
            providerVerification = VerificationStatus.VERIFIED,
            startDay = 20000L,
            endDay = 20001L,
            seatsRequested = 2,
            maxCapacity = 4,
            bookingType = BookingType.HUB_PICKUP,
            pickupHubCode = "KUU"
        )
        assertTrue(result is RuleResult.Denied)
    }

    @Test
    fun hubPickupRequiresStationOrAirportCode() {
        val result = BookingRulesEngine.validateMobilityRequest(
            approvalStatus = ListingApprovalStatus.APPROVED,
            providerVerification = VerificationStatus.VERIFIED,
            startDay = 20000L,
            endDay = 20000L,
            seatsRequested = 2,
            maxCapacity = 4,
            bookingType = BookingType.HUB_PICKUP,
            pickupHubCode = null
        )
        assertTrue(result is RuleResult.Denied)
    }

    @Test
    fun overlappingAcceptedVehicleBookingIsBlocked() {
        val existing = sampleBooking("b1", "veh_1", BookingType.HUB_PICKUP, 20010L, 20012L, TimeSlot.MORNING_06_12, BookingStatus.ACCEPTED)
        val candidateOverlap = sampleBooking("b2", "veh_1", BookingType.LOCAL_MOBILITY, 20011L, 20011L, TimeSlot.FULL_DAY, BookingStatus.PENDING)
        val candidateNonOverlap = sampleBooking("b3", "veh_1", BookingType.HUB_PICKUP, 20011L, 20011L, TimeSlot.EVENING_18_24, BookingStatus.PENDING)

        assertTrue(BookingRulesEngine.hasMobilityConflict(candidateOverlap, listOf(existing)))
        assertFalse(BookingRulesEngine.hasMobilityConflict(candidateNonOverlap, listOf(existing)))
    }

    @Test
    fun stayValidationRejectsInvalidDatesCapacityAndOverbooking() {
        val room = RoomType("room_1", "prop_1", "Deodar Attic", occupancy = 3, ratePerNightInr = 2200, inventoryCount = 1)
        val existing = sampleBooking("s1", "room_1", BookingType.STAY, 20010L, 20013L, TimeSlot.FULL_DAY, BookingStatus.ACCEPTED)

        val badCheckout = BookingRulesEngine.validateStayRequest(
            ListingApprovalStatus.APPROVED, VerificationStatus.VERIFIED, room, 20015L, 20015L, 2, emptyList()
        )
        assertTrue(badCheckout is RuleResult.Denied)

        val overCapacity = BookingRulesEngine.validateStayRequest(
            ListingApprovalStatus.APPROVED, VerificationStatus.VERIFIED, room, 20015L, 20017L, 4, emptyList()
        )
        assertTrue(overCapacity is RuleResult.Denied)

        val overBooked = BookingRulesEngine.validateStayRequest(
            ListingApprovalStatus.APPROVED, VerificationStatus.VERIFIED, room, 20012L, 20014L, 2, listOf(existing)
        )
        assertTrue(overBooked is RuleResult.Denied)

        val backToBackAllowed = BookingRulesEngine.validateStayRequest(
            ListingApprovalStatus.APPROVED, VerificationStatus.VERIFIED, room, 20013L, 20015L, 2, listOf(existing)
        )
        assertEquals(RuleResult.Allowed, backToBackAllowed)
    }

    @Test
    fun reviewAllowedOnlyAfterCompletedBooking() {
        val accepted = sampleBooking("b1", "veh_1", BookingType.HUB_PICKUP, 20000L, 20000L, TimeSlot.MORNING_06_12, BookingStatus.ACCEPTED)
        val completed = accepted.copy(status = BookingStatus.COMPLETED)

        assertTrue(BookingRulesEngine.isEligibleForReview(accepted, "u_tourist_1", false) is RuleResult.Denied)
        assertEquals(RuleResult.Allowed, BookingRulesEngine.isEligibleForReview(completed, "u_tourist_1", false))
        assertTrue(BookingRulesEngine.isEligibleForReview(completed, "u_tourist_1", true) is RuleResult.Denied)
    }

    @Test
    fun strictOwnershipPreventsEditingOthersBelongingsAndProfiles() {
        val ownerA = User("u_owner_1", "Owner A", "a@motomove.in", "", UserRole.VEHICLE_OWNER)
        val ownerB = User("u_owner_2", "Owner B", "b@motomove.in", "", UserRole.VEHICLE_OWNER)
        val touristA = User("u_t1", "Tourist A", "t1@motomove.in", "", UserRole.TOURIST)
        val touristB = User("u_t2", "Tourist B", "t2@motomove.in", "", UserRole.TOURIST)

        // Owner B cannot edit Owner A's vehicle
        val editDenied = BookingRulesEngine.verifyResourceOwnership(ownerB, "p_owner_2", "p_owner_1", "vehicle")
        assertTrue(editDenied is RuleResult.Denied)

        // Owner A can edit Owner A's vehicle
        assertEquals(RuleResult.Allowed, BookingRulesEngine.verifyResourceOwnership(ownerA, "p_owner_1", "p_owner_1", "vehicle"))

        // Tourist B cannot cancel Tourist A's booking
        assertTrue(BookingRulesEngine.verifyTouristBookingOwner(touristB, touristA.userId) is RuleResult.Denied)

        // Tourist B cannot edit Tourist A's profile
        assertTrue(BookingRulesEngine.verifyProfileSelfEdit(touristB, touristA.userId) is RuleResult.Denied)

        // Non-admin cannot access admin moderation
        assertTrue(BookingRulesEngine.verifyAdminOnly(ownerA, "document verification") is RuleResult.Denied)
    }

    @Test
    fun motoAiEngineGeneratesHubPlanWithoutExternalApi() {
        val hub = HimachalCatalog.hubByCode("KUU")!!
        val cluster = HimachalCatalog.destinationClusters.first { it.id == "kaza_spiti" }
        val plan = MotoAiEngine.planHubPickup(hub, cluster, 3, "3 rucksacks", emptyList())
        assertTrue(plan.recommendedVehicleClass.contains("4x4"))
        assertTrue(plan.checklist.isNotEmpty())
    }

    private fun sampleBooking(
        id: String,
        listingId: String,
        type: BookingType,
        start: Long,
        end: Long,
        slot: TimeSlot,
        status: BookingStatus
    ) = Booking(
        bookingId = id,
        touristId = "u_tourist_1",
        touristName = "Aarav",
        providerId = "p_owner_1",
        listingId = listingId,
        listingTitle = "Test Listing",
        bookingType = type,
        pickupHubCode = "KUU",
        destinationCluster = "Jibhi–Tirthan Valley",
        trainOrFlightNo = "9I-805",
        pickupNotes = "Gate 1",
        startEpochDay = start,
        endEpochDay = end,
        slot = slot,
        guestOrSeatCount = 2,
        status = status,
        quotedPriceInr = 3000,
        cancellationTerms = "Standard",
        createdAt = 1000L
    )
}
