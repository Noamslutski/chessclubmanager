package com.ptchess.club.data.remote;

import com.ptchess.club.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Searches the federation players database via the parse.bot scraper API.
 *
 * <p>The API key is read from {@link BuildConfig#PARSE_API_KEY}, which is supplied
 * at build time from a Gradle property and is <b>never committed</b> — a client
 * app cannot safely embed a shared secret, so a production build should proxy
 * this call through the club backend instead of calling parse.bot directly.</p>
 */
public final class ParseBotApi {

    private static final String ENDPOINT =
            "https://api.parse.bot/scraper/1d0d4b3a-a30d-4490-98fd-a89692c0c3e6/search_players";

    public static class FoundPlayer {
        public final String externalId;
        public final String name;
        public final int rating;
        public final String federation;
        FoundPlayer(String externalId, String name, int rating, String federation) {
            this.externalId = externalId;
            this.name = name;
            this.rating = rating;
            this.federation = federation;
        }
    }

    private ParseBotApi() { }

    public static boolean hasApiKey() {
        return BuildConfig.PARSE_API_KEY != null && !BuildConfig.PARSE_API_KEY.isEmpty();
    }

    public static List<FoundPlayer> searchPlayers(String name) {
        List<FoundPlayer> out = new ArrayList<>();
        if (!hasApiKey() || name == null || name.trim().isEmpty()) return out;

        HttpURLConnection conn = null;
        try {
            String q = URLEncoder.encode(name.trim(), "UTF-8");
            URL url = new URL(ENDPOINT + "?name=" + q);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-API-Key", BuildConfig.PARSE_API_KEY);
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(12_000);
            conn.setReadTimeout(12_000);
            if (conn.getResponseCode() != 200) return out;

            String body = read(conn.getInputStream());
            parseInto(body, out);
        } catch (Exception e) {
            return out;
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    /** Defensive parse: accepts a bare array or an object wrapping a list. */
    private static void parseInto(String body, List<FoundPlayer> out) {
        try {
            JSONArray arr = asArray(body);
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = firstString(o, "name", "full_name", "player", "fullName");
                if (name == null || name.isEmpty()) continue;
                String id = firstString(o, "id", "player_id", "fide_id", "fideId", "playerId");
                if (id == null) id = name; // fall back so dedup still has a key
                int rating = firstInt(o, "rating", "elo", "fide_rating", "std", "standard");
                String fed = firstString(o, "federation", "fed", "country", "nation");
                out.add(new FoundPlayer(id, name, rating, fed == null ? "" : fed));
            }
        } catch (Exception ignored) {
        }
    }

    private static JSONArray asArray(String body) throws Exception {
        body = body.trim();
        if (body.startsWith("[")) return new JSONArray(body);
        JSONObject root = new JSONObject(body);
        for (String key : new String[]{"players", "results", "data", "items"}) {
            JSONArray a = root.optJSONArray(key);
            if (a != null) return a;
        }
        return null;
    }

    private static String firstString(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, null);
            if (v != null && !v.isEmpty() && !"null".equals(v)) return v;
        }
        return null;
    }

    private static int firstInt(JSONObject o, String... keys) {
        for (String k : keys) {
            int v = o.optInt(k, Integer.MIN_VALUE);
            if (v != Integer.MIN_VALUE) return v;
        }
        return 0;
    }

    private static String read(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
