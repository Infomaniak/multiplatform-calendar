use crate::events::parse_ics;
use crate::models::EventEntry;

fn parse_event(ics: &str) -> EventEntry {
    parse_ics(String::new(), String::new(), ics.to_string())
        .expect("event should parse")
}

fn calendar_with(vevents: &str) -> String {
    format!(
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//tests//EN\r\n{vevents}END:VCALENDAR\r\n"
    )
}

#[test]
fn attachments_empty_when_absent() {
    let ics = calendar_with(
        "BEGIN:VEVENT\r\nUID:test\r\nDTSTART:20260916T120000Z\r\nEND:VEVENT\r\n",
    );

    let event = parse_event(&ics);
    assert!(event.content.attachments.is_empty());
}

#[test]
fn one_attachment_is_parsed() {
    let ics = calendar_with(
        "BEGIN:VEVENT\r\nUID:test\r\nDTSTART:20260916T120000Z\r\nX-INFOMANIAK-ATTACH;VALUE=URI;FMTTYPE=image/png;FILENAME=image.png:https\\://example.com/image\r\nEND:VEVENT\r\n",
    );

    let event = parse_event(&ics);
    assert_eq!(1, event.content.attachments.len());
    let attachment = &event.content.attachments[0];
    assert_eq!("https://example.com/image", attachment.url);
    assert_eq!("image.png", attachment.filename);
    assert_eq!(Some("image/png".to_string()), attachment.mime_type);
}

#[test]
fn multiple_attachments_preserve_order() {
    let ics = calendar_with(
        "BEGIN:VEVENT\r\nUID:test\r\nDTSTART:20260916T120000Z\r\nX-INFOMANIAK-ATTACH;VALUE=URI;FMTTYPE=image/png;FILENAME=a.png:https\\://example.com/a\r\nX-INFOMANIAK-ATTACH;VALUE=URI;FMTTYPE=application/pdf;FILENAME=b.pdf:https\\://example.com/b\r\nEND:VEVENT\r\n",
    );

    let event = parse_event(&ics);
    assert_eq!(2, event.content.attachments.len());
    assert_eq!("https://example.com/a", event.content.attachments[0].url);
    assert_eq!("a.png", event.content.attachments[0].filename);
    assert_eq!("https://example.com/b", event.content.attachments[1].url);
    assert_eq!("b.pdf", event.content.attachments[1].filename);
}

#[test]
fn missing_filename_falls_back_to_url() {
    let ics = calendar_with(
        "BEGIN:VEVENT\r\nUID:test\r\nDTSTART:20260916T120000Z\r\nX-INFOMANIAK-ATTACH;VALUE=URI;FMTTYPE=image/png:https\\://example.com/a\r\nEND:VEVENT\r\n",
    );

    let event = parse_event(&ics);
    assert_eq!(1, event.content.attachments.len());
    let attachment = &event.content.attachments[0];
    assert_eq!("https://example.com/a", attachment.url);
    assert_eq!("https://example.com/a", attachment.filename);
}

#[test]
fn missing_fmttype_is_allowed() {
    let ics = calendar_with(
        "BEGIN:VEVENT\r\nUID:test\r\nDTSTART:20260916T120000Z\r\nX-INFOMANIAK-ATTACH;VALUE=URI;FILENAME=a.png:https\\://example.com/a\r\nEND:VEVENT\r\n",
    );

    let event = parse_event(&ics);
    assert_eq!(1, event.content.attachments.len());
    assert_eq!(None, event.content.attachments[0].mime_type);
}

#[test]
fn master_and_override_keep_their_own_attachments() {
    let ics = calendar_with(
        "BEGIN:VEVENT\r\nUID:test\r\nDTSTART;TZID=Europe/Zurich:20260916T120000\r\nX-INFOMANIAK-ATTACH;VALUE=URI;FILENAME=master.txt:https\\://example.com/master\r\nRRULE:FREQ=DAILY;COUNT=3\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:test\r\nRECURRENCE-ID;TZID=Europe/Zurich:20260917T120000\r\nDTSTART;TZID=Europe/Zurich:20260917T130000\r\nX-INFOMANIAK-ATTACH;VALUE=URI;FILENAME=override.txt:https\\://example.com/override\r\nEND:VEVENT\r\n",
    );

    let event = parse_event(&ics);
    assert_eq!(
        vec!["master.txt"],
        event
            .content
            .attachments
            .iter()
            .map(|a| a.filename.as_str())
            .collect::<Vec<_>>()
    );
    assert_eq!(1, event.overrides.len());
    assert_eq!(
        vec!["override.txt"],
        event.overrides[0]
            .content
            .attachments
            .iter()
            .map(|a| a.filename.as_str())
            .collect::<Vec<_>>()
    );
}

#[test]
fn two_overrides_match_recurrence_id_and_tzid() {
    let ics = calendar_with(
        "BEGIN:VEVENT\r\nUID:test\r\nDTSTART;TZID=Europe/Zurich:20260916T120000\r\nRRULE:FREQ=DAILY;COUNT=4\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:test\r\nRECURRENCE-ID;TZID=Europe/Zurich:20260917T120000\r\nDTSTART;TZID=Europe/Zurich:20260917T130000\r\nX-INFOMANIAK-ATTACH;VALUE=URI;FILENAME=one.txt:https\\://example.com/one\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:test\r\nRECURRENCE-ID;TZID=UTC:20260917T120000\r\nDTSTART;TZID=UTC:20260917T140000\r\nX-INFOMANIAK-ATTACH;VALUE=URI;FILENAME=two.txt:https\\://example.com/two\r\nEND:VEVENT\r\n",
    );

    let event = parse_event(&ics);
    assert_eq!(2, event.overrides.len());
    let names: Vec<&str> = event
        .overrides
        .iter()
        .map(|override_event| override_event.content.attachments[0].filename.as_str())
        .collect();
    assert_eq!(vec!["one.txt", "two.txt"], names);
}

#[test]
fn missing_filename_in_override_falls_back_to_url() {
    let ics = calendar_with(
        "BEGIN:VEVENT\r\nUID:test\r\nDTSTART;TZID=Europe/Zurich:20260916T120000\r\nRRULE:FREQ=DAILY;COUNT=2\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:test\r\nRECURRENCE-ID;TZID=Europe/Zurich:20260917T120000\r\nDTSTART;TZID=Europe/Zurich:20260917T130000\r\nX-INFOMANIAK-ATTACH;VALUE=URI:https\\://example.com/override\r\nEND:VEVENT\r\n",
    );

    let event = parse_event(&ics);
    let attachment = &event.overrides[0].content.attachments[0];
    assert_eq!("https://example.com/override", attachment.url);
    assert_eq!("https://example.com/override", attachment.filename);
}

