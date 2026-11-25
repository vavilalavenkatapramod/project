package com.motomove.himachal.data

import com.motomove.himachal.model.DestinationCluster
import com.motomove.himachal.model.HubType
import com.motomove.himachal.model.TransitHub

/**
 * Curated Himachal Pradesh transit hubs (Railway Stations & Airports) and underserved
 * last-mile destination clusters aligned with the MotoMove SRS (Section 2 & 7).
 */
object HimachalCatalog {

    val transitHubs: List<TransitHub> = listOf(
        TransitHub(
            code = "KUU",
            name = "Bhuntar Airport (Kullu–Manali)",
            type = HubType.AIRPORT,
            city = "Bhuntar, Kullu",
            meetingPointHint = "Outside Arrival Exit Gate 1 near Taxi Union Counter",
            servedClusters = listOf("Jibhi–Tirthan Valley", "Kasol–Manikaran–Tosh", "Kaza–Kibber–Langza–Hikkim")
        ),
        TransitHub(
            code = "KLK",
            name = "Kalka Railway Station",
            type = HubType.RAILWAY_STATION,
            city = "Kalka (Gateway to Shimla & Kinnaur)",
            meetingPointHint = "Platform 1 Main Exit Bay / Heritage Toy Train Concourse",
            servedClusters = listOf("Shimla–Narkanda", "Sangla–Chitkul–Kalpa", "Jibhi–Tirthan Valley")
        ),
        TransitHub(
            code = "SML",
            name = "Shimla Railway Station",
            type = HubType.RAILWAY_STATION,
            city = "Shimla",
            meetingPointHint = "Station Forecourt Pickup Lane (below Victory Tunnel)",
            servedClusters = listOf("Shimla–Narkanda", "Sangla–Chitkul–Kalpa")
        ),
        TransitHub(
            code = "SLV",
            name = "Shimla Airport (Jubbarhatti)",
            type = HubType.AIRPORT,
            city = "Jubbarhatti, Shimla",
            meetingPointHint = "Terminal Arrival Parking Bay 2",
            servedClusters = listOf("Shimla–Narkanda", "Sangla–Chitkul–Kalpa")
        ),
        TransitHub(
            code = "DHM",
            name = "Kangra Airport (Gaggal)",
            type = HubType.AIRPORT,
            city = "Gaggal, Kangra",
            meetingPointHint = "Gaggal Airport Arrival Porch",
            servedClusters = listOf("Bir–Billing & Barot Valley", "Jibhi–Tirthan Valley")
        ),
        TransitHub(
            code = "JDNX",
            name = "Joginder Nagar Railway Station",
            type = HubType.RAILWAY_STATION,
            city = "Joginder Nagar, Mandi",
            meetingPointHint = "Narrow-Gauge Station Main Gate",
            servedClusters = listOf("Bir–Billing & Barot Valley", "Jibhi–Tirthan Valley")
        ),
        TransitHub(
            code = "IXC",
            name = "Chandigarh Airport (Shaheed Bhagat Singh Intl)",
            type = HubType.AIRPORT,
            city = "Chandigarh (Himachal Gateway)",
            meetingPointHint = "Arrival Lane Pillar 4 — Himachal Outstation Pickup",
            servedClusters = listOf("Jibhi–Tirthan Valley", "Kasol–Manikaran–Tosh", "Shimla–Narkanda", "Sangla–Chitkul–Kalpa")
        )
    )

    val destinationClusters: List<DestinationCluster> = listOf(
        DestinationCluster(
            id = "jibhi_tirthan",
            name = "Jibhi–Tirthan Valley",
            shortName = "Jibhi",
            district = "Kullu",
            elevationRange = "1,600m – 2,200m",
            pilotStage = "Pilot Phase 1",
            transportGapNote = "Sparse evening buses past Aut Tunnel; narrow village link roads to Gushaini & Shoja.",
            roadAdvisory = "High-clearance SUV/hatchback recommended; steep hairpins near Jalori Pass.",
            nearestHubCodes = listOf("KUU", "IXC", "JDNX", "KLK")
        ),
        DestinationCluster(
            id = "kasol_tosh",
            name = "Kasol–Manikaran–Tosh",
            shortName = "Kasol",
            district = "Kullu",
            elevationRange = "1,580m – 2,400m",
            pilotStage = "Pilot Phase 1",
            transportGapNote = "Barshaini–Tosh last-mile requires local hill drivers; unreliable late-night public transport.",
            roadAdvisory = "Narrow Parvati Valley roads; experienced local hill driver strongly advised.",
            nearestHubCodes = listOf("KUU", "IXC", "KLK")
        ),
        DestinationCluster(
            id = "kaza_spiti",
            name = "Kaza–Kibber–Langza–Hikkim",
            shortName = "Kaza",
            district = "Lahaul & Spiti",
            elevationRange = "3,800m – 4,400m",
            pilotStage = "Pilot Phase 2",
            transportGapNote = "Once-daily HRTC buses; remote high-altitude villages lack on-demand local cabs.",
            roadAdvisory = "4x4 SUV mandatory in snow/shoulder months; weak cellular signal — keep offline booking ID.",
            nearestHubCodes = listOf("KUU", "KLK", "IXC")
        ),
        DestinationCluster(
            id = "sangla_chitkul",
            name = "Sangla–Chitkul–Kalpa",
            shortName = "Sangla",
            district = "Kinnaur",
            elevationRange = "2,680m – 3,450m",
            pilotStage = "Pilot Phase 2",
            transportGapNote = "Last village Chitkul has minimal shared transport from Karcham / Reckong Peo.",
            roadAdvisory = "Cliff-hugging NH-5 & Sangla link road; daylight hub pickups recommended.",
            nearestHubCodes = listOf("KLK", "SML", "SLV", "IXC")
        ),
        DestinationCluster(
            id = "bir_barot",
            name = "Bir–Billing & Barot Valley",
            shortName = "Bir–Barot",
            district = "Kangra / Mandi",
            elevationRange = "1,520m – 2,400m",
            pilotStage = "Pilot Extension",
            transportGapNote = "Barot & Rajgundha villages have limited last-mile buses from Joginder Nagar & Billing.",
            roadAdvisory = "Single-lane mountain stretches to Barot; compact SUV or local rider preferred.",
            nearestHubCodes = listOf("DHM", "JDNX", "IXC")
        ),
        DestinationCluster(
            id = "shimla_narkanda",
            name = "Shimla–Narkanda",
            shortName = "Shimla",
            district = "Shimla",
            elevationRange = "2,200m – 2,708m",
            pilotStage = "Pilot Extension",
            transportGapNote = "Offbeat apple-orchard stays beyond Narkanda & Hatu Peak lack evening feeder cabs.",
            roadAdvisory = "Winter black-ice caution between Kufri and Narkanda.",
            nearestHubCodes = listOf("KLK", "SML", "SLV", "IXC")
        )
    )

    fun hubByCode(code: String?): TransitHub? = transitHubs.firstOrNull { it.code == code }
}
