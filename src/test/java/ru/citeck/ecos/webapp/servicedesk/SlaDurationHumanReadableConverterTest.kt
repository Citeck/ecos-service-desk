package ru.citeck.ecos.webapp.servicedesk

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.webapp.servicedesk.domain.sla.converter.SlaDurationHumanReadableConverter
import kotlin.time.Duration

class SlaDurationHumanReadableConverterTest {

    @Test
    fun `empty duration to human readable`() {
        val duration = Duration.ZERO

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("0M")
    }

    @Test
    fun `seconds to human readable`() {
        val duration = Duration.parse("PT5S")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("0M")
    }

    @Test
    fun `minutes to human readable`() {
        val duration = Duration.parse("PT5M")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("5M")
    }

    @Test
    fun `minutes and seconds to human readable`() {
        val duration = Duration.parse("PT5M30S")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("5M")
    }

    @Test
    fun `hours to human readable`() {
        val duration = Duration.parse("PT5H")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("5H")
    }

    @Test
    fun `hours and minutes to human readable`() {
        val duration = Duration.parse("PT5H30M")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("5H 30M")
    }

    @Test
    fun `hours, minutes and seconds to human readable`() {
        val duration = Duration.parse("PT5H30M45S")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("5H 30M")
    }

    @Test
    fun `days to human readable`() {
        val duration = Duration.parse("P5D")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("120H")
    }

    @Test
    fun `days and hours to human readable`() {
        val duration = Duration.parse("P5DT5H")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("125H")
    }

    @Test
    fun `days, hours and minutes to human readable`() {
        val duration = Duration.parse("P5DT5H30M")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("125H 30M")
    }


    @Test
    fun `days, hours, minutes and seconds to human readable`() {
        val duration = Duration.parse("P5DT5H30M45S")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("125H 30M")
    }

    @Test
    fun `negative seconds to human readable`() {
        val duration = Duration.parse("-PT5S")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("0M")
    }

    @Test
    fun `negative minutes to human readable`() {
        val duration = Duration.parse("-PT5M")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("-5M")
    }

    @Test
    fun `negative hours to human readable`() {
        val duration = Duration.parse("-PT5H")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("-5H")
    }

    @Test
    fun `negative days to human readable`() {
        val duration = Duration.parse("-P5D")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("-120H")
    }

    @Test
    fun `negative days, hours and minutes to human readable`() {
        val duration = Duration.parse("-P2DT3H15M")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("-51H 15M")
    }

    @Test
    fun `negative days, hours, minutes and seconds to human readable`() {
        val duration = Duration.parse("-P1DT2H30M45S")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("-26H 30M")
    }

    @Test
    fun `negative hours, minutes and seconds to human readable`() {
        val duration = Duration.parse("-PT4H45M30S")

        assertThat(SlaDurationHumanReadableConverter.convert(duration)).isEqualTo("-4H 45M")
    }

}
