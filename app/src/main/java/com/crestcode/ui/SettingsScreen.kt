package com.crestcode.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crestcode.core.settings.SettingDefinition
import com.crestcode.core.settings.SettingType
import com.crestcode.core.settings.SettingsManager
import com.crestcode.ui.theme.*
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler { onClose() }

    // Observe reactive settings state
    val settingsState by settingsManager.settingsState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var isRawJsonMode by remember { mutableStateOf(false) }

    // Raw JSON text state
    var rawJsonText by remember { mutableStateOf(settingsManager.getRawJson()) }
    var jsonError by remember { mutableStateOf<String?>(null) }

    // Dialog for enum selection
    var enumDialogTarget by remember { mutableStateOf<SettingDefinition?>(null) }
    // Confirmation dialog for Reset All
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val categories = remember {
        listOf("All", "Files", "Editor", "Terminal", "Appearance", "Modified")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isRawJsonMode) "settings.json" else "Settings",
                            color = CrestTextActive,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isRawJsonMode) "Direct JSON configuration (AI & Devs)" else "Preferences & Configuration",
                            color = CrestTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = CrestTextPrimary
                        )
                    }
                },
                actions = {
                    // Toggle between GUI and Raw JSON
                    IconButton(onClick = {
                        if (!isRawJsonMode) {
                            rawJsonText = settingsManager.getRawJson()
                            jsonError = null
                        }
                        isRawJsonMode = !isRawJsonMode
                    }) {
                        Icon(
                            imageVector = if (isRawJsonMode) Icons.Default.Tune else Icons.Default.Code,
                            contentDescription = if (isRawJsonMode) "Switch to UI" else "Open settings.json",
                            tint = if (isRawJsonMode) CrestAccentGreen else CrestTextPrimary
                        )
                    }

                    // Reset All
                    IconButton(onClick = { showResetConfirmDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset All Settings",
                            tint = CrestTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CrestSurfaceHeader
                )
            )
        },
        containerColor = CrestBackground,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->

        if (isRawJsonMode) {
            // Raw JSON View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                if (jsonError != null) {
                    Surface(
                        color = CrestAccentRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CrestAccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = jsonError ?: "",
                                color = CrestAccentRed,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = rawJsonText,
                    onValueChange = {
                        rawJsonText = it
                        jsonError = null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = CrestTextActive
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CrestSurface,
                        unfocusedContainerColor = CrestSurface,
                        focusedBorderColor = CrestAccentPrimary,
                        unfocusedBorderColor = CrestBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val obj = JSONObject(rawJsonText)
                                rawJsonText = obj.toString(2)
                                jsonError = null
                            } catch (e: Exception) {
                                jsonError = "Format error: ${e.message}"
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrestTextPrimary)
                    ) {
                        Text("Format JSON")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val res = settingsManager.setRawJson(rawJsonText)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
                                jsonError = null
                                isRawJsonMode = false
                            } else {
                                jsonError = "Invalid JSON: ${res.exceptionOrNull()?.message}"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrestAccentPrimary)
                    ) {
                        Text("Save & Apply")
                    }
                }
            }
        } else {
            // VS Code Style GUI View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text("Search settings (e.g. autoSave, fontSize)", color = CrestTextSecondary, fontSize = 14.sp)
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CrestTextSecondary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = CrestTextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CrestSurface,
                            unfocusedContainerColor = CrestSurface,
                            focusedBorderColor = CrestAccentPrimary,
                            unfocusedBorderColor = CrestBorder,
                            cursorColor = CrestAccentPrimary
                        )
                    )
                }

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            color = if (isSelected) CrestAccentPrimary else CrestSurface,
                            shape = RoundedCornerShape(16.dp),
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, CrestBorder),
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else CrestTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Divider(color = CrestBorder, thickness = 1.dp, modifier = Modifier.padding(top = 8.dp))

                // Filtered Settings List
                val filteredSettings = remember(searchQuery, selectedCategory, settingsState) {
                    SettingsManager.SCHEMA.filter { def ->
                        // Search query match
                        val matchesSearch = searchQuery.isEmpty() ||
                                def.title.contains(searchQuery, ignoreCase = true) ||
                                def.key.contains(searchQuery, ignoreCase = true) ||
                                def.description.contains(searchQuery, ignoreCase = true)

                        // Category match
                        val matchesCategory = when (selectedCategory) {
                            "All" -> true
                            "Modified" -> settingsManager.isModified(def.key)
                            "Files" -> def.category == SettingsManager.CAT_FILES
                            "Editor" -> def.category == SettingsManager.CAT_EDITOR
                            "Terminal" -> def.category == SettingsManager.CAT_TERMINAL
                            "Appearance" -> def.category == SettingsManager.CAT_APPEARANCE
                            else -> true
                        }

                        matchesSearch && matchesCategory
                    }
                }

                if (filteredSettings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedCategory == "Modified") "No settings have been modified from defaults." else "No settings match your search.",
                            color = CrestTextSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(filteredSettings, key = { it.key }) { def ->
                            val isModified = settingsManager.isModified(def.key)
                            val currentValue = settingsState[def.key] ?: def.defaultValue

                            SettingCard(
                                definition = def,
                                currentValue = currentValue,
                                isModified = isModified,
                                onValueChange = { newVal ->
                                    settingsManager.set(def.key, newVal)
                                },
                                onReset = {
                                    settingsManager.reset(def.key)
                                },
                                onOpenEnumPicker = {
                                    enumDialogTarget = def
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Enum Picker Dialog
    if (enumDialogTarget != null) {
        val target = enumDialogTarget!!
        val currentVal = settingsState[target.key]?.toString() ?: target.defaultValue.toString()

        AlertDialog(
            onDismissRequest = { enumDialogTarget = null },
            title = {
                Text(
                    text = target.title,
                    color = CrestTextActive,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = target.description,
                        color = CrestTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    target.options.forEach { opt ->
                        val isChosen = opt == currentVal
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isChosen) CrestAccentPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    settingsManager.set(target.key, opt)
                                    enumDialogTarget = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = opt,
                                color = if (isChosen) CrestAccentPrimary else CrestTextPrimary,
                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                            if (isChosen) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CrestAccentPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { enumDialogTarget = null }) {
                    Text("Close", color = CrestAccentPrimary)
                }
            },
            containerColor = CrestSurface
        )
    }

    // Reset All Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset all settings?", color = CrestTextActive) },
            text = {
                Text(
                    "This will restore all configuration options in settings.json to their factory default values.",
                    color = CrestTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsManager.resetAll()
                        showResetConfirmDialog = false
                        Toast.makeText(context, "All settings restored to default", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reset All", color = CrestAccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel", color = CrestTextPrimary)
                }
            },
            containerColor = CrestSurface
        )
    }
}

