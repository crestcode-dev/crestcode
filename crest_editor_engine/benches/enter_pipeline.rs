//! Benchmark measuring each stage of the Enter key pipeline on a 70-line document:
//! 1. Rust Document mutation (`insert_text("\n")`)
//! 2. RenderState generation & JSON serialization
//! 3. Full document syntax highlighting (0..70 lines)
//! 4. Incremental syntax highlighting (2 lines: 10..11)
//! 5. 70-line paste & undo/redo
//!
//! Run with:
//! cargo test --release --bench enter_pipeline -- --nocapture

use std::hint::black_box;
use std::time::Instant;
use crest_editor_engine::editor::EditorEngine;
use crest_editor_engine::buffer::Position;

fn generate_70_line_document() -> String {
    let sample = [
        "use std::collections::HashMap;",
        "use std::sync::Arc;",
        "",
        "pub struct CodeModel {",
        "    id: usize,",
        "    name: String,",
        "    items: Vec<String>,",
        "    lookup: HashMap<String, usize>,",
        "}",
        "",
        "impl CodeModel {",
        "    pub fn new(id: usize, name: &str) -> Self {",
        "        Self {",
        "            id,",
        "            name: name.to_string(),",
        "            items: Vec::new(),",
        "            lookup: HashMap::new(),",
        "        }",
        "    }",
        "",
        "    pub fn add_item(&mut self, item: &str) -> bool {",
        "        if self.lookup.contains_key(item) {",
        "            return false;",
        "        }",
        "        let idx = self.items.len();",
        "        self.items.push(item.to_string());",
        "        self.lookup.insert(item.to_string(), idx);",
        "        true",
        "    }",
        "}",
    ];

    let mut doc = String::new();
    while doc.lines().count() < 70 {
        for line in &sample {
            doc.push_str(line);
            doc.push('\n');
            if doc.lines().count() >= 70 {
                break;
            }
        }
    }
    doc
}

