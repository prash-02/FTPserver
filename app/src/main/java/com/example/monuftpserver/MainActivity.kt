package com.example.monuftpserver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.net.NetworkInterface
import java.util.*

class MainActivity : ComponentActivity() {
    private var ftpServer: FtpServer? = null
    private var serverStatus by mutableStateOf("Stopped")
    private var ipAddress by mutableStateOf("")
    private var isServerRunning by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startFtpServer()
        } else {
            serverStatus = "Permissions denied"
            Toast.makeText(this, "Storage permissions are required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FtpServerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FtpServerApp(
                        serverStatus = serverStatus,
                        ipAddress = ipAddress,
                        isServerRunning = isServerRunning,
                        onStartServer = { startFtpServer() },
                        onStopServer = { stopFtpServer() }
                    )
                }
            }
        }
        updateIpAddress()
    }

    private fun startFtpServer() {
        if (!checkPermissions()) {
            serverStatus = "Need permissions"
            return
        }

        ftpServer = FtpServer(this, 2121) { status ->
            runOnUiThread {
                serverStatus = status
            }
        }
        ftpServer?.start()
        isServerRunning = true
        updateIpAddress()
    }

    private fun stopFtpServer() {
        ftpServer?.stop()
        ftpServer = null
        isServerRunning = false
        serverStatus = "Stopped"
    }

    private fun checkPermissions(): Boolean {
        // For Android 10+ (API 29+), we need to request different permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, check if we have all files access
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback if the above intent doesn't work
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
                return false
            }
            return true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10, use legacy storage option and check basic permissions
            val permissions = arrayOf(
                Manifest.permission.INTERNET,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            val notGrantedPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (notGrantedPermissions.isNotEmpty()) {
                requestPermissionLauncher.launch(notGrantedPermissions.toTypedArray())
                return false
            }
            return true
        } else {
            // For Android 9 and below, check basic permissions
            val permissions = arrayOf(
                Manifest.permission.INTERNET,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

            val notGrantedPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (notGrantedPermissions.isNotEmpty()) {
                requestPermissionLauncher.launch(notGrantedPermissions.toTypedArray())
                return false
            }
            return true
        }
    }

    private fun updateIpAddress() {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val addresses = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val interfaceAddresses = networkInterface.inetAddresses
                    while (interfaceAddresses.hasMoreElements()) {
                        val address = interfaceAddresses.nextElement()
                        if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') < 0) {
                            addresses.add(address.hostAddress)
                        }
                    }
                }
            }

            ipAddress = if (addresses.isNotEmpty()) {
                "IP Address: ${addresses.joinToString(", ")}:2121"
            } else {
                "IP Address: Not available"
            }
        } catch (e: Exception) {
            ipAddress = "IP Address: Error ${e.message}"
        }
    }
}

@Composable
fun FtpServerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun FtpServerApp(
    serverStatus: String,
    ipAddress: String,
    isServerRunning: Boolean,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Title
        Text(
            text = "FTP Server",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = if (isServerRunning) Color.Green else Color.Red,
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Server Status: $serverStatus",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // IP Address
                Text(
                    text = ipAddress,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                // Info text
                if (isServerRunning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connect using any FTP client with the IP address above",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onStartServer,
                enabled = !isServerRunning,
                modifier = Modifier.padding(end = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start Server",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Start Server")
            }
            
            Button(
                onClick = onStopServer,
                enabled = isServerRunning,
                modifier = Modifier.padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Server",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("Stop Server")
            }
        }
        
        // FTP Icon
        Icon(
            imageVector = Icons.Default.CloudUpload,
            contentDescription = "FTP Server",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 32.dp)
                .size(64.dp)
        )
    }
}

private val Typography = androidx.compose.material3.Typography()