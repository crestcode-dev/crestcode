//! Standalone Benchmark Harness: TextBuffer (Piece Table / Vec<String>) vs RopeBuffer (Zed Rope)
//!
//! Evaluates the core performance characteristics under identical workloads:
//! 1. Large document loading (12,000 lines / ~750 KB and 25,000 lines / ~1.5 MB)
//! 2. Edits near the beginning of the file (worst case for Vec<String> insertion)
//! 3. Edits in the middle and near the end of the file
//! 4. Repeated small cursor insertions (typing simulation: 500 keystrokes)
//! 5. Deletions across lines (multiline range deletions: 10, 100, and 1,000 lines)
//! 6. Text extraction (full document `get_text` and single-line `get_line` queries)
//!
//! Run with:
//! cargo test --release --bench buffer_comparison --features zed-rope -- --nocapture

use std::hint::black_box;
use std::time::{Duration, Instant};

use crest_editor_engine::buffer::{Position, TextBuffer};

#[cfg(feature = "zed-rope")]
use crest_editor_engine::buffer::RopeBuffer;

/// Generates a realistic synthetic code/text file with `lines` lines.
fn generate_sample_document(lines: usize) -> String {
    let mut doc = String::with_capacity(lines * 64);
    let sample_lines = [
        "    let mut map: HashMap<String, Vec<usize>> = HashMap::new();",
        "    for (idx, token) in tokens.iter().enumerate() {",
        "        if token.is_keyword() && token.len() > 3 {",
        "            map.entry(token.to_string()).or_default().push(idx);",
        "        } else if token.is_whitespace() {",
        "            continue;",
        "        }",
        "    }",
        "    // Perform invariant validation across sum-tree leaf nodes",
        "    assert!(map.len() <= MAX_ALLOWED_ENTRIES, \"Invariant violation\");",
        "    log::debug!(\"Processed {} tokens successfully\", tokens.len());",
        "    Ok(ProcessingResult::new(map, tokens.len()))",
    ];

    for i in 0..lines {
        let template = sample_lines[i % sample_lines.len()];
        doc.push_str(&format!("/* Line {:05} */ {}\n", i + 1, template));
    }
    doc
}

fn generate_multiline_block(lines: usize) -> String {
    let mut block = String::with_capacity(lines * 48);
    for i in 0..lines {
        block.push_str(&format!("    let pasted_var_{} = compute_val({});\n", i, i * 2));
    }
    block
}

#[derive(Debug, Clone)]
pub struct BenchResult {
    pub category: String,
    pub workload: String,
    pub iterations: usize,
    pub text_buffer_time: Duration,
    pub rope_buffer_time: Duration,
}

impl BenchResult {
    pub fn speedup_ratio(&self) -> (String, f64) {
        let tb_ns = self.text_buffer_time.as_nanos() as f64;
        let rb_ns = self.rope_buffer_time.as_nanos() as f64;
        if rb_ns == 0.0 || tb_ns == 0.0 {
            return ("Equal".to_string(), 1.0);
        }
        if rb_ns < tb_ns {
            ("RopeBuffer".to_string(), tb_ns / rb_ns)
        } else {
            ("TextBuffer".to_string(), rb_ns / tb_ns)
        }
    }
}

fn format_duration(d: Duration) -> String {
    let nanos = d.as_nanos();
    if nanos < 1_000 {
        format!("{} ns", nanos)
    } else if nanos < 1_000_000 {
        format!("{:.2} µs", nanos as f64 / 1_000.0)
    } else if nanos < 1_000_000_000 {
        format!("{:.2} ms", nanos as f64 / 1_000_000.0)
    } else {
        format!("{:.2} s", d.as_secs_f64())
    }
}

