package com.crestcode.editor

import org.json.JSONArray
import org.json.JSONObject
import com.crestcode.trace.CrestTrace

data class EditorPosition(val line: Int, val character: Int)
data class EditorSelection(val anchor: EditorPosition, val head: EditorPosition)
data class TokenSpan(val colStart: Int, val colEnd: Int, val tokenType: String)
data class TextChangeEvent(
    val start: EditorPosition,
    val end: EditorPosition,
    val text: String
)
data class EditorLine(
    val id: Long,
    val text: String
)

class EditorLineTracker {
    private var nextId = 1L
    private var currentLines: List<EditorLine> = emptyList()

    fun reset() {
        nextId = 1L
        currentLines = emptyList()
    }

    fun getLines(): List<EditorLine> = currentLines

    fun updateLines(newTextLines: List<String>, changeEvent: TextChangeEvent? = null): List<EditorLine> {
        if (currentLines.isEmpty() || newTextLines.isEmpty()) {
            currentLines = newTextLines.map { EditorLine(nextId++, it) }
            return currentLines
        }

        // Fast path: if line count and text identical
        if (currentLines.size == newTextLines.size) {
            var allMatch = true
            for (i in newTextLines.indices) {
                if (currentLines[i].text != newTextLines[i]) {
                    allMatch = false
                    break
                }
            }
            if (allMatch) return currentLines
        }

        if (changeEvent != null) {
            val startLine = changeEvent.start.line.coerceIn(0, currentLines.size)
            val endLine = changeEvent.end.line.coerceIn(startLine, (currentLines.size - 1).coerceAtLeast(startLine))
            val oldLineCount = currentLines.size
            val newLineCount = newTextLines.size
            val deletedCount = (endLine - startLine + 1).coerceAtMost(oldLineCount - startLine)
            val insertedCount = newLineCount - (oldLineCount - deletedCount)

            if (insertedCount >= 0 && (oldLineCount - deletedCount) + insertedCount == newLineCount) {
                val result = ArrayList<EditorLine>(newLineCount)
                // 1. Prefix: lines strictly before startLine
                for (i in 0 until startLine.coerceAtMost(newLineCount)) {
                    val oldLine = currentLines[i]
                    val newText = newTextLines[i]
                    result.add(if (oldLine.text == newText) oldLine else EditorLine(oldLine.id, newText))
                }

                // 2. Edit range: lines inserted / modified
                for (i in 0 until insertedCount) {
                    val lineIdx = startLine + i
                    if (lineIdx < newTextLines.size) {
                        val text = newTextLines[lineIdx]
                        if (i == 0 && startLine < currentLines.size) {
                            result.add(EditorLine(currentLines[startLine].id, text))
                        } else {
                            result.add(EditorLine(nextId++, text))
                        }
                    }
                }

                // 3. Suffix: lines after deleted range
                val oldSuffixStart = startLine + deletedCount
                val newSuffixStart = startLine + insertedCount
                val suffixCount = oldLineCount - oldSuffixStart
                for (i in 0 until suffixCount) {
                    val oldIdx = oldSuffixStart + i
                    val newIdx = newSuffixStart + i
                    if (oldIdx < currentLines.size && newIdx < newTextLines.size) {
                        val oldLine = currentLines[oldIdx]
                        val newText = newTextLines[newIdx]
                        result.add(if (oldLine.text == newText) oldLine else EditorLine(oldLine.id, newText))
                    }
                }

                if (result.size == newLineCount) {
                    currentLines = result
                    return currentLines
                }
            }
        }

        // Fallback: prefix-suffix reconciliation (e.g. undo/redo)
        val oldSize = currentLines.size
        val newSize = newTextLines.size

        var prefix = 0
        while (prefix < oldSize && prefix < newSize && currentLines[prefix].text == newTextLines[prefix]) {
            prefix++
        }

        var suffix = 0
        while (suffix < (oldSize - prefix) && suffix < (newSize - prefix) &&
            currentLines[oldSize - 1 - suffix].text == newTextLines[newSize - 1 - suffix]) {
            suffix++
        }

        val result = ArrayList<EditorLine>(newSize)
        for (i in 0 until prefix) {
            result.add(currentLines[i])
        }
        val middleOldCount = oldSize - prefix - suffix
        val middleNewCount = newSize - prefix - suffix
        for (i in 0 until middleNewCount) {
            val newIdx = prefix + i
            val text = newTextLines[newIdx]
            if (i < middleOldCount) {
                result.add(EditorLine(currentLines[prefix + i].id, text))
            } else {
                result.add(EditorLine(nextId++, text))
            }
        }
        val oldSuffixStart = oldSize - suffix
        for (i in 0 until suffix) {
            result.add(currentLines[oldSuffixStart + i])
        }

        currentLines = result
        return currentLines
    }

