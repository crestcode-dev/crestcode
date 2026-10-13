//! Zed-Rope backed text buffer implementation.
//!
//! When the `zed-rope` feature is enabled, this module provides
//! `RopeBuffer` — an efficient, tree-indexed buffer backed by Zed's
//! `rope::Rope` (a sum-tree B-tree of UTF-8 chunks).
//!
//! # Architecture
//! The default `TextBuffer` stores lines in a `Vec<String>`.
//! `RopeBuffer` amortizes large document modifications to O(log n),
//! provides sub-tree slicing, and preserves character boundary invariants.

use crate::buffer::position::Position;
use rope::{Point, Rope};

/// A text buffer backed by Zed's `Rope` data structure.
#[derive(Clone, Default, Debug)]
pub struct RopeBuffer {
    rope: Rope,
}

impl RopeBuffer {
    /// Create an empty rope buffer.
    pub fn new() -> Self {
        Self { rope: Rope::new() }
    }

    /// Create a rope buffer from initial string content.
    /// Normalizes CRLF and CR to LF internally for consistency with TextBuffer.
    pub fn from_str(content: &str) -> Self {
        if content.is_empty() {
            return Self::new();
        }
        let mut rope = Rope::new();
        if content.contains('\r') {
            let normalized = content.replace("\r\n", "\n").replace('\r', "\n");
            rope.push(&normalized);
        } else {
            rope.push(content);
        }
        Self { rope }
    }

    /// Return the full document text as a newly allocated `String`.
    pub fn get_text(&self) -> String {
        self.rope.to_string()
    }

    /// Number of lines (newline-delimited rows) in the buffer.
    /// Always at least 1, even if empty, matching TextBuffer semantics.
    pub fn line_count(&self) -> usize {
        (self.rope.max_point().row as usize) + 1
    }

    /// Total byte length of the buffer.
    pub fn len(&self) -> usize {
        self.rope.len()
    }

    /// Whether the buffer contains 0 bytes.
    pub fn is_empty(&self) -> bool {
        self.rope.is_empty()
    }

    /// Retrieve the text of a single line (excluding trailing newline `\n`).
    pub fn get_line(&self, line_idx: usize) -> Option<String> {
        if line_idx >= self.line_count() {
            return None;
        }
        let row = line_idx as u32;
        let line_len = self.rope.line_len(row) as usize;
        let start = self.rope.point_to_offset(Point::new(row, 0));
        let end = start + line_len;

        let mut line = String::with_capacity(line_len);
        for chunk in self.rope.chunks_in_range(start..end) {
            line.push_str(chunk);
        }
        Some(line)
    }

    /// Number of Unicode scalar values (chars) on a given line.
    pub fn line_length_chars(&self, line_idx: usize) -> usize {
        if line_idx >= self.line_count() {
            return 0;
        }
        let row = line_idx as u32;
        let line_len = self.rope.line_len(row) as usize;
        let start = self.rope.point_to_offset(Point::new(row, 0));
        let end = start + line_len;

        self.rope
            .chunks_in_range(start..end)
            .map(|chunk| chunk.chars().count())
            .sum()
    }

    /// Length in UTF-16 code units for LSP position translations.
    pub fn line_length_utf16(&self, line_idx: usize) -> usize {
        if line_idx >= self.line_count() {
            return 0;
        }
        let row = line_idx as u32;
        let line_len = self.rope.line_len(row);
        self.rope
            .point_to_point_utf16(Point::new(row, line_len))
            .column as usize
    }

    /// Clamp a Position so it resides strictly within valid line and char boundaries.
    pub fn clamp_position(&self, pos: Position) -> Position {
        self.pos_to_clamped_offset(pos).1
    }