#[test]
fn bench_enter_pipeline_stages() {
    let doc_content = generate_70_line_document();
    assert_eq!(doc_content.lines().count(), 70);

    println!("\n========================================================");
    println!(" ENTER-KEY PIPELINE BENCHMARK (70-LINE CODE DOCUMENT)");
    println!("========================================================");

    let iterations = 10_000;

    // Stage 1: Rust insert_text("\n") mutation
    let mut engine = EditorEngine::new();
    engine.open_document("file:///test.rs", "rust", &doc_content);
    engine.set_selection(Position::new(10, 5), Position::new(10, 5));

    let _start = Instant::now();
    for _ in 0..iterations {
        let mut e = EditorEngine::new();
        e.open_document("file:///test.rs", "rust", &doc_content);
        e.set_selection(Position::new(10, 5), Position::new(10, 5));
        let change = e.insert_text("\n");
        black_box(change);
    }
    // Measure single mutation cost (excluding setup)
    let mut e = EditorEngine::new();
    e.open_document("file:///test.rs", "rust", &doc_content);
    e.set_selection(Position::new(10, 5), Position::new(10, 5));
    let mut t_mut_total = std::time::Duration::ZERO;
    for _ in 0..iterations {
        let t0 = Instant::now();
        let change = e.insert_text("\n");
        t_mut_total += t0.elapsed();
        black_box(change);
        e.delete_backspace();
    }
    let avg_insert_us = (t_mut_total.as_nanos() as f64 / iterations as f64) / 1000.0;
    println!(" Stage 1: insert_text(\"\\n\") mutation:      {:.3} µs", avg_insert_us);

    // Stage 2: get_render_state() vs get_editor_state()
    let mut t_render_total = std::time::Duration::ZERO;
    let mut t_json_total = std::time::Duration::ZERO;
    let mut t_editor_state_total = std::time::Duration::ZERO;
    let mut t_editor_json_total = std::time::Duration::ZERO;
    for _ in 0..iterations {
        let t0 = Instant::now();
        let state = e.get_render_state().unwrap();
        t_render_total += t0.elapsed();

        let t1 = Instant::now();
        let json = serde_json::to_string(&state).unwrap();
        t_json_total += t1.elapsed();
        black_box(json);

        let t2 = Instant::now();
        let ed_state = e.get_editor_state().unwrap();
        t_editor_state_total += t2.elapsed();

        let t3 = Instant::now();
        let ed_json = serde_json::to_string(&ed_state).unwrap();
        t_editor_json_total += t3.elapsed();
        black_box(ed_json);
    }
    let avg_render_us = (t_render_total.as_nanos() as f64 / iterations as f64) / 1000.0;
    let avg_json_us = (t_json_total.as_nanos() as f64 / iterations as f64) / 1000.0;
    let avg_ed_state_us = (t_editor_state_total.as_nanos() as f64 / iterations as f64) / 1000.0;
    let avg_ed_json_us = (t_editor_json_total.as_nanos() as f64 / iterations as f64) / 1000.0;
    println!(" Stage 2a: get_render_state() (70 lines):    {:.3} µs", avg_render_us);
    println!(" Stage 2b: JSON serialize RenderState:       {:.3} µs", avg_json_us);
    println!(" Stage 2c: get_editor_state() (70 lines):    {:.3} µs ({:.1}x faster)", avg_ed_state_us, avg_render_us / avg_ed_state_us.max(0.001));
    println!(" Stage 2d: JSON serialize EditorState:       {:.3} µs ({:.1}x faster)", avg_ed_json_us, avg_json_us / avg_ed_json_us.max(0.001));

    // Stage 3: Full 70-line syntax highlighting
    let highlight_iters = 1_000;
    let start_full = Instant::now();
    for _ in 0..highlight_iters {
        let spans = e.highlight_visible_lines(0, 70);
        black_box(spans);
    }
    let avg_full_highlight_us = (start_full.elapsed().as_nanos() as f64 / highlight_iters as f64) / 1000.0;
    println!(" Stage 3a: Full 70-line syntax highlight:    {:.3} µs ({:.3} ms)", avg_full_highlight_us, avg_full_highlight_us / 1000.0);

    // Stage 4: Incremental 2-line syntax highlighting (lines 10..11)
    let start_inc = Instant::now();
    for _ in 0..highlight_iters {
        let spans = e.highlight_visible_lines(10, 11);
        black_box(spans);
    }
    let avg_inc_highlight_us = (start_inc.elapsed().as_nanos() as f64 / highlight_iters as f64) / 1000.0;
    println!(" Stage 3b: Incremental 2-line highlight:     {:.3} µs ({:.3} ms)", avg_inc_highlight_us, avg_inc_highlight_us / 1000.0);
    println!("           => Highlight speedup:             {:.1}x", avg_full_highlight_us / avg_inc_highlight_us.max(0.001));

    // Stage 5: 70-line paste & undo
    let paste_content = generate_70_line_document();
    let start_paste = Instant::now();
    for _ in 0..1_000 {
        e.insert_text(&paste_content);
        e.undo();
    }
    let avg_paste_us = (start_paste.elapsed().as_nanos() as f64 / 1_000.0) / 1000.0;
    println!(" Stage 4: 70-line paste + undo cycle:        {:.3} µs ({:.3} ms)", avg_paste_us, avg_paste_us / 1000.0);

    println!("========================================================\n");
}

