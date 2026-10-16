use crate::principal::emails_from_addresses;

fn addresses(values: &[&str]) -> Vec<String> {
    values.iter().map(|value| value.to_string()).collect()
}

#[test]
fn user_emails_keep_only_the_mailto_entries() {
    let addresses = addresses(&[
        "mailto:alice@example.com",
        "/principals/alice/",
        "urn:uuid:3f2a",
        "MAILTO:alias@example.com",
    ]);

    assert_eq!(emails_from_addresses(&addresses), vec!["alice@example.com", "alias@example.com"]);
}

#[test]
fn user_emails_are_deduplicated_ignoring_case() {
    let addresses = addresses(&["mailto:alice@example.com", "mailto:Alice@Example.com"]);

    assert_eq!(emails_from_addresses(&addresses), vec!["alice@example.com"]);
}

#[test]
fn user_emails_are_empty_without_addresses() {
    assert!(emails_from_addresses(&[]).is_empty());
}

#[test]
fn user_emails_ignore_a_non_ascii_href_without_scheme() {
    let addresses = addresses(&["/a用户/", "mailto:alice@example.com"]);

    assert_eq!(emails_from_addresses(&addresses), vec!["alice@example.com"]);
}
