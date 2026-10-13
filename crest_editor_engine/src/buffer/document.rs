#[cfg(not(feature = "zed-rope"))]
use crate::buffer::piece_table::TextBuffer;
#[cfg(feature = "zed-rope")]
use crate::buffer::rope_buffer::RopeBuffer;

use crate::buffer::position::{Position, Selection};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TextChangeEvent {
    pub start: Position,
    pub end: Position,
    pub text: String,
}

#[derive(Debug, Clone)]
pub struct Document {
    pub uri: String,
    pub language_id: String,
    pub version: i64,
    #[cfg(not(feature = "zed-rope"))]
    pub buffer: TextBuffer,
    #[cfg(feature = "zed-rope")]
    pub buffer: RopeBuffer,
    pub selection: Selection,
    pub is_modified: bool,
}

impl Document {
    pub fn new(uri: String, language_id: String, content: &str) -> Self {
        #[cfg(not(feature = "zed-rope"))]
        let buffer = TextBuffer::from_str(content);
        #[cfg(feature = "zed-rope")]
        let buffer = RopeBuffer::from_str(content);

        Self {
            uri,
            language_id,
            version: 1,
            buffer,
            selection: Selection::caret(Position::zero()),
            is_modified: false,
        }
    }

    /// Returns a static string identifier of the buffer backend used by Document.
    pub fn backend_name() -> &'static str {
        #[cfg(not(feature = "zed-rope"))]
        {
            "piece_table"
        }
        #[cfg(feature = "zed-rope")]
        {
            "zed_rope"
        }
    }

    pub fn apply_insert(&mut self, text: &str) -> TextChangeEvent {
        let start = self.selection.start();
        let end = self.selection.end();

        if start != end {
            self.buffer.delete_range(start, end);
        }

        let new_pos = self.buffer.insert(start, text);
        self.selection = Selection::caret(new_pos);
        self.version += 1;
        TextChangeEvent {
            start,
            end: new_pos,
            text: text.to_string(),
        }
    }

    pub fn apply_delete(&mut self, start: Position, end: Position) -> TextChangeEvent {
        self.buffer.delete_range(start, end);
        self.selection = Selection::caret(start);
        self.version += 1;
        self.is_modified = true;
        TextChangeEvent {
            start,
            end,
            text: String::new(),
        }
    }

    pub fn apply_delete_backspace(&mut self) -> Option<TextChangeEvent> {
        if !self.selection.is_empty() {
            let start = self.selection.start();
            let end = self.selection.end();
            self.buffer.delete_range(start, end);
            self.selection = Selection::caret(start);
            self.version += 1;
            self.is_modified = true;
            return Some(TextChangeEvent {
                start,
                end,
                text: String::new(),
            });
        }

        let caret = self.selection.head;
        if caret.line == 0 && caret.character == 0 {
            return None;
        }

        let start = if caret.character > 0 {
            Position::new(caret.line, caret.character - 1)
        } else {
            let prev_line = caret.line - 1;
            let prev_line_len = self.buffer.line_length_chars(prev_line);
            Position::new(prev_line, prev_line_len)
        };

        self.buffer.delete_range(start, caret);
        self.selection = Selection::caret(start);
        self.version += 1;
        self.is_modified = true;

        Some(TextChangeEvent {
            start,
            end: caret,
            text: String::new(),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_document_backend_identifier() {
        #[cfg(not(feature = "zed-rope"))]
        assert_eq!(Document::backend_name(), "piece_table");
        #[cfg(feature = "zed-rope")]
        assert_eq!(Document::backend_name(), "zed_rope");
    }

    #[test]
    fn test_document_pipeline_insert_and_replace() {
        let mut doc = Document::new("file:///test.rs".to_string(), "rust".to_string(), "hello world");
        assert_eq!(doc.buffer.get_text(), "hello world");

        // Insert at caret (0, 5)
        doc.selection = Selection::caret(Position::new(0, 5));
        let ev = doc.apply_insert(", brave");
        assert_eq!(ev.start, Position::new(0, 5));
        assert_eq!(ev.end, Position::new(0, 12));
        assert_eq!(ev.text, ", brave");
        assert_eq!(doc.buffer.get_text(), "hello, brave world");
        assert_eq!(doc.selection.head, Position::new(0, 12));
        assert_eq!(doc.version, 2);

        // Replace range by selecting "brave" (0, 7) to (0, 12)
        doc.selection = Selection::new(Position::new(0, 7), Position::new(0, 12));
        let ev2 = doc.apply_insert("new");
        assert_eq!(ev2.start, Position::new(0, 7));
        assert_eq!(ev2.end, Position::new(0, 10));
        assert_eq!(doc.buffer.get_text(), "hello, new world");
        assert_eq!(doc.version, 3);
    }

    #[test]
    fn test_document_pipeline_delete_and_backspace() {
        let mut doc = Document::new(
            "file:///multiline.rs".to_string(),
            "rust".to_string(),
            "line 1\nline 2\nline 3",
        );

        // Explicit delete
        let del_ev = doc.apply_delete(Position::new(0, 4), Position::new(1, 4));
        assert_eq!(del_ev.start, Position::new(0, 4));
        assert_eq!(del_ev.end, Position::new(1, 4));
        assert_eq!(doc.buffer.get_text(), "line 2\nline 3");

        // Backspace at line boundary
        doc.selection = Selection::caret(Position::new(1, 0));
        let bs_ev = doc.apply_delete_backspace().expect("should delete across newline");
        assert_eq!(bs_ev.start, Position::new(0, 6));
        assert_eq!(bs_ev.end, Position::new(1, 0));
        assert_eq!(doc.buffer.get_text(), "line 2line 3");
    }

    #[test]
    fn test_document_pipeline_unicode_handling() {
        let mut doc = Document::new(
            "file:///unicode.rs".to_string(),
            "rust".to_string(),
            "🦀 Rust 🚀 Rocket",
        );
        assert_eq!(doc.buffer.line_length_chars(0), 15);

        // Insert after crab emoji (char 2)
        doc.selection = Selection::caret(Position::new(0, 2));
        doc.apply_insert("is ");
        assert_eq!(doc.buffer.get_text(), "🦀 is Rust 🚀 Rocket");
        assert_eq!(doc.selection.head, Position::new(0, 5));

        // Delete backspace 3 chars ("is ")
        doc.apply_delete_backspace();
        doc.apply_delete_backspace();
        doc.apply_delete_backspace();
        assert_eq!(doc.buffer.get_text(), "🦀 Rust 🚀 Rocket");
        assert_eq!(doc.selection.head, Position::new(0, 2));
    }
}
