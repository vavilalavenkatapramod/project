package com.motomove.himachal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motomove.himachal.model.ListingApprovalStatus
import com.motomove.himachal.model.VerificationStatus
import com.motomove.himachal.ui.MotoMoveUiState
import com.motomove.himachal.ui.theme.MotoGreen
import com.motomove.himachal.ui.theme.MotoGreenContainer

@Composable
fun AdminDashboardScreen(
    state: MotoMoveUiState,
    onVerifyDoc: (String, VerificationStatus, String) -> Unit,
    onModerateVehicle: (String, ListingApprovalStatus) -> Unit,
    onModerateDriver: (String, ListingApprovalStatus) -> Unit,
    onModerateProperty: (String, ListingApprovalStatus) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.pilotMetrics?.let { m ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MotoGreenContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pilot Evaluation Metrics (SRS Section 6)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MotoGreen)
                    Text("Approved Supply: ${m.approvedVehicles} Vehicles • ${m.approvedDrivers} Drivers • ${m.approvedProperties} Stays")
                    Text("Freshness (<=7d): ${m.freshnessPercent}% • Median Response: ${m.medianResponseMinutes} min • Completion: ${m.bookingCompletionPercent}%")
                }
            }
        }

        Text("Restricted Document Verification (FR-06, FR-07)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        state.verificationDocs.forEach { doc ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${doc.providerName} — ${doc.documentType}", fontWeight = FontWeight.Bold)
                    Text("Status: ${doc.reviewStatus.label} • Expiry: ${doc.expiryDate}", style = MaterialTheme.typography.bodySmall)
                    Text("Private Ref: ${doc.fileReference} • Notes: ${doc.reviewerNotes}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { onVerifyDoc(doc.documentId, VerificationStatus.VERIFIED, "Verified by Admin") }, colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)) { Text("Verify") }
                        OutlinedButton(onClick = { onVerifyDoc(doc.documentId, VerificationStatus.REJECTED, "Document illegible/invalid") }) { Text("Reject") }
                        OutlinedButton(onClick = { onVerifyDoc(doc.documentId, VerificationStatus.EXPIRED, "Renewal needed") }) { Text("Expired") }
                    }
                }
            }
        }

        Text("Listing Approvals & Moderation (FR-22)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        state.vehicles.forEach { v ->
            ModerationRow(
                title = "Vehicle: ${v.title}",
                status = v.approvalStatus.label,
                onApprove = { onModerateVehicle(v.vehicleId, ListingApprovalStatus.APPROVED) },
                onSuspend = { onModerateVehicle(v.vehicleId, ListingApprovalStatus.SUSPENDED) }
            )
        }
        state.drivers.forEach { d ->
            ModerationRow(
                title = "Driver: ${d.name}",
                status = d.approvalStatus.label,
                onApprove = { onModerateDriver(d.driverId, ListingApprovalStatus.APPROVED) },
                onSuspend = { onModerateDriver(d.driverId, ListingApprovalStatus.SUSPENDED) }
            )
        }
        state.properties.forEach { p ->
            ModerationRow(
                title = "Stay: ${p.name} (${p.registrationNumber})",
                status = p.approvalStatus.label,
                onApprove = { onModerateProperty(p.propertyId, ListingApprovalStatus.APPROVED) },
                onSuspend = { onModerateProperty(p.propertyId, ListingApprovalStatus.SUSPENDED) }
            )
        }

        Text("Security & Audit Logs (FR-24)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        state.auditLogs.take(12).forEach { log ->
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("${log.action} (${log.actorRole})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text("Target: ${log.targetId} • ${log.details}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ModerationRow(
    title: String,
    status: String,
    onApprove: () -> Unit,
    onSuspend: () -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("Status: $status", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onApprove, colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)) { Text("Approve") }
                OutlinedButton(onClick = onSuspend) { Text("Suspend") }
            }
        }
    }
}
