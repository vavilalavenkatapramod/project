package com.motomove.himachal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.motomove.himachal.data.HimachalCatalog
import com.motomove.himachal.ui.MotoMoveUiState
import com.motomove.himachal.ui.theme.MotoGreen
import com.motomove.himachal.ui.theme.MotoPurple
import com.motomove.himachal.ui.theme.MotoPurpleSoft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiCopilotScreen(
    state: MotoMoveUiState,
    onUpdateInputs: (hubCode: String, clusterId: String, passengers: Int, luggage: String) -> Unit,
    onRunPickupPlan: () -> Unit,
    onApplyPlanToExplore: (hubCode: String, clusterName: String, passengers: Int) -> Unit,
    onCompareStays: () -> Unit,
    onFindFoodMatch: (String) -> Unit,
    onAskQuestion: (String) -> Unit
) {
    var chatInput by remember { mutableStateOf("") }
    val selectedCluster = HimachalCatalog.destinationClusters.firstOrNull { it.id == state.aiClusterId }
        ?: HimachalCatalog.destinationClusters.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Purple MotoAI Header Card (no external API keys)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(MotoPurple, Color(0xFF4F46E5))))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    Text("MotoAI Mountain Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(
                    "Built-in smart Himachal trip assistant • Works 100% offline in remote valleys with zero API keys.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }

        // 1. Railway Station / Airport -> Underserved Valley Planner
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Station / Airport Pickup Planner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Arrival Hub (Railway Station / Airport)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HimachalCatalog.transitHubs.forEach { hub ->
                        FilterChip(
                            selected = state.aiHubCode == hub.code,
                            onClick = { onUpdateInputs(hub.code, state.aiClusterId, state.aiPassengers, state.aiLuggageNote) },
                            label = { Text("${hub.code} (${hub.type.label.substringBefore(" ")})") }
                        )
                    }
                }
                Text("Target Himachal Valley", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HimachalCatalog.destinationClusters.forEach { cl ->
                        FilterChip(
                            selected = state.aiClusterId == cl.id,
                            onClick = { onUpdateInputs(state.aiHubCode, cl.id, state.aiPassengers, state.aiLuggageNote) },
                            label = { Text(cl.shortName) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        onUpdateInputs(state.aiHubCode, state.aiClusterId, (state.aiPassengers % 6) + 1, state.aiLuggageNote)
                    }) {
                        Text("Travellers: ${state.aiPassengers}")
                    }
                    OutlinedTextField(
                        value = state.aiLuggageNote,
                        onValueChange = { onUpdateInputs(state.aiHubCode, state.aiClusterId, state.aiPassengers, it) },
                        label = { Text("Luggage") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = onRunPickupPlan,
                    colors = ButtonDefaults.buttonColors(containerColor = MotoPurple),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Suggest Vehicle & Pickup Plan")
                }
            }
        }

        state.aiPlan?.let { plan ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MotoPurpleSoft), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MotoAI Pickup Recommendation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4C1D95))
                    Text("Vehicle: ${plan.recommendedVehicleClass} (${plan.suggestedRentalMode})", fontWeight = FontWeight.SemiBold)
                    Text("Est. Drive: ${plan.estimatedDriveHours} • Wait Buffer: ${plan.pickupBufferMinutes} mins", style = MaterialTheme.typography.bodySmall)
                    Text("Hub Meeting Point: ${plan.hubMeetingProtocol}", style = MaterialTheme.typography.bodySmall)
                    Text("Last-Mile Gap: ${plan.lastMileGapAdvice}", style = MaterialTheme.typography.bodySmall)
                    Text("Road Advisory: ${plan.roadAndWeatherAdvisory}", style = MaterialTheme.typography.bodySmall)
                    plan.checklist.forEach { tip ->
                        Text("• $tip", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { onApplyPlanToExplore(state.aiHubCode, selectedCluster.name, state.aiPassengers) },
                        colors = ButtonDefaults.buttonColors(containerColor = MotoGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Show Matching Verified Vehicles (${plan.matchedVehicles.size})")
                    }
                }
            }
        }

        // 2. Compare Stays Side-by-Side & Food Face-Off (matching Screenshot 5 MotoAI teaser!)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Smart Comparisons & Food Face-Off", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onCompareStays, modifier = Modifier.weight(1f)) {
                        Text("Compare Stays")
                    }
                    OutlinedButton(onClick = { onFindFoodMatch(selectedCluster.shortName) }, modifier = Modifier.weight(1f)) {
                        Text("Best Local Food")
                    }
                }

                state.aiStayComparison?.let { cmp ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${cmp.stayA.name} vs ${cmp.stayB.name}", fontWeight = FontWeight.Bold)
                            Text(cmp.verdict, style = MaterialTheme.typography.bodySmall)
                            cmp.highlights.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }

                if (state.aiFoodMatches.isNotEmpty()) {
                    state.aiFoodMatches.forEach { m ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(m.spot.name, fontWeight = FontWeight.Bold)
                                Text(m.reason, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // 3. Instant Mountain Q&A
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Ask MotoAI (Trains, Flights, Snow Roads, Food)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                state.aiChatMessages.forEach { b ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (b.isUser) MotoPurpleSoft else MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(if (b.isUser) "You" else "MotoAI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(b.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        label = { Text("Ask about Kalka train, Bhuntar flight, Jalori Pass...") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAskQuestion(chatInput)
                            chatInput = ""
                        },
                        enabled = chatInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MotoPurple)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
