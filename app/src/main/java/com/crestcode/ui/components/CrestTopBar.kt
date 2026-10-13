package com.crestcode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crestcode.ui.theme.*

@Composable
fun CrestTopBar(
    projectName: String = "Crest Project",
    onMenuClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    onRunClick: () -> Unit = {},
    onUndoClick: () -> Unit = {},
    onRedoClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    isTracing: Boolean = false,
    onToggleTrace: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(CrestSurfaceHeader)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open File Explorer",
                tint = CrestTextPrimary
            )
        }

        Text(
            text = projectName,
            color = CrestTextActive,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )

        // CrestTrace Active Recording Indicator Pill
        if (isTracing) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE06C75).copy(alpha = 0.25f),
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clickable { onToggleTrace() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFE06C75), shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "REC",
                        color = Color(0xFFE06C75),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        IconButton(onClick = onSaveClick) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = "Save File",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onRunClick) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Run / Preview",
                tint = CrestAccentGreen
            )
        }

        IconButton(onClick = onUndoClick) {
            Icon(
                imageVector = Icons.Default.Undo,
                contentDescription = "Undo",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onRedoClick) {
            Icon(
                imageVector = Icons.Default.Redo,
                contentDescription = "Redo",
                tint = CrestTextPrimary
            )
        }

        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = CrestTextPrimary
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = CrestTextPrimary
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(CrestSurfaceHeader)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (isTracing) "Stop Trace & Export" else "Start CrestTrace (Diagnostics)",
                            color = if (isTracing) Color(0xFFE06C75) else CrestTextActive,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isTracing) Icons.Default.Stop else Icons.Default.Speed,
                            contentDescription = null,
                            tint = if (isTracing) Color(0xFFE06C75) else CrestAccentPrimary
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleTrace()
                    }
                )
            }
        }
    }
}
