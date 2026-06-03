package com.logtosql.console

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.project.Project

/** 拦截 Run / Debug 控制台每一行显示。 */
class MyBatisConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> =
        arrayOf(MyBatisConsoleFilter(project))
}

private class MyBatisConsoleFilter(
    private val project: Project,
) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val handler = MyBatisLogLineHandler.getInstance(project)
        (RunContentManager.getInstance(project).selectedContent?.executionConsole as? ConsoleView)
            ?.let { handler.activeConsole = it }
        handler.onLine(line)
        return null
    }
}
