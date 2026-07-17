package com.ptchess.club.data.remote;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Consumes the Lichess TV feed — a live NDJSON stream of the currently featured
 * top game (the "DGT board" of Lichess). Each line is a position update; the
 * watcher forwards the FEN, last move and player names to a {@link Listener}.
 *
 * <p>Runs on a caller-provided background thread. Call {@link #stop()} to end
 * the stream (e.g. when the Watch screen is left).</p>
 */
public final class LichessTv {

    public interface Listener {
        void onPosition(String fen, String lastMove, String white, String black);
        void onError();
    }

    private static final String FEED = "https://lichess.org/api/tv/feed";

    private volatile boolean cancelled;
    private volatile HttpURLConnection conn;

    public void stop() {
        cancelled = true;
        HttpURLConnection c = conn;
        if (c != null) c.disconnect();
    }

    public void stream(Listener listener) {
        String white = "";
        String black = "";
        try {
            URL url = new URL(FEED);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Accept", "application/x-ndjson");
            conn.setRequestProperty("User-Agent", "PTChessClub/1.0");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(0); // streaming: no read timeout
            if (conn.getResponseCode() != 200) {
                listener.onError();
                return;
            }
            try (InputStream in = conn.getInputStream()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while (!cancelled && (line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        JSONObject o = new JSONObject(line);
                        JSONObject d = o.optJSONObject("d");
                        if (d == null) continue;

                        if ("featured".equals(o.optString("t"))) {
                            JSONArray players = d.optJSONArray("players");
                            if (players != null) {
                                for (int i = 0; i < players.length(); i++) {
                                    JSONObject p = players.getJSONObject(i);
                                    JSONObject u = p.optJSONObject("user");
                                    String nm = u != null ? u.optString("name", "") : "";
                                    int rating = p.optInt("rating", 0);
                                    String label = rating > 0 ? nm + " (" + rating + ")" : nm;
                                    if ("white".equals(p.optString("color"))) white = label;
                                    else if ("black".equals(p.optString("color"))) black = label;
                                }
                            }
                        }

                        String fen = d.optString("fen", "");
                        String lm = d.optString("lm", d.optString("lastMove", ""));
                        if (!fen.isEmpty()) listener.onPosition(fen, lm, white, black);
                    } catch (Exception ignore) {
                        // skip a malformed line, keep streaming
                    }
                }
            }
        } catch (Exception e) {
            if (!cancelled) listener.onError();
        } finally {
            HttpURLConnection c = conn;
            if (c != null) c.disconnect();
        }
    }
}
