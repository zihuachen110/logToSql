package com.logtosql.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SqlLogMergerTest {

    @Test
    fun `mybatis basic select`() {
        val log = """
            ==>  Preparing: SELECT id, name FROM user WHERE id = ? AND status = ?
            ==> Parameters: 100(Long), ACTIVE(String)
        """.trimIndent()

        val result = SqlLogMerger.merge(log)
        assertEquals("mybatis", result.format)
        assertEquals(
            "SELECT id, name FROM user WHERE id = 100 AND status = 'ACTIVE'",
            result.sql,
        )
    }

    @Test
    fun `mybatis with null parameter`() {
        val log = """
            Preparing: UPDATE user SET name = ? WHERE id = ?
            Parameters: null, 1(Long)
        """.trimIndent()

        val result = SqlLogMerger.merge(log)
        assertEquals("UPDATE user SET name = NULL WHERE id = 1", result.sql)
    }

    @Test
    fun `mybatis string with quote`() {
        val log = """
            ==>  Preparing: SELECT * FROM user WHERE name = ?
            ==> Parameters: O'Brien(String)
        """.trimIndent()

        val result = SqlLogMerger.merge(log)
        assertEquals("SELECT * FROM user WHERE name = 'O''Brien'", result.sql)
    }

    @Test
    fun `hibernate binding parameters`() {
        val log = """
            Hibernate: select user0_.id as id1_0_ from user user0_ where user0_.id=? and user0_.name=?
            binding parameter [1] as [BIGINT] - [42]
            binding parameter [2] as [VARCHAR] - [alice]
        """.trimIndent()

        val result = SqlLogMerger.merge(log)
        assertEquals("hibernate", result.format)
        assertTrue(result.sql.contains("42"))
        assertTrue(result.sql.contains("'alice'"))
    }

    @Test
    fun `spring jdbc bracket params`() {
        val log = """
            Executing SQL: SELECT * FROM orders WHERE user_id = ? AND amount > ?
            Parameters: [1001, 99.5]
        """.trimIndent()

        val result = SqlLogMerger.merge(log)
        assertEquals("spring-jdbc", result.format)
        assertEquals("SELECT * FROM orders WHERE user_id = 1001 AND amount > 99.5", result.sql)
    }

    @Test
    fun `parse mybatis params with types`() {
        val params = SqlLogMerger.parseMyBatisParams("1(Long), test(String), null")
        assertEquals(3, params.size)
        assertEquals("1", params[0].raw)
        assertEquals("Long", params[0].type)
        assertEquals(null, params[2].raw)
    }

    @Test
    fun `merge mybatis with empty parameters`() {
        val log = """
            ==>  Preparing: select id from ogsm_period WHERE deleted = 0
            ==> Parameters:
        """.trimIndent()
        val result = SqlLogMerger.merge(log)
        assertEquals("mybatis", result.format)
        assertTrue(result.sql.contains("select id from ogsm_period"))
        assertFalse(result.sql.contains('?'))
    }

    @Test
    fun `merge from console mybatis line pair`() {
        val preparing = "==>  Preparing: SELECT id FROM ogsm_approval_task WHERE id=?"
        val parameters = "==> Parameters: 965(Long)"
        val sql = SqlLogMerger.mergeMyBatisLines(preparing, parameters)
        assertEquals("SELECT id FROM ogsm_approval_task WHERE id=965", sql)
    }

    @Test
    fun `parse bracket params with strings`() {
        val params = SqlLogMerger.parseBracketParams("[1, 'hello', null]")
        assertEquals(3, params.size)
        assertEquals("hello", params[1].raw)
        assertEquals(null, params[2].raw)
    }
}
