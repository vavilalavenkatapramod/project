package com.motomove.himachal.ai

import com.motomove.himachal.data.HimachalCatalog
import com.motomove.himachal.model.DestinationCluster
import com.motomove.himachal.model.DiningSpot
import com.motomove.himachal.model.PropertyListing
import com.motomove.himachal.model.RoomType
import com.motomove.himachal.model.TransitHub
import com.motomove.himachal.model.VehicleListing

data class MotoAiPickupPlan(
    val recommendedVehicleClass: String,
    val suggestedRentalMode: String,
    val pickupBufferMinutes: Int,
    val estimatedDriveHours: String,
    val hubMeetingProtocol: String,
    val lastMileGapAdvice: String,
    val roadAndWeatherAdvisory: String,
    val matchedVehicles: List<VehicleListing>,
    val checklist: List<String>
)

data class StayComparisonResult(
    val stayA: PropertyListing,
    val stayB: PropertyListing,
    val rateA: Int,
    val rateB: Int,
    val verdict: String,
    val highlights: List<String>
)

data class FoodMatchResult(
    val spot: DiningSpot,
    val reason: String
)

/**
 * Built-in on-device MotoAI Assistant (zero external API keys, works 100% offline in
 * low-bandwidth Himachal valleys).
 */
object MotoAiEngine {

    fun planHubPickup(
        hub: TransitHub,
        cluster: DestinationCluster,
        passengerCount: Int,
        luggageNote: String,
        availableVehicles: List<VehicleListing>
    ): MotoAiPickupPlan {
        val highAltitudeOrSnow = cluster.id in setOf("kaza_spiti", "sangla_chitkul", "shimla_narkanda")
        val heavyLuggage = luggageNote.contains("big", ignoreCase = true) ||
            luggageNote.contains("rucksack", ignoreCase = true) ||
            passengerCount >= 4

        val recommendedClass = when {
            passengerCount > 4 -> "6-Seater 4x4 SUV (Scorpio-N / Innova)"
            highAltitudeOrSnow || heavyLuggage -> "4x4 SUV (Mahindra Scorpio-N / Maruti Jimny)"
            passengerCount <= 2 -> "Compact 4x4 SUV or Adventure Motorcycle"
            else -> "Compact Hill SUV / Crossover"
        }

        val rentalMode = if (cluster.id in setOf("kaza_spiti", "sangla_chitkul", "kasol_tosh")) {
            "With Verified Local Hill Driver (Recommended)"
        } else {
            "With Local Driver or Self-Drive"
        }

        val bufferMins = if (hub.type.name == "AIRPORT") 45 else 30
        val driveEst = estimateDriveTime(hub.code, cluster.id)

        val matched = availableVehicles.filter { v ->
            v.pickupHubs.contains(hub.code) &&
                v.capacity >= passengerCount &&
                v.serviceArea.contains(cluster.name.substringBefore("–"), ignoreCase = true)
        }.ifEmpty {
            availableVehicles.filter { it.pickupHubs.contains(hub.code) && it.capacity >= passengerCount }
        }.take(3)

        val checklist = buildList {
            add("Meet driver at: ${hub.meetingPointHint}")
            add("Share train/flight number so driver tracks arrival delays (up to 90 min wait buffer)")
            if (highAltitudeOrSnow) add("Carry thermal layers, ID proof for inner-line/hill checkpoints, and offline Booking ID")
            if (cluster.id == "jibhi_tirthan" || cluster.id == "kasol_tosh") {
                add("Avoid late-night last-mile travel beyond Aut / Bhuntar without a pre-confirmed local cab")
            }
        }

        return MotoAiPickupPlan(
            recommendedVehicleClass = recommendedClass,
            suggestedRentalMode = rentalMode,
            pickupBufferMinutes = bufferMins,
            estimatedDriveHours = driveEst,
            hubMeetingProtocol = "Wait at ${hub.meetingPointHint}. Verify driver's MotoMove Verified badge & plate before boarding.",
            lastMileGapAdvice = cluster.transportGapNote,
            roadAndWeatherAdvisory = cluster.roadAdvisory,
            matchedVehicles = matched,
            checklist = checklist
        )
    }

