package com.logtosql.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import com.logtosql.core.SqlLogMerger
import com.logtosql.ui.LogToSqlPanel

class ConvertSelectionAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selected = editor.selectionModel.selectedText?.trim().orEmpty()
        if (selected.isEmpty()) return

        val result = SqlLogMerger.merge(selected)
        showInToolWindow(project, selected, result.sql, result.warnings)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }

    companion object {
        fun showInToolWindow(
            project: com.intellij.openapi.project.Project,
            input: String,
            output: String,
            warnings: List<String>,
        ) {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Log To SQL")
                ?: return
            toolWindow.show {
                val content = toolWindow.contentManager.contents.firstOrNull() ?: return@show
                val panel = content.component as? LogToSqlPanel ?: return@show
                panel.setInput(input)
                panel.setOutput(output, warnings)
            }
        }
    }
}
