package dev.komrd.core.network.dto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KomgaSearchConditionsTest {
    @Test
    fun readStatusCondition_producesOperatorIsAndValueUnread() {
        val obj: JsonObject = readStatusCondition("UNREAD")
        val readStatus = obj["readStatus"]!!.jsonObject
        assertEquals("is", readStatus["operator"]!!.jsonPrimitive.content)
        assertEquals("UNREAD", readStatus["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun libraryCondition_producesOperatorIsAndValueLibraryId() {
        val obj: JsonObject = libraryCondition("lib-1")
        val libraryId = obj["libraryId"]!!.jsonObject
        assertEquals("is", libraryId["operator"]!!.jsonPrimitive.content)
        assertEquals("lib-1", libraryId["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun andCondition_wrapsConditionsInAllOfArray() {
        val conditions = arrayOf(libraryCondition("lib-1"), readStatusCondition("READ"))
        val obj: JsonObject = andCondition(*conditions)
        val allOf = obj["allOf"]!!.jsonArray
        assertEquals(2, allOf.size)

        val first = allOf[0].jsonObject
        assertEquals("lib-1", first["libraryId"]!!.jsonObject["value"]!!.jsonPrimitive.content)

        val second = allOf[1].jsonObject
        assertEquals("READ", second["readStatus"]!!.jsonObject["value"]!!.jsonPrimitive.content)
    }

    @Test
    fun andCondition_withEmptyConditions_producesEmptyAllOfArray() {
        val obj: JsonObject = andCondition()
        val allOf = obj["allOf"]!!.jsonArray
        assertTrue("allOf should be empty", allOf.isEmpty())
    }

    @Test
    fun andCondition_singleCondition_keepsNestingShape() {
        val obj: JsonObject = andCondition(readStatusCondition("UNREAD"))
        val allOf = obj["allOf"]!!.jsonArray
        assertEquals(1, allOf.size)
        val inner = allOf[0].jsonObject["readStatus"]!!.jsonObject["value"]!!.jsonPrimitive
        assertEquals("UNREAD", inner.content)
    }

    @Test
    fun isCondition_genericFactory_isAlsoExercised() {
        // M3 から継続: 既存ヘルパが壊れていないことを確認（Read Status / Library とは別フィールドでも同じ形状を返す）。
        val obj = isCondition("seriesId", "series-1")
        val inner = obj["seriesId"]!!.jsonObject
        assertEquals("is", inner["operator"]!!.jsonPrimitive.content)
        assertEquals("series-1", inner["value"]!!.jsonPrimitive.content)
        // 既存ヘルパの leaf に operator/value の boolean 誤型が紛れていないこと
        assertTrue(inner.containsKey("operator"))
        assertTrue(inner.containsKey("value"))
        // 念のため: leaf は object でラップされている（文字列 boolean 変換で例外が出ないことを確認）
        val opContent = inner["operator"]!!.jsonPrimitive.content
        assertEquals("is", opContent)
    }
}
