package com.crestcode.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.crestcode.ui.theme.CrestAccentPrimary
import com.crestcode.ui.theme.CrestSurfaceHeader
import com.crestcode.ui.theme.CrestTextActive
import kotlin.math.hypot

/**
 * Single Full-Editor Overlay Canvas for Selection Highlights & Teardrop Handles.
 * Handles GPU drawing of selection bounds & handles, and single-point gesture interception.
 */
@Composable
fun CrestSelectionOverlay(
    engine: NativeEditorEngine,
    state: EditorRenderState,
    onSelectionChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selAnchor = state.selection.anchor
    val selHead = state.selection.head

    val selStart = if (selAnchor.line < selHead.line || (selAnchor.line == selHead.line && selAnchor.character <= selHead.character)) {
        selAnchor
    } else {
        selHead
    }

    val selEnd = if (selAnchor.line < selHead.line || (selAnchor.line == selHead.line && selAnchor.character <= selHead.character)) {
        selHead
    } else {
        selAnchor
    }

    val isSelectionActive = (selStart.line != selEnd.line || selStart.character != selEnd.character)
    if (!isSelectionActive) return

    val density = LocalDensity.current
    val fontWidthPx = with(density) { 13.sp.toPx() * 0.6f }
    val lineHeightPx = with(density) { 20.dp.toPx() }
    val handleSizePx = with(density) { 22.dp.toPx() }
    val hitRadiusPx = with(density) { 36.dp.toPx() }

    val startLineText = state.lines.getOrNull(selStart.line) ?: ""
    val startChar = selStart.character.coerceIn(0, startLineText.length)
    val startX = fontWidthPx * startChar
    val startY = lineHeightPx * selStart.line

    val endLineText = state.lines.getOrNull(selEnd.line) ?: ""
    val endChar = selEnd.character.coerceIn(0, endLineText.length)
    val endX = fontWidthPx * endChar
    val endY = lineHeightPx * selEnd.line

    // Handle touch centers
    val startHandleCenterX = startX - (handleSizePx / 2f)
    val startHandleCenterY = startY + lineHeightPx + (handleSizePx / 2f)
    val endHandleCenterX = endX + (handleSizePx / 2f)
    val endHandleCenterY = endY + lineHeightPx + (handleSizePx / 2f)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .zIndex(100f)
            .pointerInput(state.lineCount, state.selection) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial)
                    val downPos = down.position

                    val distStart = hypot(downPos.x - startHandleCenterX, downPos.y - startHandleCenterY)
                    val distEnd = hypot(downPos.x - endHandleCenterX, downPos.y - endHandleCenterY)

                    val isDraggingStart = distStart <= hitRadiusPx
                    val isDraggingEnd = !isDraggingStart && (distEnd <= hitRadiusPx)

                    if (isDraggingStart || isDraggingEnd) {
                        down.consume()
                        var currTouchX = downPos.x
                        var currTouchY = downPos.y

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break

                            val dragAmount = change.positionChange()
                            change.consume()

                            currTouchX += dragAmount.x
                            currTouchY += dragAmount.y

                            val targetLine = (currTouchY / lineHeightPx).toInt().coerceIn(0, (state.lineCount - 1).coerceAtLeast(0))
                            val targetLineText = state.lines.getOrNull(targetLine) ?: ""
                            val targetChar = if (fontWidthPx > 0) {
                                (currTouchX / fontWidthPx).toInt().coerceIn(0, targetLineText.length)
                            } else 0

                            if (isDraggingStart) {
                                if (targetLine < selEnd.line || (targetLine == selEnd.line && targetChar <= selEnd.character)) {
                                    engine.setSelection(targetLine, targetChar, selEnd.line, selEnd.character)
                                    onSelectionChanged()
                                }
                            } else {
                                if (targetLine > selStart.line || (targetLine == selStart.line && targetChar >= selStart.character)) {
                                    engine.setSelection(selStart.line, selStart.character, targetLine, targetChar)
                                    onSelectionChanged()
                                }
                            }
                        }
                    }
                }
            }
    ) {
        // 1. Draw Selection Highlights across lines
        for (l in selStart.line..selEnd.line.coerceAtMost(state.lineCount - 1)) {
            val lineText = state.lines.getOrNull(l) ?: ""
            val cStart = if (l == selStart.line) selStart.character.coerceIn(0, lineText.length) else 0
            val cEnd = if (l == selEnd.line) selEnd.character.coerceIn(0, lineText.length) else lineText.length

            val rectLeft = cStart * fontWidthPx
            val rectWidth = ((cEnd - cStart) * fontWidthPx).coerceAtLeast(6f)
            val rectTop = l * lineHeightPx

            drawRect(
                color = CrestAccentPrimary.copy(alpha = 0.35f),
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectWidth, lineHeightPx)
            )
        }

        // 2. Draw Selection Start Handle & Vertical Cursor Line
        // Vertical line
        drawRect(
            color = Color.White,
            topLeft = Offset(startX, startY),
            size = Size(with(density) { 2.dp.toPx() }, lineHeightPx)
        )
        // Left teardrop handle (curves down & left)
        val leftPath = Path().apply {
            val topR = Offset(startX, startY + lineHeightPx)
            val size = handleSizePx
            moveTo(topR.x, topR.y)
            lineTo(topR.x - size, topR.y)
            lineTo(topR.x - size, topR.y + size)
            lineTo(topR.x, topR.y + size)
            close()
        }
        drawPath(path = leftPath, color = CrestAccentPrimary)

        // 3. Draw Selection End Handle & Vertical Cursor Line
        // Vertical line
        drawRect(
            color = Color.White,
            topLeft = Offset(endX, endY),
            size = Size(with(density) { 2.dp.toPx() }, lineHeightPx)
        )
        // Right teardrop handle (curves down & right)
        val rightPath = Path().apply {
            val topL = Offset(endX, endY + lineHeightPx)
            val size = handleSizePx
            moveTo(topL.x, topL.y)
            lineTo(topL.x + size, topL.y)
            lineTo(topL.x + size, topL.y + size)
            lineTo(topL.x, topL.y + size)
            close()
        }
        drawPath(path = rightPath, color = CrestAccentPrimary)
    }
}

/**
 * Floating Context Menu Toolbar (Copy, Cut, Paste, Select All)
 */
@Composable
fun CrestSelectionToolbar(
    engine: NativeEditorEngine,
    onStateRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    Surface(
        modifier = modifier.padding(top = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = CrestSurfaceHeader,
        shadowElevation = 8.dp,
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, CrestAccentPrimary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    val selText = engine.getSelectedText()
                    if (selText.isNotEmpty()) {
                        clipboardManager?.setPrimaryClip(ClipData.newPlainText("CrestCode", selText))
                    }
                }
            ) {
                Text("Copy", color = CrestTextActive, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = {
                    val selText = engine.getSelectedText()
                    if (selText.isNotEmpty()) {
                        clipboardManager?.setPrimaryClip(ClipData.newPlainText("CrestCode", selText))
                        engine.deleteSelection()
                        onStateRefresh()
                    }
                }
            ) {
                Text("Cut", color = CrestTextActive, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = {
                    val clip = clipboardManager?.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                        if (pasteText.isNotEmpty()) {
                            engine.insertText(pasteText)
                            onStateRefresh()
                        }
                    }
                }
            ) {
                Text("Paste", color = CrestTextActive, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = {
                    engine.selectAll()
                    onStateRefresh()
                }
            ) {
                Text("Select All", color = CrestAccentPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
