package com.crestcode.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TermuxExtraKeysToolbar(
    isCtrlActive: Boolean,
    isAltActive: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onKeyClick: (String) -> Unit
) {
    val scrollState1 = rememberScrollState()
    val scrollState2 = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: ESC, ⌨, HOME, ↑, END, PGUP
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState1)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TermuxKeyButton(label = "ESC", isActive = false, onClick = { onKeyClick("\u001b") })
                TermuxKeyButton(label = "⌨", isActive = false, onClick = onToggleKeyboard)
                TermuxKeyButton(label = "HOME", isActive = false, onClick = { onKeyClick("\u001b[1~") })
                TermuxKeyButton(label = "↑", isActive = false, onClick = { onKeyClick("\u001b[A") })
                TermuxKeyButton(label = "END", isActive = false, onClick = { onKeyClick("\u001b[4~") })
                TermuxKeyButton(label = "PGUP", isActive = false, onClick = { onKeyClick("\u001b[5~") })
            }

            // Row 2: TAB, CTRL, ALT, ←, ↓, →, PGDN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState2)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TermuxKeyButton(label = "TAB", isActive = false, onClick = { onKeyClick("\t") })
                TermuxKeyButton(label = "CTRL", isActive = isCtrlActive, onClick = onToggleCtrl)
                TermuxKeyButton(label = "ALT", isActive = isAltActive, onClick = onToggleAlt)
                TermuxKeyButton(label = "←", isActive = false, onClick = { onKeyClick("\u001b[D") })
                TermuxKeyButton(label = "↓", isActive = false, onClick = { onKeyClick("\u001b[B") })
                TermuxKeyButton(label = "→", isActive = false, onClick = { onKeyClick("\u001b[C") })
                TermuxKeyButton(label = "PGDN", isActive = false, onClick = { onKeyClick("\u001b[6~") })
            }
        }
    }
}

@Composable
fun TermuxKeyButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (isActive) 8.dp else 2.dp,
        border = BorderStroke(
            1.dp,
            if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = Modifier.height(34.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}
