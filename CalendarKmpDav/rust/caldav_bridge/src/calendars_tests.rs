use crate::calendars::infomaniak_properties;

#[test]
fn infomaniak_properties_reads_values_from_success_propstats_only() {
	let xml = r#"<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:" xmlns:I="http://infomaniak.com/ns">
  <D:response>
	<D:href>/dav/cal/a/</D:href>
	<D:propstat>
	  <D:prop>
		<I:account-id>42</I:account-id>
		<I:calendar-type>team</I:calendar-type>
		<I:default-calendar>1</I:default-calendar>
	  </D:prop>
	  <D:status>HTTP/1.1 200 OK</D:status>
	</D:propstat>
	<D:propstat>
	  <D:prop>
		<I:calendar-type>ignored</I:calendar-type>
	  </D:prop>
	  <D:status>HTTP/1.1 404 Not Found</D:status>
	</D:propstat>
  </D:response>
</D:multistatus>"#;

	let parsed = infomaniak_properties(xml).expect("must parse");
	let props = parsed.get("/dav/cal/a").expect("entry for normalized href");

	assert_eq!(props.account_id, Some(42));
	assert_eq!(props.calendar_type, "team");
	assert!(props.default_calendar);
}

#[test]
fn infomaniak_properties_keeps_defaults_when_values_are_missing_or_invalid() {
	let xml = r#"<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:" xmlns:I="http://infomaniak.com/ns">
  <D:response>
	<D:href>/dav/cal/b/</D:href>
	<D:propstat>
	  <D:prop>
		<I:account-id>invalid</I:account-id>
	  </D:prop>
	  <D:status>HTTP/1.1 200 OK</D:status>
	</D:propstat>
  </D:response>
</D:multistatus>"#;

	let parsed = infomaniak_properties(xml).expect("must parse");
	let props = parsed.get("/dav/cal/b").expect("entry for normalized href");

	assert_eq!(props.account_id, None);
	assert_eq!(props.calendar_type, "");
	assert!(!props.default_calendar);
}

