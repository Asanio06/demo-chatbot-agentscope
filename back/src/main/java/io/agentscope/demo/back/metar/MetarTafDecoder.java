package io.agentscope.demo.back.metar;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decodes a raw METAR/TAF bulletin into a structured, readable summary.
 *
 * <p>This is the domain logic of the aviation chatbot. It is a pure function of the
 * raw bulletin string and carries no external dependency (no model call), which makes
 * it trivially unit-testable.</p>
 *
 * <p>Recognized token formats (aviation meteorological shorthand):</p>
 * <ul>
 *   <li>Airport ICAO: {@code [A-Z]{4}} (ex. {@code LFPG})</li>
 *   <li>Issue time: {@code \d{6}Z} (ex. {@code 181500Z} = day 18, 15:00 UTC)</li>
 *   <li>Wind: {@code \d{3}(G\d{2})?(KT|MPS|KMH)} (ex. {@code 24018G30KT})</li>
 *   <li>Visibility: {@code \d{4}} (meters) or {@code 9999} (10km+)</li>
 *   <li>Clouds: {@code (FEW|SCT|BKN|OVC|VV)\d{3}} (ex. {@code SCT040})</li>
 *   <li>Temperature/dew point: {@code -?\d{2}/(-?\d{2})?} (ex. {@code 24/15})</li>
 *   <li>QNH / pressure: {@code Q\d{4}} (ex. {@code Q1015})</li>
 *   <li>Trend: {@code NOSIG|BECMG|TEMPO}</li>
 *   <li>Present weather: {@code [-+]?(TS|RA|SN|SH|FG|HZ|BR|DZ|GR|GS|MI|BC|DR|BL|VC)?...}</li>
 * </ul>
 */
public class MetarTafDecoder {

    private static final Pattern AIRPORT = Pattern.compile("\\b[A-Z]{4}\\b");
    private static final Pattern TIME = Pattern.compile("\\b(\\d{2})(\\d{2})(\\d{2})Z\\b");
    private static final Pattern WIND = Pattern.compile("\\b(\\d{3})(\\d{2,3})(?:G(\\d{2,3}))?(KT|MPS|KMH)\\b");
    private static final Pattern VISIBILITY = Pattern.compile("\\b(\\d{4})\\b");
    private static final Pattern CLOUD = Pattern.compile("\\b((?:FEW|SCT|BKN|OVC|VV)\\d{3})\\b");
    private static final Pattern TEMP = Pattern.compile("\\b((?:-?\\d{2}|M\\d{2})/(?:-?\\d{2}|M\\d{2}|/)?)\\b");
    private static final Pattern QNH = Pattern.compile("\\bQ(\\d{4})\\b");
    private static final Pattern TREND = Pattern.compile("\\b(NOSIG|BECMG|TEMPO)\\b");
    private static final Pattern WEATHER = Pattern.compile(
        "(?:^|\\s)([-+]?(?:VC)?(?:MI|BC|DR|BL|SH|TS|FZ|RA|SN|SG|IC|PL|GR|GS|UP|DZ|FG|BR|HZ|FU|VA|DU|SA|SQ|PO|FC|SS|DS){1,3})(?=\\s|$)");

    /**
     * Decodes the given raw bulletin.
     *
     * @param raw the raw METAR/TAF bulletin (never null)
     * @return the structured decoding
     * @throws IllegalArgumentException if the bulletin cannot be recognized
     */
    public MetarDecoding decode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Bulletin vide ou null : bulletin non reconnu");
        }

        Matcher airport = AIRPORT.matcher(raw);
        // first token is usually the airport; prefer the leading 4-letter group if present
        String airportCode = airport.find() ? airport.group() : null;

        // Time: DD HH MM Z -> "jour DD à HH:MM UTC"
        String issuedAt = null;
        Matcher time = TIME.matcher(raw);
        if (time.find()) {
            issuedAt = "jour " + time.group(1) + " à " + time.group(2) + ":" + time.group(3) + " UTC";
        }

        String wind = null;
        Matcher w = WIND.matcher(raw);
        if (w.find()) {
            String direction = w.group(1);
            String speed = w.group(2);
            String gust = w.group(3); // may be null
            String unit = w.group(4);
            String unitLabel = unitToLabel(unit);
            String gustPart = gust == null ? "" : " (rafales " + gust + " " + unitLabel + ")";
            wind = direction + "° / " + speed + " " + unitLabel + gustPart;
        }

        String visibility = null;
        Matcher vis = VISIBILITY.matcher(raw);
        if (vis.find()) {
            String v = vis.group(1);
            visibility = "9999".equals(v) || "CAVOK".equals(v) ? "10 km+" : v + " m";
        }
        if (raw.contains("CAVOK")) {
            visibility = "10 km+ (CAVOK)";
        }

        String clouds = null;
        List<String> cloudLayers = new ArrayList<>();
        Matcher c = CLOUD.matcher(raw);
        while (c.find()) {
            String layer = c.group(1);
            int alt = Integer.parseInt(layer.substring(3)) * 100;
            cloudLayers.add(layer + " -> " + alt + " ft");
        }
        if (!cloudLayers.isEmpty()) {
            clouds = String.join(", ", cloudLayers);
        } else if (raw.contains("SKC") || raw.contains("NSC") || raw.contains("CLR")) {
            clouds = "ciel clair";
        }

        String temp = null;
        Matcher t = TEMP.matcher(raw);
        if (t.find()) {
            String group = t.group(1);
            String[] parts = group.split("/");
            String tempPart = normalizeTemp(parts[0]);
            String dew = parts.length > 1 && !parts[1].isBlank() ? normalizeTemp(parts[1]) : "n/a";
            temp = tempPart + "°C / " + dew + "°C";
        }

        String qnh = null;
        Matcher q = QNH.matcher(raw);
        if (q.find()) {
            qnh = q.group(1) + " hPa";
        }

        String trend = null;
        Matcher tr = TREND.matcher(raw);
        if (tr.find()) {
            trend = tr.group(1);
        }

        String weather = null;
        Matcher wx = WEATHER.matcher(raw);
        if (wx.find()) {
            weather = wx.group(1);
        }

        if (airportCode == null && issuedAt == null && wind == null && qnh == null) {
            throw new IllegalArgumentException("Bulletin non reconnu : " + raw);
        }

        return new MetarDecoding(
            airportCode != null ? airportCode : "inconnu",
            issuedAt != null ? issuedAt : "inconnue",
            wind != null ? wind : "n/a",
            visibility != null ? visibility : "n/a",
            clouds != null ? clouds : "n/a",
            temp != null ? temp : "n/a",
            qnh != null ? qnh : "n/a",
            weather != null ? weather : "",
            trend != null ? trend : "",
            raw);
    }

    private static String unitToLabel(String unit) {
        return switch (unit) {
            case "MPS" -> "m/s";
            case "KMH" -> "km/h";
            default -> "kt";
        };
    }

    private static String normalizeTemp(String raw) {
        if (raw == null || raw.isBlank()) {
            return "n/a";
        }
        return raw.startsWith("M") ? "-" + raw.substring(1) : raw;
    }
}