    fun setLines(newTextLines: List<String>): List<EditorLine> {
        currentLines = newTextLines.map { EditorLine(nextId++, it) }
        return currentLines
    }

    fun applyChange(change: TextChangeEvent): List<EditorLine> {
        if (currentLines.isEmpty()) {
            val parts = change.text.replace("\r\n", "\n").split("\n")
            currentLines = parts.map { EditorLine(nextId++, it) }
            return currentLines
        }

        val startLine = change.start.line.coerceIn(0, (currentLines.size - 1).coerceAtLeast(0))
        val endLine = change.end.line.coerceIn(startLine, (currentLines.size - 1).coerceAtLeast(startLine))
        val startChar = change.start.character
        val endChar = change.end.character

        val firstOldLine = currentLines[startLine].text
        val lastOldLine = currentLines[endLine].text

        val prefix = if (startChar <= firstOldLine.length) firstOldLine.substring(0, startChar) else firstOldLine
        val suffix = if (endChar <= lastOldLine.length) lastOldLine.substring(endChar) else ""

        val normalizedText = change.text.replace("\r\n", "\n")
        val parts = normalizedText.split("\n")

        val replacementLines = mutableListOf<EditorLine>()
        if (parts.size == 1) {
            replacementLines.add(EditorLine(currentLines[startLine].id, prefix + parts[0] + suffix))
        } else {
            replacementLines.add(EditorLine(currentLines[startLine].id, prefix + parts[0]))
            for (i in 1 until parts.size - 1) {
                replacementLines.add(EditorLine(nextId++, parts[i]))
            }
            replacementLines.add(EditorLine(nextId++, parts.last() + suffix))
        }

        val result = ArrayList<EditorLine>(currentLines.size - (endLine - startLine + 1) + replacementLines.size)
        for (i in 0 until startLine) {
            result.add(currentLines[i])
        }
        result.addAll(replacementLines)
        for (i in (endLine + 1) until currentLines.size) {
            result.add(currentLines[i])
        }

        currentLines = result
        return currentLines
    }
}

data class EditorState(
    val uri: String,
    val languageId: String,
    val lineCount: Int,
    val cursor: EditorPosition,
    val selection: EditorSelection,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val isModified: Boolean,
    val matchingBrackets: Pair<EditorPosition, EditorPosition>? = null
)

data class EditResponse(
    val change: TextChangeEvent?,
    val state: EditorState?
)

data class RenderState(
    val uri: String,
    val languageId: String,
    val lines: List<String>,
    val lineCount: Int,
    val cursor: EditorPosition,
    val selection: EditorSelection,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val isModified: Boolean,
    val matchingBrackets: Pair<EditorPosition, EditorPosition>? = null
)

data class CompletionItem(
    val label: String,
    val detail: String?,
    val insertText: String?
)

class NativeEditorEngine : AutoCloseable {
    private var nativePtr: Long = 0
    private var isLibraryLoaded = false

    private fun safeLogI(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (t: Throwable) {
            println("[$tag] INFO: $msg")
        }
    }

