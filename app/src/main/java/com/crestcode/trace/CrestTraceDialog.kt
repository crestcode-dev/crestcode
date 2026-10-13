package com.crestcode.trace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crestcode.ui.theme.*

@Composable
fun CrestTraceDialog(
    reportText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CrestTrace Report",
                    color = CrestTextActive,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                Text(
                    text = "Detailed UI performance diagnostics captured below. You can share this report or copy it to the clipboard.",
                    color = CrestTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(CrestBackground, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = reportText,
                        color = CrestTextActive,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("CrestTrace Report", reportText))
                        Toast.makeText(context, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrestAccentPrimary)
                ) {
                    Text("Copy")
                }

                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "CrestTrace Diagnostic Report")
                                putExtra(Intent.EXTRA_TEXT, reportText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share CrestTrace Report"))
                        } catch (t: Throwable) {
                            Toast.makeText(context, "Could not open share chooser: ${t.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrestAccentPrimary)
                ) {
                    Text("Share", color = CrestTextActive)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = CrestTextSecondary)
            }
        },
        containerColor = CrestSurfaceHeader
    )
}
