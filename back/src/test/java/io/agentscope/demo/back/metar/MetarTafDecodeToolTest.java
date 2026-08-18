package io.agentscope.demo.back.metar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the agentscope tool wrapper {@link MetarTafDecodeTool}.
 *
 * <p>Guarantees the tool exposed to the ReActAgent returns the human-readable
 * decoding produced by the (tested) {@link MetarTafDecoder} rules, and degrades
 * gracefully on unrecognised input instead of throwing.</p>
 */
class MetarTafDecodeToolTest {

    private final MetarTafDecodeTool tool = new MetarTafDecodeTool();

    @Test
    void decodesViaUnderlyingDecoder() {
        String result = tool.decode("LFPG 181500Z 24012KT 9999 SCT040 24/15 Q1015 NOSIG");

        assertThat(result)
            .contains("Aéroport : LFPG")
            .contains("Vent : 240° / 12 kt")
            .contains("Pression (QNH) : 1015 hPa")
            .contains("Tendance : NOSIG");
    }

    @Test
    void handlesUnrecognisedBulletinGracefully() {
        String result = tool.decode("hello world foo bar");

        assertThat(result).contains("Bulletin non reconnu");
    }

    @Test
    void handlesNullAndBlankBulletin() {
        assertThat(tool.decode(null)).contains("Bulletin non reconnu");
        assertThat(tool.decode("   ")).contains("Bulletin non reconnu");
    }
}