/**
 * Individual Setting Card replicating the VS Code Settings Tree item appearance.
 */
@Composable
fun SettingCard(
    definition: SettingDefinition,
    currentValue: Any,
    isModified: Boolean,
    onValueChange: (Any) -> Unit,
    onReset: () -> Unit,
    onOpenEnumPicker: () -> Unit
) {
    Surface(
        color = CrestSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CrestBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Blue vertical modified indicator line (identical to VS Code!)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (isModified) CrestAccentPrimary else Color.Transparent)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                // Header: Category & Setting Key
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = definition.category.uppercase(),
                        color = CrestAccentPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = definition.key,
                            color = CrestTextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )

                        if (isModified) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = onReset,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset to default",
                                    tint = CrestTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Setting Title
                Text(
                    text = definition.title,
                    color = CrestTextActive,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Setting Description
                Text(
                    text = definition.description,
                    color = CrestTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Control Widget based on Type
                when (definition.type) {
                    SettingType.ENUM -> {
                        Surface(
                            color = CrestBackground,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CrestBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEnumPicker() }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentValue.toString(),
                                    color = CrestTextActive,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = CrestTextSecondary
                                )
                            }
                        }
                    }

                    SettingType.BOOLEAN -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (currentValue as? Boolean == true) "Enabled" else "Disabled",
                                color = if (currentValue as? Boolean == true) CrestAccentGreen else CrestTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Switch(
                                checked = currentValue as? Boolean ?: false,
                                onCheckedChange = { onValueChange(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CrestAccentPrimary,
                                    uncheckedThumbColor = CrestTextSecondary,
                                    uncheckedTrackColor = CrestSurfaceHeader
                                )
                            )
                        }
                    }

                    SettingType.INT -> {
                        val currentNum = (currentValue as? Number)?.toInt() ?: (definition.defaultValue as Int)
                        val min = definition.min ?: 0
                        val max = definition.max ?: 99999

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    val step = if (definition.key.contains("Delay")) 500 else 1
                                    if (currentNum - step >= min) {
                                        onValueChange(currentNum - step)
                                    }
                                },
                                enabled = currentNum > min,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = CrestSurfaceHeader,
                                    contentColor = CrestTextActive
                                )
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                            }

                            Surface(
                                color = CrestBackground,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CrestBorder),
                                modifier = Modifier.width(90.dp)
                            ) {
                                Text(
                                    text = "$currentNum${if (definition.key.contains("Delay")) " ms" else if (definition.key.contains("FontSize")) " px" else ""}",
                                    color = CrestTextActive,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    val step = if (definition.key.contains("Delay")) 500 else 1
                                    if (currentNum + step <= max) {
                                        onValueChange(currentNum + step)
                                    }
                                },
                                enabled = currentNum < max,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = CrestSurfaceHeader,
                                    contentColor = CrestTextActive
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    SettingType.STRING -> {
                        OutlinedTextField(
                            value = currentValue.toString(),
                            onValueChange = { onValueChange(it) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CrestBackground,
                                unfocusedContainerColor = CrestBackground,
                                focusedBorderColor = CrestAccentPrimary,
                                unfocusedBorderColor = CrestBorder
                            )
                        )
                    }
                }
            }
        }
    }
}
