package com.ptchess.club.data.remote;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads official OTB broadcasts (relays) from the public Lichess Broadcast API:
 * {@code GET /api/broadcast} for the tournament + round list, and
 * {@code GET /api/broadcast/round/{id}.pgn} for a round's live games PGN.
 *
 * <p>All calls are blocking and must run on a background thread.</p>
 */
public final class LichessBroadcast {

    private static final String LIST = "https://lichess.org/api/broadcast?nb=30";
    private static final String ROUND_PGN = "https://lichess.org/api/broadcast/round/";

    public static class Round {
        public final String id;
        public final String name;
        public final boolean ongoing;
        Round(String id, String name, boolean ongoing) {
            this.id = id;
            this.name = name;
            this.ongoing = ongoing;
        }
    }

    public static class Broadcast {
        public final String name;
        public final List<Round> rounds = new ArrayList<>();
        Broadcast(String name) {
            this.name = name;
        }
        /** The round to open by default: the ongoing one, else the last. */
        public Round currentRound() {
            for (Round r : rounds) if (r.ongoing) return r;
            return rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
        }
    }

    private LichessBroadcast() { }

    public static List<Broadcast> list() {
        List<Broadcast> out = new ArrayList<>();
        String body = httpGet(LIST, "application/x-ndjson");
        if (body == null) return out;
        for (String line : body.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            try {
                JSONObject o = new JSONObject(line);
                JSONObject tour = o.optJSONObject("tour");
                if (tour == null) continue;
                Broadcast b = new Broadcast(tour.optString("name", "Broadcast"));
                JSONArray rounds = o.optJSONArray("rounds");
                if (rounds != null) {
                    for (int i = 0; i < rounds.length(); i++) {
                        JSONObject r = rounds.getJSONObject(i);
                        String id = r.optString("id", "");
                        if (id.isEmpty()) continue;
                        b.rounds.add(new Round(id, r.optString("name", "Round"),
                                r.optBoolean("ongoing", false)));
                    }
                }
                if (!b.rounds.isEmpty()) out.add(b);
            } catch (Exception ignore) {
                // skip a malformed line
            }
        }
        return out;
    }

    public static String roundPgn(String roundId) {
        return httpGet(ROUND_PGN + roundId + ".pgn", "application/x-chess-pgn");
    }

    private static String httpGet(String urlString, String accept) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", accept);
            conn.setRequestProperty("User-Agent", "PTChessClub/1.0");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            if (conn.getResponseCode() != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