#[test]
fn bench_large_document_editor_state() {
    let mut doc_5k = String::with_capacity(5000 * 50);
    for i in 0..5000 {
        doc_5k.push_str(&format!("    let var_{:04} = compute_value({}); // comment for line {}\n", i, i * 3, i));
    }

    println!("\n========================================================");
    println!(" LARGE-DOCUMENT (5,000 LINES) PIPELINE BENCHMARK");
    println!("========================================================");

    let mut engine = EditorEngine::new();
    engine.open_document("file:///large.rs", "rust", &doc_5k);

    let iterations = 100;

    // 1. Full RenderState generation + JSON serialization (Old path)
    let mut t_old_total = std::time::Duration::ZERO;
    for _ in 0..iterations {
        let t0 = Instant::now();
        let state = engine.get_render_state().unwrap();
        let json = serde_json::to_string(&state).unwrap();
        t_old_total += t0.elapsed();
        black_box(json);
    }
    let avg_old_us = (t_old_total.as_nanos() as f64 / iterations as f64) / 1000.0;

    // 2. EditorState generation + JSON serialization (New path)
    let mut t_new_total = std::time::Duration::ZERO;
    for _ in 0..iterations {
        let t0 = Instant::now();
        let state = engine.get_editor_state().unwrap();
        let json = serde_json::to_string(&state).unwrap();
        t_new_total += t0.elapsed();
        black_box(json);
    }
    let avg_new_us = (t_new_total.as_nanos() as f64 / iterations as f64) / 1000.0;

    println!(" 5k-line Old: get_render_state() + JSON:     {:.3} µs ({:.3} ms)", avg_old_us, avg_old_us / 1000.0);
    println!(" 5k-line New: get_editor_state() + JSON:     {:.3} µs ({:.3} ms)", avg_new_us, avg_new_us / 1000.0);
    println!(" => State Serialization Speedup:             {:.1}x", avg_old_us / avg_new_us.max(0.001));

    // 3. Cursor movement: Old path (set_selection + get_render_state + JSON) vs New path (move_cursor_with_state + JSON)
    let mut t_old_move = std::time::Duration::ZERO;
    for i in 0..iterations {
        let line = (i * 37) % 5000;
        let t0 = Instant::now();
        engine.set_selection(Position::new(line, 5), Position::new(line, 5));
        let state = engine.get_render_state().unwrap();
        let json = serde_json::to_string(&state).unwrap();
        t_old_move += t0.elapsed();
        black_box(json);
    }
    let avg_old_move_us = (t_old_move.as_nanos() as f64 / iterations as f64) / 1000.0;

    let mut t_new_move = std::time::Duration::ZERO;
    for i in 0..iterations {
        let line = (i * 37) % 5000;
        let t0 = Instant::now();
        let res = engine.set_selection_with_state(Position::new(line, 5), Position::new(line, 5));
        let json = serde_json::to_string(&res).unwrap();
        t_new_move += t0.elapsed();
        black_box(json);
    }
    let avg_new_move_us = (t_new_move.as_nanos() as f64 / iterations as f64) / 1000.0;

    println!(" 5k-line Old Cursor Move + Refresh:          {:.3} µs ({:.3} ms)", avg_old_move_us, avg_old_move_us / 1000.0);
    println!(" 5k-line New Cursor Move (set_selection_with_state): {:.3} µs ({:.3} ms)", avg_new_move_us, avg_new_move_us / 1000.0);
    println!(" => Cursor Move Speedup:                     {:.1}x", avg_old_move_us / avg_new_move_us.max(0.001));

    // 4. Single Keystroke edit: Old path (insert_text + get_render_state + JSON) vs New path (insert_text_with_state + JSON)
    let mut t_old_edit = std::time::Duration::ZERO;
    for _ in 0..iterations {
        let t0 = Instant::now();
        let _change = engine.insert_text("a");
        let state = engine.get_render_state().unwrap();
        let json = serde_json::to_string(&state).unwrap();
        t_old_edit += t0.elapsed();
        black_box(json);
        engine.delete_backspace();
    }
    let avg_old_edit_us = (t_old_edit.as_nanos() as f64 / iterations as f64) / 1000.0;

    let mut t_new_edit = std::time::Duration::ZERO;
    for _ in 0..iterations {
        let t0 = Instant::now();
        let res = engine.insert_text_with_state("a");
        let json = serde_json::to_string(&res).unwrap();
        t_new_edit += t0.elapsed();
        black_box(json);
        engine.delete_backspace();
    }
    let avg_new_edit_us = (t_new_edit.as_nanos() as f64 / iterations as f64) / 1000.0;

    println!(" 5k-line Old Keystroke Edit + Refresh:       {:.3} µs ({:.3} ms)", avg_old_edit_us, avg_old_edit_us / 1000.0);
    println!(" 5k-line New Keystroke Edit (insert_text_with_state): {:.3} µs ({:.3} ms)", avg_new_edit_us, avg_new_edit_us / 1000.0);
    println!(" => Keystroke Edit Speedup:                  {:.1}x", avg_old_edit_us / avg_new_edit_us.max(0.001));

    println!("========================================================\n");
}