    /// Convert a Position to its valid byte offset and clamped Position in a single pass.
    fn pos_to_clamped_offset(&self, pos: Position) -> (usize, Position) {
        let max_row = self.rope.max_point().row;
        let row = (pos.line as u32).min(max_row);
        let start = self.rope.point_to_offset(Point::new(row, 0));
        let line_len = self.rope.line_len(row) as usize;

        if pos.character == 0 {
            return (start, Position::new(row as usize, 0));
        }

        let end = start + line_len;
        let mut chars_seen = 0;
        let mut match_offset = None;
        let mut chunk_start = start;

        for chunk in self.rope.chunks_in_range(start..end) {
            for (byte_idx, _) in chunk.char_indices() {
                if chars_seen == pos.character {
                    match_offset = Some(chunk_start + byte_idx);
                }
                chars_seen += 1;
            }
            chunk_start += chunk.len();
        }

        if let Some(offset) = match_offset {
            (offset, Position::new(row as usize, pos.character))
        } else {
            // pos.character was beyond line end: clamp to end of line
            (end, Position::new(row as usize, chars_seen))
        }
    }

    /// Convert a Position (line, char index) to byte offset in the rope.
    fn pos_to_offset(&self, pos: Position) -> usize {
        self.pos_to_clamped_offset(pos).0
    }

    /// Insert text at a given Position (line, character index).
    /// Returns the ending Position after insertion.
    pub fn insert(&mut self, pos: Position, text: &str) -> Position {
        if text.is_empty() {
            return self.clamp_position(pos);
        }
        let (offset, clamped) = self.pos_to_clamped_offset(pos);

        let (normalized, has_newline) = if text.contains('\r') {
            let n = text.replace("\r\n", "\n").replace('\r', "\n");
            let has_nl = n.contains('\n');
            (std::borrow::Cow::Owned(n), has_nl)
        } else {
            (std::borrow::Cow::Borrowed(text), text.contains('\n'))
        };

        // Perform in-place rope replacement without full-buffer string roundtrips
        self.rope.replace(offset..offset, &normalized);

        // Fast path for single-line insertion (no newline):
        if !has_newline {
            let inserted_chars = normalized.chars().count();
            return Position::new(clamped.line, clamped.character + inserted_chars);
        }

        // Multiline insertion: calculate ending Position
        let new_offset = offset + normalized.len();
        let end_point = self.rope.offset_to_point(new_offset);
        let end_line = end_point.row as usize;
        let line_start = self.rope.point_to_offset(Point::new(end_point.row, 0));
        let end_char = self
            .rope
            .chunks_in_range(line_start..new_offset)
            .map(|c| c.chars().count())
            .sum();
        Position::new(end_line, end_char)
    }

    /// Delete text in the range [start, end).
    /// Returns the deleted text string.
    pub fn delete_range(&mut self, start: Position, end: Position) -> String {
        let (start, end) = if start <= end {
            (start, end)
        } else {
            (end, start)
        };
        let start_offset = self.pos_to_offset(start);
        let end_offset = self.pos_to_offset(end);

        if start_offset == end_offset {
            return String::new();
        }

        let len = end_offset - start_offset;
        let mut deleted = String::with_capacity(len);
        for chunk in self.rope.chunks_in_range(start_offset..end_offset) {
            deleted.push_str(chunk);
        }
        self.rope.replace(start_offset..end_offset, "");
        deleted
    }

    /// Extract text in the range [start, end) without mutating the buffer.
    pub fn get_text_range(&self, start: Position, end: Position) -> String {
        let (start, end) = if start <= end {
            (start, end)
        } else {
            (end, start)
        };
        let start_offset = self.pos_to_offset(start);
        let end_offset = self.pos_to_offset(end);

        if start_offset == end_offset {
            return String::new();
        }

        let len = end_offset - start_offset;
        let mut text = String::with_capacity(len);
        for chunk in self.rope.chunks_in_range(start_offset..end_offset) {
            text.push_str(chunk);
        }
        text
    }

