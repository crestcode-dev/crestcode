use crate::buffer::Position;
use crate::editor::EditorEngine;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, JNI_TRUE};
use jni::JNIEnv;

fn get_engine<'a>(ptr: jlong) -> &'a mut EditorEngine {
    unsafe { &mut *(ptr as *mut EditorEngine) }
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeCreateEngine(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let engine = Box::new(EditorEngine::new());
    Box::into_raw(engine) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeDestroyEngine(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    if ptr != 0 {
        unsafe {
            let _ = Box::from_raw(ptr as *mut EditorEngine);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeOpenFile<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    uri: JString<'local>,
    language_id: JString<'local>,
    content: JString<'local>,
) {
    let engine = get_engine(ptr);
    let u: String = env.get_string(&uri).unwrap().into();
    let l: String = env.get_string(&language_id).unwrap().into();
    let c: String = env.get_string(&content).unwrap().into();
    engine.open_document(&u, &l, &c);
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeCloseFile<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    uri: JString<'local>,
) {
    let engine = get_engine(ptr);
    let u: String = env.get_string(&uri).unwrap().into();
    engine.close_document(&u);
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeInsertText<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    text: JString<'local>,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let t: String = env.get_string(&text).unwrap().into();
    let change = engine.insert_text(&t);
    let json_str = serde_json::to_string(&change).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeDeleteBackspace<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let change = engine.delete_backspace();
    let json_str = serde_json::to_string(&change).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeMoveCursor<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    direction: JString<'local>,
    select: jboolean,
) {
    let engine = get_engine(ptr);
    let dir: String = env.get_string(&direction).unwrap().into();
    engine.move_cursor(&dir, select == JNI_TRUE);
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeSetSelection(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    anchor_line: jint,
    anchor_char: jint,
    head_line: jint,
    head_char: jint,
) {
    let engine = get_engine(ptr);
    engine.set_selection(
        Position::new(anchor_line as usize, anchor_char as usize),
        Position::new(head_line as usize, head_char as usize),
    );
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeSelectWordAt(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    line: jint,
    col: jint,
) {
    let engine = get_engine(ptr);
    engine.select_word_at(line as usize, col as usize);
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetSelectedText<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let text = engine.get_selected_text();
    env.new_string(text).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeDeleteSelection<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let change = engine.delete_selection();
    let json_str = serde_json::to_string(&change).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeSelectAll(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    let engine = get_engine(ptr);
    engine.select_all();
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeUndo(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jboolean {
    let engine = get_engine(ptr);
    if engine.undo() {
        JNI_TRUE
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeRedo(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) -> jboolean {
    let engine = get_engine(ptr);
    if engine.redo() {
        JNI_TRUE
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetRenderStateJson<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let state = engine.get_render_state();
    let json_str = serde_json::to_string(&state).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetEditorStateJson<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let state = engine.get_editor_state();
    let json_str = serde_json::to_string(&state).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeInsertTextWithState<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    text: JString<'local>,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let t: String = env.get_string(&text).unwrap().into();
    let resp = engine.insert_text_with_state(&t);
    let json_str = serde_json::to_string(&resp).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeDeleteBackspaceWithState<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let resp = engine.delete_backspace_with_state();
    let json_str = serde_json::to_string(&resp).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeDeleteSelectionWithState<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let resp = engine.delete_selection_with_state();
    let json_str = serde_json::to_string(&resp).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeMoveCursorWithState<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    direction: JString<'local>,
    select: jboolean,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let dir: String = env.get_string(&direction).unwrap().into();
    let state = engine.move_cursor_with_state(&dir, select == JNI_TRUE);
    let json_str = serde_json::to_string(&state).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeSetSelectionWithState<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    anchor_line: jint,
    anchor_char: jint,
    head_line: jint,
    head_char: jint,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let state = engine.set_selection_with_state(
        Position::new(anchor_line as usize, anchor_char as usize),
        Position::new(head_line as usize, head_char as usize),
    );
    let json_str = serde_json::to_string(&state).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeUndoWithState<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let (success, state) = engine.undo_with_state();
    let resp = serde_json::json!({
        "success": success,
        "state": state
    });
    env.new_string(resp.to_string()).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeRedoWithState<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let (success, state) = engine.redo_with_state();
    let resp = serde_json::json!({
        "success": success,
        "state": state
    });
    env.new_string(resp.to_string()).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetLine<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    line: jint,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let line_str = engine.get_line(line as usize).unwrap_or_default();
    env.new_string(line_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetLinesJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    start_line: jint,
    end_line: jint,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let lines = engine.get_lines(start_line as usize, end_line as usize);
    let json_str = serde_json::to_string(&lines).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetHighlightSpansJson<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
    start_line: jint,
    end_line: jint,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let spans = engine.highlight_visible_lines(start_line as usize, end_line as usize);
    let json_str = serde_json::to_string(&spans).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeGetContent<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let content = engine
        .get_active_document()
        .map(|d| d.buffer.get_text())
        .unwrap_or_default();
    env.new_string(content).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeLspCompletionJson<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let items = engine.lsp_completion().unwrap_or_default();
    let json_str = serde_json::to_string(&items).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeLspHoverJson<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let hover = engine.lsp_hover().ok().flatten();
    let json_str = serde_json::to_string(&hover).unwrap_or_default();
    env.new_string(json_str).unwrap()
}

#[no_mangle]
pub extern "system" fn Java_com_crestcode_editor_NativeEditorEngine_nativeLspGotoDefinitionJson<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    ptr: jlong,
) -> JString<'local> {
    let engine = get_engine(ptr);
    let locs = engine.lsp_goto_definition().unwrap_or_default();
    let json_str = serde_json::to_string(&locs).unwrap_or_default();
    env.new_string(json_str).unwrap()
}