#[test]
#[cfg(feature = "zed-rope")]
fn bench_text_buffer_vs_rope_buffer() {
    println!("\n=========================================================================================");
    println!("        CREST EDITOR ENGINE: TextBuffer (Piece Table) vs RopeBuffer (Zed Rope)           ");
    println!("=========================================================================================");

    const LINE_COUNT: usize = 12_000;
    println!("Generating test dataset ({} lines)...", LINE_COUNT);
    let doc_12k = generate_sample_document(LINE_COUNT);
    let doc_25k = generate_sample_document(25_000);
    let block_10_lines = generate_multiline_block(10);
    let block_500_lines = generate_multiline_block(500);

    println!(
        "Dataset 1: 12,000 lines, {:.2} KB ({} bytes)",
        doc_12k.len() as f64 / 1024.0,
        doc_12k.len()
    );
    println!(
        "Dataset 2: 25,000 lines, {:.2} KB ({} bytes)\n",
        doc_25k.len() as f64 / 1024.0,
        doc_25k.len()
    );

    // Sanity check: Ensure behavioral equivalence on sample ops
    {
        let mut tb = TextBuffer::from_str(&doc_12k);
        let mut rb = RopeBuffer::from_str(&doc_12k);
        assert_eq!(tb.line_count(), rb.line_count(), "Line count mismatch");
        assert_eq!(tb.get_line(0), rb.get_line(0).as_deref(), "Line 0 mismatch");
        assert_eq!(tb.get_line(6000), rb.get_line(6000).as_deref(), "Line 6000 mismatch");

        tb.insert(Position::new(100, 0), "/* insert */\n");
        rb.insert(Position::new(100, 0), "/* insert */\n");
        assert_eq!(tb.line_count(), rb.line_count());
        assert_eq!(tb.get_line(100), rb.get_line(100).as_deref());
    }

    let mut results: Vec<BenchResult> = Vec::new();

    // =========================================================================
    // WORKLOAD 1: Large Document Loading
    // =========================================================================
    {
        // 1a. Load 12,000 lines (~750 KB)
        let iters = 25;
        let mut tb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            let buf = TextBuffer::from_str(black_box(&doc_12k));
            black_box(buf);
            tb_total += start.elapsed();
        }

        let mut rb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            let buf = RopeBuffer::from_str(black_box(&doc_12k));
            black_box(buf);
            rb_total += start.elapsed();
        }

        results.push(BenchResult {
            category: "1. Loading".to_string(),
            workload: format!("Load 12,000 lines ({:.1} KB)", doc_12k.len() as f64 / 1024.0),
            iterations: iters,
            text_buffer_time: tb_total / (iters as u32),
            rope_buffer_time: rb_total / (iters as u32),
        });

        // 1b. Load 25,000 lines (~1.5 MB)
        let iters = 15;
        let mut tb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            let buf = TextBuffer::from_str(black_box(&doc_25k));
            black_box(buf);
            tb_total += start.elapsed();
        }

        let mut rb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            let buf = RopeBuffer::from_str(black_box(&doc_25k));
            black_box(buf);
            rb_total += start.elapsed();
        }

        results.push(BenchResult {
            category: "1. Loading".to_string(),
            workload: format!("Load 25,000 lines ({:.1} KB)", doc_25k.len() as f64 / 1024.0),
            iterations: iters,
            text_buffer_time: tb_total / (iters as u32),
            rope_buffer_time: rb_total / (iters as u32),
        });
    }

    // =========================================================================
    // WORKLOAD 2: Edits near Beginning of file (Line 0)
    // =========================================================================
    {
        let iters = 100;

        // 2a. Single-line insert at line 0 (isolated mutation)
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            buf.insert(Position::new(0, 0), black_box("// single line\n"));
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            buf.insert(Position::new(0, 0), black_box("// single line\n"));
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "2. Beginning Edits".to_string(),
            workload: "Insert 1 line at Line 0 (top of 12k lines)".to_string(),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });

        // 2b. Multiline insert (10 lines) at line 0
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            buf.insert(Position::new(0, 0), black_box(&block_10_lines));
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            buf.insert(Position::new(0, 0), black_box(&block_10_lines));
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "2. Beginning Edits".to_string(),
            workload: "Insert 10 lines at Line 0".to_string(),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });

        // 2c. Large block paste (500 lines) at line 0 (Vec<String> shift stress)
        let iters = 50;
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            buf.insert(Position::new(0, 0), black_box(&block_500_lines));
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            buf.insert(Position::new(0, 0), black_box(&block_500_lines));
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "2. Beginning Edits".to_string(),
            workload: "Paste 500 lines at Line 0 (severe shift)".to_string(),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });
    }

    // =========================================================================
    // WORKLOAD 3: Edits in Middle and near End of file
    // =========================================================================
    {
        let iters = 100;
        let mid = LINE_COUNT / 2;
        let end = LINE_COUNT - 10;

        // 3a. Single line insert at Middle (Line 6000)
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            buf.insert(Position::new(mid, 0), black_box("// mid line\n"));
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            buf.insert(Position::new(mid, 0), black_box("// mid line\n"));
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "3. Middle / End Edits".to_string(),
            workload: format!("Insert 1 line at Middle (Line {})", mid),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });

        // 3b. Paste 500 lines at Middle (Line 6000)
        let iters = 50;
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            buf.insert(Position::new(mid, 0), black_box(&block_500_lines));
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            buf.insert(Position::new(mid, 0), black_box(&block_500_lines));
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "3. Middle / End Edits".to_string(),
            workload: format!("Paste 500 lines at Middle (Line {})", mid),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });

        // 3c. Paste 500 lines near End (Line 11990)
        let iters = 50;
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            buf.insert(Position::new(end, 0), black_box(&block_500_lines));
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            buf.insert(Position::new(end, 0), black_box(&block_500_lines));
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "3. Middle / End Edits".to_string(),
            workload: format!("Paste 500 lines near End (Line {})", end),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });
    }

    // =========================================================================
    // WORKLOAD 4: Repeated Small Cursor Insertions (Typing Simulation: 500 keystrokes)
    // =========================================================================
    {
        let iters = 10;

        // 4a. Typing 500 keystrokes sequentially at line 10
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            let mut pos = Position::new(10, 0);
            for _ in 0..500 {
                pos = buf.insert(pos, "x");
            }
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            let mut pos = Position::new(10, 0);
            for _ in 0..500 {
                pos = buf.insert(pos, "x");
            }
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "4. Typing Simulation".to_string(),
            workload: "500 keystrokes typing at Line 10".to_string(),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });

        // 4b. Typing 500 keystrokes sequentially at middle (Line 6000)
        let mid = LINE_COUNT / 2;
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            let mut pos = Position::new(mid, 0);
            for _ in 0..500 {
                pos = buf.insert(pos, "a");
            }
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            let mut pos = Position::new(mid, 0);
            for _ in 0..500 {
                pos = buf.insert(pos, "a");
            }
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "4. Typing Simulation".to_string(),
            workload: format!("500 keystrokes typing at Middle (Line {})", mid),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });
    }

    // =========================================================================
    // WORKLOAD 5: Deletions across lines (multiline range deletion)
    // =========================================================================
    {
        let iters = 50;

        // 5a. Delete 10 lines near beginning (lines 5..15)
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            let deleted = buf.delete_range(Position::new(5, 0), Position::new(15, 0));
            black_box(deleted);
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            let deleted = buf.delete_range(Position::new(5, 0), Position::new(15, 0));
            black_box(deleted);
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "5. Multiline Deletions".to_string(),
            workload: "Delete 10 lines (lines 5..15)".to_string(),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });

        // 5b. Delete 100 lines near beginning (lines 50..150)
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            let deleted = buf.delete_range(Position::new(50, 0), Position::new(150, 0));
            black_box(deleted);
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            let deleted = buf.delete_range(Position::new(50, 0), Position::new(150, 0));
            black_box(deleted);
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "5. Multiline Deletions".to_string(),
            workload: "Delete 100 lines (lines 50..150)".to_string(),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });

        // 5c. Large block delete: 1,000 lines in middle (lines 5,000..6,000)
        let mut tb_batch: Vec<TextBuffer> = (0..iters).map(|_| TextBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut tb_batch {
            let deleted = buf.delete_range(Position::new(5000, 0), Position::new(6000, 0));
            black_box(deleted);
        }
        let tb_time = start.elapsed() / (iters as u32);

        let mut rb_batch: Vec<RopeBuffer> = (0..iters).map(|_| RopeBuffer::from_str(&doc_12k)).collect();
        let start = Instant::now();
        for buf in &mut rb_batch {
            let deleted = buf.delete_range(Position::new(5000, 0), Position::new(6000, 0));
            black_box(deleted);
        }
        let rb_time = start.elapsed() / (iters as u32);

        results.push(BenchResult {
            category: "5. Multiline Deletions".to_string(),
            workload: "Delete 1,000 lines in middle (5000..6000)".to_string(),
            iterations: iters,
            text_buffer_time: tb_time,
            rope_buffer_time: rb_time,
        });
    }

    // =========================================================================
    // WORKLOAD 6: Text Extraction (get_text, get_line)
    // =========================================================================
    {
        let tb = TextBuffer::from_str(&doc_12k);
        let rb = RopeBuffer::from_str(&doc_12k);

        // 6a. Full document extraction (get_text)
        let iters = 50;
        let mut tb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            let text = tb.get_text();
            black_box(text);
            tb_total += start.elapsed();
        }

        let mut rb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            let text = rb.get_text();
            black_box(text);
            rb_total += start.elapsed();
        }

        results.push(BenchResult {
            category: "6. Text Extraction".to_string(),
            workload: "Full doc get_text() (12k lines / 746 KB)".to_string(),
            iterations: iters,
            text_buffer_time: tb_total / (iters as u32),
            rope_buffer_time: rb_total / (iters as u32),
        });

        // 6b. Viewport rendering: 100 sequential get_line() queries
        let iters = 500;
        let mut tb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            for i in 6000..6100 {
                let line = tb.get_line(i);
                black_box(line);
            }
            tb_total += start.elapsed();
        }

        let mut rb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            for i in 6000..6100 {
                let line = rb.get_line(i);
                black_box(line);
            }
            rb_total += start.elapsed();
        }

        results.push(BenchResult {
            category: "6. Text Extraction".to_string(),
            workload: "Viewport rendering (100 seq get_line)".to_string(),
            iterations: iters,
            text_buffer_time: tb_total / (iters as u32),
            rope_buffer_time: rb_total / (iters as u32),
        });

        // 6c. Random line queries: 100 scattered get_line() lookups
        let random_indices: Vec<usize> = (0..100)
            .map(|i| (i * 119 + 37) % LINE_COUNT)
            .collect();

        let mut tb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            for &idx in &random_indices {
                let line = tb.get_line(idx);
                black_box(line);
            }
            tb_total += start.elapsed();
        }

        let mut rb_total = Duration::ZERO;
        for _ in 0..iters {
            let start = Instant::now();
            for &idx in &random_indices {
                let line = rb.get_line(idx);
                black_box(line);
            }
            rb_total += start.elapsed();
        }

        results.push(BenchResult {
            category: "6. Text Extraction".to_string(),
            workload: "Random line access (100 scattered get_line)".to_string(),
            iterations: iters,
            text_buffer_time: tb_total / (iters as u32),
            rope_buffer_time: rb_total / (iters as u32),
        });
    }

    // =========================================================================
    // FORMATTED REPORT OUTPUT
    // =========================================================================
    println!("\n{:-<125}", "");
    println!(
        "{:<20} | {:<40} | {:<7} | {:<15} | {:<15} | {:<15}",
        "Category", "Workload", "Iters", "TextBuffer", "RopeBuffer", "Winner (Ratio)"
    );
    println!("{:-<125}", "");

    for r in &results {
        let (winner, ratio) = r.speedup_ratio();
        let speedup_str = format!("{} ({:.2}x)", winner, ratio);
        println!(
            "{:<20} | {:<40} | {:<7} | {:<15} | {:<15} | {:<15}",
            r.category,
            r.workload,
            r.iterations,
            format_duration(r.text_buffer_time),
            format_duration(r.rope_buffer_time),
            speedup_str
        );
    }
    println!("{:-<125}\n", "");
}
