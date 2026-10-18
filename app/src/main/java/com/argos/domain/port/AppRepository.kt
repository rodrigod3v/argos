package com.argos.domain.port

import com.argos.core.model.AppEntry

/** Acesso a apps instalados. Implementado na camada data. */
interface AppRepository {
    fun all(): List<AppEntry>
    fun find(query: String): AppEntry?
    fun launch(entry: AppEntry): Boolean
    fun invalidate()
}
