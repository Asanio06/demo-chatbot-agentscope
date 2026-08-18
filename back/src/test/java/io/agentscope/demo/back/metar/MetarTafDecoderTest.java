package io.agentscope.demo.back.metar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MetarTafDecoder}.
 *
 * <p>Covers the pure business logic of decoding a raw METAR/TAF bulletin into a
 * structured human-readable summary (TDD RED first, then GREEN implementation).</p>
 */
class MetarTafDecoderTest {

    private final MetarTafDecoder decoder = new MetarTafDecoder();

    @Test
    void decodesWindDirectionAndSpeed() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 SCT040 24/15 Q1015 NOSIG");

        assertThat(decoding.wind()).isEqualTo("240° / 12 kt");
    }

    @Test
    void decodesWindGustsWhenPresent() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24018G30KT 9999 SCT040 24/15 Q1015");

        assertThat(decoding.wind()).isEqualTo("240° / 18 kt (rafales 30 kt)");
    }

    @Test
    void decodesAirportAndIssueTime() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 SCT040 24/15 Q1015 NOSIG");

        assertThat(decoding.airport()).isEqualTo("LFPG");
        assertThat(decoding.issuedAt()).isEqualTo("jour 18 à 15:00 UTC");
    }

    @Test
    void decodesVisibilityTenKmPlus() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 SCT040 24/15 Q1015");

        assertThat(decoding.visibility()).isEqualTo("10 km+");
    }

    @Test
    void decodesVisibilityInMeters() {
        MetarDecoding decoding = decoder.decode("LFPO 181500Z 24012KT 4500 SCT040 24/15 Q1015");

        assertThat(decoding.visibility()).isEqualTo("4500 m");
    }

    @Test
    void decodesCloudLayersWithAltitude() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 FEW020 BKN040 24/15 Q1015");

        assertThat(decoding.clouds()).isEqualTo("FEW020 -> 2000 ft, BKN040 -> 4000 ft");
    }

    @Test
    void decodesTemperatureAndDewPoint() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 SCT040 24/15 Q1015");

        assertThat(decoding.temperatures()).isEqualTo("24°C / 15°C");
    }

    @Test
    void decodesNegativeTemperature() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 SCT040 M05/M10 Q1015");

        assertThat(decoding.temperatures()).isEqualTo("-05°C / -10°C");
    }

    @Test
    void decodesQnhPressure() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 SCT040 24/15 Q1015");

        assertThat(decoding.qnh()).isEqualTo("1015 hPa");
    }

    @Test
    void decodesPresentWeather() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 -RA SCT040 24/15 Q1015");

        assertThat(decoding.weather()).isEqualTo("-RA");
    }

    @Test
    void decodesTrendNosig() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 SCT040 24/15 Q1015 NOSIG");

        assertThat(decoding.trend()).isEqualTo("NOSIG");
    }

    @Test
    void producesReadableSummaryLines() {
        MetarDecoding decoding = decoder.decode("LFPG 181500Z 24012KT 9999 SCT040 24/15 Q1015 NOSIG");

        assertThat(decoding.summaryLines())
            .contains("Aéroport : LFPG")
            .contains("Vent : 240° / 12 kt")
            .contains("Pression (QNH) : 1015 hPa")
            .contains("Tendance : NOSIG");
    }

    @Test
    void rejectsEmptyBulletin() {
        assertThatThrownBy(() -> decoder.decode("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non reconnu");
    }

    @Test
    void rejectsGarbageBulletin() {
        assertThatThrownBy(() -> decoder.decode("hello world foo bar"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non reconnu");
    }

    @Test
    void rejectsNullBulletin() {
        assertThatThrownBy(() -> decoder.decode(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non reconnu");
    }
}
