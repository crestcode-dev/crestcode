/// Pass-through proc-macro for `#[instrument]` when tracing is disabled.
/// When ztracing is not enabled, this macro simply strips the attribute
/// and returns the function unchanged.
use proc_macro::TokenStream;

#[proc_macro_attribute]
pub fn instrument(_attr: TokenStream, item: TokenStream) -> TokenStream {
    // Pass-through: return the function unchanged
    item
}
