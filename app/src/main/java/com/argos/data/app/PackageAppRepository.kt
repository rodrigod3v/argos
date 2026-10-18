package com.argos.data.app

import android.content.Context
import android.content.Intent
import com.argos.core.model.AppEntry
import com.argos.core.text.matchesWord
import com.argos.core.text.norm
import com.argos.domain.port.AppRepository

/** AppRepository via PackageManager. Lista cacheada — muda pouco e a query e caro. */
class PackageAppRepository(private val context: Context) : AppRepository {

    @Volatile
    private var cache: List<AppEntry>? = null

    override fun all(): List<AppEntry> {
        cache?.let { return it }
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val entries = pm.queryIntentActivities(launcher, 0)
            .mapNotNull { info ->
                val label = runCatching { info.loadLabel(pm).toString() }.getOrNull()
                val pkg = info.activityInfo?.packageName
                if (label.isNullOrBlank() || pkg.isNullOrBlank()) null else AppEntry(label, pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.norm() }
        cache = entries
        return entries
    }

    /** exato > prefixo > contem > palavra aproximada (STT erra nome proprio). */
    override fun find(query: String): AppEntry? {
        val q = query.norm()
        if (q.isEmpty()) return null
        val entries = all()
        return entries.firstOrNull { it.label.norm() == q }
            ?: entries.firstOrNull { it.label.norm().startsWith(q) }
            ?: entries.firstOrNull { it.label.norm().contains(q) }
            ?: entries.firstOrNull { it.label.norm().matchesWord(q) }
    }

    override fun launch(entry: AppEntry): Boolean {
        val intent = context.packageManager
            .getLaunchIntentForPackage(entry.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    override fun invalidate() {
        cache = null
    }
}
