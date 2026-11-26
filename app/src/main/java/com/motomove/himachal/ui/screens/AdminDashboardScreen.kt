package com.motomove.himachal.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.ListingApprovalStatus
import com.motomove.himachal.model.User
import com.motomove.himachal.model.VerificationStatus
import com.motomove.himachal.ui.MotoMoveUiState
import com.motomove.himachal.ui.theme.MotoGreen
import com.motomove.himachal.ui.theme.MotoGreenContainer
import com.motomove.himachal.ui.theme.MotoOrangeStart

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardScreen(
    state: MotoMoveUiState,
    onVerifyDoc: (String, VerificationStatus, String) -> Unit,
    onModerateVehicle: (String, ListingApprovalStatus) -> Unit,
    onModerateDriver: (String, ListingApprovalStatus) -> Unit,
    onModerateProperty: (String, ListingApprovalStatus) -> Unit,
    onSetUserStatus: (User, String) -> Unit,
    onModerateReview: (String, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var docFilter by remember { mutableStateOf("ALL") }
    var listingFilter by remember { mutableStateOf("ALL") }
    var bookingFilter by remember { mutableStateOf("ALL") }

    val pendingDocs = state.verificationDocs.count { it.reviewStatus == VerificationStatus.PENDING }
    val pendingListings = state.vehicles.count { it.approvalStatus == ListingApprovalStatus.PENDING_REVIEW } +
        state.drivers.count { it.approvalStatus == ListingApprovalStatus.PENDING_REVIEW } +
        state.properties.count { it.approvalStatus == ListingApprovalStatus.PENDING_REVIEW }
    val pendingBookings = state.bookings.count { it.status == BookingStatus.PENDING }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Admin Top Summary + Pilot Metrics
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MotoGreenContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Platform Admin Portal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MotoGreen
                    )
                    StatusPill(
                        text = if (state.isFirebaseConnected) "LIVE CLOUD SYNC" else "LOCAL SQLITE",
                        bgColor = if (state.isFirebaseConnected) MotoGreen else Color(0xFF64748B),
                        textColor = Color.White
                    )
                }
                state.pilotMetrics?.let { m ->
                    Text(
                        "Live Supply: ${m.approvedVehicles} Vehicles • ${m.approvedDrivers} Drivers • ${m.approvedProperties} Stays • ${state.diningSpots.size} Dining",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Freshness <=7d: ${m.freshnessPercent}% • Median Reply: ${m.medianResponseMinutes}m • Completion: ${m.bookingCompletionPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF334155)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickCounterBadge("Pending Docs: $pendingDocs", pendingDocs > 0)
                    QuickCounterBadge("Pending Listings: $pendingListings", pendingListings > 0)
                    QuickCounterBadge("Live Pending Bookings: $pendingBookings", pendingBookings > 0)
                }
            }
        }

        val tabTitles = listOf(
            "Docs (${state.verificationDocs.size})",
            "Listings",
            "Users & Bookings",
            "Reviews & Audit"
        )
        TabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                // TAB 0: DOCUMENT VERIFICATION
                0 -> {
                    Text(
                        "Provider Licence & Registration Verification",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All", "PENDING" to "Pending", "VERIFIED" to "Verified", "ISSUE" to "Rejected / Expired").forEach { (key, label) ->
                            FilterChip(
                                selected = docFilter == key,
                                onClick = { docFilter = key },
                                label = { Text(label) }
                            )
                        }
                    }
                    val filteredDocs = state.verificationDocs.filter { doc ->
                        when (docFilter) {
                            "PENDING" -> doc.reviewStatus == VerificationStatus.PENDING
                            "VERIFIED" -> doc.reviewStatus == VerificationStatus.VERIFIED
                            "ISSUE" -> doc.reviewStatus == VerificationStatus.REJECTED || doc.reviewStatus == VerificationStatus.EXPIRED
                            else -> true
                        }
                    }
                    if (filteredDocs.isEmpty()) {
                        EmptyAdminCard("No verification documents in this filter.")
                    } else {
                        filteredDocs.forEach { doc ->
                            var notesDraft by remember(doc.documentId, doc.reviewerNotes) {
                                mutableStateOf(doc.reviewerNotes)
                            }
                            val borderColor = if (doc.reviewStatus == VerificationStatus.PENDING) MotoOrangeStart else Color(0xFFE2E8F0)
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, borderColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("${doc.providerName} — ${doc.documentType}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        VerificationStatusPill(doc.reviewStatus)
                                    }
                                    Text("Expiry: ${doc.expiryDate} • File Ref: ${doc.fileReference}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                                    OutlinedTextField(
                                        value = notesDraft,
                                        onValueChange = { notesDraft = it },
                                        label = { Text("Admin Verification Notes / Reason") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onVerifyDoc(doc.documentId, VerificationStatus.VERIFIED, notesDraft.ifBlank { "Verified by Admin" }) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)
                                        ) { Text("Verify") }
                                        OutlinedButton(
                                            onClick = { onVerifyDoc(doc.documentId, VerificationStatus.REJECTED, notesDraft.ifBlank { "Document illegible / rejected" }) }
                                        ) { Text("Reject", color = Color(0xFFDC2626)) }
                                        OutlinedButton(
                                            onClick = { onVerifyDoc(doc.documentId, VerificationStatus.EXPIRED, notesDraft.ifBlank { "Renewal required" }) }
                                        ) { Text("Mark Expired", color = Color(0xFFD97706)) }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: LISTINGS MODERATION
                1 -> {
                    Text(
                        "Vehicles, Drivers & Stay Listings Moderation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All", "PENDING_REVIEW" to "Pending", "APPROVED" to "Approved", "CORRECTION_REQUESTED" to "Correction", "SUSPENDED" to "Suspended").forEach { (key, label) ->
                            FilterChip(
                                selected = listingFilter == key,
                                onClick = { listingFilter = key },
                                label = { Text(label) }
                            )
                        }
                    }

                    val matchesFilter: (ListingApprovalStatus) -> Boolean = { st ->
                        listingFilter == "ALL" || st.name == listingFilter
                    }

                    state.vehicles.filter { matchesFilter(it.approvalStatus) }.forEach { v ->
                        ListingModerationCard(
                            category = "VEHICLE",
                            title = v.title,
                            subtitle = "Owner: ${v.ownerName} • ${v.serviceArea} • ₹${v.rateInr}/${v.rateBasis}",
                            status = v.approvalStatus,
                            onApprove = { onModerateVehicle(v.vehicleId, ListingApprovalStatus.APPROVED) },
                            onRequestCorrection = { onModerateVehicle(v.vehicleId, ListingApprovalStatus.CORRECTION_REQUESTED) },
                            onSuspend = { onModerateVehicle(v.vehicleId, ListingApprovalStatus.SUSPENDED) }
                        )
                    }
                    state.drivers.filter { matchesFilter(it.approvalStatus) }.forEach { d ->
                        ListingModerationCard(
                            category = "DRIVER",
                            title = d.name,
                            subtitle = "Exp: ${d.experienceYears} yrs • ${d.serviceArea} • ₹${d.ratePerDayInr}/day",
                            status = d.approvalStatus,
                            onApprove = { onModerateDriver(d.driverId, ListingApprovalStatus.APPROVED) },
                            onRequestCorrection = { onModerateDriver(d.driverId, ListingApprovalStatus.CORRECTION_REQUESTED) },
                            onSuspend = { onModerateDriver(d.driverId, ListingApprovalStatus.SUSPENDED) }
                        )
                    }
                    state.properties.filter { matchesFilter(it.approvalStatus) }.forEach { p ->
                        ListingModerationCard(
                            category = "STAY",
                            title = "${p.name} (${p.propertyType})",
                            subtitle = "Host: ${p.hostName} • Reg: ${p.registrationNumber} • ${p.locationCluster}",
                            status = p.approvalStatus,
                            onApprove = { onModerateProperty(p.propertyId, ListingApprovalStatus.APPROVED) },
                            onRequestCorrection = { onModerateProperty(p.propertyId, ListingApprovalStatus.CORRECTION_REQUESTED) },
                            onSuspend = { onModerateProperty(p.propertyId, ListingApprovalStatus.SUSPENDED) }
                        )
                    }
                }

                // TAB 2: USERS & PLATFORM-WIDE BOOKINGS
                2 -> {
                    Text(
                        "User Accounts — Suspend / Activate (${state.allUsers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val selfId = state.currentUser?.userId
                    val selfUid = state.firebaseUid
                    state.allUsers.forEach { acct ->
                        val isSelf = acct.userId == selfId || acct.userId == selfUid
                        val isActive = acct.accountStatus == "ACTIVE"
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(acct.name, fontWeight = FontWeight.Bold)
                                    Text("${acct.role.shortLabel} • ${acct.contact}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                                    StatusPill(
                                        text = acct.accountStatus,
                                        bgColor = if (isActive) MotoGreenContainer else Color(0xFFFEE2E2),
                                        textColor = if (isActive) MotoGreen else Color(0xFFB91C1C)
                                    )
                                }
                                if (!isSelf && acct.role != com.motomove.himachal.model.UserRole.ADMINISTRATOR) {
                                    if (isActive) {
                                        OutlinedButton(onClick = { onSetUserStatus(acct, "SUSPENDED") }) {
                                            Text("Suspend", color = Color(0xFFDC2626))
                                        }
                                    } else {
                                        Button(
                                            onClick = { onSetUserStatus(acct, "ACTIVE") },
                                            colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)
                                        ) {
                                            Text("Activate")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        "All Platform Bookings (${state.bookings.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All", "PENDING" to "Pending", "ACCEPTED" to "Accepted", "COMPLETED" to "Completed", "OTHER" to "Rejected / Cancelled").forEach { (key, label) ->
                            FilterChip(selected = bookingFilter == key, onClick = { bookingFilter = key }, label = { Text(label) })
                        }
                    }
                    val filteredBookings = state.bookings.filter { b ->
                        when (bookingFilter) {
                            "PENDING" -> b.status == BookingStatus.PENDING
                            "ACCEPTED" -> b.status == BookingStatus.ACCEPTED
                            "COMPLETED" -> b.status == BookingStatus.COMPLETED
                            "OTHER" -> b.status in setOf(BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.EXPIRED)
                            else -> true
                        }
                    }
                    if (filteredBookings.isEmpty()) {
                        EmptyAdminCard("No bookings match this filter yet.")
                    } else {
                        filteredBookings.take(40).forEach { b ->
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("${b.bookingType.label}: ${b.listingTitle}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        BookingStatusPill(b.status)
                                    }
                                    Text("Tourist: ${b.touristName} • Guests/Seats: ${b.guestOrSeatCount} • ₹${b.quotedPriceInr}", style = MaterialTheme.typography.bodySmall)
                                    Text("Route/Area: ${b.pickupHubCode ?: "Local"} -> ${b.destinationCluster} • Provider: ${b.providerId}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF475569))
                                }
                            }
                        }
                    }
                }

                // TAB 3: REVIEWS & SECURITY AUDIT LOGS
                3 -> {
                    Text("Review Moderation (FR-23 • ${state.reviews.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (state.reviews.isEmpty()) {
                        EmptyAdminCard("No tourist reviews yet.")
                    } else {
                        state.reviews.forEach { rev ->
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("${rev.touristName} • ${rev.rating}★ on ${rev.listingId}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        StatusPill(
                                            text = rev.moderationStatus,
                                            bgColor = if (rev.moderationStatus == "VISIBLE") MotoGreenContainer else Color(0xFFFEF3C7),
                                            textColor = if (rev.moderationStatus == "VISIBLE") MotoGreen else Color(0xFFB45309)
                                        )
                                    }
                                    Text(rev.comment, style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { onModerateReview(rev.reviewId, "VISIBLE") }) { Text("Show") }
                                        OutlinedButton(onClick = { onModerateReview(rev.reviewId, "FLAGGED") }) { Text("Flag") }
                                        OutlinedButton(onClick = { onModerateReview(rev.reviewId, "HIDDEN") }) { Text("Hide", color = Color(0xFFDC2626)) }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text("Security & Audit Logs (FR-24 • ${state.auditLogs.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    state.auditLogs.take(25).forEach { log ->
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${log.action} (${log.actorRole})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                                Text("Target: ${log.targetId} • ${log.details}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun QuickCounterBadge(label: String, highlight: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlight) Color(0xFFFFEDD5) else Color.White.copy(alpha = 0.7f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MotoOrangeStart else Color(0xFF334155)
        )
    }
}

@Composable
private fun StatusPill(text: String, bgColor: Color, textColor: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = bgColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun VerificationStatusPill(status: VerificationStatus) {
    val (bg, fg) = when (status) {
        VerificationStatus.VERIFIED -> MotoGreenContainer to MotoGreen
        VerificationStatus.PENDING -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        VerificationStatus.REJECTED, VerificationStatus.EXPIRED -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
    }
    StatusPill(status.label, bg, fg)
}

@Composable
private fun BookingStatusPill(status: BookingStatus) {
    val (bg, fg) = when (status) {
        BookingStatus.ACCEPTED -> MotoGreenContainer to MotoGreen
        BookingStatus.COMPLETED -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
        BookingStatus.PENDING -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        else -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
    }
    StatusPill(status.label, bg, fg)
}

@Composable
private fun EmptyAdminCard(message: String) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingModerationCard(
    category: String,
    title: String,
    subtitle: String,
    status: ListingApprovalStatus,
    onApprove: () -> Unit,
    onRequestCorrection: () -> Unit,
    onSuspend: () -> Unit
) {
    val (bg, fg) = when (status) {
        ListingApprovalStatus.APPROVED -> MotoGreenContainer to MotoGreen
        ListingApprovalStatus.PENDING_REVIEW -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        ListingApprovalStatus.CORRECTION_REQUESTED -> Color(0xFFFFEDD5) to MotoOrangeStart
        ListingApprovalStatus.SUSPENDED -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
    }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("[$category] $title", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                StatusPill(status.label, bg, fg)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF475569))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)) {
                    Text("Approve")
                }
                OutlinedButton(onClick = onRequestCorrection) {
                    Text("Request Correction")
                }
                OutlinedButton(onClick = onSuspend) {
                    Text("Suspend", color = Color(0xFFDC2626))
                }
            }
        }
    }
}
