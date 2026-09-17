package com.gato.client.router.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gato.client.R
import com.gato.client.util.LocalSnackbarHostState
import com.gato.client.util.SnackbarHostStateScope
import com.gato.client.viewmodel.MainScreenViewModel

data class ServerInfo(
    val name: String,
    val iconRes: Int,
    val address: String,
    val port: Int,
    val hasSubServers: Boolean = false,
    val subServers: List<SubServerInfo> = emptyList()
)

data class SubServerInfo(
    val name: String,
    val region: String,
    val address: String,
    val port: Int,
    val isSelected: Boolean = false
)

val zeqaServers = listOf(
    // Asia Servers
    SubServerInfo("AS1", "Asia", "104.234.6.50", 10001),
    SubServerInfo("AS2", "Asia", "104.234.6.50", 10002),
    SubServerInfo("AS3", "Asia", "104.234.6.50", 10003),
    SubServerInfo("AS4", "Asia", "104.234.6.50", 10004),
    SubServerInfo("AS5", "Asia", "104.234.6.50", 10005),

    // Europe Servers
    SubServerInfo("EU1", "Europe", "178.32.145.167", 10001),
    SubServerInfo("EU2", "Europe", "178.32.145.167", 10002),
    SubServerInfo("EU3", "Europe", "178.32.145.167", 10003),
    SubServerInfo("EU4", "Europe", "178.32.145.167", 10004),
    SubServerInfo("EU5", "Europe", "178.32.145.167", 10005),

    // North America Servers
    SubServerInfo("NA1", "North America", "51.79.62.8", 10001),
    SubServerInfo("NA2", "North America", "51.79.62.8", 10002),
    SubServerInfo("NA3", "North America", "51.79.62.8", 10003),
    SubServerInfo("NA4", "North America", "51.79.62.8", 10004),
    SubServerInfo("NA5", "North America", "51.79.62.8", 10005),

    // South Africa Servers
    SubServerInfo("SA1", "South Africa", "38.54.63.126", 10001),
    SubServerInfo("SA2", "South Africa", "38.54.63.126", 10002),
    SubServerInfo("SA3", "South Africa", "38.54.63.126", 10003),
    SubServerInfo("SA4", "South Africa", "38.54.63.126", 10004),
    SubServerInfo("SA5", "South Africa", "38.54.63.126", 10005)
)

val servers = listOf(
    ServerInfo(
        name = "The Hive",
        iconRes = R.drawable.hive_icon,
        address = "geo.hivebedrock.network",
        port = 19132
    ),
    ServerInfo(
        name = "Lifeboat",
        iconRes = R.drawable.lifeboat_icon,
        address = "play.lbsg.net",
        port = 19132
    ),
    ServerInfo(
        name = "CubeCraft",
        iconRes = R.drawable.cubecraft_icon,
        address = "play.cubecraft.net",
        port = 19132
    ),
    ServerInfo(
        name = "Zeqa",
        iconRes = R.drawable.zeqa_icon,
        address = "",
        port = 19132,
        hasSubServers = true,
        subServers = zeqaServers
    ),
    ServerInfo(
        name = "DonutSMP",
        iconRes = R.drawable.donutsmp_icon,
        address = "donutsmp.net",
        port = 19132,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPageContent() {
    SnackbarHostStateScope {
        val mainScreenViewModel: MainScreenViewModel = viewModel()
        val snackbarHostState = LocalSnackbarHostState.current
        var selectedServer by remember { mutableStateOf<ServerInfo?>(null) }
        var showSubServers by remember { mutableStateOf(false) }
        var selectedSubServer by remember { mutableStateOf<SubServerInfo?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Servers") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)
                    )
                )
            },
            bottomBar = {
                SnackbarHost(
                    snackbarHostState,
                    modifier = Modifier.animateContentSize()
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) { padding ->
            Box {
                Column(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    servers.forEach { server ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (server.hasSubServers) {
                                        selectedServer = server
                                        showSubServers = true
                                    } else {
                                        selectedServer = server
                                        selectedSubServer = null
                                        showSubServers = false
                                        mainScreenViewModel.selectCaptureModeModel(
                                            mainScreenViewModel.captureModeModel.value.copy(
                                                serverHostName = server.address,
                                                serverPort = server.port
                                            )
                                        )
                                    }
                                }
                                .then(
                                    if (selectedServer == server) {
                                        Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.shapes.medium
                                        )
                                    } else Modifier
                                ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(server.iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Unspecified
                                )

                                Text(
                                    text = server.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }

                if (showSubServers && selectedServer?.hasSubServers == true) {
                    Dialog(onDismissRequest = { showSubServers = false }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    "Select Region and Server",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                selectedServer?.subServers?.groupBy { it.region }?.forEach { (region, subServers) ->
                                    Text(
                                        region,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )

                                    subServers.forEach { subServer ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedSubServer = subServer
                                                    mainScreenViewModel.selectCaptureModeModel(
                                                        mainScreenViewModel.captureModeModel.value.copy(
                                                            serverHostName = subServer.address,
                                                            serverPort = subServer.port
                                                        )
                                                    )
                                                    showSubServers = false
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = selectedSubServer == subServer,
                                                onClick = null
                                            )
                                            Text(
                                                subServer.name,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}