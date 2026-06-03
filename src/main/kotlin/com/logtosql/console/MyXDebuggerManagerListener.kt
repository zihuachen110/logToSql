package com.logtosql.console

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManagerListener

/**
 * Debug 启动 / 切换 Tab 时绑定 Console，并监听 debuggee 进程 stdout。
 *
 * 注意：须使用 [XDebuggerManagerListener.processStarted]，2024.2 没有 sessionStarted 方法。
 */
class MyXDebuggerManagerListener : XDebuggerManagerListener {

    override fun processStarted(debugProcess: XDebugProcess) {
        attachToSession(debugProcess.session)
        debugProcess.processHandler?.addProcessListener(
            ProcessOutputListener(debugProcess.session.project),
        )
    }

    override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
        currentSession?.let { attachToSession(it) }
    }

    private fun attachToSession(session: XDebugSession) {
        val project = session.project
        val handler = MyBatisLogLineHandler.getInstance(project)
        ApplicationManager.getApplication().invokeLater {
            val console = session.runContentDescriptor?.executionConsole as? ConsoleView
            if (console != null) {
                handler.activeConsole = console
            }
        }
    }
}