    fun compareStaysSideBySide(
        stayA: PropertyListing,
        stayB: PropertyListing,
        roomsByProperty: Map<String, List<RoomType>>
    ): StayComparisonResult {
        val rateA = roomsByProperty[stayA.propertyId]?.minOfOrNull { it.ratePerNightInr } ?: 2000
        val rateB = roomsByProperty[stayB.propertyId]?.minOfOrNull { it.ratePerNightInr } ?: 2000
        val hubA = HimachalCatalog.hubByCode(stayA.nearestHubCode)?.name ?: stayA.nearestHubCode
        val hubB = HimachalCatalog.hubByCode(stayB.nearestHubCode)?.name ?: stayB.nearestHubCode

        val cheaper = if (rateA <= rateB) stayA.name else stayB.name
        val verdict = "$cheaper offers the lower entry tariff (₹${minOf(rateA, rateB)}/night). " +
            "Both carry verified HP Tourism registrations (${stayA.registrationNumber} vs ${stayB.registrationNumber})."

        val highlights = listOf(
            "Tariff: ${stayA.name} from ₹$rateA/night vs ${stayB.name} from ₹$rateB/night",
            "Nearest Transit Hub: ${stayA.name} -> $hubA | ${stayB.name} -> $hubB",
            "Amenities (${stayA.name}): ${stayA.amenities}",
            "Amenities (${stayB.name}): ${stayB.amenities}"
        )

        return StayComparisonResult(
            stayA = stayA,
            stayB = stayB,
            rateA = rateA,
            rateB = rateB,
            verdict = verdict,
            highlights = highlights
        )
    }

    fun recommendFoodFaceOff(
        clusterFilter: String,
        spots: List<DiningSpot>
    ): List<FoodMatchResult> {
        val pool = if (clusterFilter == "ALL") spots else spots.filter {
            it.locationCluster.contains(clusterFilter, ignoreCase = true)
        }
        return pool.sortedByDescending { it.rating }.take(3).map { spot ->
            FoodMatchResult(
                spot = spot,
                reason = "Top-rated (${spot.rating}★) in ${spot.locationCluster} • Known for ${spot.specialty} (Avg ₹${spot.avgCostInr}/person)"
            )
        }
    }

    fun answerTravelQuestion(
        question: String,
        hub: TransitHub,
        cluster: DestinationCluster
    ): String {
        val q = question.lowercase()
        return when {
            "train" in q || "kalka" in q || "shimla" in q || "toy" in q ->
                "Toy Train & Railway Pickups (${hub.name}): Drivers wait at ${hub.meetingPointHint}. " +
                    "Kalka Shatabdi arrivals often connect to ${cluster.name} in ${estimateDriveTime("KLK", cluster.id)}. " +
                    "Always include your train number in booking notes."
            "flight" in q || "airport" in q || "bhuntar" in q || "kangra" in q || "chandigarh" in q ->
                "Airport Arrival Tip (${hub.code}): Mountain flights can shift due to valley cloud cover. " +
                    "Book a Verified driver with 90-minute arrival buffer at ${hub.meetingPointHint}."
            "snow" in q || "4x4" in q || "spiti" in q || "kaza" in q || "jalori" in q ->
                "Hill Terrain Advisory (${cluster.name}): ${cluster.roadAdvisory} " +
                    "In winter/shoulder months, pick a 4x4 SUV with a Verified local hill driver."
            "food" in q || "trout" in q || "siddu" in q || "eat" in q ->
                "Local Food Tip: Try fresh Himalayan Rainbow Trout in Jibhi–Tirthan & Sangla, " +
                    "and hot Pahadi Siddu with walnut chutney in Kasol–Manikaran. Check the Food tab for FSSAI-verified spots!"
            "permit" in q || "id" in q || "safe" in q ->
                "Safety & Verification: Every MotoMove driver & homestay displays an Admin-verified badge and HP registration. " +
                    "Carry original Govt Photo ID for valley checkpoints and homestay check-in."
            else ->
                "MotoAI (${hub.code} → ${cluster.name}): ${cluster.transportGapNote} ${cluster.roadAdvisory} " +
                    "Estimated drive: ${estimateDriveTime(hub.code, cluster.id)}. Meeting point: ${hub.meetingPointHint}."
        }
    }

    private fun estimateDriveTime(hubCode: String, clusterId: String): String = when {
        hubCode == "KUU" && clusterId == "kasol_tosh" -> "1.5 – 2.5 hrs"
        hubCode == "KUU" && clusterId == "jibhi_tirthan" -> "2.0 – 2.5 hrs"
        hubCode == "KUU" && clusterId == "kaza_spiti" -> "6.5 – 8.5 hrs (via Atal Tunnel)"
        hubCode == "KLK" && clusterId == "shimla_narkanda" -> "3.5 – 4.5 hrs"
        hubCode == "KLK" && clusterId == "sangla_chitkul" -> "8.0 – 10.0 hrs"
        hubCode == "SML" && clusterId == "shimla_narkanda" -> "2.0 – 2.5 hrs"
        hubCode == "DHM" && clusterId == "bir_barot" -> "2.0 – 3.0 hrs"
        hubCode == "JDNX" && clusterId == "bir_barot" -> "1.0 – 1.5 hrs"
        else -> "3.5 – 6.0 hrs"
    }
}
