package com.logtosql.core

/**
 * 解析 ORM / JDBC 日志并合并为可执行 SQL。
 * 支持 MyBatis、Hibernate、Spring JDBC 及通用格式。
 */
object SqlLogMerger {

    data class MergeResult(
        val sql: String,
        val format: String,
        val warnings: List<String> = emptyList(),
    )

    /** 从控制台两行 MyBatis 日志合并 SQL（供自动打印使用） */
    fun mergeMyBatisLines(preparingLine: String, parametersLine: String): String? {
        val result = merge("$preparingLine\n$parametersLine")
        return result.sql.takeIf { result.format == "mybatis" && it.isNotBlank() }
    }

    fun merge(logText: String): MergeResult {
        val text = logText.trim()
        if (text.isEmpty()) {
            return MergeResult("", "empty", listOf("输入为空"))
        }

        val mybatis = tryMyBatis(text)
        if (mybatis != null) return mybatis

        val hibernate = tryHibernate(text)
        if (hibernate != null) return hibernate

        val springJdbc = trySpringJdbc(text)
        if (springJdbc != null) return springJdbc

        val generic = tryGeneric(text)
        if (generic != null) return generic

        return MergeResult(
            sql = text,
            format = "unknown",
            warnings = listOf("未能识别日志格式，已原样返回。请检查是否包含 SQL 与参数行。"),
        )
    }

    /** MyBatis: Preparing + Parameters */
    private fun tryMyBatis(text: String): MergeResult? {
        val sql = findGroup(text, MYBATIS_PREPARE_PATTERNS) ?: return null
        val paramLine = findGroup(text, MYBATIS_PARAM_PATTERNS) ?: return null

        val params = parseMyBatisParams(paramLine)
        val merged = bindParams(sql, params)
        return MergeResult(merged, "mybatis")
    }

    /** Hibernate: SQL line + binding parameter lines */
    private fun tryHibernate(text: String): MergeResult? {
        val sqlLine = HibernatePatterns.SQL_LINE.find(text)?.value?.trim()?.let { line ->
            line.removePrefix("Hibernate:").trim()
        } ?: return null
        val bindings = HibernatePatterns.BINDING.findAll(text)
            .map { match ->
                val index = match.groupValues[1].toInt()
                val type = match.groupValues[2]
                val value = match.groupValues[3]
                index to TypedValue(if (value.equals("null", ignoreCase = true)) null else value, type)
            }
            .sortedBy { it.first }
            .map { it.second }
            .toList()

        if (bindings.isEmpty()) return null

        val merged = bindParams(sqlLine, bindings)
        return MergeResult(merged, "hibernate")
    }

    /** Spring JDBC / 通用: Executing SQL + Parameters: [a, b] */
    private fun trySpringJdbc(text: String): MergeResult? {
        val sql = findGroup(text, SPRING_SQL_PATTERNS) ?: return null
        val paramLine = findGroup(text, SPRING_PARAM_PATTERNS) ?: return null

        val params = parseBracketParams(paramLine)
        val merged = bindParams(sql, params)
        return MergeResult(merged, "spring-jdbc")
    }

    /** 通用: 任意含 ? 的 SQL + 逗号分隔参数字符串 */
    private fun tryGeneric(text: String): MergeResult? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val sqlLine = lines.firstOrNull { looksLikeSql(it) && it.contains('?') } ?: return null
        val paramLine = lines.firstOrNull {
            it.contains("Parameters", ignoreCase = true) ||
                it.contains("params", ignoreCase = true) ||
                it.startsWith("binding parameter", ignoreCase = true)
        } ?: return null

        val sql = sqlLine.substringAfter(":").trim().ifEmpty { sqlLine }
        val rawParams = paramLine.substringAfter(":").trim()
        val params = if (rawParams.startsWith("[")) {
            parseBracketParams(rawParams)
        } else {
            parseMyBatisParams(rawParams)
        }

