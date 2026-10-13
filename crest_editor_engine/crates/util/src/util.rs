/// Minimal util stub providing only what rope/sum_tree need.

/// In debug builds, panic with the given message. In release builds, log a warning.
#[macro_export]
macro_rules! debug_panic {
    ($($arg:tt)*) => {
        if cfg!(debug_assertions) {
            panic!($($arg)*);
        } else {
            eprintln!("[util::debug_panic] {}", format_args!($($arg)*));
        }
    };
}

/// Check if a byte is a UTF-8 character boundary (i.e., not a continuation byte).
/// Continuation bytes have the form 10xxxxxx.
#[inline]
pub fn is_utf8_char_boundary(byte: u8) -> bool {
    // A byte is a char boundary if it's not a UTF-8 continuation byte.
    // Continuation bytes are in the range 0x80..=0xBF (10xxxxxx).
    (byte as i8) >= -0x40
}

/// Iterator over random Unicode characters, used in tests.
#[cfg(feature = "test-support")]
pub struct RandomCharIter<R: rand::Rng> {
    rng: R,
}

#[cfg(feature = "test-support")]
impl<R: rand::Rng> RandomCharIter<R> {
    pub fn new(rng: R) -> Self {
        Self { rng }
    }
}

#[cfg(feature = "test-support")]
impl<R: rand::Rng> Iterator for RandomCharIter<R> {
    type Item = char;

    fn next(&mut self) -> Option<char> {
        use std::char;
        loop {
            // Generate a random char in the full Unicode range but skip surrogates
            let n = self.rng.gen_range(0..=0x10FFFF_u32);
            if let Some(c) = char::from_u32(n) {
                if c != '\r' {
                    return Some(c);
                }
            }
        }
    }
}
