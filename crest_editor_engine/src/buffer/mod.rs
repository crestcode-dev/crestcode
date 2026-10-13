pub mod document;
pub mod piece_table;
pub mod position;

#[cfg(feature = "zed-rope")]
pub mod rope_buffer;

pub use document::{Document, TextChangeEvent};
pub use piece_table::TextBuffer;
pub use position::{Position, Selection};

#[cfg(feature = "zed-rope")]
pub use rope_buffer::RopeBuffer;
