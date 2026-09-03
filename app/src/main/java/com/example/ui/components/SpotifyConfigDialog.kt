package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.SpotifyGenreResolver
import com.example.ui.theme.BentoHeroAccent
import com.example.ui.theme.BentoHeroContainer
import com.example.ui.theme.BentoHeroOnContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoSurfaceCard
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.BentoTileBg
import com.example.ui.theme.BentoTileBorder
import kotlinx.coroutines.launch

@Composable
fun SpotifyConfigDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var clientId by remember { mutableStateOf("") }
    var clientSecret by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val (id, secret) = SpotifyGenreResolver.getCredentials(context)
        clientId = id
        clientSecret = secret
        if (id.isNotBlank() && secret.isNotBlank()) {
            isSuccess = true
            statusMessage = "Spotify Developer credentials currently configured."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BentoSurfaceCard,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1DB954).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Spotify",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Spotify Genre API",
                            color = BentoTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Strict Official Genre Detection",
                            color = BentoTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = BentoTextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Info Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BentoTileBg)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = BentoPrimary,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enter your Spotify Developer Client ID and Secret to query official artist genres directly from the Spotify Web API. If left empty, the app seamlessly uses iTunes & open music APIs as fallback.",
                            color = BentoTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Client ID Field
                Column {
                    Text(
                        text = "Spotify Client ID",
                        color = BentoTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = {
                            clientId = it
                            statusMessage = null
                        },
                        placeholder = { Text("e.g. 3a7f8b9...", color = BentoTextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = BentoTileBorder,
                            focusedContainerColor = BentoTileBg,
                            unfocusedContainerColor = BentoTileBg
                        )
                    )
                }

                // Client Secret Field
                Column {
                    Text(
                        text = "Spotify Client Secret",
                        color = BentoTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = {
                            clientSecret = it
                            statusMessage = null
                        },
                        placeholder = { Text("e.g. 9b8c7d6...", color = BentoTextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = BentoTileBorder,
                            focusedContainerColor = BentoTileBg,
                            unfocusedContainerColor = BentoTileBg
                        )
                    )
                }

                // Status Message Box
                if (statusMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSuccess) Color(0xFF1DB954).copy(alpha = 0.15f)
                                else Color(0xFFFF5252).copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isSuccess) Color(0xFF1DB954) else Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusMessage ?: "",
                            color = if (isSuccess) Color(0xFF1DB954) else Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Test Connection Button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            statusMessage = null
                            val res = SpotifyGenreResolver.testConnection(clientId, clientSecret)
                            isTesting = false
                            if (res.isSuccess) {
                                isSuccess = true
                                statusMessage = res.getOrNull() ?: "Connected to Spotify Developer API!"
                            } else {
                                isSuccess = false
                                statusMessage = res.exceptionOrNull()?.message ?: "Connection test failed."
                            }
                        }
                    },
                    enabled = clientId.isNotBlank() && clientSecret.isNotBlank() && !isTesting,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Test", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Save Button
                Button(
                    onClick = {
                        SpotifyGenreResolver.saveCredentials(context, clientId, clientSecret)
                        onSaved()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (clientId.isNotBlank() || clientSecret.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        clientId = ""
                        clientSecret = ""
                        SpotifyGenreResolver.saveCredentials(context, "", "")
                        statusMessage = "Cleared credentials. Fallback public APIs active."
                        isSuccess = true
                        onSaved()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear", color = BentoTextSecondary, fontSize = 12.sp)
                }
            }
        }
    )
}
