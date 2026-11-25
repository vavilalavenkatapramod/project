package com.motomove.himachal.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motomove.himachal.data.HimachalCatalog
import com.motomove.himachal.model.BookingType
import com.motomove.himachal.model.DestinationCluster
import com.motomove.himachal.model.DriverProfile
import com.motomove.himachal.model.PropertyListing
import com.motomove.himachal.model.RoomType
import com.motomove.himachal.model.TimeSlot
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.model.VehicleListing
import com.motomove.himachal.ui.MotoMoveUiState
import com.motomove.himachal.ui.theme.MotoAmberEnd
import com.motomove.himachal.ui.theme.MotoBlueEnd
import com.motomove.himachal.ui.theme.MotoBlueStart
import com.motomove.himachal.ui.theme.MotoGreen
import com.motomove.himachal.ui.theme.MotoGreenContainer
import com.motomove.himachal.ui.theme.MotoPurple
import com.motomove.himachal.ui.theme.MotoPurpleSoft
import com.motomove.himachal.ui.theme.MotoVerifiedGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TouristExploreScreen(
    state: MotoMoveUiState,
    onUpdateFilters: (category: Int, hubCode: String, clusterName: String, startDay: Long, endDay: Long, passengers: Int, sortLowest: Boolean) -> Unit,
    onOpenFoodTab: () -> Unit,
    onOpenMotoAiTab: () -> Unit,
    onBookVehicle: (VehicleListing, BookingType, String?, String, String, String, Long, Long, TimeSlot, Int) -> Unit,
    onBookDriver: (DriverProfile, BookingType, String?, String, String, String, Long, Long, TimeSlot) -> Unit,
    onBookStay: (PropertyListing, RoomType, Long, Long, Int, String) -> Unit
) {
    var heroSlide by remember { mutableIntStateOf(0) }
    var clusterMenuExpanded by remember { mutableStateOf(false) }
    var hubMenuExpanded by remember { mutableStateOf(false) }
    var selectedGuide by remember { mutableStateOf<DestinationCluster?>(null) }
    var showSosDialog by remember { mutableStateOf(false) }
    var bookingVehicleTarget by remember { mutableStateOf<VehicleListing?>(null) }
    var bookingDriverTarget by remember { mutableStateOf<DriverProfile?>(null) }
    var bookingStayTarget by remember { mutableStateOf<Pair<PropertyListing, RoomType>?>(null) }

    val isTourist = state.currentUser?.role == UserRole.TOURIST
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }

    val filteredVehicles = state.vehicles.filter { v ->
        val hubOk = state.selectedHubCode == "ALL" || v.pickupHubs.contains(state.selectedHubCode)
        val clusterOk = state.selectedClusterName == "ALL" || v.serviceArea.contains(state.selectedClusterName, ignoreCase = true)
        val capOk = v.capacity >= state.searchPassengers
        hubOk && clusterOk && capOk
    }.let { list -> if (state.sortLowestPrice) list.sortedBy { it.rateInr } else list }

    val filteredDrivers = state.drivers.filter { d ->
        val hubOk = state.selectedHubCode == "ALL" || d.pickupHubs.contains(state.selectedHubCode)
        val clusterOk = state.selectedClusterName == "ALL" || d.serviceArea.contains(state.selectedClusterName, ignoreCase = true)
        hubOk && clusterOk
    }.let { list -> if (state.sortLowestPrice) list.sortedBy { it.ratePerDayInr } else list }

    val filteredProps = state.properties.filter { p ->
        val hubOk = state.selectedHubCode == "ALL" || p.nearestHubCode == state.selectedHubCode
        val clusterOk = state.selectedClusterName == "ALL" || p.locationCluster == state.selectedClusterName
        hubOk && clusterOk
    }.let { list ->
        if (state.sortLowestPrice) list.sortedBy { state.roomsByProperty[it.propertyId]?.minOfOrNull { r -> r.ratePerNightInr } ?: 99999 }
        else list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Hero Carousel Banner
        item {
            val slides = listOf(
                Triple("Cozy Stays & Boutique Hotels", "Verified homestays, cottages & mud-houses across Himachal valleys.", MotoBlueStart to MotoBlueEnd),
                Triple("Railway & Airport Pickups", "Direct pickup from Bhuntar, Kalka, Shimla & Kangra straight to offbeat villages.", MotoGreen to Color(0xFF16A34A)),
                Triple("Mountain Dining & Fresh Trout", "Explore FSSAI-verified Himachali kitchens, riverfront cafes & Siddu spots.", Color(0xFFD84315) to Color(0xFFF59E0B))
            )
            val current = slides[heroSlide % slides.size]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(current.third.first, current.third.second)))
                    .clickable {
                        if (heroSlide == 2) onOpenFoodTab()
                        else heroSlide = (heroSlide + 1) % slides.size
                    }
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(current.first, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(current.second, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Swipe left/right for tabs • Tap banner", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            slides.indices.forEach { idx ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .height(6.dp)
                                        .width(if (idx == heroSlide) 20.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (idx == heroSlide) Color.White else Color.White.copy(alpha = 0.45f))
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Four-Column Live Stats Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatCell(Icons.Default.DirectionsCar, state.vehicles.size.toString(), "Vehicles", Color(0xFFDC2626), Modifier.weight(1f)) {
                        onUpdateFilters(0, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                    }
                    VerticalDivider(modifier = Modifier.height(34.dp))
                    StatCell(Icons.Default.PersonPin, state.drivers.size.toString(), "Drivers", Color(0xFFD97706), Modifier.weight(1f)) {
                        onUpdateFilters(1, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                    }
                    VerticalDivider(modifier = Modifier.height(34.dp))
                    StatCell(Icons.Default.Hotel, state.properties.size.toString(), "Stays", MotoGreen, Modifier.weight(1f)) {
                        onUpdateFilters(2, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                    }
                    VerticalDivider(modifier = Modifier.height(34.dp))
                    StatCell(Icons.Default.Restaurant, state.diningSpots.size.toString(), "Dining", Color(0xFFEA580C), Modifier.weight(1f)) {
                        onOpenFoodTab()
                    }
                }
            }
        }

        // 3. One-Tap Quick Station / Airport Pickup Strip + Reels & SOS Shortcut (Super Easy To Use!)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1-Tap Station & Airport Pickups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HimachalCatalog.transitHubs, key = { it.code }) { hub ->
                        val selected = state.selectedHubCode == hub.code
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val nextHub = if (selected) "ALL" else hub.code
                                onUpdateFilters(0, nextHub, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                            },
                            leadingIcon = {
                                Icon(
                                    if (hub.type.name == "RAILWAY_STATION") Icons.Default.Train else Icons.Default.FlightLand,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("${hub.code} • ${hub.name.substringBefore(" (")}") }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onOpenMotoAiTab,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MotoPurple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pickup Planner", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { showSosDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Emergency, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Hill SOS & Help", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 4. MotoAI Assistant Promo Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MotoPurpleSoft),
                border = BorderStroke(1.dp, MotoPurple.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth().clickable { onOpenMotoAiTab() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(MotoPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("MotoAI Assistant", fontWeight = FontWeight.Bold, color = Color(0xFF4C1D95))
                            Surface(color = MotoAmberEnd, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "AI PICK",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Plan Station/Airport pickups, compare hotels side-by-side & find local food →",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF5B21B6)
                        )
                    }
                }
            }
        }

        // 5. "What are you looking for?" 4 Category Tiles
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What are you looking for?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryTile("Vehicles", Icons.Default.DirectionsCar, state.selectedCategory == 0, Modifier.weight(1f)) {
                        onUpdateFilters(0, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                    }
                    CategoryTile("Drivers", Icons.Default.PersonPin, state.selectedCategory == 1, Modifier.weight(1f)) {
                        onUpdateFilters(1, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                    }
                    CategoryTile("Stays", Icons.Default.Hotel, state.selectedCategory == 2, Modifier.weight(1f)) {
                        onUpdateFilters(2, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                    }
                    CategoryTile("Food & Cafe", Icons.Default.Restaurant, false, Modifier.weight(1f)) {
                        onOpenFoodTab()
                    }
                }
            }
        }

        // 6. Destination Travel Guides + Popular Destinations
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Destination Travel Guides", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${HimachalCatalog.destinationClusters.size} places", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(HimachalCatalog.destinationClusters, key = { it.id }) { cl ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(1.dp),
                            modifier = Modifier.clickable { selectedGuide = cl }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column {
                                    Text(cl.shortName, fontWeight = FontWeight.Bold)
                                    Text(cl.elevationRange, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Popular Destinations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedClusterName == "ALL",
                            onClick = { onUpdateFilters(state.selectedCategory, state.selectedHubCode, "ALL", state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice) },
                            label = { Text("All Valleys") }
                        )
                    }
                    items(HimachalCatalog.destinationClusters, key = { it.id }) { cl ->
                        FilterChip(
                            selected = state.selectedClusterName == cl.name,
                            onClick = { onUpdateFilters(state.selectedCategory, state.selectedHubCode, cl.name, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice) },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp)) },
                            label = { Text(cl.name) }
                        )
                    }
                }
            }
        }

        // 7. Refine Search Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = MotoGreen)
                            Text("Refine Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        FilterChip(
                            selected = state.sortLowestPrice,
                            onClick = {
                                onUpdateFilters(state.selectedCategory, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, !state.sortLowestPrice)
                            },
                            leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Lowest Price") }
                        )
                    }

                    Box {
                        val hubLabel = if (state.selectedHubCode == "ALL") "All Railway Stations & Airports"
                        else HimachalCatalog.hubByCode(state.selectedHubCode)?.let { "${it.code} • ${it.name}" } ?: state.selectedHubCode
                        OutlinedButton(onClick = { hubMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("Pickup Hub: $hubLabel", modifier = Modifier.weight(1f))
                        }
                        DropdownMenu(expanded = hubMenuExpanded, onDismissRequest = { hubMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("All Railway Stations & Airports") }, onClick = {
                                hubMenuExpanded = false
                                onUpdateFilters(state.selectedCategory, "ALL", state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                            })
                            HimachalCatalog.transitHubs.forEach { h ->
                                DropdownMenuItem(text = { Text("${h.code} • ${h.name}") }, onClick = {
                                    hubMenuExpanded = false
                                    onUpdateFilters(state.selectedCategory, h.code, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                                })
                            }
                        }
                    }

                    Box {
                        OutlinedButton(onClick = { clusterMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Text("Destination: ${if (state.selectedClusterName == "ALL") "All destinations" else state.selectedClusterName}", modifier = Modifier.weight(1f))
                        }
                        DropdownMenu(expanded = clusterMenuExpanded, onDismissRequest = { clusterMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("All destinations") }, onClick = {
                                clusterMenuExpanded = false
                                onUpdateFilters(state.selectedCategory, state.selectedHubCode, "ALL", state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                            })
                            HimachalCatalog.destinationClusters.forEach { cl ->
                                DropdownMenuItem(text = { Text("${cl.name} (${cl.district})") }, onClick = {
                                    clusterMenuExpanded = false
                                    onUpdateFilters(state.selectedCategory, state.selectedHubCode, cl.name, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                                })
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                val nextStart = state.searchStartDay + 1
                                onUpdateFilters(state.selectedCategory, state.selectedHubCode, state.selectedClusterName, nextStart, maxOf(nextStart + 1, state.searchEndDay), state.searchPassengers, state.sortLowestPrice)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MotoGreen)
                            Spacer(Modifier.width(4.dp))
                            Text("From: ${LocalDate.ofEpochDay(state.searchStartDay).format(dateFmt)}", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                val nextEnd = if (state.searchEndDay - state.searchStartDay >= 7) state.searchStartDay + 1 else state.searchEndDay + 1
                                onUpdateFilters(state.selectedCategory, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, nextEnd, state.searchPassengers, state.sortLowestPrice)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp), tint = MotoGreen)
                            Spacer(Modifier.width(4.dp))
                            Text("To: ${LocalDate.ofEpochDay(state.searchEndDay).format(dateFmt)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Passengers / Guests", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = {
                                onUpdateFilters(state.selectedCategory, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers - 1, state.sortLowestPrice)
                            }) { Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = MotoGreen) }
                            Text(state.searchPassengers.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                onUpdateFilters(state.selectedCategory, state.selectedHubCode, state.selectedClusterName, state.searchStartDay, state.searchEndDay, state.searchPassengers + 1, state.sortLowestPrice)
                            }) { Icon(Icons.Default.Add, contentDescription = "Increase", tint = MotoGreen) }
                        }
                    }
                }
            }
        }

        // 8. Available Options Header
        val resultCount = when (state.selectedCategory) {
            0 -> filteredVehicles.size
            1 -> filteredDrivers.size
            else -> filteredProps.size
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Available Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(color = MotoGreenContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "$resultCount found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MotoGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 9. Listing Cards
        when (state.selectedCategory) {
            0 -> items(filteredVehicles, key = { it.vehicleId }) { v ->
                BlueGradientOptionCard(
                    badgeLabel = "VEHICLE",
                    priceLabel = "₹${v.rateInr} ${v.rateBasis}",
                    isVerified = v.providerVerification.label == "Verified",
                    title = v.title,
                    location = v.serviceArea,
                    subtitle = "${v.vehicleType} • ${v.capacity} seats • ${v.rentalMode.label} • Pickups: ${v.pickupHubs.joinToString(", ")}",
                    ownerLabel = v.ownerName,
                    icon = Icons.Default.DirectionsCar,
                    actionLabel = if (isTourist) "View & Book →" else "Tourist Only",
                    actionEnabled = isTourist,
                    onClick = { bookingVehicleTarget = v }
                )
            }
            1 -> items(filteredDrivers, key = { it.driverId }) { d ->
                BlueGradientOptionCard(
                    badgeLabel = "DRIVER",
                    priceLabel = "₹${d.ratePerDayInr} per day",
                    isVerified = d.verificationStatus.label == "Verified",
                    title = "${d.name} (${d.experienceYears} yrs exp)",
                    location = d.serviceArea,
                    subtitle = "${d.languages} • Pickups: ${d.pickupHubs.joinToString(", ")}",
                    ownerLabel = d.availabilitySummary,
                    icon = Icons.Default.PersonPin,
                    actionLabel = if (isTourist) "Book Driver →" else "Tourist Only",
                    actionEnabled = isTourist,
                    onClick = { bookingDriverTarget = d }
                )
            }
            else -> items(filteredProps, key = { it.propertyId }) { p ->
                val rooms = state.roomsByProperty[p.propertyId].orEmpty()
                val minRate = rooms.minOfOrNull { it.ratePerNightInr } ?: 2000
                BlueGradientOptionCard(
                    badgeLabel = p.propertyType.uppercase(),
                    priceLabel = "from ₹$minRate / night",
                    isVerified = p.providerVerification.label == "Verified",
                    title = p.name,
                    location = "${p.locationCluster} (Hub: ${p.nearestHubCode})",
                    subtitle = "HP Reg: ${p.registrationNumber} • ${rooms.size} room type(s) • ${p.amenities}",
                    ownerLabel = p.hostName,
                    icon = Icons.Default.Hotel,
                    actionLabel = if (isTourist && rooms.isNotEmpty()) "Book Stay →" else "View",
                    actionEnabled = isTourist && rooms.isNotEmpty(),
                    onClick = { rooms.firstOrNull()?.let { bookingStayTarget = p to it } }
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showSosDialog) {
        AlertDialog(
            onDismissRequest = { showSosDialog = false },
            title = { Text("Himachal Mountain SOS & Help") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Emergency / Police / Rescue: 112", fontWeight = FontWeight.Bold)
                    Text("• HP Tourism Helpline: 1800-180-8077")
                    Text("• Ambulance / Medical: 108")
                    Text("• Disaster Management (SDMA): 1070 / 1077")
                    Text("• Tip: Screenshot or note your Booking ID before crossing Jalori Pass, Atal Tunnel, or Spiti low-signal zones.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showSosDialog = false }) { Text("Close") } }
        )
    }

    selectedGuide?.let { cl ->
        AlertDialog(
            onDismissRequest = { selectedGuide = null },
            title = { Text("${cl.name} (${cl.elevationRange})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("District: ${cl.district} • ${cl.pilotStage}", fontWeight = FontWeight.SemiBold)
                    Text("Local Transport Reality: ${cl.transportGapNote}", style = MaterialTheme.typography.bodySmall)
                    Text("Road Advisory: ${cl.roadAdvisory}", style = MaterialTheme.typography.bodySmall)
                    Text("Nearest Pickup Hubs: ${cl.nearestHubCodes.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MotoGreen, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateFilters(0, cl.nearestHubCodes.firstOrNull() ?: "ALL", cl.name, state.searchStartDay, state.searchEndDay, state.searchPassengers, state.sortLowestPrice)
                    selectedGuide = null
                }) { Text("Show Pickups Here") }
            },
            dismissButton = { TextButton(onClick = { selectedGuide = null }) { Text("Close") } }
        )
    }

    bookingVehicleTarget?.let { v ->
        MobilityBookingDialog(
            title = v.title,
            ratePerDay = v.rateInr,
            maxCapacity = v.capacity,
            supportedHubs = v.pickupHubs,
            cancellationTerms = v.cancellationTerms,
            defaultHub = if (state.selectedHubCode != "ALL") state.selectedHubCode else v.pickupHubs.firstOrNull(),
            defaultCluster = if (state.selectedClusterName != "ALL") state.selectedClusterName else v.serviceArea.substringBefore(",").trim(),
            defaultStartDay = state.searchStartDay,
            defaultEndDay = state.searchEndDay,
            defaultSeats = state.searchPassengers.coerceAtMost(v.capacity),
            onDismiss = { bookingVehicleTarget = null },
            onConfirm = { type, hub, cluster, trainFlight, notes, start, end, slot, seats ->
                onBookVehicle(v, type, hub, cluster, trainFlight, notes, start, end, slot, seats)
                bookingVehicleTarget = null
            }
        )
    }

    bookingDriverTarget?.let { d ->
        MobilityBookingDialog(
            title = "Driver: ${d.name}",
            ratePerDay = d.ratePerDayInr,
            maxCapacity = 6,
            supportedHubs = d.pickupHubs,
            cancellationTerms = d.cancellationTerms,
            defaultHub = if (state.selectedHubCode != "ALL") state.selectedHubCode else d.pickupHubs.firstOrNull(),
            defaultCluster = if (state.selectedClusterName != "ALL") state.selectedClusterName else d.serviceArea.substringBefore(",").trim(),
            defaultStartDay = state.searchStartDay,
            defaultEndDay = state.searchEndDay,
            defaultSeats = state.searchPassengers.coerceAtMost(6),
            onDismiss = { bookingDriverTarget = null },
            onConfirm = { type, hub, cluster, trainFlight, notes, start, end, slot, _ ->
                onBookDriver(d, type, hub, cluster, trainFlight, notes, start, end, slot)
                bookingDriverTarget = null
            }
        )
    }

    bookingStayTarget?.let { (prop, room) ->
        val rooms = state.roomsByProperty[prop.propertyId].orEmpty()
        StayBookingDialog(
            property = prop,
            rooms = rooms.ifEmpty { listOf(room) },
            defaultCheckIn = state.searchStartDay,
            defaultCheckOut = state.searchEndDay.coerceAtLeast(state.searchStartDay + 1),
            defaultGuests = state.searchPassengers,
            onDismiss = { bookingStayTarget = null },
            onConfirm = { chosenRoom, checkIn, checkOut, guests, notes ->
                onBookStay(prop, chosenRoom, checkIn, checkOut, guests, notes)
                bookingStayTarget = null
            }
        )
    }
}

@Composable
private fun StatCell(icon: ImageVector, count: String, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MotoGreen)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
private fun CategoryTile(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MotoGreen else Color.White),
        elevation = CardDefaults.cardElevation(if (selected) 4.dp else 1.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else MotoGreen, modifier = Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color(0xFF1F2937), maxLines = 1)
        }
    }
}

@Composable
private fun BlueGradientOptionCard(
    badgeLabel: String,
    priceLabel: String,
    isVerified: Boolean,
    title: String,
    location: String,
    subtitle: String,
    ownerLabel: String,
    icon: ImageVector,
    actionLabel: String,
    actionEnabled: Boolean,
    onClick: () -> Unit
) {
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
                    .background(Brush.horizontalGradient(listOf(MotoBlueStart, MotoBlueEnd)))
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Surface(color = Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(8.dp)) {
                            Text(badgeLabel, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                        Text(priceLabel, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (isVerified) {
                        Surface(color = MotoVerifiedGreen, shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Text("Verified", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                    Text(location, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4B5563))
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Text(ownerLabel, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    TextButton(onClick = onClick, enabled = actionEnabled) {
                        Text(actionLabel, color = MotoGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobilityBookingDialog(
    title: String,
    ratePerDay: Int,
    maxCapacity: Int,
    supportedHubs: List<String>,
    cancellationTerms: String,
    defaultHub: String?,
    defaultCluster: String,
    defaultStartDay: Long,
    defaultEndDay: Long,
    defaultSeats: Int,
    onDismiss: () -> Unit,
    onConfirm: (BookingType, String?, String, String, String, Long, Long, TimeSlot, Int) -> Unit
) {
    var bookingType by remember { mutableStateOf(BookingType.HUB_PICKUP) }
    var selectedHub by remember { mutableStateOf(defaultHub ?: supportedHubs.firstOrNull() ?: "KUU") }
    var cluster by remember { mutableStateOf(defaultCluster) }
    var trainOrFlight by remember { mutableStateOf("12011 / 9I-805") }
    var notes by remember { mutableStateOf("Arriving at exit gate with backpacks") }
    var slot by remember { mutableStateOf(TimeSlot.MORNING_06_12) }
    var seats by remember { mutableIntStateOf(defaultSeats.coerceIn(1, maxCapacity)) }

    val fmt = DateTimeFormatter.ofPattern("dd MMM")
    val days = (defaultEndDay - defaultStartDay + 1).coerceAtLeast(1L).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request: $title") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(BookingType.HUB_PICKUP, BookingType.LOCAL_MOBILITY).forEach { t ->
                        FilterChip(selected = bookingType == t, onClick = { bookingType = t }, label = { Text(t.label) })
                    }
                }
                if (bookingType == BookingType.HUB_PICKUP) {
                    Text("Pickup Railway Station / Airport:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        supportedHubs.forEach { code ->
                            FilterChip(selected = selectedHub == code, onClick = { selectedHub = code }, label = { Text(code) })
                        }
                    }
                    OutlinedTextField(value = trainOrFlight, onValueChange = { trainOrFlight = it }, label = { Text("Train / Flight No & ETA") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(value = cluster, onValueChange = { cluster = it }, label = { Text("Destination Cluster") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("${LocalDate.ofEpochDay(defaultStartDay).format(fmt)} → ${LocalDate.ofEpochDay(defaultEndDay).format(fmt)} ($days day(s)) • Seats: $seats")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimeSlot.entries.forEach { s -> FilterChip(selected = slot == s, onClick = { slot = s }, label = { Text(s.label) }) }
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Pickup Instructions") }, modifier = Modifier.fillMaxWidth())
                Text("Quoted Total: ₹${ratePerDay * days} • $cancellationTerms", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            Button(colors = ButtonDefaults.buttonColors(containerColor = MotoGreen), onClick = {
                onConfirm(bookingType, if (bookingType == BookingType.HUB_PICKUP) selectedHub else null, cluster, trainOrFlight, notes, defaultStartDay, defaultEndDay, slot, seats)
            }) { Text("Submit Request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StayBookingDialog(
    property: PropertyListing,
    rooms: List<RoomType>,
    defaultCheckIn: Long,
    defaultCheckOut: Long,
    defaultGuests: Int,
    onDismiss: () -> Unit,
    onConfirm: (RoomType, Long, Long, Int, String) -> Unit
) {
    var selectedRoom by remember { mutableStateOf(rooms.first()) }
    var guests by remember { mutableIntStateOf(defaultGuests.coerceIn(1, selectedRoom.occupancy)) }
    var notes by remember { mutableStateOf("Arriving from hub pickup") }
    val nights = (defaultCheckOut - defaultCheckIn).coerceAtLeast(1L).toInt()
    val fmt = DateTimeFormatter.ofPattern("dd MMM")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(property.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rooms.forEach { r ->
                    FilterChip(
                        selected = selectedRoom.roomTypeId == r.roomTypeId,
                        onClick = {
                            selectedRoom = r
                            guests = guests.coerceIn(1, r.occupancy)
                        },
                        label = { Text("${r.name} (₹${r.ratePerNightInr}/night, max ${r.occupancy})") }
                    )
                }
                Text("Check-in: ${LocalDate.ofEpochDay(defaultCheckIn).format(fmt)} → ${LocalDate.ofEpochDay(defaultCheckOut).format(fmt)} ($nights nights) • Guests: $guests")
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Arrival Notes") }, modifier = Modifier.fillMaxWidth())
                Text("Total: ₹${selectedRoom.ratePerNightInr * nights} • ${property.policies}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedRoom, defaultCheckIn, defaultCheckOut, guests, notes) }) { Text("Request Stay") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
