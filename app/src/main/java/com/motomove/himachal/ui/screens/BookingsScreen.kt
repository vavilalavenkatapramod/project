package com.motomove.himachal.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motomove.himachal.data.HimachalCatalog
import com.motomove.himachal.model.Booking
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.ui.MotoMoveUiState
import com.motomove.himachal.ui.theme.MotoDarkNavy
import com.motomove.himachal.ui.theme.MotoGreen
import com.motomove.himachal.ui.theme.MotoOrangeStart
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun BookingsScreen(
    state: MotoMoveUiState,
    onCancelBooking: (String) -> Unit,
    onSubmitReview: (String, Int, String) -> Unit
) {
    val user = state.currentUser ?: return
    var reviewTarget by remember { mutableStateOf<Booking?>(null) }
    var showGuideModal by remember { mutableStateOf(false) }
    val reviewedBookingIds = state.reviews.map { it.bookingId }.toSet()
    val fmt = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("My Bookings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${state.bookings.size} booking(s) total", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }

        if (state.bookings.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text(
                        "No bookings yet. Explore Railway/Airport Pickups, Homestays, or Mountain Dining!",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(state.bookings, key = { it.bookingId }) { b ->
                val accentColor = when (b.status) {
                    BookingStatus.ACCEPTED, BookingStatus.COMPLETED -> MotoGreen
                    BookingStatus.PENDING -> Color(0xFFD97706)
                    else -> Color(0xFFDC2626)
                }
                val hub = HimachalCatalog.hubByCode(b.pickupHubCode)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(accentColor)
                        )
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(b.listingTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(b.bookingType.label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = accentColor.copy(alpha = 0.1f),
                                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        b.status.label,
                                        color = accentColor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (hub != null) {
                                Text(
                                    "Pickup Hub: ${hub.name} (${hub.code}) ${b.trainOrFlightNo?.let { "• $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MotoGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (b.status == BookingStatus.ACCEPTED) {
                                    Text("Meeting Point: ${hub.meetingPointHint}", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MotoGreen, modifier = Modifier.size(16.dp))
                                    Text(
                                        "${LocalDate.ofEpochDay(b.startEpochDay).format(fmt)} – ${LocalDate.ofEpochDay(b.endEpochDay).format(fmt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF4B5563)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Group, contentDescription = null, tint = MotoGreen, modifier = Modifier.size(16.dp))
                                    Text("${b.guestOrSeatCount} guests/seats", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4B5563))
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MotoGreen, modifier = Modifier.size(16.dp))
                                Text("Total: ₹${b.quotedPriceInr} • ${b.destinationCluster}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }

                            if (user.role == UserRole.TOURIST) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (b.status in setOf(BookingStatus.PENDING, BookingStatus.ACCEPTED)) {
                                        OutlinedButton(onClick = { onCancelBooking(b.bookingId) }) {
                                            Text("Cancel Request")
                                        }
                                    }
                                    if (b.status == BookingStatus.COMPLETED && !reviewedBookingIds.contains(b.bookingId)) {
                                        Button(
                                            colors = ButtonDefaults.buttonColors(containerColor = MotoGreen),
                                            onClick = { reviewTarget = b }
                                        ) { Text("Submit Review") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sponsored Workshop Card + Dark Navy Travel Guide + Green Travel Tip (Screenshot 3)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEFCE8)),
                border = BorderStroke(1.dp, Color(0xFFFDE047)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(color = Color(0xFFFACC15), shape = RoundedCornerShape(6.dp)) {
                        Text("AD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Travel Photography Workshop", fontWeight = FontWeight.Bold)
                        Text("Learn to shoot Himachal valleys like a pro.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    TextButton(onClick = { showGuideModal = true }) {
                        Text("Register Free →", color = MotoOrangeStart, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MotoDarkNavy),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEA580C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Plan Your Next Trip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Browse our seasonal Himachal travel guides", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                            TextButton(onClick = { showGuideModal = true }) {
                                Text("▶ Watch now", color = MotoOrangeStart, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MotoGreen)
                    Column {
                        Text("Travel Tip", fontWeight = FontWeight.Bold, color = MotoGreen, style = MaterialTheme.typography.labelLarge)
                        Text("Best time to visit Spiti: June to September", style = MaterialTheme.typography.bodySmall, color = MotoGreen)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    reviewTarget?.let { b ->
        var rating by remember { mutableIntStateOf(5) }
        var comment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reviewTarget = null },
            title = { Text("Rate ${b.listingTitle}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { r ->
                            FilterChip(selected = rating == r, onClick = { rating = r }, label = { Text("${r}★") })
                        }
                    }
                    OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Share pickup & service feedback") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onSubmitReview(b.bookingId, rating, comment)
                    reviewTarget = null
                }) { Text("Post Review") }
            },
            dismissButton = { TextButton(onClick = { reviewTarget = null }) { Text("Close") } }
        )
    }

    if (showGuideModal) {
        AlertDialog(
            onDismissRequest = { showGuideModal = false },
            title = { Text("Seasonal Himachal Travel Guide") },
            text = {
                Text("• Jibhi–Tirthan & Kasol–Tosh: Best Mar–Jun & Sep–Nov.\n• Kaza–Spiti: Jun–Sep via Kunzum / year-round via Kinnaur (4x4 recommended).\n• Always pre-book Station/Airport transfers before entering low-signal valleys.")
            },
            confirmButton = { TextButton(onClick = { showGuideModal = false }) { Text("Got it") } }
        )
    }
}
