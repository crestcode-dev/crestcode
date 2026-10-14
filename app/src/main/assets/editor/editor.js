(function() {
  'use strict';

  // Configure Monaco Environment worker fallback for Android WebView & test environments
  window.MonacoEnvironment = {
    getWorkerUrl: function (moduleId, label) {
      return 'data:text/javascript;charset=utf-8,' + encodeURIComponent('self.onmessage = function() {};');
    }
  };

  // Polyfill window.matchMedia for Android WebView compatibility
  if (typeof window.matchMedia !== 'function') {
    window.matchMedia = function(query) {
      return {
        matches: false,
        media: query || '',
        onchange: null,
        addListener: function() {},
        removeListener: function() {},
        addEventListener: function() {},
        removeEventListener: function() {},
        dispatchEvent: function() { return false; }
      };
    };
  }

  // Polyfill window.ResizeObserver for Android WebView compatibility
  if (typeof window.ResizeObserver !== 'function') {
    window.ResizeObserver = class ResizeObserver {
      constructor(callback) {
        this.callback = callback;
      }
      observe(target) {
        if (target) {
          const rect = (target.getBoundingClientRect && typeof target.getBoundingClientRect === 'function') 
            ? target.getBoundingClientRect() 
            : { width: 800, height: 600, top: 0, left: 0, right: 800, bottom: 600 };
          try {
            this.callback([{ target: target, contentRect: rect }], this);
          } catch(e) {}
        }
      }
      unobserve() {}
      disconnect() {}
    };
  }

  // Base path for Monaco AMD loader
  require.config({ paths: { 'vs': 'vs' } });

  let editor = null;
  let isReady = false;

  const statusPill = document.getElementById('status-pill');
  const statLines = document.getElementById('stat-lines');
  const statPos = document.getElementById('stat-pos');
  const footerStatusText = document.getElementById('footer-status-text');
  const btnTheme = document.getElementById('btn-theme');

  // Initial code template to test Monaco functionality
  const sampleCode = `// Crest Code - Monaco Editor on Android WebView
// Milestone 1 Verification Code

function greetCrestUser(name) {
    const greeting = "Hello, " + name + "! Monaco Editor is fully functional on Crest Android.";
    console.log(greeting);
    return greeting;
}

// Perform basic calculation test
const result = greetCrestUser("Developer");
`;

  // Initialize Monaco Editor
  require(['vs/editor/editor.main'], function() {
    try {
      const container = document.getElementById('editor-container');

      editor = monaco.editor.create(container, {
        value: sampleCode,
        language: 'javascript',
        theme: 'vs-dark',
        automaticLayout: true,
        fontSize: 14,
        lineNumbers: 'on',
        wordWrap: 'on',
        minimap: { enabled: false }, // Space optimization for mobile
        scrollBeyondLastLine: false,
        smoothScrolling: true,
        contextmenu: false,
        quickSuggestions: true,
        cursorBlinking: 'smooth'
      });

      isReady = true;

      // Enable Mobile Touch Drag Handles & Dynamic Floating Toolbar
      setupMobileTouchSelection(editor, container);

      // Add Ctrl+S / Cmd+S save command shortcut
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, function() {
        if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onSaveRequested === 'function') {
          window.CrestAndroidBridge.onSaveRequested(editor.getValue());
        }
      });

      // Update UI Status
      if (statusPill) {
        statusPill.textContent = 'Monaco Active';
        statusPill.style.background = 'rgba(34, 197, 94, 0.2)';
        statusPill.style.color = '#22c55e';
      }

      if (footerStatusText) {
        footerStatusText.textContent = 'Crest Mobile • Monaco v0.57.0 Active';
      }

      updateStats();

      // Listen to model content change (typing / editing)
      editor.onDidChangeModelContent(function(e) {
        updateStats();
        if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onContentChanged === 'function') {
          try {
            window.CrestAndroidBridge.onContentChanged(JSON.stringify({
              lineCount: editor.getModel().getLineCount(),
              length: editor.getValue().length,
              versionId: editor.getModel().getVersionId()
            }));
          } catch(err) {
            console.error('Bridge onContentChanged error:', err);
          }
        }
      });

      // Listen to cursor position changes
      editor.onDidChangeCursorPosition(function(e) {
        if (statPos) {
          statPos.textContent = 'Ln ' + e.position.lineNumber + ', Col ' + e.position.column;
        }
      });

      // Notify native Android bridge
      if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onEditorReady === 'function') {
        try {
          window.CrestAndroidBridge.onEditorReady(JSON.stringify({
            status: 'READY',
            engine: 'Monaco Editor',
            lineCount: editor.getModel().getLineCount()
          }));
        } catch(err) {
          console.error('Bridge onEditorReady error:', err);
        }
      }

      console.log('[Crest Monaco] Monaco Editor successfully initialized!');

    } catch (error) {
      console.error('[Crest Monaco] Initialization error:', error);
      if (statusPill) {
        statusPill.textContent = 'Init Error';
        statusPill.style.background = 'rgba(239, 68, 68, 0.2)';
        statusPill.style.color = '#ef4444';
      }
    }
  });

  function updateStats() {
    if (!editor) return;
    const model = editor.getModel();
    if (model && statLines) {
      const count = model.getLineCount();
      statLines.textContent = count + (count === 1 ? ' line' : ' lines');
    }
  }

  // Toggle Theme between Dark and Light
  let isDarkMode = true;
  if (btnTheme) {
    btnTheme.addEventListener('click', function() {
      if (!editor) return;
      isDarkMode = !isDarkMode;
      monaco.editor.setTheme(isDarkMode ? 'vs-dark' : 'vs');
      btnTheme.textContent = isDarkMode ? '🌙' : '☀️';
    });
  }

  // Global API for verification testing
  window.CrestEditorAPI = {
    isReady: function() {
      return isReady && editor !== null;
    },
    getValue: function() {
      return editor ? editor.getValue() : '';
    },
    setValue: function(val) {
      if (editor) {
        editor.setValue(val);
        updateStats();
        return true;
      }
      return false;
    },
    typeText: function(text) {
      if (editor) {
        const position = editor.getPosition();
        editor.executeEdits('test-typing', [{
          range: new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column),
          text: text,
          forceMoveMarkers: true
        }]);
        updateStats();
        return true;
      }
      return false;
    },
    clearEditor: function() {
      if (!editor) return false;
      let emptyModel = monaco.editor.getModel(monaco.Uri.file('/empty'));
      if (!emptyModel) {
        emptyModel = monaco.editor.createModel('', 'plaintext', monaco.Uri.file('/empty'));
      }
      editor.setModel(emptyModel);
      updateStats();
      return true;
    },
    openFile: function(filePath, content, languageId) {
      if (!editor) return false;
      const uri = monaco.Uri.file(filePath);
      let model = monaco.editor.getModel(uri);
      if (!model) {
        model = monaco.editor.createModel(content, languageId, uri);
      } else {
        if (model.getValue() !== content) {
          model.setValue(content);
        }
        monaco.editor.setModelLanguage(model, languageId);
      }
      editor.setModel(model);
      updateStats();
      return true;
    },
    getEditorStats: function() {
      if (!editor) return null;
      const model = editor.getModel();
      const pos = editor.getPosition();
      return {
        lineCount: model.getLineCount(),
        characterCount: model.getValueLength(),
        cursorPosition: { lineNumber: pos.lineNumber, column: pos.column },
        language: model.getLanguageId()
      };
    },
    undo: function() {
      if (editor) editor.trigger('keyboard', 'undo', null);
    },
    redo: function() {
      if (editor) editor.trigger('keyboard', 'redo', null);
    },
    paste: function() {
      if (typeof window.pasteClipboard === 'function') {
        window.pasteClipboard();
      }
    }
  };

  // ============================================================
  // Mobile Touch Selection, Handles & Dynamic Floating Toolbar
  // ============================================================
  function setupMobileTouchSelection(editor, container) {
    const startHandle = document.getElementById('start-handle');
    const endHandle = document.getElementById('end-handle');
    const floatingToolbar = document.getElementById('floating-toolbar');
    const floatingCopy = document.getElementById('floating-copy');
    const floatingPaste = document.getElementById('floating-paste');

    if (!startHandle || !endHandle || !floatingToolbar) return;

    const editorNode = editor.getDomNode();
    let pressTimer = null;
    let startX = 0, startY = 0;
    let activeDrag = null;
    let isScrolling = false;
    let scrollResetTimer = null;
    let suppressMouseUntil = 0;
    let isLongPressConfirmed = false;
    let isHandleMoving = false;
    let handleStillTimer = null;
    let pendingUiRaf = null;

    // Fast Bulk Paste Functionality
    window.pasteClipboard = async function() {
      const model = editor.getModel();
      if (!model) return;
      let text = '';
      try {
        if (navigator.clipboard && navigator.clipboard.readText) {
          text = await navigator.clipboard.readText();
        }
      } catch (_) {}
      if (!text && window.CrestAndroidBridge && typeof window.CrestAndroidBridge.getClipboardText === 'function') {
        try {
          text = window.CrestAndroidBridge.getClipboardText();
        } catch (_) {}
      }
      if (!text) text = prompt('Paste text here:');
      if (!text) return;

      const selection = editor.getSelection();
      const range = (selection && !selection.isEmpty())
        ? selection
        : (selection ? new monaco.Range(selection.startLineNumber, selection.startColumn, selection.startLineNumber, selection.startColumn) : model.getFullModelRange());

      editor.executeEdits('ui-paste', [{ range, text, forceMoveMarkers: true }]);
      editor.pushUndoStop();
      editor.focus();
      floatingToolbar.classList.remove('visible');
    };

    function updateCopyToolbar(cachedWrapRect, cachedEditorRect) {
      if (isHandleMoving) {
        floatingToolbar.classList.remove('visible');
        return;
      }
      const selection = editor.getSelection();
      const hasSelection = selection && !selection.isEmpty();
      const wrapRect = cachedWrapRect || container.getBoundingClientRect();
      const editorRect = cachedEditorRect || editorNode.getBoundingClientRect();
      const editorLeft = editorRect.left - wrapRect.left;
      const editorTop = editorRect.top - wrapRect.top;

      if (hasSelection) {
        floatingCopy.style.display = '';
        floatingPaste.style.display = '';
        const start = editor.getScrolledVisiblePosition(selection.getStartPosition());
        const end = editor.getScrolledVisiblePosition(selection.getEndPosition());
        if (!start || !end) {
          floatingToolbar.classList.remove('visible');
          return;
        }
        const toolbarWidth = floatingToolbar.offsetWidth || 140;
        const toolbarHeight = floatingToolbar.offsetHeight || 32;
        const isSingleLine = selection.startLineNumber === selection.endLineNumber;
        const startXPos = editorLeft + start.left;
        const endXPos = editorLeft + end.left;
        const selectionMidX = isSingleLine ? ((startXPos + endXPos) / 2) : (startXPos + 35);
        const left = Math.max(4, Math.min(Math.round(selectionMidX - (toolbarWidth / 2)), wrapRect.width - toolbarWidth - 4));

        const gap = 2; // Pura chipka ke directly above selection
        const topOfSelection = editorTop + start.top;
        let top = Math.round(topOfSelection - toolbarHeight - gap);
        if (top < 2) {
          top = Math.round(topOfSelection + (start.height || 19) + gap);
        }
        floatingToolbar.style.transform = `translate3d(${left}px, ${top}px, 0)`;
        floatingToolbar.classList.add('visible');
      } else if (Date.now() < suppressMouseUntil) {
        floatingCopy.style.display = 'none';
        floatingPaste.style.display = '';
        const pos = editor.getPosition();
        if (!pos) return;
        const visible = editor.getScrolledVisiblePosition(pos);
        if (!visible) return;
        const toolbarWidth = floatingToolbar.offsetWidth || 75;
        const toolbarHeight = floatingToolbar.offsetHeight || 32;
        const left = Math.max(4, Math.min(Math.round(editorLeft + visible.left - (toolbarWidth / 2)), wrapRect.width - toolbarWidth - 4));
        const gap = 2;
        let top = Math.round(editorTop + visible.top - toolbarHeight - gap);
        if (top < 2) top = Math.round(editorTop + visible.top + (visible.height || 19) + gap);

        floatingToolbar.style.transform = `translate3d(${left}px, ${top}px, 0)`;
        floatingToolbar.classList.add('visible');
      } else {
        floatingToolbar.classList.remove('visible');
      }
    }

    function positionForHandle(handle, position, isStart, wrapRect, editorRect) {
      const visible = editor.getScrolledVisiblePosition(position);
      if (!visible) {
        handle.classList.remove('visible');
        return;
      }
      const x = Math.round(editorRect.left - wrapRect.left + visible.left - 12);
      const y = Math.round(editorRect.top - wrapRect.top + visible.top + (isStart ? -3 : (visible.height || 18)));
      handle.style.transform = `translate3d(${x}px, ${y}px, 0)`;
      handle.classList.add('visible');
    }

    function updateHandles(wrapRect, editorRect) {
      if (activeDrag) return;
      const selection = editor.getSelection();
      if (!selection || selection.isEmpty()) {
        startHandle.classList.remove('visible');
        endHandle.classList.remove('visible');
        return;
      }
      positionForHandle(startHandle, selection.getStartPosition(), true, wrapRect, editorRect);
      positionForHandle(endHandle, selection.getEndPosition(), false, wrapRect, editorRect);
    }

    function scheduleUiUpdate() {
      if (pendingUiRaf) return;
      pendingUiRaf = requestAnimationFrame(() => {
        pendingUiRaf = null;
        const wrapRect = container.getBoundingClientRect();
        const editorRect = editorNode.getBoundingClientRect();
        updateHandles(wrapRect, editorRect);
        updateCopyToolbar(wrapRect, editorRect);
      });
    }

    floatingPaste.addEventListener('click', () => window.pasteClipboard());
    floatingCopy.addEventListener('click', async () => {
      const sel = editor.getSelection();
      const model = editor.getModel();
      if (!sel || sel.isEmpty() || !model) return;
      const text = model.getValueInRange(sel);
      if (!text) return;
      try {
        await navigator.clipboard.writeText(text);
      } catch (_) {
        const temp = document.createElement('textarea');
        temp.value = text;
        temp.style.position = 'fixed';
        temp.style.left = '-9999px';
        document.body.appendChild(temp);
        temp.select();
        document.execCommand('copy');
        temp.remove();
      }
    });

    ['touchstart', 'mousedown', 'pointerdown'].forEach(evt => {
      floatingToolbar.addEventListener(evt, e => e.stopPropagation());
    });

    function dropSyntheticEvent(event) {
      const target = event.target;
      if (target && target.closest && (target.closest('.selection-handle') || target.closest('#floating-toolbar'))) return;
      if (Date.now() < suppressMouseUntil || isLongPressConfirmed) {
        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
        return false;
      }
    }

    ['mousedown', 'mouseup', 'click'].forEach(evt => {
      window.addEventListener(evt, dropSyntheticEvent, true);
      document.addEventListener(evt, dropSyntheticEvent, true);
      editorNode.addEventListener(evt, dropSyntheticEvent, true);
      container.addEventListener(evt, dropSyntheticEvent, true);
    });

    document.addEventListener('selectionchange', event => {
      if (Date.now() < suppressMouseUntil || isLongPressConfirmed) event.stopImmediatePropagation();
    }, true);

    container.addEventListener('contextmenu', e => { e.preventDefault(); e.stopPropagation(); }, true);

    function selectWordAt(x, y) {
      const target = editor.getTargetAtClientPoint(x, y);
      if (!target || !target.position) return;
      const model = editor.getModel();
      const word = model.getWordAtPosition(target.position);
      if (!word) {
        editor.setPosition(target.position);
        scheduleUiUpdate();
        return;
      }
      editor.setSelection({
        startLineNumber: target.position.lineNumber,
        startColumn: word.startColumn,
        endLineNumber: target.position.lineNumber,
        endColumn: word.endColumn
      });
      scheduleUiUpdate();
    }

    editorNode.addEventListener('touchstart', e => {
      if (e.touches.length !== 1) { clearTimeout(pressTimer); return; }
      const touch = e.touches[0];
      startX = touch.clientX;
      startY = touch.clientY;
      clearTimeout(pressTimer);
      isLongPressConfirmed = false;

      pressTimer = setTimeout(() => {
        if (!isScrolling) {
          isLongPressConfirmed = true;
          suppressMouseUntil = Date.now() + 5000;
          selectWordAt(startX, startY);
          if (navigator.vibrate) { try { navigator.vibrate(35); } catch(_) {} }
        }
      }, 380);
    }, { passive: true });

    editorNode.addEventListener('touchmove', e => {
      if (isLongPressConfirmed) return;
      if (e.touches && e.touches.length === 1) {
        const touch = e.touches[0];
        if (Math.abs(touch.clientX - startX) > 12 || Math.abs(touch.clientY - startY) > 12) {
          clearTimeout(pressTimer);
          isScrolling = true;
          clearTimeout(scrollResetTimer);
          scrollResetTimer = setTimeout(() => { isScrolling = false; }, 200);
        }
      } else {
        clearTimeout(pressTimer);
      }
    }, { passive: true });

    editorNode.addEventListener('touchend', e => {
      clearTimeout(pressTimer);
      if (isLongPressConfirmed) {
        e.preventDefault();
        e.stopPropagation();
        isLongPressConfirmed = false;
        suppressMouseUntil = Date.now() + 300;
        return;
      }
      if (Date.now() < suppressMouseUntil) {
        e.preventDefault();
        e.stopPropagation();
        return;
      }
    }, { passive: false });

    editorNode.addEventListener('touchcancel', () => {
      clearTimeout(pressTimer);
      isLongPressConfirmed = false;
    }, { passive: true });

    function beginHandleDrag(isStart, event) {
      event.preventDefault();
      event.stopPropagation();
      suppressMouseUntil = 0;
      const model = editor.getModel();
      const initialSelection = editor.getSelection();
      if (!model || !initialSelection || initialSelection.isEmpty()) return;

      floatingToolbar.classList.remove('visible');
      isHandleMoving = true;
      clearTimeout(handleStillTimer);

      const fixedAnchor = isStart ? initialSelection.getEndPosition() : initialSelection.getStartPosition();
      const pointerId = event.pointerId;
      activeDrag = { isStart };
      let pendingDragRaf = null;
      let latestX = event.clientX, latestY = event.clientY;

      function processDrag() {
        pendingDragRaf = null;
        if (!activeDrag) return;
        const target = editor.getTargetAtClientPoint(latestX, latestY);
        if (!target || !target.position) return;
        const moving = target.position;
        const fixedOffset = model.getOffsetAt(fixedAnchor);
        const movingOffset = model.getOffsetAt(moving);
        const start = movingOffset <= fixedOffset ? moving : fixedAnchor;
        const end = movingOffset <= fixedOffset ? fixedAnchor : moving;

        editor.setSelection({
          startLineNumber: start.lineNumber,
          startColumn: start.column,
          endLineNumber: end.lineNumber,
          endColumn: end.column
        });

        const current = editor.getSelection();
        if (current) {
          const wrapRect = container.getBoundingClientRect();
          const editorRect = editorNode.getBoundingClientRect();
          positionForHandle(startHandle, current.getStartPosition(), true, wrapRect, editorRect);
          positionForHandle(endHandle, current.getEndPosition(), false, wrapRect, editorRect);
        }
      }

      function move(e) {
        if (!activeDrag || e.pointerId !== pointerId) return;
        if (!isHandleMoving) {
          isHandleMoving = true;
          floatingToolbar.classList.remove('visible');
        }
        latestX = e.clientX;
        latestY = e.clientY;
        clearTimeout(handleStillTimer);
        handleStillTimer = setTimeout(() => {
          isHandleMoving = false;
          scheduleUiUpdate();
        }, 250);

        if (!pendingDragRaf) pendingDragRaf = requestAnimationFrame(processDrag);
      }

      function finish(e) {
        if (e.pointerId !== pointerId) return;
        clearTimeout(handleStillTimer);
        isHandleMoving = false;
        if (pendingDragRaf) { cancelAnimationFrame(pendingDragRaf); pendingDragRaf = null; }
        window.removeEventListener('pointermove', move, true);
        window.removeEventListener('pointerup', finish, true);
        window.removeEventListener('pointercancel', finish, true);
        activeDrag = null;
        scheduleUiUpdate();
      }

      window.addEventListener('pointermove', move, true);
      window.addEventListener('pointerup', finish, true);
      window.addEventListener('pointercancel', finish, true);
    }

    startHandle.addEventListener('pointerdown', e => beginHandleDrag(true, e));
    endHandle.addEventListener('pointerdown', e => beginHandleDrag(false, e));

    editor.onDidChangeCursorSelection(scheduleUiUpdate);
    editor.onDidScrollChange(scheduleUiUpdate);
    editor.onDidLayoutChange(scheduleUiUpdate);
    window.addEventListener('resize', scheduleUiUpdate);
  }

})();
