package com.ptchess.club.data.remote;

import com.ptchess.club.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists federation tournaments via the parse.bot scraper API
 * ({@code POST /scraper/.../list_tournaments} with {@code {year, month}}).
 *
 * <p>The response shape is parsed defensively (fields vary), and the API key
 * comes from {@link BuildConfig#PARSE_API_KEY} — supplied at build time and
 * never committed. A production build should proxy this through the backend.</p>
 */
public final class FederationApi {

    private static final String ENDPOINT =
            "https://api.parse.bot/scraper/4bff514a-a012-496d-bc59-a96e48e14647/list_tournaments";

    public static class FedTournament {
        public String name = "";
        public String date = "";
        public String location = "";
        public String timeControl = "";
        public String organizer = "";
        public String info = "";
        public String link = "";
        public int minRating = 0;   // 0 = no floor
        public int maxRating = 0;   // 0 = no cap
        public int places = -1;     // -1 = unknown

        /** Whether a player of the given rating may register (0 rating = unknown → allowed). */
        public boolean eligible(int rating) {
            if (rating <= 0) return true;
            if (minRating > 0 && rating < minRating) return false;
            if (maxRating > 0 && rating > maxRating) return false;
            return true;
        }
    }

    private FederationApi() { }

    public static boolean hasApiKey() {
        return BuildConfig.PARSE_API_KEY != null && !BuildConfig.PARSE_API_KEY.isEmpty();
    }

    public static List<FedTournament> listTournaments(int year, int month) {
        List<FedTournament> out = new ArrayList<>();
        if (!hasApiKey()) return out;

        HttpURLConnection conn = null;
        try {
            URL url = new URL(ENDPOINT);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("X-API-Key", BuildConfig.PARSE_API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(15_000);

            String body = new JSONObject()
                    .put("year", String.valueOf(year))
                    .put("month", String.valueOf(month))
                    .toString();
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return out;
            parseInto(read(conn.getInputStream()), out);
        } catch (Exception e) {
            return out;
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    private static void parseInto(String body, List<FedTournament> out) {
        try {
            JSONArray arr = asArray(body);
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                FedTournament t = new FedTournament();
                t.name = firstString(o, "name", "title", "tournament", "event");
                if (t.name.isEmpty()) continue;
                t.date = firstString(o, "date", "dates", "start_date", "startDate", "when");
                t.location = firstString(o, "location", "place", "city", "venue", "hall");
                t.timeControl = firstString(o, "time_control", "timeControl", "rate", "type", "tempo");
                t.organizer = firstString(o, "organizer", "organiser", "host", "club", "arranger");
                t.info = firstString(o, "info", "requirements", "notes", "category", "description");
                t.link = firstString(o, "link", "url", "registration", "register_url", "href");
                t.minRating = firstInt(o, "min_rating", "rating_min", "from_rating", "minRating", "min");
                t.maxRating = firstInt(o, "max_rating", "rating_max", "to_rating", "maxRating", "max");
                t.places = o.has("places") ? o.optInt("places", -1)
                        : o.optInt("remaining", o.optInt("spots", -1));
                out.add(t);
            }
        } catch (Exception ignored) {
        }
    }

    private static JSONArray asArray(String body) throws Exception {
        body = body.trim();
        if (body.startsWith("[")) return new JSONArray(body);
        JSONObject root = new JSONObject(body);
        for (String key : new String[]{"tournaments", "results", "data", "items", "list"}) {
            JSONArray a = root.optJSONArray(key);
            if (a != null) return a;
        }
        return null;
    }

    private static String firstString(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, null);
            if (v != null && !v.isEmpty() && !"null".equals(v)) return v.trim();
        }
        return "";
    }

    private static int firstInt(JSONObject o, String... keys) {
        for (String k : keys) {
            int v = o.optInt(k, Integer.MIN_VALUE);
            if (v != Integer.MIN_VALUE) return v;
            // some feeds send numbers as strings
            String s = o.optString(k, "");
            if (s.matches("\\d{3,4}")) return Integer.parseInt(s);
        }
        return 0;
    }

    private static String read(InputStream in) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
