/// Minimal zlog stub - re-exports log macros and provides init_test for testing.
pub use log::{debug, error, info, trace, warn};

/// No-op init for tests. With "test-support" feature, uses env_logger.
pub fn init_test() {
    #[cfg(feature = "test-support")]
    let _ = env_logger::builder().is_test(true).try_init();
}
