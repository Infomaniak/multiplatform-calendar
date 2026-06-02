use crate::{
    DavAddressBook, DavCalendar, DavCalendarObject, DavContactObject, DavError,
};
//use roxmltree::{Document, Node};
//
//pub fn parse_first_href_under(xml: &str, property_name: &str) -> Option<String> {
//    let document = Document::parse(xml).ok()?;
//
//    for node in document.descendants().filter(|node| local_name(*node) == property_name) {
//        if let Some(href) = node.descendants().find(|child| local_name(*child) == "href") {
//            return href.text().map(ToOwned::to_owned);
//        }
//    }
//
//    None
//}
//
//pub fn parse_calendars(xml: &str) -> Result<Vec<DavCalendar>, DavError> {
//    let document = parse(xml)?;
//    let calendars = document
//        .descendants()
//        .filter(|node| local_name(*node) == "response")
//        .filter_map(|response| {
//            let href = child_text(response, "href")?;
//            let resource_type = descendant_text(response, "resourcetype").unwrap_or_default();
//            let has_calendar_node = response
//                .descendants()
//                .any(|node| local_name(node) == "calendar");
//
//            if !has_calendar_node && !resource_type.contains("calendar") {
//                return None;
//            }
//
//            Some(DavCalendar {
//                href,
//                display_name: descendant_text(response, "displayname"),
//                color: descendant_text(response, "calendar-color"),
//                ctag: descendant_text(response, "getctag"),
//                supports_sync: supports_sync(response),
//            })
//        })
//        .collect();
//
//    Ok(calendars)
//}
//
//pub fn parse_calendar_objects(xml: &str) -> Result<Vec<DavCalendarObject>, DavError> {
//    let document = parse(xml)?;
//    let objects = document
//        .descendants()
//        .filter(|node| local_name(*node) == "response")
//        .filter_map(|response| {
//            let href = child_text(response, "href")?;
//            let ics = descendant_text(response, "calendar-data")?;
//            Some(DavCalendarObject {
//                href,
//                etag: descendant_text(response, "getetag"),
//                ics,
//            })
//        })
//        .collect();
//
//    Ok(objects)
//}
//
//pub fn parse_address_books(xml: &str) -> Result<Vec<DavAddressBook>, DavError> {
//    let document = parse(xml)?;
//    let address_books = document
//        .descendants()
//        .filter(|node| local_name(*node) == "response")
//        .filter_map(|response| {
//            let href = child_text(response, "href")?;
//            let resource_type = descendant_text(response, "resourcetype").unwrap_or_default();
//            let has_address_book_node = response
//                .descendants()
//                .any(|node| local_name(node) == "addressbook");
//
//            if !has_address_book_node && !resource_type.contains("addressbook") {
//                return None;
//            }
//
//            Some(DavAddressBook {
//                href,
//                display_name: descendant_text(response, "displayname"),
//                ctag: descendant_text(response, "getctag"),
//                supports_sync: supports_sync(response),
//            })
//        })
//        .collect();
//
//    Ok(address_books)
//}
//
//pub fn parse_contact_objects(xml: &str) -> Result<Vec<DavContactObject>, DavError> {
//    let document = parse(xml)?;
//    let objects = document
//        .descendants()
//        .filter(|node| local_name(*node) == "response")
//        .filter_map(|response| {
//            let href = child_text(response, "href")?;
//            let vcard = descendant_text(response, "address-data")?;
//            Some(DavContactObject {
//                href,
//                etag: descendant_text(response, "getetag"),
//                vcard,
//            })
//        })
//        .collect();
//
//    Ok(objects)
//}
//
//fn parse(xml: &str) -> Result<Document<'_>, DavError> {
//    Document::parse(xml).map_err(|error| DavError::Xml {
//        message: error.to_string(),
//    })
//}
//
//fn child_text(node: Node<'_, '_>, name: &str) -> Option<String> {
//    node.children()
//        .find(|child| local_name(*child) == name)
//        .and_then(|child| child.text())
//        .map(ToOwned::to_owned)
//}
//
//fn descendant_text(node: Node<'_, '_>, name: &str) -> Option<String> {
//    node.descendants()
//        .find(|child| local_name(*child) == name)
//        .and_then(|child| child.text())
//        .map(ToOwned::to_owned)
//}
//
//fn supports_sync(node: Node<'_, '_>) -> bool {
//    node.descendants()
//        .any(|child| local_name(child) == "sync-collection")
//}
//
//fn local_name(node: Node<'_, '_>) -> &str {
//    node.tag_name().name()
//}
