use crate::principal::parse_user_emails;

fn multistatus(prop: &str) -> String {
    format!(
        r#"<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav">
  <d:response>
    <d:href>/principals/alice/</d:href>
    <d:propstat>
      <d:prop>{prop}</d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
</d:multistatus>"#
    )
}

#[test]
fn user_emails_keep_only_the_mailto_entries() {
    let xml = multistatus(
        "<cal:calendar-user-address-set>\
           <d:href>mailto:alice@example.com</d:href>\
           <d:href>/principals/alice/</d:href>\
           <d:href>urn:uuid:3f2a</d:href>\
           <d:href> MAILTO:alias@example.com </d:href>\
         </cal:calendar-user-address-set>",
    );

    assert_eq!(parse_user_emails(xml.as_bytes()), vec!["alice@example.com", "alias@example.com"]);
}

#[test]
fn user_emails_are_deduplicated_ignoring_case() {
    let xml = multistatus(
        "<cal:calendar-user-address-set>\
           <d:href>mailto:alice@example.com</d:href>\
           <d:href>mailto:Alice@Example.com</d:href>\
         </cal:calendar-user-address-set>",
    );

    assert_eq!(parse_user_emails(xml.as_bytes()), vec!["alice@example.com"]);
}

#[test]
fn user_emails_ignore_the_principal_href() {
    let xml = multistatus("");

    assert!(parse_user_emails(xml.as_bytes()).is_empty());
}

#[test]
fn user_emails_are_empty_for_a_malformed_body() {
    assert!(parse_user_emails(b"<not xml").is_empty());
}

#[test]
fn user_emails_ignore_a_non_ascii_href_without_scheme() {
    let xml = multistatus(
        "<cal:calendar-user-address-set>\
           <d:href>/a用户/</d:href>\
           <d:href>mailto:alice@example.com</d:href>\
         </cal:calendar-user-address-set>",
    );

    assert_eq!(parse_user_emails(xml.as_bytes()), vec!["alice@example.com"]);
}
