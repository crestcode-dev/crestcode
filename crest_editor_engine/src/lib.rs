pub mod buffer;
pub mod editor;
pub mod history;
pub mod jni_api;
pub mod lsp;
pub mod syntax;

pub use buffer::{Document, Position, Selection, TextBuffer};
pub use editor::EditorEngine;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_buffer_insert_delete_replace() {
        let mut buf = TextBuffer::from_str("Hello World");
        let end_pos = buf.insert(Position::new(0, 5), ", Rust");
        assert_eq!(buf.get_text(), "Hello, Rust World");
        assert_eq!(end_pos, Position::new(0, 11));

        let deleted = buf.delete_range(Position::new(0, 5), Position::new(0, 11));
        assert_eq!(deleted, ", Rust");
        assert_eq!(buf.get_text(), "Hello World");
    }

    #[test]
    fn test_buffer_multiline_operations() {
        let mut buf = TextBuffer::from_str("line 1\nline 2\nline 3");
        assert_eq!(buf.line_count(), 3);

        buf.insert(Position::new(1, 6), "\nline 2.5");
        assert_eq!(buf.line_count(), 4);
        assert_eq!(buf.get_line(2), Some("line 2.5"));
    }

    #[test]
    fn test_unicode_position_mapping() {
        use buffer::position::{
            byte_col_to_char_col, char_col_to_byte_col, utf16_col_to_utf8_col,
            utf8_col_to_utf16_col,
        };

        let sample = "🚀 Hello 世界";
        // 🚀 is 4 bytes UTF-8, 2 code units UTF-16, 1 char
        // 世界 is 3 bytes each UTF-8, 1 code unit each UTF-16, 1 char each

        let u16_offset = utf8_col_to_utf16_col(sample, 3); // 🚀, ' ', 'H'
        assert_eq!(u16_offset, 4); // 2 + 1 + 1

        let u8_idx = utf16_col_to_utf8_col(sample, 4);
        assert_eq!(u8_idx, 3);

        let byte_col = char_col_to_byte_col(sample, 1); // after rocket
        assert_eq!(byte_col, 4);

        let char_col = byte_col_to_char_col(sample, 4);
        assert_eq!(char_col, 1);
    }

    #[test]
    fn test_editor_undo_redo() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///test.rs", "rust", "fn main() {}");

        engine.insert_text("\n// test comment");
        assert!(engine
            .get_active_document()
            .unwrap()
            .buffer
            .get_text()
            .contains("// test comment"));

        let undone = engine.undo();
        assert!(undone);
        assert_eq!(
            engine.get_active_document().unwrap().buffer.get_text(),
            "fn main() {}"
        );

        let redone = engine.redo();
        assert!(redone);
        assert!(engine
            .get_active_document()
            .unwrap()
            .buffer
            .get_text()
            .contains("// test comment"));

        // Undo again to return to "fn main() {}"
        assert!(engine.undo());
        assert_eq!(
            engine.get_active_document().unwrap().buffer.get_text(),
            "fn main() {}"
        );

        // Test delete_selection followed by undo/redo
        engine.set_selection(Position::new(0, 3), Position::new(0, 7)); // select "main"
        let deleted = engine.delete_selection();
        assert!(deleted.is_some());
        assert_eq!(
            engine.get_active_document().unwrap().buffer.get_line(0).as_deref(),
            Some("fn () {}")
        );

        assert!(engine.undo());
        assert_eq!(
            engine.get_active_document().unwrap().buffer.get_line(0).as_deref(),
            Some("fn main() {}")
        );

        assert!(engine.redo());
        assert_eq!(
            engine.get_active_document().unwrap().buffer.get_line(0).as_deref(),
            Some("fn () {}")
        );
    }

    #[test]
    fn test_multi_document_management() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///a.rs", "rust", "struct A;");
        engine.open_document("file:///b.kt", "kotlin", "class B");

        assert_eq!(engine.get_active_document().unwrap().uri, "file:///b.kt");

        engine.set_active_document("file:///a.rs");
        assert_eq!(engine.get_active_document().unwrap().uri, "file:///a.rs");

        engine.close_document("file:///a.rs");
        assert_eq!(engine.get_active_document().unwrap().uri, "file:///b.kt");
    }

    #[test]
    fn test_syntax_highlighting() {
        let highlighter = syntax::SyntaxHighlighter::for_language("rust");
        let spans = highlighter.highlight_line("fn main() { let x = 42; }");
        assert!(!spans.is_empty());
        assert_eq!(spans[0].token_type, syntax::TokenType::Keyword); // "fn"
    }

    #[test]
    fn test_jsonrpc_framing() {
        use lsp::transport::{read_message, write_message};
        use std::io::Cursor;

        let payload = r#"{"jsonrpc":"2.0","id":1,"method":"test","params":{}}"#;
        let mut buf = Vec::new();
        write_message(&mut buf, payload).unwrap();

        let mut cursor = std::io::BufReader::new(Cursor::new(buf));
        let read_back = read_message(&mut cursor).unwrap().unwrap();
        assert_eq!(read_back, payload);
    }

    #[test]
    fn test_auto_closing_pairs_and_bracket_matching() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///test.rs", "rust", "fn test");

        // Move cursor to end of line
        engine.set_selection(Position::new(0, 7), Position::new(0, 7));

        // Insert opening bracket '(' -> should auto-close to '()'
        engine.insert_text("(");
        let doc = engine.get_active_document().unwrap();
        assert_eq!(doc.buffer.get_line(0).as_deref(), Some("fn test()"));
        assert_eq!(doc.selection.head, Position::new(0, 8)); // Cursor inside ()

        // Verify bracket matching detects (0,7) and (0,8)
        let brackets = doc.buffer.find_matching_bracket(Position::new(0, 8));
        assert!(brackets.is_some());
        let (open_pos, close_pos) = brackets.unwrap();
        assert_eq!(open_pos, Position::new(0, 7));
        assert_eq!(close_pos, Position::new(0, 8));

        // Delete backspace between () -> should erase both ( and )
        engine.delete_backspace();
        let doc_after = engine.get_active_document().unwrap();
        assert_eq!(doc_after.buffer.get_line(0).as_deref(), Some("fn test"));
    }

    #[test]
    fn test_word_selection_and_clipboard() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///test.rs", "rust", "hello world_code test");

        // Long press word selection at 'world_code' (char 7)
        engine.select_word_at(0, 7);
        assert_eq!(engine.get_selected_text(), "world_code");

        // Delete selection
        engine.delete_selection();
        let doc = engine.get_active_document().unwrap();
        assert_eq!(doc.buffer.get_line(0).as_deref(), Some("hello  test"));

        // Select all
        engine.select_all();
        assert_eq!(engine.get_selected_text(), "hello  test");
    }

    #[test]
    fn test_enter_key_line_splitting_and_cursor_positioning() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///enter_test.rs", "rust", "fn main() {\n    println!(\"hello\");\n}");
        assert_eq!(engine.get_active_document().unwrap().buffer.line_count(), 3);

        // Position cursor after '{' at line 0, char 11
        engine.set_selection(Position::new(0, 11), Position::new(0, 11));

        // Press Enter
        let change = engine.insert_text("\n").expect("Expected TextChangeEvent");
        assert_eq!(change.start, Position::new(0, 11));
        assert_eq!(change.end, Position::new(1, 0));
        assert_eq!(change.text, "\n");

        let doc = engine.get_active_document().unwrap();
        assert_eq!(doc.buffer.line_count(), 4);
        assert_eq!(doc.buffer.get_line(0).as_deref(), Some("fn main() {"));
        assert_eq!(doc.selection.head, Position::new(1, 0));

        // Backspace should merge line 1 back into line 0
        let backspace_change = engine.delete_backspace().expect("Expected backspace change");
        assert_eq!(backspace_change.text, "");

        let doc_after = engine.get_active_document().unwrap();
        assert_eq!(doc_after.buffer.line_count(), 3);
        assert_eq!(doc_after.selection.head, Position::new(0, 11));
    }

    #[test]
    fn test_70_line_paste_selection_replacement_and_undo_redo() {
        let mut engine = EditorEngine::new();
        let mut initial_70_lines = String::new();
        for i in 0..70 {
            initial_70_lines.push_str(&format!("let val_{} = {};\n", i, i * 10));
        }
        engine.open_document("file:///paste_test.rs", "rust", &initial_70_lines);
        assert_eq!(engine.get_active_document().unwrap().buffer.line_count(), 71);

        // Select lines 10..20
        engine.set_selection(Position::new(10, 0), Position::new(20, 0));
        assert!(!engine.get_active_document().unwrap().selection.is_empty());

        // Replace selection with 10 lines
        let mut replacement = String::new();
        for i in 0..10 {
            replacement.push_str(&format!("let replaced_{} = true;\n", i));
        }
        let change = engine.insert_text(&replacement).expect("Expected change");
        assert_eq!(change.start, Position::new(10, 0));
        assert_eq!(change.end, Position::new(20, 0));
        assert_eq!(change.text, replacement);

        let doc = engine.get_active_document().unwrap();
        assert_eq!(doc.buffer.line_count(), 71);

        // Undo replacement
        assert!(engine.undo());
        let doc_undo = engine.get_active_document().unwrap();
        assert_eq!(doc_undo.buffer.get_line(10).as_deref(), Some("let val_10 = 100;"));

        // Redo replacement
        assert!(engine.redo());
        let doc_redo = engine.get_active_document().unwrap();
        assert_eq!(doc_redo.buffer.get_line(10).as_deref(), Some("let replaced_0 = true;"));
    }

    #[test]
    fn test_unicode_multiline_editing_and_syntax() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///unicode_test.rs", "rust", "let greet = \"👋 Hello 🌍\";\n// 🚀 Comment");

        // Insert at end of line 0
        engine.set_selection(Position::new(0, 24), Position::new(0, 24));
        engine.insert_text("\n");

        let doc = engine.get_active_document().unwrap();
        assert_eq!(doc.buffer.line_count(), 3);
        assert_eq!(doc.selection.head, Position::new(1, 0));

        // Highlight line 0 and line 1
        let spans = engine.highlight_visible_lines(0, 2);
        assert_eq!(spans.len(), 3);
        // Line 0 keyword 'let'
        assert_eq!(spans[0].1[0].token_type, syntax::TokenType::Keyword);
        // Line 2 comment '// 🚀 Comment'
        assert_eq!(spans[2].1[0].token_type, syntax::TokenType::Comment);
    }
}
