package com.logtosql.console

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.application.ApplicationManager

/** Run 模式：绑定控制台并监听进程输出。 */
class MyBatisConsoleExecutionListener : ExecutionListener {

    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val project = env.project ?: return
        handler.addProcessListener(ProcessOutputListener(project))

        ApplicationManager.getApplication().invokeLater {
            val console = RunContentManager.getInstance(project).allDescriptors
                .firstOrNull { it.processHandler == handler }
                ?.executionConsole as? ConsoleView
            if (console != null) {
                MyBatisLogLineHandler.getInstance(project).activeConsole = console
            }
        }
    }
}