        if (params.isEmpty()) return null
        return MergeResult(bindParams(sql, params), "generic")
    }

    private fun findGroup(text: String, patterns: List<Regex>): String? {
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            return match.groupValues[1].trim()
        }
        return null
    }

    private val MYBATIS_PREPARE_PATTERNS = listOf(
        Regex("""(?m)^\s*==>\s*Preparing:\s*(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""(?m)^\s*Preparing:\s*(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""(?m)^\s*DEBUG\s+.*Preparing:\s*(.+)$""", RegexOption.IGNORE_CASE),
    )

    private val MYBATIS_PARAM_PATTERNS = listOf(
        Regex("""(?m)^\s*==>\s*Parameters:\s*(.*)$""", RegexOption.IGNORE_CASE),
        Regex("""(?m)^\s*Parameters:\s*(.*)$""", RegexOption.IGNORE_CASE),
    )

    private val SPRING_SQL_PATTERNS = listOf(
        Regex("""(?m)^\s*Executing\s+SQL:\s*(.+)$""", RegexOption.IGNORE_CASE),
        Regex("""(?m)^\s*SQL:\s*(.+)$""", RegexOption.IGNORE_CASE),
    )

    private val SPRING_PARAM_PATTERNS = listOf(
        Regex("""(?m)^\s*Parameters:\s*(\[.+\])\s*$""", RegexOption.IGNORE_CASE),
    )

    private object HibernatePatterns {
        val SQL_LINE = Regex(
            """(?m)^\s*(?:Hibernate:\s*)?(select|insert|update|delete|with)\b.+$""",
            RegexOption.IGNORE_CASE,
        )
        val BINDING = Regex(
            """(?m)^\s*binding parameter \[(\d+)] as \[(\w+)] - \[(.*)]\s*$""",
            RegexOption.IGNORE_CASE,
        )
    }

    /** 解析 MyBatis 参数: 1(Long), test(String), null */
    internal fun parseMyBatisParams(paramLine: String): List<TypedValue> {
        if (paramLine.equals("null", ignoreCase = true)) return emptyList()

        val result = mutableListOf<TypedValue>()
        var i = 0
        val s = paramLine.trim()

        while (i < s.length) {
            while (i < s.length && (s[i].isWhitespace() || s[i] == ',')) i++
            if (i >= s.length) break

            if (s.regionMatches(i, "null", 0, 4, ignoreCase = true)) {
                val end = i + 4
                if (end >= s.length || !s[end].isLetterOrDigit()) {
                    result.add(TypedValue(null, "null"))
                    i = end
                    continue
                }
            }

            if (s[i] == '(') {
                i++
                continue
            }

            val valueStart = i
            var depth = 0
            while (i < s.length) {
                when (s[i]) {
                    '(' -> depth++
                    ')' -> {
                        if (depth == 0) break
                        depth--
                    }
                    ',' -> if (depth == 0) break
                }
                i++
            }

            val token = s.substring(valueStart, i).trim()
            if (token.isNotEmpty()) {
                result.add(parseMyBatisToken(token))
            }

            if (i < s.length && s[i] == ')') i++
        }

        return result
    }

    private fun parseMyBatisToken(token: String): TypedValue {
        val typeMatch = Regex("""^(.+)\((\w+)\)$""").find(token)
        return if (typeMatch != null) {
            val value = typeMatch.groupValues[1]
            val type = typeMatch.groupValues[2]
            TypedValue(if (value.equals("null", ignoreCase = true)) null else value, type)
        } else {
            TypedValue(token, "String")
        }
    }

    /** 解析 [1, 'a', null] 或 1, 'a' 形式 */
    internal fun parseBracketParams(raw: String): List<TypedValue> {
        val inner = raw.trim().removeSurrounding("[", "]").trim()
        if (inner.isEmpty()) return emptyList()

        val tokens = splitParamList(inner)
        return tokens.map { token ->
            val t = token.trim()
            when {
                t.equals("null", ignoreCase = true) -> TypedValue(null, "null")
                t.startsWith("'") && t.endsWith("'") -> TypedValue(t.substring(1, t.length - 1), "String")
                t.startsWith("\"") && t.endsWith("\"") -> TypedValue(t.substring(1, t.length - 1), "String")
                t.endsWith("L", ignoreCase = true) -> TypedValue(t.dropLast(1), "Long")
                t.endsWith("D", ignoreCase = true) -> TypedValue(t.dropLast(1), "Double")
                t.matches(Regex("""-?\d+""")) -> TypedValue(t, "Integer")
                t.matches(Regex("""-?\d+\.\d+""")) -> TypedValue(t, "Double")
                else -> TypedValue(t, "String")
            }
        }
    }

    private fun splitParamList(inner: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote: Char? = null
        var depth = 0

        for (ch in inner) {
            when {
                inQuote != null -> {
                    current.append(ch)
                    if (ch == inQuote) inQuote = null
                }
                ch == '\'' || ch == '"' -> {
                    inQuote = ch
                    current.append(ch)
                }
                ch == '(' || ch == '[' -> {
                    depth++
                    current.append(ch)
                }
                ch == ')' || ch == ']' -> {
                    depth--
                    current.append(ch)
                }
                ch == ',' && depth == 0 -> {
                    tokens.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }

    internal fun bindParams(sql: String, params: List<TypedValue>): String {
        if (params.isEmpty()) return sql

        val sb = StringBuilder()
        var paramIndex = 0
        var i = 0

        while (i < sql.length) {
            if (sql[i] == '?' && paramIndex < params.size) {
                sb.append(formatValue(params[paramIndex]))
                paramIndex++
                i++
            } else if (sql[i] == '?' && paramIndex >= params.size) {
                sb.append('?')
                i++
            } else if (sql.startsWith("?", i) && i > 0 && sql[i - 1].isDigit()) {
                // MyBatis 有时用 ?0 ?1 占位
                sb.append(sql[i])
                i++
            } else {
                sb.append(sql[i])
                i++
            }
        }

        return sb.toString().trim()
    }

    internal fun formatValue(value: TypedValue): String {
        if (value.raw == null) return "NULL"

        val type = value.type?.lowercase() ?: inferType(value.raw)

        return when (type) {
            "null" -> "NULL"
            "string", "varchar", "char", "text", "nvarchar", "clob" -> "'${escapeSqlString(value.raw)}'"
            "date", "localdate", "timestamp", "localdatetime", "datetime", "time" ->
                "'${escapeSqlString(value.raw)}'"
            "boolean", "bit" -> if (value.raw.equals("true", ignoreCase = true)) "1" else "0"
            "byte", "short", "int", "integer", "long", "bigint", "float", "double",
            "bigdecimal", "decimal", "number", "numeric" -> value.raw
            else -> {
                if (value.raw.matches(Regex("""-?\d+(\.\d+)?"""))) value.raw
                else "'${escapeSqlString(value.raw)}'"
            }
        }
    }

    private fun inferType(raw: String): String {
        return when {
            raw.equals("true", ignoreCase = true) || raw.equals("false", ignoreCase = true) -> "boolean"
            raw.matches(Regex("""-?\d+""")) -> "integer"
            raw.matches(Regex("""-?\d+\.\d+""")) -> "double"
            else -> "string"
        }
    }

    private fun escapeSqlString(s: String): String = s.replace("'", "''")

    private fun looksLikeSql(line: String): Boolean {
        val upper = line.uppercase()
        return upper.contains("SELECT ") || upper.contains("INSERT ") ||
            upper.contains("UPDATE ") || upper.contains("DELETE ") ||
            upper.contains("WITH ")
    }
}

data class TypedValue(val raw: String?, val type: String?)
