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
package com.infomaniak.multiplatform_calendar.core.localization

import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.Frequency
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceRule
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.RecurrenceUntil
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.WeekDayNum
import com.infomaniak.multiplatform_calendar.core.domain.model.event.recurrenceRule.toLocalizedString
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class RecurrenceLocalizationResourceTest {

    private val start = LocalDateTime(2026, 9, 30, 18, 30)

    @Test
    fun everyDay_resolvesEverySupportedLocaleFromComposeResources() = runTest {
        val expected = mapOf(
            "en" to "Every day",
            "de" to "Jeden Tag",
            "es" to "Cada día",
            "fr" to "Chaque jour",
            "it" to "Ogni giorno",
            "da" to "Hver dag",
            "el" to "Κάθε μέρα",
            "fi" to "Joka päivä",
            "nb" to "Hver dag",
            "nl" to "Elke dag",
            "pl" to "Codziennie",
            "pt" to "Todos os dias",
            "sv" to "Varje dag",
        )
        val rule = RecurrenceRule(freq = Frequency.Daily)

        expected.forEach { (locale, translation) ->
            withPreferredLanguage(locale) {
                assertEquals(translation, rule.toLocalizedString(start), "for locale $locale")
            }
        }
    }

    @Test
    fun everyTwoDays_resolvesLocalizedPluralsForEverySupportedLocale() = runTest {
        val expected = mapOf(
            "en" to "Every 2 days",
            "de" to "Alle 2 Tage",
            "es" to "Cada 2 días",
            "fr" to "Tous les 2 jours",
            "it" to "Ogni 2 giorni",
            "da" to "Hver 2. dag",
            "el" to "Κάθε 2 ημέρες",
            "fi" to "2 päivän välein",
            "nb" to "Hver 2. dag",
            "nl" to "Elke 2 dagen",
            "pl" to "Co 2 dni",
            "pt" to "A cada 2 dias",
            "sv" to "Var 2 dag",
        )
        val rule = RecurrenceRule(freq = Frequency.Daily, interval = 2)

        expected.forEach { (locale, translation) ->
            withPreferredLanguage(locale) {
                assertEquals(translation, rule.toLocalizedString(start), "for locale $locale")
            }
        }
    }

    @Test
    fun referenceRule_resolvesTheCompleteSentenceInEverySupportedLocale() = runTest {
        val expected = mapOf(
            "en" to "Every month, in January, in February, in March, in April, in May, in June, in September, " +
                    "in October, in November, and in December, on the last matching day (Monday, Tuesday, Wednesday, " +
                    "Thursday, and Friday), until January 1, 2029",
            "de" to "Jeden Monat, im Januar, im Februar, im März, im April, im Mai, im Juni, im September, " +
                    "im Oktober, im November und im Dezember, am letzten passenden Tag (Montag, Dienstag, Mittwoch, " +
                    "Donnerstag und Freitag), bis 1. Januar 2029",
            "es" to "Cada mes, en enero, en febrero, en marzo, en abril, en mayo, en junio, en septiembre, " +
                    "en octubre, en noviembre y en diciembre, el último día coincidente (lunes, martes, miércoles, " +
                    "jueves y viernes), hasta el 1 de enero de 2029",
            "fr" to "Chaque mois, en janvier, en février, en mars, en avril, en mai, en juin, en septembre, " +
                    "en octobre, en novembre et en décembre, le dernier jour correspondant (lundi, mardi, mercredi, " +
                    "jeudi et vendredi), jusqu’au 1 janvier 2029",
            "it" to "Ogni mese, a gennaio, a febbraio, a marzo, ad aprile, a maggio, a giugno, a settembre, " +
                    "a ottobre, a novembre e a dicembre, nell’ultimo giorno corrispondente (lunedì, martedì, mercoledì, " +
                    "giovedì e venerdì), fino al 1 gennaio 2029",
            "da" to "Hver måned, i januar, i februar, i marts, i april, i maj, i juni, i september, i oktober, " +
                    "i november og i december, på den sidste matchende dag (mandag, tirsdag, onsdag, torsdag og fredag), " +
                    "indtil 1. januar 2029",
            "el" to "Κάθε μήνα, τον Ιανουάριο, τον Φεβρουάριο, τον Μάρτιο, τον Απρίλιο, τον Μάιο, τον Ιούνιο, " +
                    "τον Σεπτέμβριο, τον Οκτώβριο, τον Νοέμβριο και τον Δεκέμβριο, την τελευταία ημέρα που αντιστοιχεί " +
                    "(Δευτέρα, Τρίτη, Τετάρτη, Πέμπτη και Παρασκευή), έως 1 Ιανουαρίου 2029",
            "fi" to "Joka kuukausi, tammikuussa, helmikuussa, maaliskuussa, huhtikuussa, toukokuussa, kesäkuussa, " +
                    "syyskuussa, lokakuussa, marraskuussa ja joulukuussa, viimeisenä ehtoja vastaavana päivänä " +
                    "(maanantai, tiistai, keskiviikko, torstai ja perjantai), asti 1. tammikuuta 2029",
            "nb" to "Hver måned, i januar, i februar, i mars, i april, i mai, i juni, i september, i oktober, " +
                    "i november og i desember, på den siste samsvarende dagen (mandag, tirsdag, onsdag, torsdag og fredag), " +
                    "til 1. januar 2029",
            "nl" to "Elke maand, in januari, in februari, in maart, in april, in mei, in juni, in september, " +
                    "in oktober, in november en in december, op de laatste overeenkomende dag (maandag, dinsdag, woensdag, " +
                    "donderdag en vrijdag), tot 1 januari 2029",
            "pl" to "Co miesiąc, w styczniu, w lutym, w marcu, w kwietniu, w maju, w czerwcu, we wrześniu, " +
                    "w październiku, w listopadzie i w grudniu, w ostatnim pasującym dniu (poniedziałek, wtorek, środa, " +
                    "czwartek i piątek), do 1 stycznia 2029",
            "pt" to "Todos os meses, em janeiro, em fevereiro, em março, em abril, em maio, em junho, em setembro, " +
                    "em outubro, em novembro e em dezembro, no último dia correspondente (segunda-feira, terça-feira, " +
                    "quarta-feira, quinta-feira e sexta-feira), até 1 de janeiro de 2029",
            "sv" to "Varje månad, i januari, i februari, i mars, i april, i maj, i juni, i september, i oktober, " +
                    "i november och i december, på den sista matchande dagen (måndag, tisdag, onsdag, torsdag och fredag), " +
                    "till 1 januari 2029",
        )
        val rule = referenceRule()

        expected.forEach { (locale, translation) ->
            withPreferredLanguage(locale) {
                assertEquals(
                    translation,
                    rule.toLocalizedString(start, TimeZone.of("Europe/Zurich")),
                    "for locale $locale",
                )
            }
        }
    }

    private fun referenceRule(): RecurrenceRule = RecurrenceRule(
        freq = Frequency.Monthly,
        byDay = listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        ).map { WeekDayNum(dayOfWeek = it) },
        byOccurrencePosition = listOf(-1),
        byMonth = listOf(1, 2, 3, 4, 5, 6, 9, 10, 11, 12),
        until = RecurrenceUntil.DateTimeUtc(Instant.parse("2028-12-31T23:59:59Z")),
    )

    private suspend fun withPreferredLanguage(language: String, block: suspend () -> Unit) {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = "AppleLanguages"
        val previous = defaults.objectForKey(key)

        try {
            defaults.setObject(arrayListOf(language), forKey = key)
            defaults.synchronize()
            val currentLanguage = NSLocale.preferredLanguages.firstOrNull() as? String
            assertEquals(language, currentLanguage, "test locale did not apply")
            block()
        } finally {
            if (previous == null) defaults.removeObjectForKey(key)
            else defaults.setObject(previous, forKey = key)
            defaults.synchronize()
        }
    }
}
