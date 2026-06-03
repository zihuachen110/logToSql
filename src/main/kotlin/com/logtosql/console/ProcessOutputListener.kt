package com.logtosql.console

import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/** 监听进程 stdout/stderr 文本。 */
class ProcessOutputListener(
    private val project: Project,
) : ProcessListener {

    private val lineBuffer = OutputLineBuffer()
    private val lineHandler = MyBatisLogLineHandler.getInstance(project)

    override fun onTextAvailable(event: com.intellij.execution.process.ProcessEvent, outputType: Key<*>) {
        if (outputType != ProcessOutputTypes.STDOUT && outputType != ProcessOutputTypes.STDERR) {
            return
        }
        lineBuffer.append(event.text) { lineHandler.onLine(it) }
    }
}

/** 分片输出按行拆分 */
class OutputLineBuffer {
    private val buffer = StringBuilder()

    fun append(chunk: String, onLine: (String) -> Unit) {
        buffer.append(chunk)
        while (true) {
            val newlineIndex = buffer.indexOf('\n')
            if (newlineIndex < 0) break
            var line = buffer.substring(0, newlineIndex)
            buffer.delete(0, newlineIndex + 1)
            if (line.endsWith('\r')) line = line.dropLast(1)
            if (line.isNotEmpty()) onLine(line)
        }
    }
}
