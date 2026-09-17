package com.gato.client.router.main

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Slider
import com.gato.client.util.LocalSnackbarHostState
import com.gato.client.util.SnackbarHostStateScope
import androidx.core.content.edit
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gato.client.R
import com.gato.client.util.LocalSnackbarHostState
import com.gato.client.util.SnackbarHostStateScope
import com.gato.client.overlay.OverlayManager
import com.gato.client.ui.component.ColorPicker
import com.gato.client.ui.theme.ColorTheme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.edit
import com.gato.client.game.ModuleManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageContent() {
    SnackbarHostStateScope {
        val context = LocalContext.current
        val snackbarHostState = LocalSnackbarHostState.current
        val coroutineScope = rememberCoroutineScope()

        val sharedPreferences = remember {
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        }

        var showOpacityDialog by remember { mutableStateOf(false) }
        var showColumnsDialog by remember { mutableStateOf(false) }
        var showColorPickerDialog by remember { mutableStateOf(false) }
        var showThemeEditorDialog by remember { mutableStateOf(false) }
        var editingSlot by remember { mutableStateOf<ColorTheme.Slot?>(null) }
        var showImportField by remember { mutableStateOf(false) }
        var showVersionDialog by remember { mutableStateOf(false) }
        var showRelayLogsDialog by remember { mutableStateOf(false) }
        var relayLogText by remember { mutableStateOf("") }
        var mcVersionOverride by remember {
            mutableStateOf(sharedPreferences.getString("mc_version_override", "auto") ?: "auto")
        }
        var importText by remember { mutableStateOf("") }
        var selectedBorderColor by remember {
            mutableStateOf(
                Color(sharedPreferences.getInt("overlay_border_color", Color.Cyan.toArgb()))
            )
        }

        var columnCount by remember {
            mutableStateOf(sharedPreferences.getInt("module_columns", 2))
        }

        var overlayOpacity by remember {
            mutableStateOf(sharedPreferences.getFloat("overlay_opacity", 1f))
        }
        var shortcutOpacity by remember {
            mutableStateOf(sharedPreferences.getFloat("shortcut_opacity", 1f))
        }

        val iconPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { selectedUri ->
                try {
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        val options = BitmapFactory.Options().apply {
                            inScaled = false // Prevents automatic scaling
                        }
                        val bitmap = BitmapFactory.decodeStream(input, null, options)
                        // Save bitmap to app's internal storage
                        val iconFile = File(context.filesDir, "custom_overlay_icon.png")
                        if (bitmap != null) {
                            FileOutputStream(iconFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                        }
                        // Save the path in SharedPreferences
                        sharedPreferences.edit {
                            putString("overlay_icon_path", iconFile.absolutePath)
                        }
                        // Notify overlay to update icon
                        OverlayManager.updateOverlayIcon()
                    }
                } catch (e: Exception) {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Failed to set custom icon")
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)
                    )
                )
            },
            bottomBar = {
                SnackbarHost(
                    LocalSnackbarHostState.current,
                    modifier = Modifier.animateContentSize()
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Opacity Settings Card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = { showOpacityDialog = true }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Opacity,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.overlay_opacity_settings),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.overlay_opacity_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .scale(0.8f)
                                .size(20.dp)
                        )
                    }
                }

                // Module Columns Settings Card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = { showColumnsDialog = true }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.ViewColumn,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Module Layout",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Adjust number of module columns (${columnCount} columns)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .scale(0.8f)
                                .size(20.dp)
                        )
                    }
                }

                // Overlay Icon Settings Card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = { iconPickerLauncher.launch("image/*") }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.overlay_icon),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.overlay_icon_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Overlay Border Color Settings Card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = { showColorPickerDialog = true }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(selectedBorderColor, CircleShape)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.overlay_border_color),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(R.string.overlay_border_color_description),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Relay logs card
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        relayLogText = com.gato.client.util.RelayLog.asText()
                        showRelayLogsDialog = true
                    }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Registros del relay",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Diagnósticos de conexión y login ([AUTH-DIAG], errores)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Minecraft client version card (relay advertisement)
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = { showVersionDialog = true }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Versión del client Minecraft",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                if (mcVersionOverride == "auto") "Auto-detectada del teléfono"
                                else "Fijada: $mcVersionOverride",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Interface personalization card (full theme editor)
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = { showThemeEditorDialog = true }
                ) {
                    Row(
                        Modifier.padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(ColorTheme.accent.value, CircleShape)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Personalización de interfaz",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Colores de toda la app y la click gui, configurables uno por uno",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                var showFileNameDialog by remember { mutableStateOf(false) }
                var configFileName by remember { mutableStateOf("") }
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    uri?.let {
                        if (ModuleManager.importConfigFromFile(context, it)) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Config imported successfully")
                            }
                        } else {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Failed to import config")
                            }
                        }
                    }
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Config Management",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    "Save and load module configurations",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = { filePickerLauncher.launch("application/json") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Import Config")
                        }

                        FilledTonalButton(
                            onClick = { showFileNameDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Rounded.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Export Config")
                        }
                    }
                }

                if (showFileNameDialog) {
                    BasicAlertDialog(
                        onDismissRequest = {
                            showFileNameDialog = false
                            configFileName = ""
                        },
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = AlertDialogDefaults.shape,
                            tonalElevation = AlertDialogDefaults.TonalElevation
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "Save Configuration",
                                    style = MaterialTheme.typography.headlineSmall
                                )

                                OutlinedTextField(
                                    value = configFileName,
                                    onValueChange = { configFileName = it },
                                    label = { Text("Configuration Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    supportingText = { Text("The config will be saved as '$configFileName.json'") }
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            showFileNameDialog = false
                                            configFileName = ""
                                        }
                                    ) {
                                        Text("Cancel")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            if (configFileName.isNotBlank()) {
                                                if (ModuleManager.exportConfigToFile(context, configFileName)) {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Configuration saved successfully")
                                                    }
                                                } else {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar("Failed to save configuration")
                                                    }
                                                }
                                            }
                                            showFileNameDialog = false
                                            configFileName = ""
                                        },
                                        enabled = configFileName.isNotBlank()
                                    ) {
                                        Text("Save")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Opacity Dialog
        if (showOpacityDialog) {
            BasicAlertDialog(
                onDismissRequest = { showOpacityDialog = false },
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {
                    Column(
                        Modifier
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.overlay_opacity_settings),
                            modifier = Modifier.align(Alignment.Start),
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.overlay_opacity),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Slider(
                                    value = overlayOpacity,
                                    onValueChange = { value ->
                                        overlayOpacity = value
                                        sharedPreferences.edit {
                                            putFloat("overlay_opacity", value)
                                        }
                                        OverlayManager.currentContext?.let {
                                            OverlayManager.updateOverlayOpacity(value)
                                        }
                                    },
                                    valueRange = 0.0f..1f
                                )
                            }

                            Column {
                                Text(
                                    stringResource(R.string.shortcut_opacity),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Slider(
                                    value = shortcutOpacity,
                                    onValueChange = { value ->
                                        shortcutOpacity = value
                                        sharedPreferences.edit {
                                            putFloat("shortcut_opacity", value)
                                        }
                                        OverlayManager.currentContext?.let {
                                            OverlayManager.updateShortcutOpacity(value)
                                        }
                                    },
                                    valueRange = 0.0f..1f
                                )
                            }
                        }
                    }
                }
            }
        }

        // Module Columns Dialog
        if (showColumnsDialog) {
            BasicAlertDialog(
                onDismissRequest = { showColumnsDialog = false },
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Module Layout Settings",
                            modifier = Modifier.align(Alignment.Start),
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Column {
                            Text(
                                "Number of Columns",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Slider(
                                value = columnCount.toFloat(),
                                onValueChange = { value ->
                                    val newCount = value.toInt()
                                    columnCount = newCount
                                    sharedPreferences.edit {
                                        putInt("module_columns", newCount)
                                    }
                                },
                                valueRange = 1f..3f,
                                steps = 1
                            )
                            Text(
                                "${columnCount.toInt()} columns",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Overlay Border Color Dialog
        if (showColorPickerDialog) {
            BasicAlertDialog(
                onDismissRequest = { showColorPickerDialog = false },
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.overlay_border_color),
                            style = MaterialTheme.typography.headlineSmall
                        )

                        // Predefined color options
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val colors = listOf(
                                Color.Cyan, Color.Red, Color.Green, Color.Blue,
                                Color.Yellow, Color.Magenta, Color.White,
                                Color.Gray, Color.DarkGray, Color.LightGray
                            )

                            items(colors.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(colors[index], CircleShape)
                                        .border(
                                            width = if (selectedBorderColor == colors[index]) 2.dp else 1.dp,
                                            color = if (selectedBorderColor == colors[index])
                                                MaterialTheme.colorScheme.primary
                                            else
                                                Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedBorderColor = colors[index]
                                            sharedPreferences.edit {
                                                putInt("overlay_border_color", colors[index].toArgb())
                                            }
                                            OverlayManager.updateOverlayBorder()
                                            showColorPickerDialog = false
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Relay logs viewer dialog
        if (showRelayLogsDialog) {
            BasicAlertDialog(
                onDismissRequest = { showRelayLogsDialog = false },
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Registros del relay", style = MaterialTheme.typography.headlineSmall)

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                        ) {
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                androidx.compose.foundation.rememberScrollState().let { scroll ->
                                    Column(
                                        Modifier
                                            .verticalScroll(scroll)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            relayLogText.ifBlank { "(vacío — activa el relay y conéctate primero)" },
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("gato_logs", relayLogText)
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Logs copiados")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Copiar") }
                            FilledTonalButton(
                                onClick = {
                                    com.gato.client.util.RelayLog.clear()
                                    relayLogText = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Limpiar") }
                            TextButton(onClick = { showRelayLogsDialog = false }) { Text("Cerrar") }
                        }
                    }
                }
            }
        }

        // Minecraft version selector dialog
        if (showVersionDialog) {
            BasicAlertDialog(
                onDismissRequest = { showVersionDialog = false },
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {
                    Column(
                        Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Versión del client Minecraft",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "El relay anuncia esta versión al conectarte. 'Auto' usa la de Minecraft instalada en el teléfono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val versions = listOf("auto") + com.gato.relay.util.SUPPORTED_VERSIONS.keys.toList()
                        versions.forEach { version ->
                            val selected = mcVersionOverride == version
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        mcVersionOverride = version
                                        sharedPreferences.edit { putString("mc_version_override", version) }
                                        showVersionDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = selected,
                                    onClick = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (version == "auto") "Auto (detectar del teléfono)"
                                    else version,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        // Interface personalization editor
        if (showThemeEditorDialog) {
            BasicAlertDialog(
                onDismissRequest = { showThemeEditorDialog = false },
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Personalización de interfaz",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "Toca un elemento para editar su color. La config se guarda sola y puedes copiarla/pegarla como texto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ColorTheme.all.forEach { slot ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editingSlot = slot }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(slot.value, CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                                Text(slot.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("gato_theme", ColorTheme.exportJson())
                                    )
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Config copiada al portapapeles")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copiar config")
                            }
                            FilledTonalButton(
                                onClick = { showImportField = !showImportField },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Pegar config")
                            }
                            FilledTonalButton(
                                onClick = { ColorTheme.resetAll() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Restablecer")
                            }
                        }

                        if (showImportField) {
                            OutlinedTextField(
                                value = importText,
                                onValueChange = { importText = it },
                                label = { Text("Pega aquí la config JSON") },
                                singleLine = false,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FilledTonalButton(
                                onClick = {
                                    val result = ColorTheme.importJson(importText)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(
                                            if (result > 0) "Config aplicada ($result colores)"
                                            else "Config inválida"
                                        )
                                    }
                                },
                                enabled = importText.isNotBlank(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Aplicar config")
                            }
                        }
                    }
                }
            }
        }

        // per-slot color picker dialog
        editingSlot?.let { slot ->
            BasicAlertDialog(
                onDismissRequest = { editingSlot = null },
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                Surface(
                    shape = AlertDialogDefaults.shape,
                    tonalElevation = AlertDialogDefaults.TonalElevation
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(slot.label, style = MaterialTheme.typography.headlineSmall)
                        ColorPicker(
                            initialColor = slot.value,
                            onColorChange = { ColorTheme.setSlot(slot, it) }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { editingSlot = null }) {
                                Text("Listo")
                            }
                        }
                    }
                }
            }
        }
    }
}