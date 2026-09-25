use crate::alarms::splice_alarms_into_vevent;
use crate::events::{build_event_ics, parse_ics, patch_event_ics, upsert_override_vevent};
use crate::models::{
    AlarmEdit, AlarmEntry, AlarmRepetitionSpec, AlarmsChange, ColorChange, DateListChange, EventEdit,
    OverrideRemoval, RecurrenceChange, RecurrenceIdSpec, VeventSeed,
};

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

const CUSTOM_ALARM: &str = "BEGIN:VALARM\r\nACTION:X-CUSTOM\r\nTRIGGER:-PT30M\r\nREPEAT:2\r\nDURATION:PT5M\r\nX-VENDOR:v\r\nEND:VALARM\r\n";

fn series_ics() -> String {
    format!(
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:series\r\nDTSTART:20260615T100000Z\r\nDTEND:20260615T110000Z\r\n\
         RRULE:FREQ=DAILY;COUNT=5\r\nSUMMARY:Series\r\nBEGIN:VALARM\r\nACTION:DISPLAY\r\nTRIGGER:-PT15M\r\nEND:VALARM\r\n\
         {CUSTOM_ALARM}END:VEVENT\r\nEND:VCALENDAR\r\n"
    )
}

fn display_alarm(trigger: &str) -> AlarmEdit {
    AlarmEdit {
        uid: None,
        action: "DISPLAY".into(),
        trigger_duration: Some(trigger.into()),
        trigger_absolute: None,
        trigger_related_to: "START".into(),
        description: Some("Reminder".into()),
        summary: None,
        attendees: vec![],
        attach: vec![],
        repetition: None,
    }
}

fn edit(alarms_change: AlarmsChange) -> EventEdit {
    EventEdit {
        summary: Some("Edited".into()),
        dtstart: "20260615T100000Z".into(),
        dtstart_tzid: None,
        dtend: Some("20260615T110000Z".into()),
        dtend_tzid: None,
        all_day: false,
        location: None,
        description: None,
        transp: None,
        timezones: vec![],
        color_change: ColorChange::Unchanged,
        recurrence_change: RecurrenceChange::Unchanged,
        ex_date_change: DateListChange::Unchanged,
        r_date_change: DateListChange::Unchanged,
        override_removal: OverrideRemoval::Unchanged,
        alarms_change,
        stamp: "20260601T000000Z".into(),
    }
}

fn replacing_display_with(trigger: &str) -> EventEdit {
    edit(AlarmsChange::Set {
        alarms: vec![display_alarm(trigger)],
    })
}

fn assert_single_custom_alarm_kept(ics: &str) {
    assert_eq!(ics.matches("ACTION:X-CUSTOM").count(), 1, "{ics}");
    for line in ["TRIGGER:-PT30M", "REPEAT:2", "DURATION:PT5M", "X-VENDOR:v"] {
        assert!(ics.contains(line), "{line} lost in {ics}");
    }
}

#[test]
fn patch_replacing_alarms_keeps_an_uneditable_one_verbatim() {
    let patched = patch_event_ics(&series_ics(), replacing_display_with("-PT5M")).unwrap();

    assert!(patched.ics_data.contains("TRIGGER:-PT5M"));
    assert!(
        !patched.ics_data.contains("TRIGGER:-PT15M"),
        "the editable alarm is replaced"
    );
    assert_single_custom_alarm_kept(&patched.ics_data);
}

#[test]
fn patch_clearing_alarms_keeps_an_uneditable_one() {
    let patched =
        patch_event_ics(&series_ics(), edit(AlarmsChange::Set { alarms: vec![] })).unwrap();

    assert!(!patched.ics_data.contains("ACTION:DISPLAY"));
    assert_single_custom_alarm_kept(&patched.ics_data);
}

#[test]
fn patch_replacing_alarms_drops_one_without_action() {
    let ics = series_ics().replace("ACTION:DISPLAY\r\n", "");

    let patched = patch_event_ics(&ics, replacing_display_with("-PT5M")).unwrap();

    assert!(
        !patched.ics_data.contains("TRIGGER:-PT15M"),
        "no ACTION reads as DISPLAY, so it is editable"
    );
    assert_single_custom_alarm_kept(&patched.ics_data);
}

#[test]
fn patch_replacing_alarms_reads_a_folded_or_quoted_action() {
    let action_lines = [
        "ACTION;X-VENDOR-PARAM=some-long-value\r\n :X-CUSTOM\r\n",
        "ACTION:X-CUS\r\n TOM\r\n",
        "ACTION;X-VENDOR-PARAM=\"a:b\":X-CUSTOM\r\n",
    ];
    for action_line in action_lines {
        let ics = series_ics().replace("ACTION:X-CUSTOM\r\n", action_line);

        let patched = patch_event_ics(&ics, replacing_display_with("-PT5M")).unwrap();

        assert!(patched.ics_data.contains("TRIGGER:-PT30M"), "{action_line:?} lost in {}", patched.ics_data);
    }
}

#[test]
fn detaching_an_occurrence_carries_the_masters_uneditable_alarm() {
    let recurrence_id = RecurrenceIdSpec {
        tzid: None,
        is_date_only: false,
        value: "20260617T100000Z".into(),
    };

    let patched = upsert_override_vevent(
        &series_ics(),
        recurrence_id,
        replacing_display_with("-PT5M"),
        None,
    )
    .unwrap();

    let override_ics = &patched.ics_data[patched.ics_data.find("RECURRENCE-ID").unwrap()..];
    assert!(override_ics.contains("TRIGGER:-PT5M"));
    assert!(!override_ics.contains("TRIGGER:-PT15M"));
    assert_single_custom_alarm_kept(override_ics);
}

#[test]
fn building_from_a_seed_carries_its_uneditable_alarm() {
    let seed = VeventSeed {
        ics: series_ics(),
        recurrence_id: None,
    };

    let built = build_event_ics(replacing_display_with("-PT5M"), Some(seed)).unwrap();

    assert!(built.ics_data.contains("TRIGGER:-PT5M"));
    assert!(!built.ics_data.contains("TRIGGER:-PT15M"));
    assert_single_custom_alarm_kept(&built.ics_data);
}
