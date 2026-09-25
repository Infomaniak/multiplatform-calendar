use crate::alarms::splice_alarms_into_vevent;
use crate::events::parse_ics;
use crate::models::{AlarmEdit, AlarmEntry, AlarmRepetitionSpec};

fn alarms_of(valarms: &str) -> Vec<AlarmEntry> {
    let ics = calendar_with(&format!(
        "BEGIN:VEVENT\r\nUID:test\r\nDTSTART:20260916T120000Z\r\n{valarms}END:VEVENT\r\n"
    ));
    parse_ics(String::new(), String::new(), ics).expect("event should parse").content.alarms
}

fn calendar_with(vevents: &str) -> String {
    format!("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//tests//EN\r\n{vevents}END:VCALENDAR\r\n")
}

fn display_edit(repetition: Option<AlarmRepetitionSpec>) -> AlarmEdit {
    AlarmEdit {
        uid: None,
        action: "DISPLAY".to_string(),
        trigger_duration: Some("-PT15M".to_string()),
        trigger_absolute: None,
        trigger_related_to: "START".to_string(),
        description: None,
        summary: None,
        attendees: vec![],
        attach: vec![],
        repetition,
    }
}

#[test]
fn repetition_is_parsed() {
    let alarms = alarms_of(
        "BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT15M\r\nREPEAT:2\r\nDURATION:PT5M\r\nEND:VALARM\r\n",
    );

    let repetition = alarms[0].repetition.as_ref().expect("repetition should be parsed");
    assert_eq!(2, repetition.count);
    assert_eq!("PT5M", repetition.interval);
}

#[test]
fn repetition_is_dropped_when_half_of_it_is_missing() {
    let alarms = alarms_of(
        "BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT15M\r\nREPEAT:2\r\nEND:VALARM\r\n\
         BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT10M\r\nDURATION:PT5M\r\nEND:VALARM\r\n\
         BEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT5M\r\nREPEAT:x\r\nDURATION:PT5M\r\nEND:VALARM\r\n",
    );

    assert_eq!(3, alarms.len());
    assert!(alarms.iter().all(|a| a.repetition.is_none()));
}

#[test]
fn repetition_is_written_back_with_its_alarm() {
    let ics = calendar_with("BEGIN:VEVENT\r\nUID:test\r\nDTSTART:20260916T120000Z\r\nEND:VEVENT\r\n");
    let repeated = display_edit(Some(AlarmRepetitionSpec { count: 3, interval: "PT10M".to_string() }));

    let spliced = splice_alarms_into_vevent(&ics, 0, &[display_edit(None), repeated]);

    let alarms = parse_ics(String::new(), String::new(), spliced).unwrap().content.alarms;
    assert!(alarms[0].repetition.is_none());
    let repetition = alarms[1].repetition.as_ref().expect("repetition should be written back");
    assert_eq!(3, repetition.count);
    assert_eq!("PT10M", repetition.interval);
}
