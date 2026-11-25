package com.motomove.himachal.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motomove.himachal.model.DiningSpot
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.ui.MotoMoveUiState
import com.motomove.himachal.ui.theme.MotoAmberEnd
import com.motomove.himachal.ui.theme.MotoGreen
import com.motomove.himachal.ui.theme.MotoOrangeStart

@Composable
fun FoodScreen(
    state: MotoMoveUiState,
    onFilterChange: (cluster: String, specialty: String) -> Unit,
    onReserveTable: (DiningSpot, Int, String) -> Unit
) {
    val destinations = listOf("ALL" to "All", "Jibhi" to "Jibhi", "Kasol" to "Kasol", "Kaza" to "Kaza", "Sangla" to "Sangla")
    val specialties = listOf("ALL", "Fresh Trout", "Pahadi Dham & Siddu", "Woodfired Cafe")

    val filtered = state.diningSpots.filter { spot ->
        val destOk = state.foodClusterFilter == "ALL" || spot.locationCluster.contains(state.foodClusterFilter, ignoreCase = true)
        val specOk = state.foodSpecialtyFilter == "ALL" || spot.specialty.equals(state.foodSpecialtyFilter, ignoreCase = true)
        destOk && specOk
    }

    var reserveTarget by remember { mutableStateOf<DiningSpot?>(null) }
    val isTourist = state.currentUser?.role == UserRole.TOURIST

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Warm Orange Gradient Hero Card (Screenshot 2)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(MotoOrangeStart, MotoAmberEnd)))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.White)
                        Text(
                            "Mountain Dining & Cafes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        "From fresh river trout to high-altitude monastery teas and authentic woodfired bakeries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.94f)
                    )
                }
            }
        }

        // Destination Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Destination", style = MaterialTheme.typography.labelLarge, color = Color(0xFF4B5563))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(destinations) { (key, label) ->
                        val selected = state.foodClusterFilter == key
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterChange(key, state.foodSpecialtyFilter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MotoGreen,
                                selectedLabelColor = Color.White
                            ),
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Cuisine & Specialty", style = MaterialTheme.typography.labelLarge, color = Color(0xFF4B5563))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(specialties) { spec ->
                        val selected = state.foodSpecialtyFilter == spec
                        FilterChip(
                            selected = selected,
                            onClick = { onFilterChange(state.foodClusterFilter, spec) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MotoOrangeStart,
                                selectedLabelColor = Color.White
                            ),
                            label = { Text(if (spec == "ALL") "All" else spec) }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Available Spots (${filtered.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(color = Color(0xFFFFF3E0), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "FSSAI Verified",
                        color = Color(0xFFB45309),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        items(filtered, key = { it.spotId }) { spot ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(MotoOrangeStart, Color(0xFFEA580C))))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        if (spot.isOpenNow) "DINING • OPEN NOW" else "DINING • FULL",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                                Text(spot.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MotoOrangeStart, modifier = Modifier.size(16.dp))
                            Text("${spot.locationCluster} • ${spot.liveStatusNote}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Text("Dishes: ${spot.dishes}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("Ambience: ${spot.ambience} • ${spot.rating}★", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Avg. Cost", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text("₹${spot.avgCostInr} per person / meal", color = MotoOrangeStart, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { reserveTarget = spot },
                                enabled = isTourist,
                                colors = ButtonDefaults.buttonColors(containerColor = MotoOrangeStart),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (isTourist) "Reserve Table →" else "Tourist Only")
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    reserveTarget?.let { spot ->
        var guests by remember { mutableIntStateOf(2) }
        var notes by remember { mutableStateOf("Evening dinner after valley check-in") }
        AlertDialog(
            onDismissRequest = { reserveTarget = null },
            title = { Text("Reserve at ${spot.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Guests: $guests • Est. ₹${spot.avgCostInr * guests}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 4, 6).forEach { count ->
                            FilterChip(selected = guests == count, onClick = { guests = count }, label = { Text("$count") })
                        }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Dish / Seating Request") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MotoOrangeStart),
                    onClick = {
                        onReserveTable(spot, guests, notes)
                        reserveTarget = null
                    }
                ) { Text("Confirm Table") }
            },
            dismissButton = { TextButton(onClick = { reserveTarget = null }) { Text("Cancel") } }
        )
    }
}
