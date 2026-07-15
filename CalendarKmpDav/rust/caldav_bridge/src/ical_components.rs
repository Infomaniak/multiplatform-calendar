//! Shared iCalendar component boundary helpers.
//!
//! Text-level BEGIN/END matching stays here because `icalendar` does not expose
//! nested component mutation.

pub(crate) const VEVENT: &str = "VEVENT";

pub(crate) const VALARM: &str = "VALARM";

pub(crate) const SUMMARY: &str = "SUMMARY";

pub(crate) const DESCRIPTION: &str = "DESCRIPTION";

pub(crate) const ATTENDEE: &str = "ATTENDEE";

pub(crate) const VALUE_PARAM: &str = "VALUE";

pub(crate) fn push_begin(out: &mut String, name: &str) {
    out.push_str("BEGIN:");
    out.push_str(name);
    out.push_str("\r\n");
}

pub(crate) fn push_end(out: &mut String, name: &str) {
    out.push_str("END:");
    out.push_str(name);
    out.push_str("\r\n");
}

pub(crate) fn is_begin_marker(line: &str, name: &str) -> bool {
    is_marker(line, "BEGIN:", name)
}

pub(crate) fn is_end_marker(line: &str, name: &str) -> bool {
    is_marker(line, "END:", name)
}

/// Byte-wise comparison so a content line whose length happens to equal the marker length but which
/// contains multibyte UTF-8 can never panic on a char-boundary slice.
fn is_marker(line: &str, prefix: &str, name: &str) -> bool {
    let bytes = line.as_bytes();
    bytes.len() == prefix.len() + name.len()
        && bytes[..prefix.len()].eq_ignore_ascii_case(prefix.as_bytes())
        && bytes[prefix.len()..].eq_ignore_ascii_case(name.as_bytes())
}