    /// Find matching bracket pair for position near a bracket character.
    pub fn find_matching_bracket(&self, pos: Position) -> Option<(Position, Position)> {
        let line_count = self.line_count();
        if line_count == 0 {
            return None;
        }
        let line_idx = pos.line.min(line_count - 1);
        let line_str = self.get_line(line_idx)?;
        let chars: Vec<char> = line_str.chars().collect();
        let char_idx = pos.character.min(chars.len());

        let (target_char, target_pos) = if char_idx > 0 && is_bracket(chars[char_idx - 1]) {
            (chars[char_idx - 1], Position::new(line_idx, char_idx - 1))
        } else if char_idx < chars.len() && is_bracket(chars[char_idx]) {
            (chars[char_idx], Position::new(line_idx, char_idx))
        } else {
            return None;
        };

        let matching_char = get_matching_bracket(target_char)?;
        let is_opening = is_opening_bracket(target_char);

        let target_offset = self.pos_to_offset(target_pos);

        if is_opening {
            let mut depth = 0i32;
            let mut curr_offset = target_offset;
            for ch in self.rope.chars_at(target_offset) {
                if ch == target_char {
                    depth += 1;
                } else if ch == matching_char {
                    depth -= 1;
                    if depth == 0 {
                        let match_pt = self.rope.offset_to_point(curr_offset);
                        let match_line_start = self.rope.point_to_offset(Point::new(match_pt.row, 0));
                        let match_char = self
                            .rope
                            .chunks_in_range(match_line_start..curr_offset)
                            .map(|c| c.chars().count())
                            .sum();
                        return Some((target_pos, Position::new(match_pt.row as usize, match_char)));
                    }
                }
                curr_offset += ch.len_utf8();
            }
        } else {
            let mut depth = 0i32;
            let mut curr_offset = target_offset + target_char.len_utf8();
            for ch in self.rope.reversed_chars_at(curr_offset) {
                curr_offset -= ch.len_utf8();
                if ch == target_char {
                    depth += 1;
                } else if ch == matching_char {
                    depth -= 1;
                    if depth == 0 {
                        let match_pt = self.rope.offset_to_point(curr_offset);
                        let match_line_start = self.rope.point_to_offset(Point::new(match_pt.row, 0));
                        let match_char = self
                            .rope
                            .chunks_in_range(match_line_start..curr_offset)
                            .map(|c| c.chars().count())
                            .sum();
                        return Some((Position::new(match_pt.row as usize, match_char), target_pos));
                    }
                }
            }
        }

        None
    }
}

fn is_bracket(ch: char) -> bool {
    matches!(ch, '(' | ')' | '{' | '}' | '[' | ']')
}

fn is_opening_bracket(ch: char) -> bool {
    matches!(ch, '(' | '{' | '[')
}

