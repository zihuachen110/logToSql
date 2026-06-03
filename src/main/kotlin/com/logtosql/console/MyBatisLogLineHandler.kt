package com.logtosql.console

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.logtosql.core.SqlLogMerger

/**
 * 解析 MyBatis 控制台日志行，合并 SQL 并输出到 ConsoleView。
 */
@Service(Service.Level.PROJECT)
class MyBatisLogLineHandler(private val project: Project) {

    private var lastPreparingLine: String? = null
    private var lastMergedSql: String? = null
    private var lastPrintedSql: String? = null

    @Volatile
    var activeConsole: ConsoleView? = null

    @Synchronized
    fun onLine(rawLine: String) {
        val line = extractMyBatisSegment(rawLine) ?: return
        when {
            MyBatisLinePatterns.isPreparing(line) -> lastPreparingLine = line
            MyBatisLinePatterns.isParameters(line) -> {
                val preparing = lastPreparingLine ?: return
                val sql = SqlLogMerger.mergeMyBatisLines(preparing, line) ?: return
                if (sql == lastMergedSql) return
                lastMergedSql = sql
                lastPreparingLine = null
                printSql(sql)
            }
            line.startsWith("<==") -> lastPreparingLine = null
        }
    }

    private fun printSql(sql: String) {
        ApplicationManager.getApplication().invokeLater({
            tryPrint(sql, allowRetry = true)
        }, ModalityState.any())
    }

    private fun tryPrint(sql: String, allowRetry: Boolean) {
        if (sql == lastPrintedSql) return
        val console = resolveConsole()
        if (console != null) {
            lastPrintedSql = sql
            console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
            console.print("▶ [Log To SQL] ", ConsoleViewContentType.ERROR_OUTPUT)
            console.print("$sql\n", ConsoleViewContentType.ERROR_OUTPUT)
        } else if (allowRetry) {
            ApplicationManager.getApplication().invokeLater({
                tryPrint(sql, allowRetry = false)
            }, ModalityState.any())
        }
    }

    private fun resolveConsole(): ConsoleView? {
        activeConsole?.let { return it }
        val selected = RunContentManager.getInstance(project).selectedContent
        (selected?.executionConsole as? ConsoleView)?.let { return it }
        val session = XDebuggerManager.getInstance(project).currentSession
        return session?.runContentDescriptor?.executionConsole as? ConsoleView
    }

    companion object {
        fun getInstance(project: Project): MyBatisLogLineHandler =
            project.getService(MyBatisLogLineHandler::class.java)

        /** 从带 log4j 前缀的行里截取 MyBatis 段，如 "... traceLogId:xxx - ==> Preparing:" */
        fun extractMyBatisSegment(line: String): String? {
            val trimmed = line.trim()
            val markers = listOf("==>  Preparing:", "==> Preparing:", "==>  Parameters:", "==> Parameters:", "<==")
            for (marker in markers) {
                val idx = trimmed.indexOf(marker)
                if (idx >= 0) return trimmed.substring(idx).trim()
            }
            val lower = trimmed.lowercase()
            if (trimmed.contains("Preparing:", ignoreCase = true) &&
                (lower.contains("select") || lower.contains("insert") ||
                    lower.contains("update") || lower.contains("delete"))
            ) {
                val prepIdx = trimmed.indexOf("Preparing:", ignoreCase = true)
                if (prepIdx >= 0) return trimmed.substring(prepIdx)
            }
            return null
        }
    }
}

object MyBatisLinePatterns {
    private val PREPARING = Regex("""==>\s*Preparing:\s*.+""", RegexOption.IGNORE_CASE)
    private val PARAMETERS = Regex("""==>\s*Parameters:\s*.*""", RegexOption.IGNORE_CASE)

    fun isPreparing(line: String): Boolean = PREPARING.containsMatchIn(line)

    fun isParameters(line: String): Boolean = PARAMETERS.containsMatchIn(line)
}