    private fun safeLogE(tag: String, msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(tag, msg, tr)
        } catch (t: Throwable) {
            println("[$tag] ERROR: $msg ${tr?.message ?: ""}")
        }
    }

    init {
        try {
            System.loadLibrary("crest_editor")
            isLibraryLoaded = true
            nativePtr = nativeCreateEngine()
            safeLogI("NativeEditorEngine", "Successfully initialized Rust native editor engine pointer: $nativePtr")
        } catch (e: Throwable) {
            safeLogE("NativeEditorEngine", "Could not load libcrest_editor.so, falling back to pure Kotlin mock engine", e)
        }
    }

    // Fallback state if native library isn't loaded during testing
    private var fallbackUri: String = ""
    private var fallbackLanguage: String = "plaintext"
    private var fallbackLines: MutableList<String> = mutableListOf("")
    private var fallbackCursor = EditorPosition(0, 0)
    private var fallbackModified = false

    var onStateChanged: (() -> Unit)? = null
    var onContentModified: ((EditResponse) -> Unit)? = null
    var onCursorMoved: ((EditorState) -> Unit)? = null

    var lastChangeEvent: TextChangeEvent? = null
    var lastEditorState: EditorState? = null
    var lastEditResponse: EditResponse? = null

    private fun parseEditorState(jsonStr: String): EditorState? {
        if (jsonStr.isEmpty() || jsonStr == "null") return null
        return try {
            val obj = JSONObject(jsonStr)
            val cursorObj = obj.getJSONObject("cursor")
            val selObj = obj.getJSONObject("selection")
            val anchorObj = selObj.getJSONObject("anchor")
            val headObj = selObj.getJSONObject("head")
            val brackets = if (obj.has("matching_brackets") && !obj.isNull("matching_brackets")) {
                val bArr = obj.getJSONArray("matching_brackets")
                val oObj = bArr.getJSONObject(0)
                val cObj = bArr.getJSONObject(1)
                Pair(
                    EditorPosition(oObj.getInt("line"), oObj.getInt("character")),
                    EditorPosition(cObj.getInt("line"), cObj.getInt("character"))
                )
            } else null

            EditorState(
                uri = obj.optString("uri", ""),
                languageId = obj.optString("language_id", "plaintext"),
                lineCount = obj.optInt("line_count", 0),
                cursor = EditorPosition(cursorObj.getInt("line"), cursorObj.getInt("character")),
                selection = EditorSelection(
                    EditorPosition(anchorObj.getInt("line"), anchorObj.getInt("character")),
                    EditorPosition(headObj.getInt("line"), headObj.getInt("character"))
                ),
                canUndo = obj.optBoolean("can_undo", false),
                canRedo = obj.optBoolean("can_redo", false),
                isModified = obj.optBoolean("is_modified", false),
                matchingBrackets = brackets
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseEditResponse(jsonStr: String): EditResponse {
        if (jsonStr.isEmpty() || jsonStr == "null") return EditResponse(null, null)
        return try {
            val obj = JSONObject(jsonStr)
            val change = if (obj.has("change") && !obj.isNull("change")) {
                val cObj = obj.getJSONObject("change")
                val sObj = cObj.getJSONObject("start")
                val eObj = cObj.getJSONObject("end")
                TextChangeEvent(
                    start = EditorPosition(sObj.getInt("line"), sObj.getInt("character")),
                    end = EditorPosition(eObj.getInt("line"), eObj.getInt("character")),
                    text = cObj.getString("text")
                )
            } else null
            val state = if (obj.has("state") && !obj.isNull("state")) {
                parseEditorState(obj.getJSONObject("state").toString())
            } else null
            EditResponse(change, state)
        } catch (e: Exception) {
            EditResponse(null, null)
        }
    }

    fun openFile(uri: String, languageId: String, content: String) {
        lastChangeEvent = null
        lastEditResponse = null
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeOpenFile(nativePtr, uri, languageId, content)
            lastEditorState = getEditorState()
        } else {
            fallbackUri = uri
            fallbackLanguage = languageId
            fallbackLines = if (content.isEmpty()) mutableListOf("") else content.replace("\r\n", "\n").split("\n").toMutableList()
            fallbackCursor = EditorPosition(0, 0)
            fallbackModified = false
            lastEditorState = EditorState(
                uri = uri,
                languageId = languageId,
                lineCount = fallbackLines.size,
                cursor = fallbackCursor,
                selection = EditorSelection(fallbackCursor, fallbackCursor),
                canUndo = false,
                canRedo = false,
                isModified = false
            )
        }
        onStateChanged?.invoke()
    }

    fun closeFile(uri: String) {
        lastChangeEvent = null
        lastEditResponse = null
        lastEditorState = null
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeCloseFile(nativePtr, uri)
        }
        onStateChanged?.invoke()
    }

    fun insertText(text: String): TextChangeEvent? {
        val resp: EditResponse
        if (isLibraryLoaded && nativePtr != 0L) {
            val json = CrestTrace.traceOp("JNI:nativeInsertTextWithState") {
                val t0 = if (CrestTrace.isRecording) System.nanoTime() else 0L
                val res = nativeInsertTextWithState(nativePtr, text)
                if (CrestTrace.isRecording) CrestTrace.recordJni("nativeInsertTextWithState", System.nanoTime() - t0)
                res
            }
            resp = parseEditResponse(json)
        } else {
            val startPos = fallbackCursor
            if (fallbackLines.isEmpty()) fallbackLines.add("")
            val line = fallbackLines[fallbackCursor.line]
            val prefix = line.substring(0, fallbackCursor.character.coerceAtMost(line.length))
            val suffix = line.substring(fallbackCursor.character.coerceAtMost(line.length))

            val parts = text.replace("\r\n", "\n").split("\n")
            if (parts.size == 1) {
                fallbackLines[fallbackCursor.line] = prefix + text + suffix
                fallbackCursor = EditorPosition(fallbackCursor.line, fallbackCursor.character + text.length)
            } else {
                fallbackLines[fallbackCursor.line] = prefix + parts[0]
                for (i in 1 until parts.size - 1) {
                    fallbackLines.add(fallbackCursor.line + i, parts[i])
                }
                val lastIdx = parts.size - 1
                fallbackLines.add(fallbackCursor.line + lastIdx, parts[lastIdx] + suffix)
                fallbackCursor = EditorPosition(fallbackCursor.line + lastIdx, parts[lastIdx].length)
            }
            fallbackModified = true
            val change = TextChangeEvent(startPos, startPos, text)
            val state = EditorState(
                uri = fallbackUri,
                languageId = fallbackLanguage,
                lineCount = fallbackLines.size,
                cursor = fallbackCursor,
                selection = EditorSelection(fallbackCursor, fallbackCursor),
                canUndo = false,
                canRedo = false,
                isModified = true
            )
            resp = EditResponse(change, state)
        }
        lastChangeEvent = resp.change
        lastEditResponse = resp
        if (resp.state != null) lastEditorState = resp.state
        onContentModified?.invoke(resp)
        onStateChanged?.invoke()
        return resp.change
    }

    fun deleteBackspace(): TextChangeEvent? {
        val resp: EditResponse
        if (isLibraryLoaded && nativePtr != 0L) {
            val json = CrestTrace.traceOp("JNI:nativeDeleteBackspaceWithState") {
                val t0 = if (CrestTrace.isRecording) System.nanoTime() else 0L
                val res = nativeDeleteBackspaceWithState(nativePtr)
                if (CrestTrace.isRecording) CrestTrace.recordJni("nativeDeleteBackspaceWithState", System.nanoTime() - t0)
                res
            }
            resp = parseEditResponse(json)
        } else {
            if (fallbackLines.isEmpty()) {
                resp = EditResponse(null, null)
            } else {
                val line = fallbackLines[fallbackCursor.line]
                if (fallbackCursor.character > 0) {
                    val startPos = EditorPosition(fallbackCursor.line, fallbackCursor.character - 1)
                    val endPos = fallbackCursor
                    val prefix = line.substring(0, fallbackCursor.character - 1)
                    val suffix = line.substring(fallbackCursor.character)
                    fallbackLines[fallbackCursor.line] = prefix + suffix
                    fallbackCursor = startPos
                    fallbackModified = true
                    val change = TextChangeEvent(startPos, endPos, "")
                    val state = EditorState(
                        uri = fallbackUri,
                        languageId = fallbackLanguage,
                        lineCount = fallbackLines.size,
                        cursor = fallbackCursor,
                        selection = EditorSelection(fallbackCursor, fallbackCursor),
                        canUndo = false,
                        canRedo = false,
                        isModified = true
                    )
                    resp = EditResponse(change, state)
                } else if (fallbackCursor.line > 0) {
                    val prevLine = fallbackLines[fallbackCursor.line - 1]
                    val prevLen = prevLine.length
                    val startPos = EditorPosition(fallbackCursor.line - 1, prevLen)
                    val endPos = EditorPosition(fallbackCursor.line, 0)
                    fallbackLines[fallbackCursor.line - 1] = prevLine + line
                    fallbackLines.removeAt(fallbackCursor.line)
                    fallbackCursor = startPos
                    fallbackModified = true
                    val change = TextChangeEvent(startPos, endPos, "")
                    val state = EditorState(
                        uri = fallbackUri,
                        languageId = fallbackLanguage,
                        lineCount = fallbackLines.size,
                        cursor = fallbackCursor,
                        selection = EditorSelection(fallbackCursor, fallbackCursor),
                        canUndo = false,
                        canRedo = false,
                        isModified = true
                    )
                    resp = EditResponse(change, state)
                } else {
                    resp = EditResponse(null, null)
                }
            }
        }
        lastChangeEvent = resp.change
        lastEditResponse = resp
        if (resp.state != null) lastEditorState = resp.state
        onContentModified?.invoke(resp)
        onStateChanged?.invoke()
        return resp.change
    }

    fun moveCursor(direction: String, select: Boolean = false) {
        lastChangeEvent = null
        val state: EditorState?
        if (isLibraryLoaded && nativePtr != 0L) {
            val json = CrestTrace.traceOp("JNI:nativeMoveCursorWithState") {
                val t0 = if (CrestTrace.isRecording) System.nanoTime() else 0L
                val res = nativeMoveCursorWithState(nativePtr, direction, select)
                if (CrestTrace.isRecording) CrestTrace.recordJni("nativeMoveCursorWithState", System.nanoTime() - t0)
                res
            }
            state = parseEditorState(json)
        } else {
            val maxLine = (fallbackLines.size - 1).coerceAtLeast(0)
            val currentLineLen = fallbackLines.getOrNull(fallbackCursor.line)?.length ?: 0
            fallbackCursor = when (direction) {
                "left" -> if (fallbackCursor.character > 0) EditorPosition(fallbackCursor.line, fallbackCursor.character - 1) else fallbackCursor
                "right" -> if (fallbackCursor.character < currentLineLen) EditorPosition(fallbackCursor.line, fallbackCursor.character + 1) else fallbackCursor
                "up" -> if (fallbackCursor.line > 0) EditorPosition(fallbackCursor.line - 1, fallbackCursor.character.coerceAtMost(fallbackLines[fallbackCursor.line - 1].length)) else fallbackCursor
                "down" -> if (fallbackCursor.line < maxLine) EditorPosition(fallbackCursor.line + 1, fallbackCursor.character.coerceAtMost(fallbackLines[fallbackCursor.line + 1].length)) else fallbackCursor
                "line_start" -> EditorPosition(fallbackCursor.line, 0)
                "line_end" -> EditorPosition(fallbackCursor.line, currentLineLen)
                else -> fallbackCursor
            }
            state = EditorState(
                uri = fallbackUri,
                languageId = fallbackLanguage,
                lineCount = fallbackLines.size,
                cursor = fallbackCursor,
                selection = EditorSelection(fallbackCursor, fallbackCursor),
                canUndo = false,
                canRedo = false,
                isModified = fallbackModified
            )
        }
        if (state != null) {
            lastEditorState = state
            onCursorMoved?.invoke(state)
        }
        onStateChanged?.invoke()
    }

    fun setSelection(anchorLine: Int, anchorChar: Int, headLine: Int, headChar: Int) {
        lastChangeEvent = null
        val state: EditorState?
        if (isLibraryLoaded && nativePtr != 0L) {
            val json = CrestTrace.traceOp("JNI:nativeSetSelectionWithState") {
                val t0 = if (CrestTrace.isRecording) System.nanoTime() else 0L
                val res = nativeSetSelectionWithState(nativePtr, anchorLine, anchorChar, headLine, headChar)
                if (CrestTrace.isRecording) CrestTrace.recordJni("nativeSetSelectionWithState", System.nanoTime() - t0)
                res
            }
            state = parseEditorState(json)
        } else {
            val validLine = headLine.coerceIn(0, (fallbackLines.size - 1).coerceAtLeast(0))
            val lineLen = fallbackLines.getOrNull(validLine)?.length ?: 0
            val validChar = headChar.coerceIn(0, lineLen)
            fallbackCursor = EditorPosition(validLine, validChar)
            state = EditorState(
                uri = fallbackUri,
                languageId = fallbackLanguage,
                lineCount = fallbackLines.size,
                cursor = fallbackCursor,
                selection = EditorSelection(EditorPosition(anchorLine, anchorChar), fallbackCursor),
                canUndo = false,
                canRedo = false,
                isModified = fallbackModified
            )
        }
        if (state != null) {
            lastEditorState = state
            onCursorMoved?.invoke(state)
        }
        onStateChanged?.invoke()
    }

    fun selectWordAt(line: Int, col: Int) {
        lastChangeEvent = null
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeSelectWordAt(nativePtr, line, col)
            getEditorState().let {
                lastEditorState = it
                onCursorMoved?.invoke(it)
            }
        }
        onStateChanged?.invoke()
    }

    fun getSelectedText(): String {
        if (isLibraryLoaded && nativePtr != 0L) {
            return nativeGetSelectedText(nativePtr)
        }
        return fallbackLines.getOrNull(fallbackCursor.line) ?: ""
    }

    fun deleteSelection(): TextChangeEvent? {
        val resp: EditResponse
        if (isLibraryLoaded && nativePtr != 0L) {
            val json = nativeDeleteSelectionWithState(nativePtr)
            resp = parseEditResponse(json)
        } else {
            resp = EditResponse(null, null)
        }
        lastChangeEvent = resp.change
        lastEditResponse = resp
        if (resp.state != null) lastEditorState = resp.state
        onContentModified?.invoke(resp)
        onStateChanged?.invoke()
        return resp.change
    }

    fun selectAll() {
        lastChangeEvent = null
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeSelectAll(nativePtr)
            getEditorState().let {
                lastEditorState = it
                onCursorMoved?.invoke(it)
            }
        }
        onStateChanged?.invoke()
    }

    fun setCursor(line: Int, character: Int) {
        setSelection(line, character, line, character)
    }

    fun undo(): Boolean {
        val success: Boolean
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = nativeUndoWithState(nativePtr)
            val obj = JSONObject(jsonStr)
            success = obj.optBoolean("success", false)
            if (obj.has("state") && !obj.isNull("state")) {
                lastEditorState = parseEditorState(obj.getJSONObject("state").toString())
            }
        } else {
            success = false
        }
        if (success) {
            lastEditResponse = EditResponse(null, lastEditorState)
            lastEditResponse?.let { onContentModified?.invoke(it) }
        }
        onStateChanged?.invoke()
        return success
    }

    fun redo(): Boolean {
        val success: Boolean
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = nativeRedoWithState(nativePtr)
            val obj = JSONObject(jsonStr)
            success = obj.optBoolean("success", false)
            if (obj.has("state") && !obj.isNull("state")) {
                lastEditorState = parseEditorState(obj.getJSONObject("state").toString())
            }
        } else {
            success = false
        }
        if (success) {
            lastEditResponse = EditResponse(null, lastEditorState)
            lastEditResponse?.let { onContentModified?.invoke(it) }
        }
        onStateChanged?.invoke()
        return success
    }

    fun getEditorState(): EditorState {
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = CrestTrace.traceOp("JNI:nativeGetEditorStateJson") {
                val t0 = if (CrestTrace.isRecording) System.nanoTime() else 0L
                val res = nativeGetEditorStateJson(nativePtr)
                if (CrestTrace.isRecording) CrestTrace.recordJni("nativeGetEditorStateJson", System.nanoTime() - t0)
                res
            }
            val parsed = parseEditorState(jsonStr)
            if (parsed != null) {
                lastEditorState = parsed
                return parsed
            }
        }
        val fallback = EditorState(
            uri = fallbackUri,
            languageId = fallbackLanguage,
            lineCount = fallbackLines.size,
            cursor = fallbackCursor,
            selection = EditorSelection(fallbackCursor, fallbackCursor),
            canUndo = false,
            canRedo = false,
            isModified = fallbackModified
        )
        lastEditorState = fallback
        return fallback
    }

    fun getLine(line: Int): String {
        return if (isLibraryLoaded && nativePtr != 0L) {
            nativeGetLine(nativePtr, line)
        } else {
            fallbackLines.getOrNull(line) ?: ""
        }
    }

    fun getLines(startLine: Int, endLine: Int): List<String> {
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = nativeGetLinesJson(nativePtr, startLine, endLine)
            if (jsonStr.isNotEmpty() && jsonStr != "null") {
                try {
                    val arr = JSONArray(jsonStr)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    return list
                } catch (t: Throwable) {
                    safeLogE("NativeEditorEngine", "Error parsing getLines JSON", t)
                }
            }
        }
        return fallbackLines.subList(
            startLine.coerceIn(0, fallbackLines.size),
            (endLine + 1).coerceIn(startLine, fallbackLines.size)
        )
    }

    fun getRenderState(): RenderState {
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = nativeGetRenderStateJson(nativePtr)
            if (jsonStr.isNotEmpty() && jsonStr != "null") {
                try {
                    val obj = JSONObject(jsonStr)
                    val linesArr = obj.getJSONArray("lines")
                    val linesList = mutableListOf<String>()
                    for (i in 0 until linesArr.length()) {
                        linesList.add(linesArr.getString(i))
                    }

                    val curObj = obj.getJSONObject("cursor")
                    val cursor = EditorPosition(curObj.getInt("line"), curObj.getInt("character"))

                    val selObj = obj.getJSONObject("selection")
                    val ancObj = selObj.getJSONObject("anchor")
                    val headObj = selObj.getJSONObject("head")
                    val selection = EditorSelection(
                        EditorPosition(ancObj.getInt("line"), ancObj.getInt("character")),
                        EditorPosition(headObj.getInt("line"), headObj.getInt("character"))
                    )

                    val matchingBrackets = if (obj.has("matching_brackets") && !obj.isNull("matching_brackets")) {
                        try {
                            val mbArr = obj.getJSONArray("matching_brackets")
                            val openObj = mbArr.getJSONObject(0)
                            val closeObj = mbArr.getJSONObject(1)
                            Pair(
                                EditorPosition(openObj.getInt("line"), openObj.getInt("character")),
                                EditorPosition(closeObj.getInt("line"), closeObj.getInt("character"))
                            )
                        } catch (t: Throwable) {
                            null
                        }
                    } else null

                    return RenderState(
                        uri = obj.optString("uri", ""),
                        languageId = obj.optString("language_id", "plaintext"),
                        lines = linesList,
                        lineCount = obj.optInt("line_count", linesList.size),
                        cursor = cursor,
                        selection = selection,
                        canUndo = obj.optBoolean("can_undo", false),
                        canRedo = obj.optBoolean("can_redo", false),
                        isModified = obj.optBoolean("is_modified", false),
                        matchingBrackets = matchingBrackets
                    )
                } catch (e: Exception) {
                    safeLogE("NativeEditorEngine", "Error parsing render state JSON", e)
                }
            }
        }

        return RenderState(
            uri = fallbackUri,
            languageId = fallbackLanguage,
            lines = fallbackLines,
            lineCount = fallbackLines.size,
            cursor = fallbackCursor,
            selection = EditorSelection(fallbackCursor, fallbackCursor),
            canUndo = false,
            canRedo = false,
            isModified = fallbackModified
        )
    }

    fun getHighlightSpans(startLine: Int, endLine: Int): Map<Int, List<TokenSpan>> {
        val result = mutableMapOf<Int, List<TokenSpan>>()
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = CrestTrace.traceOp("JNI:nativeGetHighlightSpansJson") {
                val t0 = if (CrestTrace.isRecording) System.nanoTime() else 0L
                val res = nativeGetHighlightSpansJson(nativePtr, startLine, endLine)
                if (CrestTrace.isRecording) CrestTrace.recordJni("nativeGetHighlightSpansJson", System.nanoTime() - t0)
                res
            }
            if (jsonStr.isNotEmpty() && jsonStr != "null") {
                try {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val tuple = arr.getJSONArray(i)
                        val lineIdx = tuple.getInt(0)
                        val spansArr = tuple.getJSONArray(1)
                        val spansList = mutableListOf<TokenSpan>()
                        for (j in 0 until spansArr.length()) {
                            val spanObj = spansArr.getJSONObject(j)
                            spansList.add(
                                TokenSpan(
                                    colStart = spanObj.getInt("col_start"),
                                    colEnd = spanObj.getInt("col_end"),
                                    tokenType = spanObj.optString("token_type", "Default")
                                )
                            )
                        }
                        result[lineIdx] = spansList
                    }
                } catch (e: Exception) {
                    safeLogE("NativeEditorEngine", "Error parsing highlight spans JSON", e)
                }
            }
        }
        return result
    }

    fun getContent(): String {
        return if (isLibraryLoaded && nativePtr != 0L) {
            nativeGetContent(nativePtr)
        } else {
            fallbackLines.joinToString("\n")
        }
    }

    fun getCompletions(): List<CompletionItem> {
        if (isLibraryLoaded && nativePtr != 0L) {
            val jsonStr = nativeLspCompletionJson(nativePtr)
            if (jsonStr.isNotEmpty() && jsonStr != "null") {
                try {
                    val arr = JSONArray(jsonStr)
                    val list = mutableListOf<CompletionItem>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        list.add(
                            CompletionItem(
                                label = obj.getString("label"),
                                detail = if (obj.has("detail")) obj.optString("detail") else null,
                                insertText = if (obj.has("insert_text")) obj.optString("insert_text") else obj.getString("label")
                            )
                        )
                    }
                    return list
                } catch (e: Exception) {
                    safeLogE("NativeEditorEngine", "Error parsing completions JSON", e)
                }
            }
        }
        return emptyList()
    }

    override fun close() {
        if (isLibraryLoaded && nativePtr != 0L) {
            nativeDestroyEngine(nativePtr)
            nativePtr = 0L
        }
    }

    // Native JNI methods
    private external fun nativeCreateEngine(): Long
    private external fun nativeDestroyEngine(ptr: Long)
    private external fun nativeOpenFile(ptr: Long, uri: String, languageId: String, content: String)
    private external fun nativeCloseFile(ptr: Long, uri: String)
    private external fun nativeInsertText(ptr: Long, text: String): String
    private external fun nativeDeleteBackspace(ptr: Long): String
    private external fun nativeMoveCursor(ptr: Long, direction: String, select: Boolean)
    private external fun nativeSetSelection(ptr: Long, anchorLine: Int, anchorChar: Int, headLine: Int, headChar: Int)
    private external fun nativeSelectWordAt(ptr: Long, line: Int, col: Int)
    private external fun nativeGetSelectedText(ptr: Long): String
    private external fun nativeDeleteSelection(ptr: Long): String
    private external fun nativeSelectAll(ptr: Long)
    private external fun nativeUndo(ptr: Long): Boolean
    private external fun nativeRedo(ptr: Long): Boolean
    private external fun nativeGetRenderStateJson(ptr: Long): String
    private external fun nativeGetHighlightSpansJson(ptr: Long, startLine: Int, endLine: Int): String
    private external fun nativeGetContent(ptr: Long): String
    private external fun nativeLspCompletionJson(ptr: Long): String
    private external fun nativeLspHoverJson(ptr: Long): String
    private external fun nativeLspGotoDefinitionJson(ptr: Long): String

    // Lightweight & incremental native JNI methods
    private external fun nativeGetEditorStateJson(ptr: Long): String
    private external fun nativeInsertTextWithState(ptr: Long, text: String): String
    private external fun nativeDeleteBackspaceWithState(ptr: Long): String
    private external fun nativeDeleteSelectionWithState(ptr: Long): String
    private external fun nativeMoveCursorWithState(ptr: Long, direction: String, select: Boolean): String
    private external fun nativeSetSelectionWithState(ptr: Long, anchorLine: Int, anchorChar: Int, headLine: Int, headChar: Int): String
    private external fun nativeUndoWithState(ptr: Long): String
    private external fun nativeRedoWithState(ptr: Long): String
    private external fun nativeGetLine(ptr: Long, line: Int): String
    private external fun nativeGetLinesJson(ptr: Long, startLine: Int, endLine: Int): String
}