fn get_matching_bracket(ch: char) -> Option<char> {
    match ch {
        '(' => Some(')'),
        ')' => Some('('),
        '{' => Some('}'),
        '}' => Some('{'),
        '[' => Some(']'),
        ']' => Some('['),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_empty_document() {
        let mut buf = RopeBuffer::new();
        assert_eq!(buf.get_text(), "");
        assert_eq!(buf.line_count(), 1);
        assert_eq!(buf.get_line(0), Some("".to_string()));
        assert_eq!(buf.get_line(1), None);
        assert!(buf.is_empty());
        assert_eq!(buf.len(), 0);

        let end = buf.insert(Position::new(0, 0), "first line");
        assert_eq!(buf.get_text(), "first line");
        assert_eq!(end, Position::new(0, 10));
    }

    #[test]
    fn test_single_line_insertion_and_deletion() {
        let mut buf = RopeBuffer::from_str("Hello World");
        let end = buf.insert(Position::new(0, 5), ", Rust");
        assert_eq!(buf.get_text(), "Hello, Rust World");
        assert_eq!(end, Position::new(0, 11));

        let deleted = buf.delete_range(Position::new(0, 5), Position::new(0, 11));
        assert_eq!(deleted, ", Rust");
        assert_eq!(buf.get_text(), "Hello World");
    }

    #[test]
    fn test_multiline_insertion_and_deletion() {
        let mut buf = RopeBuffer::from_str("line 1\nline 2\nline 3");
        assert_eq!(buf.line_count(), 3);
        assert_eq!(buf.get_line(0), Some("line 1".to_string()));
        assert_eq!(buf.get_line(1), Some("line 2".to_string()));
        assert_eq!(buf.get_line(2), Some("line 3".to_string()));

        let end = buf.insert(Position::new(1, 6), "\nline 2.5");
        assert_eq!(buf.line_count(), 4);
        assert_eq!(buf.get_line(2), Some("line 2.5".to_string()));
        assert_eq!(end, Position::new(2, 8));

        let deleted = buf.delete_range(Position::new(1, 6), Position::new(2, 8));
        assert_eq!(deleted, "\nline 2.5");
        assert_eq!(buf.line_count(), 3);
        assert_eq!(buf.get_text(), "line 1\nline 2\nline 3");
    }

    #[test]
    fn test_unicode_multibyte_characters() {
        let sample = "🚀 Hello 世界";
        let mut buf = RopeBuffer::from_str(sample);

        // Rocket: 1 char, 4 bytes, 2 utf-16
        assert_eq!(buf.line_length_chars(0), 10);
        assert_eq!(buf.line_length_utf16(0), 11);

        // Insert text between 'Hello' and '世界' (at char index 8)
        let end = buf.insert(Position::new(0, 8), "Brave ");
        assert_eq!(buf.get_text(), "🚀 Hello Brave 世界");
        assert_eq!(end, Position::new(0, 14));

        // Delete 'Brave '
        let deleted = buf.delete_range(Position::new(0, 8), Position::new(0, 14));
        assert_eq!(deleted, "Brave ");
        assert_eq!(buf.get_text(), sample);
    }

    #[test]
    fn test_positions_at_line_boundaries_and_end_of_document() {
        let mut buf = RopeBuffer::from_str("abc\ndef");
        let end1 = buf.insert(Position::new(0, 3), "!");
        assert_eq!(buf.get_text(), "abc!\ndef");
        assert_eq!(end1, Position::new(0, 4));

        let end2 = buf.insert(Position::new(1, 3), "?");
        assert_eq!(buf.get_text(), "abc!\ndef?");
        assert_eq!(end2, Position::new(1, 4));

        // Out of bounds clamp
        let clamped = buf.clamp_position(Position::new(99, 99));
        assert_eq!(clamped, Position::new(1, 4));
    }

    #[test]
    fn test_bracket_matching() {
        let buf = RopeBuffer::from_str("fn foo(bar: [u32; 4]) { let x = (1 + 2); }");

        // Cursor at index 7 (right after '(' at 6)
        let pair = buf.find_matching_bracket(Position::new(0, 7));
        assert!(pair.is_some());
        let (open, close) = pair.unwrap();
        assert_eq!(open, Position::new(0, 6));
        assert_eq!(close, Position::new(0, 20));

        // Square brackets around [u32; 4] at 12..19
        let pair_sq = buf.find_matching_bracket(Position::new(0, 13));
        assert!(pair_sq.is_some());
        let (open_sq, close_sq) = pair_sq.unwrap();
        assert_eq!(open_sq, Position::new(0, 12));
        assert_eq!(close_sq, Position::new(0, 19));
    }

    #[test]
    fn test_repeated_edits_and_range_extraction() {
        let mut buf = RopeBuffer::new();
        for i in 0..10 {
            buf.insert(Position::new(i, 0), &format!("item {}\n", i));
        }

        assert_eq!(buf.line_count(), 11);
        assert_eq!(buf.get_line(5), Some("item 5".to_string()));

        let extracted = buf.get_text_range(Position::new(2, 0), Position::new(4, 0));
        assert_eq!(extracted, "item 2\nitem 3\n");
    }
}
