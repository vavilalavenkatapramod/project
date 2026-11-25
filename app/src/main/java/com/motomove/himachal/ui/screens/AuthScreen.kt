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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.motomove.himachal.data.HimachalCatalog
import com.motomove.himachal.model.User
import com.motomove.himachal.model.UserRole
import com.motomove.himachal.ui.theme.MotoBlueEnd
import com.motomove.himachal.ui.theme.MotoBlueStart
import com.motomove.himachal.ui.theme.MotoGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    demoUsers: List<User>,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, UserRole, String) -> Unit,
    onQuickDemo: (User) -> Unit
) {
    var isRegister by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("tourist@motomove.in") }
    var password by remember { mutableStateOf("demo1234") }
    var selectedRole by remember { mutableStateOf(UserRole.TOURIST) }
    var selectedCluster by remember { mutableStateOf(HimachalCatalog.destinationClusters.first().name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.horizontalGradient(listOf(MotoGreen, Color(0xFF15803D))))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DirectionsBike, contentDescription = null, tint = Color.White)
                    }
                    Text("MotoMove Himachal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(
                    "Railway & Airport Pickups • Underserved Valleys • Verified Homestays & Mountain Dining",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }

        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MotoGreen, modifier = Modifier.size(18.dp))
                    Text(
                        if (isRegister) "Create Secure Account" else "Secure Sign In",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isRegister) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Email or Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (isRegister) {
                    Text("Select Account Role:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UserRole.entries.filter { it != UserRole.ADMINISTRATOR }.forEach { role ->
                            FilterChip(selected = selectedRole == role, onClick = { selectedRole = role }, label = { Text(role.shortLabel) })
                        }
                    }
                    if (selectedRole != UserRole.TOURIST) {
                        Text("Primary Himachal Cluster:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HimachalCatalog.destinationClusters.take(4).forEach { cl ->
                                FilterChip(selected = selectedCluster == cl.name, onClick = { selectedCluster = cl.name }, label = { Text(cl.shortName) })
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (isRegister) onRegister(name, contact, password, selectedRole, selectedCluster)
                        else onLogin(contact, password)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MotoGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRegister) "Register & Sign In" else "Sign In")
                }

                TextButton(onClick = { isRegister = !isRegister }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (isRegister) "Already have an account? Sign In" else "New user or Himachal partner? Register")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MotoBlueStart, modifier = Modifier.size(18.dp))
                Text(
                    "Strict Ownership Security: Each user can only view, edit, or delete their own listings, profile, and bookings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1E3A8A)
                )
            }
        }

        Text("Quick Role Demo (Password: demo1234)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        demoUsers.forEach { user ->
            OutlinedButton(
                onClick = { onQuickDemo(user) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("${user.role.displayName}: ${user.name}", fontWeight = FontWeight.SemiBold, color = MotoGreen)
                    Text(user.contact, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
