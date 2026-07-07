/*
 * Infomaniak Calendar - Multiplatform
 * Copyright (C) 2026 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.multiplatform_calendar.core.data.mapper.timezone

/**
 * Mapping from Microsoft Windows time-zone names (e.g. "Romance Standard Time") to their canonical IANA
 * counterpart (e.g. "Europe/Paris").
 *
 * Outlook / Exchange / Microsoft 365 emit iCalendar files whose `TZID` parameter uses Windows names instead of
 * IANA identifiers. RFC 5545 §3.2.19 allows any string here, provided the payload also carries an embedded
 * `VTIMEZONE` block describing the offsets — which we currently don't parse. Rather than dropping every event
 * from Microsoft senders, we consult the CLDR default (`territory="001"`) mapping to resolve the id to an IANA
 * zone the platform can understand.
 *
 * Source: Unicode CLDR `common/supplemental/windowsZones.xml` (`territory="001"` rows).
 * <https://github.com/unicode-org/cldr/blob/main/common/supplemental/windowsZones.xml>
 */
internal val MS_TO_IANA_TIME_ZONES: Map<String, String> by lazy {
    mapOf(
        "Dateline Standard Time" to "Etc/GMT+12",
        "UTC-11" to "Etc/GMT+11",
        "Aleutian Standard Time" to "America/Adak",
        "Hawaiian Standard Time" to "Pacific/Honolulu",
        "Marquesas Standard Time" to "Pacific/Marquesas",
        "Alaskan Standard Time" to "America/Anchorage",
        "UTC-09" to "Etc/GMT+9",
        "Pacific Standard Time (Mexico)" to "America/Tijuana",
        "UTC-08" to "Etc/GMT+8",
        "Pacific Standard Time" to "America/Los_Angeles",
        "US Mountain Standard Time" to "America/Phoenix",
        "Mountain Standard Time (Mexico)" to "America/Mazatlan",
        "Mountain Standard Time" to "America/Denver",
        "Yukon Standard Time" to "America/Whitehorse",
        "Central America Standard Time" to "America/Guatemala",
        "Central Standard Time" to "America/Chicago",
        "Easter Island Standard Time" to "Pacific/Easter",
        "Central Standard Time (Mexico)" to "America/Mexico_City",
        "Canada Central Standard Time" to "America/Regina",
        "SA Pacific Standard Time" to "America/Bogota",
        "Eastern Standard Time (Mexico)" to "America/Cancun",
        "Eastern Standard Time" to "America/New_York",
        "Haiti Standard Time" to "America/Port-au-Prince",
        "Cuba Standard Time" to "America/Havana",
        "US Eastern Standard Time" to "America/Indianapolis",
        "Turks And Caicos Standard Time" to "America/Grand_Turk",
        "Paraguay Standard Time" to "America/Asuncion",
        "Atlantic Standard Time" to "America/Halifax",
        "Venezuela Standard Time" to "America/Caracas",
        "Central Brazilian Standard Time" to "America/Cuiaba",
        "SA Western Standard Time" to "America/La_Paz",
        "Pacific SA Standard Time" to "America/Santiago",
        "Newfoundland Standard Time" to "America/St_Johns",
        "Tocantins Standard Time" to "America/Araguaina",
        "E. South America Standard Time" to "America/Sao_Paulo",
        "SA Eastern Standard Time" to "America/Cayenne",
        "Argentina Standard Time" to "America/Buenos_Aires",
        "Greenland Standard Time" to "America/Godthab",
        "Montevideo Standard Time" to "America/Montevideo",
        "Magallanes Standard Time" to "America/Punta_Arenas",
        "Saint Pierre Standard Time" to "America/Miquelon",
        "Bahia Standard Time" to "America/Bahia",
        "UTC-02" to "Etc/GMT+2",
        "Azores Standard Time" to "Atlantic/Azores",
        "Cape Verde Standard Time" to "Atlantic/Cape_Verde",
        "UTC" to "Etc/UTC",
        "GMT Standard Time" to "Europe/London",
        "Greenwich Standard Time" to "Atlantic/Reykjavik",
        "Sao Tome Standard Time" to "Africa/Sao_Tome",
        "Morocco Standard Time" to "Africa/Casablanca",
        "W. Europe Standard Time" to "Europe/Berlin",
        "Central Europe Standard Time" to "Europe/Budapest",
        "Romance Standard Time" to "Europe/Paris",
        "Central European Standard Time" to "Europe/Warsaw",
        "W. Central Africa Standard Time" to "Africa/Lagos",
        "Jordan Standard Time" to "Asia/Amman",
        "GTB Standard Time" to "Europe/Bucharest",
        "Middle East Standard Time" to "Asia/Beirut",
        "Egypt Standard Time" to "Africa/Cairo",
        "E. Europe Standard Time" to "Europe/Chisinau",
        "Syria Standard Time" to "Asia/Damascus",
        "West Bank Standard Time" to "Asia/Hebron",
        "South Africa Standard Time" to "Africa/Johannesburg",
        "FLE Standard Time" to "Europe/Kiev",
        "Israel Standard Time" to "Asia/Jerusalem",
        "South Sudan Standard Time" to "Africa/Juba",
        "Kaliningrad Standard Time" to "Europe/Kaliningrad",
        "Sudan Standard Time" to "Africa/Khartoum",
        "Libya Standard Time" to "Africa/Tripoli",
        "Namibia Standard Time" to "Africa/Windhoek",
        "Arabic Standard Time" to "Asia/Baghdad",
        "Turkey Standard Time" to "Europe/Istanbul",
        "Arab Standard Time" to "Asia/Riyadh",
        "Belarus Standard Time" to "Europe/Minsk",
        "Russian Standard Time" to "Europe/Moscow",
        "E. Africa Standard Time" to "Africa/Nairobi",
        "Iran Standard Time" to "Asia/Tehran",
        "Arabian Standard Time" to "Asia/Dubai",
        "Astrakhan Standard Time" to "Europe/Astrakhan",
        "Azerbaijan Standard Time" to "Asia/Baku",
        "Russia Time Zone 3" to "Europe/Samara",
        "Mauritius Standard Time" to "Indian/Mauritius",
        "Saratov Standard Time" to "Europe/Saratov",
        "Georgian Standard Time" to "Asia/Tbilisi",
        "Volgograd Standard Time" to "Europe/Volgograd",
        "Caucasus Standard Time" to "Asia/Yerevan",
        "Afghanistan Standard Time" to "Asia/Kabul",
        "West Asia Standard Time" to "Asia/Tashkent",
        "Ekaterinburg Standard Time" to "Asia/Yekaterinburg",
        "Pakistan Standard Time" to "Asia/Karachi",
        "Qyzylorda Standard Time" to "Asia/Qyzylorda",
        "India Standard Time" to "Asia/Calcutta",
        "Sri Lanka Standard Time" to "Asia/Colombo",
        "Nepal Standard Time" to "Asia/Katmandu",
        "Central Asia Standard Time" to "Asia/Bishkek",
        "Bangladesh Standard Time" to "Asia/Dhaka",
        "Omsk Standard Time" to "Asia/Omsk",
        "Myanmar Standard Time" to "Asia/Rangoon",
        "SE Asia Standard Time" to "Asia/Bangkok",
        "Altai Standard Time" to "Asia/Barnaul",
        "W. Mongolia Standard Time" to "Asia/Hovd",
        "North Asia Standard Time" to "Asia/Krasnoyarsk",
        "N. Central Asia Standard Time" to "Asia/Novosibirsk",
        "Tomsk Standard Time" to "Asia/Tomsk",
        "China Standard Time" to "Asia/Shanghai",
        "North Asia East Standard Time" to "Asia/Irkutsk",
        "Singapore Standard Time" to "Asia/Singapore",
        "W. Australia Standard Time" to "Australia/Perth",
        "Taipei Standard Time" to "Asia/Taipei",
        "Ulaanbaatar Standard Time" to "Asia/Ulaanbaatar",
        "Aus Central W. Standard Time" to "Australia/Eucla",
        "Transbaikal Standard Time" to "Asia/Chita",
        "Tokyo Standard Time" to "Asia/Tokyo",
        "North Korea Standard Time" to "Asia/Pyongyang",
        "Korea Standard Time" to "Asia/Seoul",
        "Yakutsk Standard Time" to "Asia/Yakutsk",
        "Cen. Australia Standard Time" to "Australia/Adelaide",
        "AUS Central Standard Time" to "Australia/Darwin",
        "E. Australia Standard Time" to "Australia/Brisbane",
        "AUS Eastern Standard Time" to "Australia/Sydney",
        "West Pacific Standard Time" to "Pacific/Port_Moresby",
        "Tasmania Standard Time" to "Australia/Hobart",
        "Vladivostok Standard Time" to "Asia/Vladivostok",
        "Lord Howe Standard Time" to "Australia/Lord_Howe",
        "Bougainville Standard Time" to "Pacific/Bougainville",
        "Russia Time Zone 10" to "Asia/Srednekolymsk",
        "Magadan Standard Time" to "Asia/Magadan",
        "Norfolk Standard Time" to "Pacific/Norfolk",
        "Sakhalin Standard Time" to "Asia/Sakhalin",
        "Central Pacific Standard Time" to "Pacific/Guadalcanal",
        "Russia Time Zone 11" to "Asia/Kamchatka",
        "New Zealand Standard Time" to "Pacific/Auckland",
        "UTC+12" to "Etc/GMT-12",
        "Fiji Standard Time" to "Pacific/Fiji",
        "Chatham Islands Standard Time" to "Pacific/Chatham",
        "UTC+13" to "Etc/GMT-13",
        "Tonga Standard Time" to "Pacific/Tongatapu",
        "Samoa Standard Time" to "Pacific/Apia",
        "Line Islands Standard Time" to "Pacific/Kiritimati",
    )
}
