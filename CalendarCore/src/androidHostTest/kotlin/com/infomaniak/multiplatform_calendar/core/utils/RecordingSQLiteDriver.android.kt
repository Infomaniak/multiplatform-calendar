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
package com.infomaniak.multiplatform_calendar.core.utils

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import java.util.Collections

internal actual class RecordingSQLiteDriver actual constructor(
    private val delegate: SQLiteDriver,
) : SQLiteDriver {

    private val mutablePreparedSql = Collections.synchronizedList(mutableListOf<String>())
    actual val preparedSql: List<String> get() = synchronized(mutablePreparedSql) { mutablePreparedSql.toList() }

    actual fun clear() {
        mutablePreparedSql.clear()
    }

    override val hasConnectionPool: Boolean get() = delegate.hasConnectionPool

    actual override fun open(fileName: String): SQLiteConnection {
        return RecordingConnection(delegate.open(fileName)) { sql -> mutablePreparedSql += sql }
    }

    private class RecordingConnection(
        private val delegate: SQLiteConnection,
        private val onPrepare: (sql: String) -> Unit,
    ) : SQLiteConnection {

        override fun inTransaction(): Boolean = delegate.inTransaction()

        override fun prepare(sql: String) = delegate.prepare(sql).also { onPrepare(sql) }

        override fun close() = delegate.close()
    }
}


