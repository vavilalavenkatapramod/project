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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.motomove.himachal.data.HimachalCatalog
import com.motomove.himachal.model.BookingStatus
import com.motomove.himachal.model.PropertyListing
import com.motomove.himachal.model.RentalMode
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.model.VehicleListing
import com.motomove.himachal.ui.MotoMoveUiState
import com.motomove.himachal.ui.theme.MotoGreen
import com.motomove.himachal.ui.theme.MotoGreenContainer

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
    onSubmitDriverDoc: (String, Int, String, List<String>, Int, String, String, String) -> Unit,
    onAddProperty: (String, String, String, String, String, String, String, Int, Int, Int) -> Unit,
    onUpdateOwnProperty: (String, String, String) -> Unit
) {
    val user = state.currentUser ?: return
    val provider = state.currentProvider
    val myProviderId = provider?.providerId

    // Strict ownership view: only show this provider's own belongings & incoming requests
    val myVehicles = state.vehicles.filter { it.providerId == myProviderId }
    val myProperties = state.properties.filter { it.providerId == myProviderId }
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
                Text("${user.role.displayName} Workspace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MotoGreen)
                Text("Verification: ${provider?.verificationStatus?.label ?: "Pending"} • Cluster: ${provider?.homeCluster ?: "Himachal"}")
                Text("Security: You can only view, edit, or delete your own belongings.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it }, label = { Text("Your Display Name") }, modifier = Modifier.weight(1f), singleLine = true)
                    Button(onClick = { onUpdateOwnName(nameDraft) }, colors = ButtonDefaults.buttonColors(containerColor = MotoGreen)) {
                        Text("Save")
                    }
                }
            }
        }

        Text("Your Incoming Booking Requests (${myBookings.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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

        // Owned Belongings Section (Edit/Delete ONLY own assets)
        if (user.role == UserRole.VEHICLE_OWNER) {
            Text("Your Vehicles (${myVehicles.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            myVehicles.forEach { v ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${v.title} (${v.approvalStatus.label})", fontWeight = FontWeight.Bold)
                        Text("₹${v.rateInr}/day • Hubs: ${v.pickupHubs.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editingVehicle = v }) { Text("Edit") }
                            OutlinedButton(onClick = { onDeleteOwnVehicle(v.vehicleId) }) { Text("Delete", color = Color(0xFFDC2626)) }
                        }
                    }
                }
            }
        } else if (user.role == UserRole.ACCOMMODATION_PROVIDER) {
            Text("Your Properties (${myProperties.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            myProperties.forEach { p ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${p.name} (${p.approvalStatus.label})", fontWeight = FontWeight.Bold)
                        Text("Reg: ${p.registrationNumber} • ${p.locationCluster}", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { editingProperty = p }) { Text("Edit Property") }
                    }
                }
            }
        } else if (user.role == UserRole.LOCAL_DRIVER) {
            Text("Your Submitted Verification Documents (${state.verificationDocs.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            state.verificationDocs.forEach { doc ->
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${doc.documentType} • ${doc.reviewStatus.label}", fontWeight = FontWeight.Bold)
                        Text("Expiry: ${doc.expiryDate} • ${doc.reviewerNotes}", style = MaterialTheme.typography.bodySmall)
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
                            RentalMode.entries.forEach { m ->
                                FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                            }
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
