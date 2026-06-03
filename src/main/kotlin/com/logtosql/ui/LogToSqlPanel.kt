package com.logtosql.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.logtosql.core.SqlLogMerger
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class LogToSqlPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val inputArea = JBTextArea(8, 60).apply {
        lineWrap = true
        wrapStyleWord = true
        emptyText.text = "粘贴 MyBatis / Hibernate / JDBC 日志..."
    }

    private val outputArea = JBTextArea(8, 60).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        emptyText.text = "转换后的 SQL 将显示在这里"
    }

    private val statusLabel = JLabel(" ").apply {
        foreground = JBColor.GRAY
    }

    init {
        val topPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(JLabel("日志输入"), BorderLayout.NORTH)
            add(JBScrollPane(inputArea), BorderLayout.CENTER)
        }

        val buttonPanel = JPanel().apply {
            border = JBUI.Borders.empty(4, 8)
            val convertBtn = JButton("转换").apply {
                addActionListener { convert() }
            }
            val copyBtn = JButton("复制 SQL").apply {
                addActionListener { copyOutput() }
            }
            val clearBtn = JButton("清空").apply {
                addActionListener {
                    inputArea.text = ""
                    outputArea.text = ""
                    statusLabel.text = " "
                }
            }
            add(convertBtn)
            add(copyBtn)
            add(clearBtn)
        }

        val bottomPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(JLabel("可执行 SQL"), BorderLayout.NORTH)
            add(JBScrollPane(outputArea), BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }

        add(topPanel, BorderLayout.NORTH)
        add(buttonPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)
        preferredSize = Dimension(600, 400)
    }

    fun setInput(text: String) {
        inputArea.text = text
    }

    fun setOutput(sql: String, warnings: List<String>) {
        outputArea.text = sql
        statusLabel.text = buildStatus(warnings)
        statusLabel.horizontalAlignment = SwingConstants.LEFT
    }

    private fun convert() {
        val input = inputArea.text.trim()
        if (input.isEmpty()) {
            statusLabel.text = "请先粘贴日志内容"
            return
        }
        val result = SqlLogMerger.merge(input)
        outputArea.text = result.sql
        val formatInfo = "格式: ${result.format}"
        statusLabel.text = if (result.warnings.isEmpty()) {
            formatInfo
        } else {
            "$formatInfo | ${result.warnings.joinToString("; ")}"
        }
    }

    private fun copyOutput() {
        val sql = outputArea.text
        if (sql.isNotBlank()) {
            CopyPasteManager.getInstance().setContents(StringSelection(sql))
            statusLabel.text = "已复制到剪贴板"
        }
    }

    private fun buildStatus(warnings: List<String>): String =
        if (warnings.isEmpty()) "转换成功" else warnings.joinToString("; ")
}
