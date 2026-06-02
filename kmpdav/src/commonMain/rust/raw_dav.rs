use crate::{
    xml, DavAddressBook, DavCalendar, DavCalendarObject, DavContactObject, DavCredentials, DavError,
};
//type HttpsClient = Client<hyper_rustls::HttpsConnector<HttpConnector>, Full<Bytes>>;
//
//#[derive(Clone)]
//pub struct RawDavClient {
//    base_url: Url,
//    username: String,
//    password: String,
//    http: Arc<HttpsClient>,
//}
//
//impl RawDavClient {
//    pub fn new(credentials: DavCredentials) -> Result<Self, DavError> {
//        let base_url = Url::parse(&credentials.base_url).map_err(|error| DavError::InvalidUrl {
//            message: error.to_string(),
//        })?;
//
//        let connector = HttpsConnectorBuilder::new()
//            .with_native_roots()
//            .map_err(|error| DavError::Unexpected {
//                message: error.to_string(),
//            })?
//            .https_or_http()
//            .enable_http1()
//            .build();
//
//        let http = Client::builder(TokioExecutor::new()).build(connector);
//
//        Ok(Self {
//            base_url,
//            username: credentials.username,
//            password: credentials.password,
//            http: Arc::new(http),
//        })
//    }
//
//    pub async fn find_calendars(&self) -> Result<Vec<DavCalendar>, DavError> {
//        let principal = self.find_current_user_principal().await?;
//        let home_set = self.find_home_set(&principal, "calendar-home-set").await?;
//        let body = r#"<?xml version="1.0" encoding="utf-8" ?>
//<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/">
//  <d:prop>
//    <d:displayname />
//    <d:resourcetype />
//    <d:getetag />
//    <cs:getctag />
//    <c:calendar-color />
//    <d:supported-report-set />
//  </d:prop>
//</d:propfind>"#;
//
//        let response = self
//            .send_text("PROPFIND", &home_set, Some("1"), "application/xml; charset=utf-8", body)
//            .await?;
//
//        xml::parse_calendars(&response)
//    }
//
//    pub async fn get_calendar_objects(
//        &self,
//        calendar_href: String,
//    ) -> Result<Vec<DavCalendarObject>, DavError> {
//        let body = r#"<?xml version="1.0" encoding="utf-8" ?>
//<c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
//  <d:prop>
//    <d:getetag />
//    <c:calendar-data />
//  </d:prop>
//</c:calendar-query>"#;
//
//        let response = self
//            .send_text("REPORT", &calendar_href, Some("1"), "application/xml; charset=utf-8", body)
//            .await?;
//
//        xml::parse_calendar_objects(&response)
//    }
//
//    pub async fn put_calendar_object(
//        &self,
//        calendar_href: String,
//        object_file_name: String,
//        ics: String,
//        etag: Option<String>,
//    ) -> Result<String, DavError> {
//        let href = self.child_href(&calendar_href, &object_file_name);
//        self.put_resource(&href, "text/calendar; charset=utf-8", ics, etag)
//            .await
//    }
//
//    pub async fn find_address_books(&self) -> Result<Vec<DavAddressBook>, DavError> {
//        let principal = self.find_current_user_principal().await?;
//        let home_set = self.find_home_set(&principal, "addressbook-home-set").await?;
//        let body = r#"<?xml version="1.0" encoding="utf-8" ?>
//<d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav" xmlns:cs="http://calendarserver.org/ns/">
//  <d:prop>
//    <d:displayname />
//    <d:resourcetype />
//    <d:getetag />
//    <cs:getctag />
//    <d:supported-report-set />
//  </d:prop>
//</d:propfind>"#;
//
//        let response = self
//            .send_text("PROPFIND", &home_set, Some("1"), "application/xml; charset=utf-8", body)
//            .await?;
//
//        xml::parse_address_books(&response)
//    }
//
//    pub async fn get_contact_objects(
//        &self,
//        address_book_href: String,
//    ) -> Result<Vec<DavContactObject>, DavError> {
//        let body = r#"<?xml version="1.0" encoding="utf-8" ?>
//<card:addressbook-query xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
//  <d:prop>
//    <d:getetag />
//    <card:address-data />
//  </d:prop>
//</card:addressbook-query>"#;
//
//        let response = self
//            .send_text("REPORT", &address_book_href, Some("1"), "application/xml; charset=utf-8", body)
//            .await?;
//
//        xml::parse_contact_objects(&response)
//    }
//
//    pub async fn put_contact_object(
//        &self,
//        address_book_href: String,
//        object_file_name: String,
//        vcard: String,
//        etag: Option<String>,
//    ) -> Result<String, DavError> {
//        let href = self.child_href(&address_book_href, &object_file_name);
//        self.put_resource(&href, "text/vcard; charset=utf-8", vcard, etag)
//            .await
//    }
//
//    pub async fn delete_resource(&self, href: String, etag: Option<String>) -> Result<(), DavError> {
//        let uri = self.uri_for_href(&href)?;
//        let mut builder = Request::builder().method(Method::DELETE).uri(uri);
//        self.apply_default_headers(builder.headers_mut().ok_or_else(|| DavError::Unexpected {
//            message: "Unable to mutate request headers".to_string(),
//        })?)?;
//
//        if let Some(etag) = etag {
//            builder = builder.header("If-Match", etag);
//        }
//
//        let request = builder.body(Full::new(Bytes::new()))?;
//        let response = self.http.request(request).await.map_err(|error| DavError::Network {
//            message: error.to_string(),
//        })?;
//
//        if response.status().is_success() || response.status() == StatusCode::NOT_FOUND {
//            Ok(())
//        } else {
//            Err(DavError::Http {
//                status_code: response.status().as_u16(),
//                message: "DELETE failed".to_string(),
//            })
//        }
//    }
//
//    async fn put_resource(
//        &self,
//        href: &str,
//        content_type: &str,
//        body: String,
//        etag: Option<String>,
//    ) -> Result<String, DavError> {
//        let uri = self.uri_for_href(href)?;
//        let bytes = Bytes::from(body);
//        let mut builder = Request::builder()
//            .method(Method::PUT)
//            .uri(uri)
//            .header("Content-Type", content_type);
//
//        self.apply_default_headers(builder.headers_mut().ok_or_else(|| DavError::Unexpected {
//            message: "Unable to mutate request headers".to_string(),
//        })?)?;
//
//        builder = match etag {
//            Some(etag) => builder.header("If-Match", etag),
//            None => builder.header("If-None-Match", "*"),
//        };
//
//        let request = builder.body(Full::new(bytes))?;
//        let response = self.http.request(request).await.map_err(|error| DavError::Network {
//            message: error.to_string(),
//        })?;
//
//        let status = response.status();
//        let etag = response
//            .headers()
//            .get("ETag")
//            .and_then(|value| value.to_str().ok())
//            .map(ToOwned::to_owned);
//
//        if status.is_success() || status == StatusCode::CREATED || status == StatusCode::NO_CONTENT {
//            Ok(etag.unwrap_or_default())
//        } else {
//            Err(DavError::Http {
//                status_code: status.as_u16(),
//                message: "PUT failed".to_string(),
//            })
//        }
//    }
//
//    async fn find_current_user_principal(&self) -> Result<String, DavError> {
//        let body = r#"<?xml version="1.0" encoding="utf-8" ?>
//<d:propfind xmlns:d="DAV:">
//  <d:prop>
//    <d:current-user-principal />
//  </d:prop>
//</d:propfind>"#;
//
//        let response = self
//            .send_text("PROPFIND", "/", Some("0"), "application/xml; charset=utf-8", body)
//            .await?;
//
//        xml::parse_first_href_under(&response, "current-user-principal")
//            .ok_or_else(|| DavError::Unexpected {
//                message: "Unable to find current-user-principal".to_string(),
//            })
//    }
//
//    async fn find_home_set(&self, principal_href: &str, property_name: &str) -> Result<String, DavError> {
//        let body = format!(
//            r#"<?xml version="1.0" encoding="utf-8" ?>
//<d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:card="urn:ietf:params:xml:ns:carddav">
//  <d:prop>
//    <c:{property_name} />
//    <card:{property_name} />
//  </d:prop>
//</d:propfind>"#
//        );
//
//        let response = self
//            .send_text("PROPFIND", principal_href, Some("0"), "application/xml; charset=utf-8", &body)
//            .await?;
//
//        xml::parse_first_href_under(&response, property_name).ok_or_else(|| DavError::Unexpected {
//            message: format!("Unable to find {property_name}"),
//        })
//    }
//
//    async fn send_text(
//        &self,
//        method: &str,
//        href: &str,
//        depth: Option<&str>,
//        content_type: &str,
//        body: &str,
//    ) -> Result<String, DavError> {
//        let uri = self.uri_for_href(href)?;
//        let method = Method::from_bytes(method.as_bytes()).map_err(|error| DavError::Unexpected {
//            message: error.to_string(),
//        })?;
//
//        let mut builder = Request::builder()
//            .method(method)
//            .uri(uri)
//            .header("Content-Type", content_type)
//            .header("Accept", "application/xml,text/xml,*/*");
//
//        if let Some(depth) = depth {
//            builder = builder.header("Depth", depth);
//        }
//
//        self.apply_default_headers(builder.headers_mut().ok_or_else(|| DavError::Unexpected {
//            message: "Unable to mutate request headers".to_string(),
//        })?)?;
//
//        let request = builder.body(Full::new(Bytes::copy_from_slice(body.as_bytes())))?;
//        let response = self.http.request(request).await.map_err(|error| DavError::Network {
//            message: error.to_string(),
//        })?;
//
//        let status = response.status();
//        let bytes = response
//            .into_body()
//            .collect()
//            .await
//            .map_err(|error| DavError::Network {
//                message: error.to_string(),
//            })?
//            .to_bytes();
//
//        let text = String::from_utf8_lossy(&bytes).to_string();
//
//        if status.is_success() || status == StatusCode::MULTI_STATUS {
//            Ok(text)
//        } else if status == StatusCode::UNAUTHORIZED || status == StatusCode::FORBIDDEN {
//            Err(DavError::Authentication {
//                message: text,
//            })
//        } else {
//            Err(DavError::Http {
//                status_code: status.as_u16(),
//                message: text,
//            })
//        }
//    }
//
//    fn apply_default_headers(&self, headers: &mut HeaderMap) -> Result<(), DavError> {
//        let auth = format!("{}:{}", self.username, self.password);
//        let encoded = base64::engine::general_purpose::STANDARD.encode(auth);
//        let value = HeaderValue::from_str(&format!("Basic {encoded}")).map_err(|error| {
//            DavError::Unexpected {
//                message: error.to_string(),
//            }
//        })?;
//
//        headers.insert("Authorization", value);
//        headers.insert("User-Agent", HeaderValue::from_static("kmp-dav/0.1.0"));
//        Ok(())
//    }
//
//    fn uri_for_href(&self, href: &str) -> Result<Uri, DavError> {
//        let url = self.base_url.join(href).map_err(|error| DavError::InvalidUrl {
//            message: error.to_string(),
//        })?;
//
//        url.as_str().parse::<Uri>().map_err(Into::into)
//    }
//
//    fn child_href(&self, collection_href: &str, file_name: &str) -> String {
//        let mut base = collection_href.to_string();
//        if !base.ends_with('/') {
//            base.push('/');
//        }
//        format!("{base}{file_name}")
//    }
//}
