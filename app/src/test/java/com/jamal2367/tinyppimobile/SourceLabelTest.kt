package com.jamal2367.tinyppimobile

import com.jamal2367.tinyppimobile.data.model.InfoGroup
import com.jamal2367.tinyppimobile.data.model.InfoRowData
import com.jamal2367.tinyppimobile.data.model.MetadataRow
import com.jamal2367.tinyppimobile.data.model.Snapshot
import com.jamal2367.tinyppimobile.util.SourceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the source badge can work out about a Dolby Vision file.
 *
 * The profile is not a field of its own in a snapshot - it arrives as one of
 * the rows the overlay prints, under whatever name the box gives it - so what
 * is tested here is the reading of those rows, in each of the shapes they have
 * been seen in.
 */
class SourceLabelTest {

    @Test
    fun `a profile is read out of the row that names it`() {
        val snapshot = withReadings("DV Profile" to "8.1")

        assertEquals("P8.1", SourceLabel.dolbyVisionSuffix(snapshot))
    }

    @Test
    fun `a profile written out in words gives up its number`() {
        val snapshot = withReadings("Dolby Vision profile" to "Profile 5")

        assertEquals("P5", SourceLabel.dolbyVisionSuffix(snapshot))
    }

    @Test
    fun `a seven is two layers whether or not a row says so`() {
        // Profile 7 is a base layer and an enhancement layer by definition, and
        // a box that prints the number has said so without a second row.
        val snapshot = withReadings("Profile" to "7.6")

        assertEquals("P7.6 EL", SourceLabel.dolbyVisionSuffix(snapshot))
    }

    @Test
    fun `the layer is named the way the box names it`() {
        // Full or minimal is the difference between a second stream's worth of
        // picture and a placeholder, and the badge should not flatten the two.
        val full = withReadings("Profile" to "7.6", "Layers" to "BL+EL+RPU (FEL)")
        val minimal = withReadings("Profile" to "7.6", "Layers" to "BL+EL+RPU (MEL)")

        assertEquals("P7.6 FEL", SourceLabel.dolbyVisionSuffix(full))
        assertEquals("P7.6 MEL", SourceLabel.dolbyVisionSuffix(minimal))
    }

    @Test
    fun `a layer named in the row's own title is read too`() {
        val snapshot = withReadings("Profile" to "7", "EL type" to "fel")

        assertEquals("P7 FEL", SourceLabel.dolbyVisionSuffix(snapshot))
    }

    @Test
    fun `three letters inside a word are not a layer`() {
        val snapshot = withReadings("Profile" to "8.1", "Codec" to "HEVC Main10 SHUFFEL")

        assertEquals("P8.1", SourceLabel.dolbyVisionSuffix(snapshot))
    }

    @Test
    fun `a composition written out is read as it stands`() {
        val snapshot = withReadings("Profile" to "8.1", "Layers" to "BL+EL+RPU")

        assertEquals("P8.1 EL", SourceLabel.dolbyVisionSuffix(snapshot))
    }

    @Test
    fun `a row that asks the question and answers yes counts`() {
        val snapshot = withReadings("Profile" to "8.1", "Enhancement layer" to "Yes")

        assertEquals("P8.1 EL", SourceLabel.dolbyVisionSuffix(snapshot))
    }

    @Test
    fun `a row that asks the question and answers no does not`() {
        val snapshot = withReadings("Profile" to "8.1", "Enhancement layer" to "No")

        assertEquals("P8.1", SourceLabel.dolbyVisionSuffix(snapshot))
    }

    @Test
    fun `the metadata view is read as well as the printed readings`() {
        val snapshot = Snapshot(
            metadata = listOf(MetadataRow(name = "Profile", value = "7.6")),
        )

        assertEquals("P7.6 EL", SourceLabel.dolbyVisionSuffix(snapshot))
    }

    @Test
    fun `a box that printed nothing to read is not guessed at`() {
        assertNull(SourceLabel.dolbyVisionSuffix(Snapshot()))
        assertNull(SourceLabel.dolbyVisionSuffix(withReadings("Bit depth" to "10")))
        // A row named for the profile with nothing in it is not a profile.
        assertNull(SourceLabel.dolbyVisionSuffix(withReadings("Profile" to "–")))
    }

    private fun withReadings(vararg rows: Pair<String, String>) = Snapshot(
        groups = listOf(
            InfoGroup(
                id = "video",
                title = "Video",
                rows = rows.map { (label, value) -> InfoRowData(label = label, value = value) },
            ),
        ),
    )
}
