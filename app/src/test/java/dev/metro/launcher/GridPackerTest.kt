package dev.metro.launcher

import dev.metro.launcher.data.GridPacker
import dev.metro.launcher.data.HomeTileItem
import dev.metro.launcher.data.InternalWidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPackerTest {

    @Test
    fun testPackGridPreservesValidPositions() {
        val clock = HomeTileItem.InternalWidget(
            id = "clock",
            type = InternalWidgetType.CLOCK,
            colSpan = 2,
            rowSpan = 2,
            col = 0,
            row = 0,
        )
        val weather = HomeTileItem.InternalWidget(
            id = "weather",
            type = InternalWidgetType.WEATHER,
            colSpan = 2,
            rowSpan = 2,
            col = 2,
            row = 0,
        )
        val packed = GridPacker.packGrid(listOf(clock, weather))
        assertEquals(0, packed[0].col)
        assertEquals(0, packed[0].row)
        assertEquals(2, packed[1].col)
        assertEquals(0, packed[1].row)
    }

    @Test
    fun testPackGridPacksUnpositionedTiles() {
        val clock = HomeTileItem.InternalWidget(
            id = "clock",
            type = InternalWidgetType.CLOCK,
            colSpan = 2,
            rowSpan = 2,
        )
        val weather = HomeTileItem.InternalWidget(
            id = "weather",
            type = InternalWidgetType.WEATHER,
            colSpan = 2,
            rowSpan = 2,
        )
        val phone = HomeTileItem.AppPin(
            id = "phone",
            packageName = "dialer",
            colSpan = 1,
            rowSpan = 1,
        )
        val packed = GridPacker.packGrid(listOf(clock, weather, phone))
        assertEquals(0, packed[0].col)
        assertEquals(0, packed[0].row)

        assertEquals(2, packed[1].col)
        assertEquals(0, packed[1].row)

        assertEquals(0, packed[2].col)
        assertEquals(2, packed[2].row)
    }

    @Test
    fun testRectHitsDetection() {
        val clock = HomeTileItem.InternalWidget(
            id = "clock",
            type = InternalWidgetType.CLOCK,
            colSpan = 2,
            rowSpan = 2,
            col = 0,
            row = 0,
        )
        val tiles = listOf(clock)

        // Hits inside (0, 0, 2x2)
        assertNotNull(GridPacker.rectHits(tiles, 0, 0, 1, 1))
        assertNotNull(GridPacker.rectHits(tiles, 1, 1, 1, 1))

        // Skips own id
        assertNull(GridPacker.rectHits(tiles, 0, 0, 1, 1, skipId = "clock"))

        // Does not hit outside (col 2, row 0)
        assertNull(GridPacker.rectHits(tiles, 2, 0, 1, 1))
        assertNull(GridPacker.rectHits(tiles, 0, 2, 1, 1))
    }

    @Test
    fun testEvalDropIntoEmptySpaceMovesDirectly() {
        // Layout with a hole:
        // Row 0: Clock (0, 0, 2x2), Weather (2, 0, 2x2)
        // Row 2: Notes (0, 2, 2x2), Phone (2, 2, 1x1), Messages (3, 2, 1x1)
        // Notice: at Row 3, (2, 3) and (3, 3) are completely EMPTY!
        val clock = HomeTileItem.InternalWidget(id = "clock", type = InternalWidgetType.CLOCK, colSpan = 2, rowSpan = 2, col = 0, row = 0)
        val weather = HomeTileItem.InternalWidget(id = "weather", type = InternalWidgetType.WEATHER, colSpan = 2, rowSpan = 2, col = 2, row = 0)
        val notes = HomeTileItem.InternalWidget(id = "notes", type = InternalWidgetType.NOTES, colSpan = 2, rowSpan = 2, col = 0, row = 2)
        val phone = HomeTileItem.AppPin(id = "phone", packageName = "dialer", colSpan = 1, rowSpan = 1, col = 2, row = 2)
        val messages = HomeTileItem.AppPin(id = "messages", packageName = "sms", colSpan = 1, rowSpan = 1, col = 3, row = 2)

        val tiles = listOf(clock, weather, notes, phone, messages)

        // User drags messages down into the hole at col = 3, row = 3
        val decision = GridPacker.evalDrop(tiles, "messages", 3, 3)
        assertTrue(decision is GridPacker.DropDecision.Move)
        val move = decision as GridPacker.DropDecision.Move
        assertEquals("messages", move.id)
        assertEquals(3, move.col)
        assertEquals(3, move.row)
    }

    @Test
    fun testEvalDropOntoSameSizeSwaps() {
        val phone = HomeTileItem.AppPin(id = "phone", packageName = "dialer", colSpan = 1, rowSpan = 1, col = 0, row = 0)
        val messages = HomeTileItem.AppPin(id = "messages", packageName = "sms", colSpan = 1, rowSpan = 1, col = 1, row = 0)

        val tiles = listOf(phone, messages)

        // Drop phone onto messages (1, 0)
        val decision = GridPacker.evalDrop(tiles, "phone", 1, 0)
        assertTrue(decision is GridPacker.DropDecision.Swap)
        val swap = decision as GridPacker.DropDecision.Swap
        assertEquals("phone", swap.idA)
        assertEquals("messages", swap.idB)
    }

    @Test
    fun testPackGridWith2x1BelowTwo1x1() {
        // Two 1x1 tiles in row 0: (0,0) and (1,0)
        val p1 = HomeTileItem.AppPin(id = "p1", packageName = "p1", colSpan = 1, rowSpan = 1, col = 0, row = 0)
        val p2 = HomeTileItem.AppPin(id = "p2", packageName = "p2", colSpan = 1, rowSpan = 1, col = 1, row = 0)
        // A 2x2 widget filling columns 2..3 in row 0 and 1
        val widget = HomeTileItem.InternalWidget(id = "widget", type = InternalWidgetType.CLOCK, colSpan = 2, rowSpan = 2, col = 2, row = 0)
        // A 2x1 widget placed right below p1 and p2 at col 0, row 1
        val w2x1 = HomeTileItem.InternalWidget(id = "w2x1", type = InternalWidgetType.NOTES, colSpan = 2, rowSpan = 1, col = 0, row = 1)

        val packed = GridPacker.packGrid(listOf(p1, p2, widget, w2x1))
        val placed2x1 = packed.find { it.id == "w2x1" }
        assertNotNull(placed2x1)
        assertEquals(0, placed2x1?.col)
        assertEquals(1, placed2x1?.row)
    }

    @Test
    fun testNearestFreeFor2x1BelowTwo1x1() {
        val p1 = HomeTileItem.AppPin(id = "p1", packageName = "p1", colSpan = 1, rowSpan = 1, col = 0, row = 0)
        val p2 = HomeTileItem.AppPin(id = "p2", packageName = "p2", colSpan = 1, rowSpan = 1, col = 1, row = 0)
        val widget = HomeTileItem.InternalWidget(id = "widget", type = InternalWidgetType.CLOCK, colSpan = 2, rowSpan = 2, col = 2, row = 0)

        val tiles = listOf(p1, p2, widget)
        // (0,1) and (1,1) are free, target is (0,1) with span (2,1)
        val spot = GridPacker.nearestFree(tiles, colSpan = 2, rowSpan = 1, gcx = 0, gry = 1)
        assertNotNull(spot)
        assertEquals(0, spot?.first)
        assertEquals(1, spot?.second)
    }

    @Test
    fun testEvalDrop2x1OntoTwo1x1TilesMoves() {
        val p1 = HomeTileItem.AppPin(id = "p1", packageName = "p1", colSpan = 1, rowSpan = 1, col = 0, row = 0)
        val p2 = HomeTileItem.AppPin(id = "p2", packageName = "p2", colSpan = 1, rowSpan = 1, col = 1, row = 0)
        val w2x1 = HomeTileItem.InternalWidget(id = "w2x1", type = InternalWidgetType.NOTES, colSpan = 2, rowSpan = 1, col = 0, row = 2)

        val tiles = listOf(p1, p2, w2x1)
        val decision = GridPacker.evalDrop(tiles, "w2x1", targetCol = 0, targetRow = 0)
        assertTrue(decision is GridPacker.DropDecision.Move)
        val move = decision as GridPacker.DropDecision.Move
        assertEquals("w2x1", move.id)
        assertEquals(0, move.col)
        assertEquals(0, move.row)
    }

    @Test
    fun testPreviewDropSameSizeSwapsPositions() {
        val phone = HomeTileItem.AppPin(id = "phone", packageName = "dialer", colSpan = 1, rowSpan = 1, col = 0, row = 0)
        val messages = HomeTileItem.AppPin(id = "messages", packageName = "sms", colSpan = 1, rowSpan = 1, col = 1, row = 0)
        val tiles = listOf(phone, messages)

        val preview = GridPacker.previewDrop(tiles, "phone", targetCol = 1, targetRow = 0)
        val prevPhone = preview.find { it.id == "phone" }
        val prevMessages = preview.find { it.id == "messages" }

        assertEquals(1, prevPhone?.col)
        assertEquals(0, prevPhone?.row)
        assertEquals(0, prevMessages?.col)
        assertEquals(0, prevMessages?.row)
    }

    @Test
    fun testPreviewDropSamePositionReturnsOriginal() {
        val phone = HomeTileItem.AppPin(id = "phone", packageName = "dialer", colSpan = 1, rowSpan = 1, col = 0, row = 0)
        val messages = HomeTileItem.AppPin(id = "messages", packageName = "sms", colSpan = 1, rowSpan = 1, col = 1, row = 0)
        val tiles = listOf(phone, messages)

        val preview = GridPacker.previewDrop(tiles, "phone", targetCol = 0, targetRow = 0)
        val prevPhone = preview.find { it.id == "phone" }
        val prevMessages = preview.find { it.id == "messages" }

        assertEquals(0, prevPhone?.col)
        assertEquals(0, prevPhone?.row)
        assertEquals(1, prevMessages?.col)
        assertEquals(0, prevMessages?.row)
    }

    @Test
    fun testPreviewDropDifferentSizeRelocatesColliding() {
        val p1 = HomeTileItem.AppPin(id = "p1", packageName = "p1", colSpan = 1, rowSpan = 1, col = 0, row = 0)
        val p2 = HomeTileItem.AppPin(id = "p2", packageName = "p2", colSpan = 1, rowSpan = 1, col = 1, row = 0)
        val w2x1 = HomeTileItem.InternalWidget(id = "w2x1", type = InternalWidgetType.NOTES, colSpan = 2, rowSpan = 1, col = 0, row = 2)
        val tiles = listOf(p1, p2, w2x1)

        val preview = GridPacker.previewDrop(tiles, "w2x1", targetCol = 0, targetRow = 0)
        val prevW = preview.find { it.id == "w2x1" }
        assertEquals(0, prevW?.col)
        assertEquals(0, prevW?.row)

        val prevP1 = preview.find { it.id == "p1" }
        val prevP2 = preview.find { it.id == "p2" }
        assertNotNull(prevP1)
        assertNotNull(prevP2)
        // Both p1 and p2 must have non-null, valid coordinates that don't collide with w2x1 (row 0, col 0..1)
        assertTrue((prevP1?.row ?: 0) >= 1 || (prevP1?.col ?: 0) >= 2)
        assertTrue((prevP2?.row ?: 0) >= 1 || (prevP2?.col ?: 0) >= 2)
    }
}


