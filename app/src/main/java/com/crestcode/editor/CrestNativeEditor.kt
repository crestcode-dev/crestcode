package com.crestcode.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.crestcode.trace.CrestTrace
import com.crestcode.ui.theme.*

@Composable
fun CrestNativeEditor(
    engine: NativeEditorEngine,
    activeTabId: String,
    onContentChanged: () -> Unit,
    keyboardToggleTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val editorState = remember { mutableStateOf(engine.getEditorState()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    SideEffect {
        if (CrestTrace.isRecording) {
            CrestTrace.recordRecomposition("CrestNativeEditor")
        }
    }
    val lazyListState = rememberLazyListState()
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val context = LocalContext.current
    var inputViewInstance by remember { mutableStateOf<CrestInputView?>(null) }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    val lineTracker = remember(activeTabId) { EditorLineTracker() }
    val editorLines = remember(activeTabId) {
        val initialLines = engine.getRenderState().lines
        mutableStateOf(lineTracker.setLines(initialLines))
    }
    val highlightCache = remember(activeTabId) {
        mutableMapOf<Long, Pair<Int, List<TokenSpan>>>()
    }

    fun requestInputFocus() {
        inputViewInstance?.let { view ->
            view.requestFocus()
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        } ?: run {
            keyboardController?.show()
        }
    }

    // Refresh state when explicitly requested (e.g. undo/redo or full sync)
    fun refreshState() {
        val es = engine.getEditorState()
        val full = engine.getRenderState()
        editorLines.value = lineTracker.updateLines(full.lines)
        editorState.value = es
        if (es.isModified) {
            onContentChanged()
        }
    }

    // Connect engine callbacks for zero-copy incremental updates
    DisposableEffect(engine) {
        engine.onCursorMoved = { newState ->
            editorState.value = newState
        }
        engine.onContentModified = { resp ->
            if (resp.change != null) {
                editorLines.value = CrestTrace.traceOp("EditorLineTracker.applyChange") {
                    lineTracker.applyChange(resp.change)
                }
            } else {
                val full = engine.getRenderState()
                editorLines.value = CrestTrace.traceOp("EditorLineTracker.updateLines") {
                    lineTracker.updateLines(full.lines)
                }
            }
            if (resp.state != null) {
                editorState.value = resp.state
            }
            onContentChanged()
        }
        engine.onStateChanged = {
            editorState.value = engine.getEditorState()
        }
        onDispose {
            engine.onCursorMoved = null
            engine.onContentModified = null
            engine.onStateChanged = null
        }
    }

    LaunchedEffect(keyboardToggleTrigger) {
        if (keyboardToggleTrigger > 0) {
            requestInputFocus()
        }
    }

    // Automatically update render state when active tab changes
    LaunchedEffect(activeTabId) {
        lineTracker.reset()
        highlightCache.clear()
        val es = engine.getEditorState()
        val full = engine.getRenderState()
        editorLines.value = lineTracker.setLines(full.lines)
        editorState.value = es
        requestInputFocus()
        val cur = es.cursor.line.coerceIn(0, (es.lineCount - 1).coerceAtLeast(0))
        lazyListState.scrollToItem(cur)
    }

    val state = editorState.value
    val lines = editorLines.value

    // Synchronize scroll position with active cursor line when cursor leaves viewport
    LaunchedEffect(state.cursor.line) {
        val layoutInfo = lazyListState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty()) {
            val first = visibleItems.first().index
            val last = visibleItems.last().index
            val cur = state.cursor.line
            if (cur < first || cur > last) {
                lazyListState.scrollToItem(cur.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))
            }
        }
    }

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

    // Track scroll events during active recording
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            Triple(
                lazyListState.firstVisibleItemIndex,
                lazyListState.layoutInfo.visibleItemsInfo.size,
                lazyListState.firstVisibleItemScrollOffset
            )
        }.collect { (first, count, offset) ->
            if (CrestTrace.isRecording) {
                CrestTrace.recordScroll(first, count, offset)
            }
        }
    }

    // Lazy on-demand syntax highlighting strictly for rendered lines
    fun getSpansForLine(lineIdx: Int, lineModel: EditorLine): List<TokenSpan> {
        val t0 = if (CrestTrace.isRecording) System.nanoTime() else 0L
        val cached = highlightCache[lineModel.id]
        if (cached != null && cached.first == lineModel.text.hashCode()) {
            if (CrestTrace.isRecording) {
                CrestTrace.recordHighlightLookup(hit = true, durationNanos = System.nanoTime() - t0)
            }
            return cached.second
        }
        val spans = engine.getHighlightSpans(lineIdx, lineIdx)[lineIdx] ?: emptyList()
        highlightCache[lineModel.id] = Pair(lineModel.text.hashCode(), spans)
        if (CrestTrace.isRecording) {
            CrestTrace.recordHighlightLookup(hit = false, durationNanos = System.nanoTime() - t0)
        }
        return spans
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(CrestBackground)
            .pointerInput(Unit) {
                detectTapGestures {
                    requestInputFocus()
                }
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    val isCtrlOrCmd = keyEvent.isCtrlPressed || keyEvent.isMetaPressed
                    if (isCtrlOrCmd) {
                        when (keyEvent.key) {
                            Key.C -> {
                                val selText = engine.getSelectedText()
                                if (selText.isNotEmpty()) {
                                    clipboardManager?.setPrimaryClip(ClipData.newPlainText("CrestCode", selText))
                                }
                                true
                            }
                            Key.V -> {
                                val clip = clipboardManager?.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                    if (pasteText.isNotEmpty()) {
                                        engine.insertText(pasteText)
                                    }
                                }
                                true
                            }
                            Key.X -> {
                                val selText = engine.getSelectedText()
                                if (selText.isNotEmpty()) {
                                    clipboardManager?.setPrimaryClip(ClipData.newPlainText("CrestCode", selText))
                                    engine.deleteSelection()
                                }
                                true
                            }
                            Key.A -> {
                                engine.selectAll()
                                true
                            }
                            Key.Z -> {
                                if (keyEvent.isShiftPressed) {
                                    engine.redo()
                                } else {
                                    engine.undo()
                                }
                                true
                            }
                            Key.Y -> {
                                engine.redo()
                                true
                            }
                            else -> false
                        }
                    } else {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                engine.moveCursor("left", keyEvent.isShiftPressed)
                                true
                            }
                            Key.DirectionRight -> {
                                engine.moveCursor("right", keyEvent.isShiftPressed)
                                true
                            }
                            Key.DirectionUp -> {
                                engine.moveCursor("up", keyEvent.isShiftPressed)
                                true
                            }
                            Key.DirectionDown -> {
                                engine.moveCursor("down", keyEvent.isShiftPressed)
                                true
                            }
                            Key.Tab -> {
                                engine.insertText("    ")
                                true
                            }
                            else -> false
                        }
                    }
                } else false
            }
            .focusable()
    ) {
        val minEditorWidth = maxWidth

        // Custom Android InputView hosting raw Zed-style InputConnection
        AndroidView(
            factory = { ctx ->
                CrestInputView(ctx, engine, onContentChanged = { /* Engine callbacks handle updates */ }).also { view ->
                    inputViewInstance = view
                    view.requestFocus()
                    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            },
            update = { view ->
                inputViewInstance = view
            },
            modifier = Modifier.size(1.dp)
        )

        val lineCountDigits = lines.size.coerceAtLeast(state.lineCount).toString().length.coerceAtLeast(2)
        val charWidthDp = with(density) { (13.sp.toPx() * 0.6f).toDp() }
        val fontWidthPx = with(density) { 13.sp.toPx() * 0.6f }
        val lineNumberWidth = (lineCountDigits * charWidthDp.value).dp + 24.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(
                    count = lines.size.coerceAtLeast(1),
                    key = { idx -> lines.getOrNull(idx)?.id ?: idx.toLong() }
                ) { lineIdx ->
                    val lineModel = lines.getOrNull(lineIdx)
                    val lineText = lineModel?.text ?: ""
                    val spans = if (lineModel != null) getSpansForLine(lineIdx, lineModel) else emptyList()
                    val isCurrentLine = (lineIdx == state.cursor.line)
                    val cursorCol = if (isCurrentLine) state.cursor.character.coerceIn(0, lineText.length) else 0

                    val openPos = state.matchingBrackets?.first
                    val closePos = state.matchingBrackets?.second
                    val bracketsOnThisLine = remember(openPos, closePos, lineIdx, lineText) {
                        val list = mutableListOf<Int>()
                        if (openPos != null && openPos.line == lineIdx && openPos.character < lineText.length) {
                            list.add(openPos.character)
                        }
                        if (closePos != null && closePos.line == lineIdx && closePos.character < lineText.length) {
                            list.add(closePos.character)
                        }
                        list
                    }

                    val isLineInSelection = isSelectionActive && (lineIdx in selStart.line..selEnd.line)
                    val selColStart = if (!isLineInSelection) 0 else if (lineIdx == selStart.line) selStart.character.coerceIn(0, lineText.length) else 0
                    val selColEnd = if (!isLineInSelection) 0 else if (lineIdx == selEnd.line) selEnd.character.coerceIn(0, lineText.length) else lineText.length

                    EditorLineRow(
                        lineIdx = lineIdx,
                        lineId = lineModel?.id ?: lineIdx.toLong(),
                        lineText = lineText,
                        spans = spans,
                        isCurrentLine = isCurrentLine,
                        cursorCol = cursorCol,
                        isSelectionActive = isSelectionActive,
                        bracketsOnThisLine = bracketsOnThisLine,
                        isLineInSelection = isLineInSelection,
                        selColStart = selColStart,
                        selColEnd = selColEnd,
                        lineNumberWidth = lineNumberWidth,
                        minEditorWidth = minEditorWidth,
                        horizontalScroll = horizontalScroll,
                        density = density,
                        fontWidthPx = fontWidthPx,
                        charWidthDp = charWidthDp,
                        onLineClick = { lIdx, col ->
                            engine.setCursor(lIdx, col)
                            requestInputFocus()
                        },
                        onSelectWord = { lIdx, col ->
                            engine.selectWordAt(lIdx, col)
                            requestInputFocus()
                        }
                    )
                }

                // Blank space below code lines - tapping sets cursor to last line end
                item(key = "bottom_spacer") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    val lastLineIdx = (lines.size - 1).coerceAtLeast(0)
                                    val lastLineText = lines.getOrNull(lastLineIdx)?.text ?: ""
                                    engine.setCursor(lastLineIdx, lastLineText.length)
                                    requestInputFocus()
                                }
                            }
                    )
                }
            }

            // Selection overlay adapter wrapping current lines dynamically
            if (isSelectionActive) {
                val lineHeightPx = with(density) { 20.dp.toPx() }
                val scrollYPx = lazyListState.firstVisibleItemIndex * lineHeightPx + lazyListState.firstVisibleItemScrollOffset
                val scrollYDp = with(density) { scrollYPx.toDp() }
                val scrollXDp = with(density) { horizontalScroll.value.toDp() }

                val renderStateAdapter = remember(state, lines) {
                    val lineList = object : java.util.AbstractList<String>() {
                        override val size: Int get() = lines.size
                        override fun get(index: Int): String = lines.getOrNull(index)?.text ?: ""
                    }
                    RenderState(
                        uri = state.uri,
                        languageId = state.languageId,
                        lines = lineList,
                        lineCount = state.lineCount.coerceAtLeast(lines.size),
                        cursor = state.cursor,
                        selection = state.selection,
                        canUndo = state.canUndo,
                        canRedo = state.canRedo,
                        isModified = state.isModified,
                        matchingBrackets = state.matchingBrackets
                    )
                }

                CrestSelectionOverlay(
                    engine = engine,
                    state = renderStateAdapter,
                    modifier = Modifier
                        .offset(
                            x = lineNumberWidth - scrollXDp,
                            y = 8.dp - scrollYDp
                        )
                )
            }
        }

        // Floating Context Menu Toolbar (Copy, Cut, Paste, Select All)
        if (isSelectionActive) {
            Box(modifier = Modifier.fillMaxSize()) {
                CrestSelectionToolbar(
                    engine = engine,
                    onStateRefresh = { refreshState() },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

/**
 * Isolated Composable row function.
 * Enables Compose compiler to skip recomposing unaffected rows when cursor or selection changes.
 */
@Composable
private fun EditorLineRow(
    lineIdx: Int,
    lineId: Long,
    lineText: String,
    spans: List<TokenSpan>,
    isCurrentLine: Boolean,
    cursorCol: Int,
    isSelectionActive: Boolean,
    bracketsOnThisLine: List<Int>,
    isLineInSelection: Boolean,
    selColStart: Int,
    selColEnd: Int,
    lineNumberWidth: Dp,
    minEditorWidth: Dp,
    horizontalScroll: androidx.compose.foundation.ScrollState,
    density: Density,
    fontWidthPx: Float,
    charWidthDp: Dp,
    onLineClick: (Int, Int) -> Unit,
    onSelectWord: (Int, Int) -> Unit,
) {
    SideEffect {
        if (CrestTrace.isRecording) {
            CrestTrace.recordRecomposition("EditorLineRow")
        }
    }
    val i = lineIdx + 1

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Line Number Column
        Text(
            text = "$i",
            color = if (isCurrentLine) CrestAccentPrimary else CrestTextSecondary.copy(alpha = 0.5f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(lineNumberWidth)
                .height(20.dp)
                .padding(end = 12.dp)
                .pointerInput(lineIdx) {
                    detectTapGestures {
                        onLineClick(lineIdx, cursorCol.coerceIn(0, lineText.length))
                    }
                }
        )

        // Code Text Content Area for this line
        Box(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScroll)
        ) {
            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

            val cursorOffsetPx = remember(textLayoutResult, cursorCol, isCurrentLine) {
                if (!isCurrentLine) 0f
                else if (textLayoutResult != null) {
                    try {
                        textLayoutResult?.getCursorRect(cursorCol)?.left ?: (cursorCol * fontWidthPx)
                    } catch (t: Throwable) {
                        cursorCol * fontWidthPx
                    }
                } else {
                    cursorCol * fontWidthPx
                }
            }
            val cursorOffsetDp = with(density) { cursorOffsetPx.toDp() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = (minEditorWidth - lineNumberWidth).coerceAtLeast(0.dp))
                    .defaultMinSize(minWidth = 2000.dp)
                    .height(20.dp)
                    .background(
                        if (isCurrentLine) CrestSurfaceHeader.copy(alpha = 0.4f) else Color.Transparent
                    )
                    .pointerInput(lineIdx, lineText) {
                        detectTapGestures(
                            onTap = { offset ->
                                val layout = textLayoutResult
                                val colIdx = if (layout == null) {
                                    if (fontWidthPx > 0) (offset.x / fontWidthPx).toInt().coerceIn(0, lineText.length) else lineText.length
                                } else {
                                    if (offset.x >= layout.size.width.toFloat()) {
                                        lineText.length
                                    } else if (offset.x <= 0f) {
                                        0
                                    } else {
                                        layout.getOffsetForPosition(offset).coerceIn(0, lineText.length)
                                    }
                                }
                                onLineClick(lineIdx, colIdx)
                            },
                            onLongPress = { offset ->
                                val layout = textLayoutResult
                                val colIdx = if (layout == null) {
                                    if (fontWidthPx > 0) (offset.x / fontWidthPx).toInt().coerceIn(0, lineText.length) else lineText.length
                                } else {
                                    if (offset.x >= layout.size.width.toFloat()) {
                                        lineText.length
                                    } else if (offset.x <= 0f) {
                                        0
                                    } else {
                                        layout.getOffsetForPosition(offset).coerceIn(0, lineText.length)
                                    }
                                }
                                onSelectWord(lineIdx, colIdx)
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                // 1. Matching bracket highlights
                for (bChar in bracketsOnThisLine) {
                    val bracketOffsetPx = remember(textLayoutResult, bChar) {
                        if (textLayoutResult != null && bChar < lineText.length) {
                            try {
                                textLayoutResult?.getCursorRect(bChar)?.left ?: (bChar * fontWidthPx)
                            } catch (t: Throwable) {
                                bChar * fontWidthPx
                            }
                        } else {
                            bChar * fontWidthPx
                        }
                    }
                    val bracketOffsetDp = with(density) { bracketOffsetPx.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(x = bracketOffsetDp)
                            .width(charWidthDp.coerceAtLeast(8.dp))
                            .height(18.dp)
                            .background(
                                color = CrestAccentPrimary.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(3.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = CrestAccentPrimary,
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                }

                val annotatedText = remember(lineText, spans) {
                    val t0 = if (CrestTrace.isRecording) System.nanoTime() else 0L
                    val res = buildAnnotatedString {
                        if (spans.isEmpty()) {
                            append(lineText)
                        } else {
                            for (span in spans) {
                                val start = span.colStart.coerceAtMost(lineText.length)
                                val end = span.colEnd.coerceAtMost(lineText.length)
                                if (start < end) {
                                    val style = getStyleForToken(span.tokenType)
                                    pushStyle(style)
                                    append(lineText.substring(start, end))
                                    pop()
                                }
                            }
                        }
                    }
                    if (CrestTrace.isRecording) {
                        CrestTrace.recordEditorOp("buildAnnotatedString", System.nanoTime() - t0)
                    }
                    res
                }

                Text(
                    text = annotatedText,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CrestTextActive,
                    onTextLayout = {
                        val t0 = if (CrestTrace.isRecording) System.nanoTime() else 0L
                        textLayoutResult = it
                        if (CrestTrace.isRecording) {
                            CrestTrace.recordTextLayout(System.nanoTime() - t0)
                        }
                    }
                )

                // 2. Render cursor indicator precisely at cursor offset when no selection
                if (isCurrentLine && !isSelectionActive) {
                    Box(
                        modifier = Modifier
                            .offset(x = cursorOffsetDp)
                            .width(2.dp)
                            .height(16.dp)
                            .background(CrestAccentPrimary)
                    )
                }
            }
        }
    }
}

private fun getStyleForToken(tokenType: String): SpanStyle {
    return when (tokenType) {
        "Keyword" -> SpanStyle(color = Color(0xFFC678DD), fontWeight = FontWeight.Bold)
        "Type" -> SpanStyle(color = Color(0xFFE5C07B))
        "String" -> SpanStyle(color = Color(0xFF98C379))
        "Number" -> SpanStyle(color = Color(0xFFD19A66))
        "Comment" -> SpanStyle(color = Color(0xFF5C6370))
        "Operator" -> SpanStyle(color = Color(0xFF56B6C2))
        else -> SpanStyle(color = Color(0xFFABB2BF))
    }
}
