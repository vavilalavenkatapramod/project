package com.motomove.himachal.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motomove.himachal.data.HimachalCatalog
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.DiningSpot
import com.motomove.himachal.model.DriverProfile
import com.motomove.himachal.model.PropertyListing
import com.motomove.himachal.model.RentalMode
import com.motomove.himachal.model.RoomType
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.model.VehicleListing
import com.motomove.himachal.ui.MotoMoveUiState
import com.motomove.himachal.ui.theme.MotoGreen
import com.motomove.himachal.ui.theme.MotoGreenContainer
import com.motomove.himachal.ui.theme.MotoOrangeStart

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderDashboardScreen(
    state: MotoMoveUiState,
    onUpdateOwnName: (String) -> Unit,
    onRespondBooking: (String, Boolean) -> Unit,
    onCompleteBooking: (String) -> Unit,
    onAddVehicle: (String, String, Int, String, String, List<String>, RentalMode, Int, String) -> Unit,
    onUpdateOwnVehicle: (String, String, Int, String) -> Unit,
    onDeleteOwnVehicle: (String) -> Unit,
    onToggleVehicleLive: (VehicleListing, Boolean, Int, String) -> Unit,
    onToggleDriverLive: (DriverProfile, Boolean, Int, String) -> Unit,
    onUpdateRoomLiveInventory: (RoomType, Int, Int) -> Unit,
    onToggleDiningLive: (DiningSpot, Boolean, String, Int) -> Unit,
    onSubmitDriverDoc: (String, Int, String, List<String>, Int, String, String, String) -> Unit,
    onAddProperty: (String, String, String, String, String, String, String, Int, Int, Int) -> Unit,
    onUpdateOwnProperty: (String, String, String) -> Unit
) {
    val user = state.currentUser ?: return
    val provider = state.currentProvider
    val myProviderId = provider?.providerId

    val myVehicles = state.vehicles.filter { it.providerId == myProviderId }
    val myDrivers = state.drivers.filter { it.providerId == myProviderId }
    val myProperties = state.properties.filter { it.providerId == myProviderId }
    val myDining = state.diningSpots.filter { it.providerId == myProviderId }
    val myBookings = state.bookings.filter { it.providerId == myProviderId }

    var editingVehicle by remember { mutableStateOf<VehicleListing?>(null) }
    var editingProperty by remember { mutableStateOf<PropertyListing?>(null) }
    var nameDraft by remember(user.name) { mutableStateOf(user.name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MotoGreenContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${user.role.displayName} Live Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MotoGreen)
                Text("Verification: ${provider?.verificationStatus?.label ?: "Pending"} • Sync: ${if (state.isFirebaseConnected || state.isLiveConnected) "CLOUD LIVE BROADCAST ON" else "Local Mode"}")
                Text("Ownership Lock: You can only update your own vehicles, drivers, rooms, or food spots.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it }, label = { Text("Your Display Name") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(onClick = { onUpdateOwnName(nameDraft) }, colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)) { Text("Save") }
                }
            }
        }

        Text("Incoming Booking Requests (${myBookings.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (myBookings.isEmpty()) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Text("No booking requests yet for your listings.", modifier = Modifier.padding(14.dp))
            }
        } else {
            myBookings.forEach { b ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${b.listingTitle} • ${b.status.label}", fontWeight = FontWeight.Bold)
                        Text("Tourist: ${b.touristName} • Hub: ${b.pickupHubCode ?: "Local"} -> ${b.destinationCluster}", style = MaterialTheme.typography.bodySmall)
                        Text("Train/Flight: ${b.trainOrFlightNo ?: "N/A"} • ₹${b.quotedPriceInr}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (b.status == BookingStatus.PENDING) {
                                Button(onClick = { onRespondBooking(b.bookingId, true) }, colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)) { Text("Accept") }
                                OutlinedButton(onClick = { onRespondBooking(b.bookingId, false) }) { Text("Reject") }
                            } else if (b.status == BookingStatus.ACCEPTED) {
                                Button(onClick = { onCompleteBooking(b.bookingId) }) { Text("Mark Completed") }
                            }
                        }
                    }
                }
            }
        }

        // LIVE BROADCAST CONTROLS BY ROLE
        if (user.role == UserRole.VEHICLE_OWNER) {
            Text("Your Vehicles — Live Availability (${myVehicles.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            myVehicles.forEach { v ->
                var noteDraft by remember(v.vehicleId, v.liveStatusNote) { mutableStateOf(v.liveStatusNote) }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(v.title, fontWeight = FontWeight.Bold)
                                Text(if (v.isAvailableNow) "● LIVE AVAILABLE • ₹${v.rateInr}/day" else "● ON TRIP / BUSY • ₹${v.rateInr}/day", style = MaterialTheme.typography.bodySmall, color = if (v.isAvailableNow) MotoGreen else Color(0xFFD97706), fontWeight = FontWeight.SemiBold)
                            }
                            Switch(checked = v.isAvailableNow, onCheckedChange = { checked -> onToggleVehicleLive(v, checked, v.rateInr, noteDraft) })
                        }
                        OutlinedTextField(value = noteDraft, onValueChange = { noteDraft = it }, label = { Text("Live Status Note (e.g., Parked at Bhuntar Gate 1)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onToggleVehicleLive(v, v.isAvailableNow, v.rateInr, noteDraft) }, colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)) { Text("Broadcast Live") }
                            OutlinedButton(onClick = { editingVehicle = v }) { Text("Edit") }
                            OutlinedButton(onClick = { onDeleteOwnVehicle(v.vehicleId) }) { Text("Delete", color = Color(0xFFDC2626)) }
                        }
                    }
                }
            }
        } else if (user.role == UserRole.LOCAL_DRIVER) {
            Text("Your Live Driver Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            myDrivers.forEach { d ->
                var summaryDraft by remember(d.driverId, d.availabilitySummary) { mutableStateOf(d.availabilitySummary) }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(d.name, fontWeight = FontWeight.Bold)
                                Text(if (d.isAvailableNow) "● ONLINE FOR PICKUPS" else "● ON TRIP / OFFLINE", color = if (d.isAvailableNow) MotoGreen else Color(0xFFD97706), fontWeight = FontWeight.SemiBold)
                            }
                            Switch(checked = d.isAvailableNow, onCheckedChange = { checked -> onToggleDriverLive(d, checked, d.ratePerDayInr, summaryDraft) })
                        }
                        OutlinedTextField(value = summaryDraft, onValueChange = { summaryDraft = it }, label = { Text("Live Location / Hub Note") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { onToggleDriverLive(d, d.isAvailableNow, d.ratePerDayInr, summaryDraft) }, colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)) {
                            Text("Broadcast Live Driver Status")
                        }
                    }
                }
            }
        } else if (user.role == UserRole.ACCOMMODATION_PROVIDER) {
            Text("Your Stays — Live Room Inventory (${myProperties.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            myProperties.forEach { p ->
                val rooms = state.roomsByProperty[p.propertyId].orEmpty()
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text("Reg: ${p.registrationNumber} • ${p.locationCluster}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { editingProperty = p }) { Text("Edit") }
                        }
                        rooms.forEach { r ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${r.inventoryCount} room(s) live • ₹${r.ratePerNightInr}/night", style = MaterialTheme.typography.bodySmall, color = MotoGreen)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedButton(onClick = { onUpdateRoomLiveInventory(r, r.inventoryCount - 1, r.ratePerNightInr) }) { Text("-1") }
                                    Button(onClick = { onUpdateRoomLiveInventory(r, r.inventoryCount + 1, r.ratePerNightInr) }, colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)) { Text("+1") }
                                }
                            }
                        }
                    }
                }
            }

            if (myDining.isNotEmpty()) {
                Text("Your Mountain Dining Spots — Live Status (${myDining.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                myDining.forEach { spot ->
                    var noteDraft by remember(spot.spotId, spot.liveStatusNote) { mutableStateOf(spot.liveStatusNote) }
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(spot.name, fontWeight = FontWeight.Bold)
                                    Text(if (spot.isOpenNow) "● TABLES OPEN NOW" else "● CURRENTLY FULL", color = if (spot.isOpenNow) MotoGreen else MotoOrangeStart, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = spot.isOpenNow, onCheckedChange = { checked -> onToggleDiningLive(spot, checked, noteDraft, spot.avgCostInr) })
                            }
                            OutlinedTextField(value = noteDraft, onValueChange = { noteDraft = it }, label = { Text("Live Dish / Table Note") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(onClick = { onToggleDiningLive(spot, spot.isOpenNow, noteDraft, spot.avgCostInr) }, colors = ButtonDefaults.buttonColors(containerColor = MotoOrangeStart)) {
                                Text("Broadcast Food Status")
                            }
                        }
                    }
                }
            }
        }

        // Creation Forms
        when (user.role) {
            UserRole.VEHICLE_OWNER -> {
                var title by remember { mutableStateOf("") }
                var vType by remember { mutableStateOf("4x4 SUV") }
                var capacity by remember { mutableIntStateOf(6) }
                var fuel by remember { mutableStateOf("Manual • Diesel 4WD") }
                var area by remember { mutableStateOf("Jibhi–Tirthan Valley, Kaza–Kibber–Langza–Hikkim") }
                var selectedHubs by remember { mutableStateOf(setOf("KUU", "KLK")) }
                var mode by remember { mutableStateOf(RentalMode.WITH_DRIVER) }
                var rate by remember { mutableStateOf("3500") }

                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add New Vehicle Listing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Vehicle Title") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = vType, onValueChange = { vType = it }, label = { Text("Vehicle Type") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = fuel, onValueChange = { fuel = it }, label = { Text("Transmission / Fuel") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Served Himachal Valleys") }, modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HimachalCatalog.transitHubs.forEach { h ->
                                FilterChip(
                                    selected = selectedHubs.contains(h.code),
                                    onClick = { selectedHubs = if (selectedHubs.contains(h.code)) selectedHubs - h.code else selectedHubs + h.code },
                                    label = { Text(h.code) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RentalMode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
                        }
                        OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Rate INR / day") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = {
                                onAddVehicle(title, vType, capacity, fuel, area, selectedHubs.toList(), mode, rate.toIntOrNull() ?: 3000, "Free cancellation 12h prior")
                                title = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MotoGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Submit Vehicle for Admin Approval") }
                    }
                }
            }

            UserRole.LOCAL_DRIVER -> {
                var langs by remember { mutableStateOf("Hindi, English, Pahari") }
                var exp by remember { mutableStateOf("8") }
                var area by remember { mutableStateOf("Sangla–Chitkul–Kalpa") }
                var hubs by remember { mutableStateOf(setOf("KLK", "SML", "SLV")) }
                var rate by remember { mutableStateOf("1500") }
                var docRef by remember { mutableStateOf("HP-DL-2026-8831") }
                var expiry by remember { mutableStateOf("2032-05-15") }

                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Submit Driver Profile & Licence", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = langs, onValueChange = { langs = it }, label = { Text("Languages") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = exp, onValueChange = { exp = it }, label = { Text("Experience (Years)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Service Areas") }, modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HimachalCatalog.transitHubs.forEach { h ->
                                FilterChip(selected = hubs.contains(h.code), onClick = { hubs = if (hubs.contains(h.code)) hubs - h.code else hubs + h.code }, label = { Text(h.code) })
                            }
                        }
                        OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Daily Driver Rate INR") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = docRef, onValueChange = { docRef = it }, label = { Text("Commercial Licence Reference") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("Expiry (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                        Button(
                            onClick = { onSubmitDriverDoc(langs, exp.toIntOrNull() ?: 5, area, hubs.toList(), rate.toIntOrNull() ?: 1400, "HP Commercial Hill Licence", docRef, expiry) },
                            colors = ButtonDefaults.buttonColors(containerColor = MotoGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Submit Driver Profile & Licence") }
                    }
                }
            }

            UserRole.ACCOMMODATION_PROVIDER -> {
                var propName by remember { mutableStateOf("") }
                var regNo by remember { mutableStateOf("HP-KUL-HS-2026-102") }
                var amenities by remember { mutableStateOf("Bukhari heater, Local dinner, Valley view") }
                var roomName by remember { mutableStateOf("Pine Cedar Deluxe Room") }
                var rate by remember { mutableStateOf("2400") }
                var inv by remember { mutableStateOf("2") }

                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add Homestay / Hotel & Room Inventory", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = propName, onValueChange = { propName = it }, label = { Text("Property Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = regNo, onValueChange = { regNo = it }, label = { Text("HP Tourism Registration #") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = amenities, onValueChange = { amenities = it }, label = { Text("Amenities") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = roomName, onValueChange = { roomName = it }, label = { Text("Room Type Name") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Rate/Night ₹") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = inv, onValueChange = { inv = it }, label = { Text("Rooms Count") }, modifier = Modifier.weight(1f))
                        }
                        Button(
                            onClick = {
                                onAddProperty(propName, "Homestay", "Jibhi–Tirthan Valley", "KUU", amenities, regNo, roomName, 3, rate.toIntOrNull() ?: 2200, inv.toIntOrNull() ?: 2)
                                propName = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MotoGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Submit Property for Admin Approval") }
                    }
                }
            }
            else -> Unit
        }
        Spacer(Modifier.height(16.dp))
    }

    editingVehicle?.let { v ->
        var title by remember { mutableStateOf(v.title) }
        var rate by remember { mutableStateOf(v.rateInr.toString()) }
        var area by remember { mutableStateOf(v.serviceArea) }
        AlertDialog(
            onDismissRequest = { editingVehicle = null },
            title = { Text("Edit Your Vehicle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Rate ₹/day") })
                    OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Service Area") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateOwnVehicle(v.vehicleId, title, rate.toIntOrNull() ?: v.rateInr, area)
                    editingVehicle = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingVehicle = null }) { Text("Cancel") } }
        )
    }

    editingProperty?.let { p ->
        var name by remember { mutableStateOf(p.name) }
        var amenities by remember { mutableStateOf(p.amenities) }
        AlertDialog(
            onDismissRequest = { editingProperty = null },
            title = { Text("Edit Your Property") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Property Name") })
                    OutlinedTextField(value = amenities, onValueChange = { amenities = it }, label = { Text("Amenities") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateOwnProperty(p.propertyId, name, amenities)
                    editingProperty = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingProperty = null }) { Text("Cancel") } }
        )
    }
}
