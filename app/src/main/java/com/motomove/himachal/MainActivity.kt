package com.motomove.himachal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.ui.MotoMoveViewModel
import com.motomove.himachal.ui.screens.AdminDashboardScreen
import com.motomove.himachal.ui.screens.AiCopilotScreen
import com.motomove.himachal.ui.screens.AlertsScreen
import com.motomove.himachal.ui.screens.AuthScreen
import com.motomove.himachal.ui.screens.BookingsScreen
import com.motomove.himachal.ui.screens.FoodScreen
import com.motomove.himachal.ui.screens.ProviderDashboardScreen
import com.motomove.himachal.ui.screens.TouristExploreScreen
import com.motomove.himachal.ui.theme.MotoGreen
import com.motomove.himachal.ui.theme.MotoMoveTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotoMoveTheme {
                MotoMoveApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotoMoveApp(vm: MotoMoveViewModel = viewModel()) {
    val state = vm.uiState
    val currentUser = state.currentUser
    val isTourist = currentUser?.role == UserRole.TOURIST
    val unreadAlerts = state.notifications.count { !it.isRead }
    var showLiveDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUser?.userId) {
        if (currentUser != null) {
            val targetPage = if (currentUser.role == UserRole.TOURIST) 0 else 3
            pagerState.scrollToPage(targetPage)
        }
    }

    fun goToTab(index: Int) {
        scope.launch { pagerState.animateScrollToPage(index) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MotoGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("MotoMove", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MotoGreen)
                            if (currentUser != null) {
                                Text("${currentUser.name} • ${currentUser.role.shortLabel}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                },
                actions = {
                    val cloudLive = state.isFirebaseConnected || state.isLiveConnected
                    Surface(
                        color = if (cloudLive) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { showLiveDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (cloudLive) Color(0xFF16A34A) else Color(0xFFD97706))
                            )
                            Text(
                                when {
                                    state.isFirebaseConnected -> "CLOUD LIVE"
                                    state.isLiveConnected -> "SSE LIVE"
                                    else -> "LOCAL"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (cloudLive) Color(0xFF166534) else Color(0xFF92400E)
                            )
                        }
                    }
                    if (currentUser != null) {
                        IconButton(onClick = { vm.logout() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = MotoGreen)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (currentUser != null) {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { goToTab(0) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                        label = { Text("Explore") }
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { goToTab(1) },
                        icon = { Icon(Icons.Default.Restaurant, contentDescription = null) },
                        label = { Text("Food") }
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 2,
                        onClick = { goToTab(2) },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        label = { Text("MotoAI") }
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 3,
                        onClick = { goToTab(3) },
                        icon = {
                            Icon(
                                when {
                                    isTourist -> Icons.Default.CalendarMonth
                                    currentUser.role == UserRole.ADMINISTRATOR -> Icons.Default.AdminPanelSettings
                                    else -> Icons.Default.Dashboard
                                },
                                contentDescription = null
                            )
                        },
                        label = {
                            Text(
                                when {
                                    isTourist -> "Bookings"
                                    currentUser.role == UserRole.ADMINISTRATOR -> "Admin"
                                    else -> "Manage"
                                }
                            )
                        }
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 4,
                        onClick = { goToTab(4) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (unreadAlerts > 0) {
                                        Badge(containerColor = Color(0xFFEA580C)) {
                                            Text(unreadAlerts.toString(), color = Color.White)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null)
                            }
                        },
                        label = { Text("Alerts") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            state.statusBanner?.let { banner ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.isErrorBanner) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(banner, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.clearBanner() }) { Text("OK") }
                    }
                }
            }

            if (currentUser == null) {
                AuthScreen(
                    demoUsers = state.demoUsers,
                    onLogin = vm::loginWithCredentials,
                    onRegister = vm::registerAccount,
                    onQuickDemo = vm::quickSignInDemo
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> TouristExploreScreen(
                            state = state,
                            onUpdateFilters = vm::updateExploreFilters,
                            onOpenFoodTab = { goToTab(1) },
                            onOpenMotoAiTab = { goToTab(2) },
                            onBookVehicle = vm::submitVehicleBooking,
                            onBookDriver = vm::submitDriverBooking,
                            onBookStay = vm::submitStayBooking
                        )
                        1 -> FoodScreen(
                            state = state,
                            onFilterChange = vm::updateFoodFilters,
                            onReserveTable = vm::reserveDiningTable
                        )
                        2 -> AiCopilotScreen(
                            state = state,
                            onUpdateInputs = vm::updateMotoAiInputs,
                            onRunPickupPlan = vm::runMotoAiPickupPlan,
                            onApplyPlanToExplore = { hubCode, clusterName, passengers ->
                                vm.updateExploreFilters(0, hubCode, clusterName, state.searchStartDay, state.searchEndDay, passengers, state.sortLowestPrice)
                                goToTab(0)
                            },
                            onCompareStays = vm::runMotoAiCompareStays,
                            onFindFoodMatch = vm::runMotoAiFoodFaceOff,
                            onAskQuestion = vm::askMotoAiQuestion
                        )
                        3 -> when {
                            isTourist -> BookingsScreen(
                                state = state,
                                onCancelBooking = vm::cancelBooking,
                                onSubmitReview = vm::submitReview
                            )
                            currentUser.role == UserRole.ADMINISTRATOR -> AdminDashboardScreen(
                                state = state,
                                onVerifyDoc = vm::adminVerifyDocument,
                                onModerateVehicle = vm::adminApproveVehicle,
                                onModerateDriver = vm::adminApproveDriver,
                                onModerateProperty = vm::adminApproveProperty,
                                onSetUserStatus = vm::adminSetUserAccountStatus,
                                onModerateReview = vm::adminModerateReview
                            )
                            else -> ProviderDashboardScreen(
                                state = state,
                                onUpdateOwnName = vm::updateOwnName,
                                onRespondBooking = vm::respondToBooking,
                                onCompleteBooking = vm::completeBooking,
                                onAddVehicle = vm::addVehicleListing,
                                onUpdateOwnVehicle = vm::updateOwnVehicle,
                                onDeleteOwnVehicle = vm::deleteOwnVehicle,
                                onToggleVehicleLive = vm::toggleVehicleLive,
                                onToggleDriverLive = vm::toggleDriverLive,
                                onUpdateRoomLiveInventory = vm::updateRoomLiveInventory,
                                onToggleDiningLive = vm::toggleDiningLive,
                                onSubmitDriverDoc = vm::submitDriverDoc,
                                onAddProperty = vm::addPropertyWithRoom,
                                onUpdateOwnProperty = vm::updateOwnProperty
                            )
                        }
                        4 -> AlertsScreen(
                            state = state,
                            onMarkAllRead = vm::markAllAlertsRead,
                            onSendTestAlert = vm::sendTestAlert
                        )
                    }
                }
            }
        }
    }

    if (showLiveDialog) {
        var urlDraft by remember(state.liveServerUrl) { mutableStateOf(state.liveServerUrl) }
        AlertDialog(
            onDismissRequest = { showLiveDialog = false },
            title = { Text("Cloud Live Sync (Firebase + SSE)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.isFirebaseConnected) "Cloud Firestore Live Sync is ACTIVE over 4G/5G/Wi-Fi! Updates from any Driver, Hotel, or Food spot appear instantly across all devices."
                        else "Cloud Firestore connects automatically on sign-in. Optional: enter a local Node SSE Server URL below for LAN testing:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("Optional Local SSE URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { urlDraft = "" }) { Text("Firebase Cloud Only") }
                        OutlinedButton(onClick = { urlDraft = "http://10.0.2.2:8080" }) { Text("Emulator SSE") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.setLiveServerUrl(urlDraft)
                    showLiveDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showLiveDialog = false }) { Text("Close") } }
        )
    }
}
