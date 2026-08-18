package io.agentscope.demo.back.metar;

/**
 * Structured, human-readable decoding of a raw METAR/TAF bulletin.
 *
 * @param airport     ICAO airport code (ex. LFPG)
 * @param issuedAt    decoded issue day/time (ex. 18 at 15:00Z)
 * @param wind        decoded wind (ex. "240° / 12 kt")
 * @param visibility  decoded visibility (ex. "10 km+")
 * @param clouds      decoded cloud layers (ex. "SCT040 -> 4000 ft")
 * @param temperatures decoded temperature / dew point (ex. "24°C / 15°C")
 * @param qnh         decoded pressure QNH in hPa (ex. "1015 hPa")
 * @param weather     present-weather phenomena (ex. "-RA", may be empty)
 * @param trend       trend / NOSIG (ex. "NOSIG", may be empty)
 * @param raw         original raw bulletin
 */
public record MetarDecoding(
        String airport,
        String issuedAt,
        String wind,
        String visibility,
        String clouds,
        String temperatures,
        String qnh,
        String weather,
        String trend,
        String raw) {

    public String[] summaryLines() {
        return new String[]{
            "Aéroport : " + airport,
            "Heure : " + issuedAt,
            "Vent : " + wind,
            "Visibilité : " + visibility,
            "Nuages : " + clouds,
            "Température / point de rosée : " + temperatures,
            "Pression (QNH) : " + qnh,
            weather == null || weather.isBlank() ? "" : "Temps présent : " + weather,
            trend == null || trend.isBlank() ? "" : "Tendance : " + trend
        };
    }
}
