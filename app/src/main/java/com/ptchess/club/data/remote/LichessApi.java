package com.ptchess.club.data.remote;

import com.ptchess.club.chess.ChessBoard;
import com.ptchess.club.chess.PgnImporter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fetches puzzles from the public Lichess API and converts them to the app's
 * {@code (fen, solutionUci, level)} shape.
 *
 * <p>The API returns a game plus the puzzle metadata. We replay the game's SAN
 * up to the puzzle position, serialize the FEN, and keep Lichess's UCI solution.
 * The puzzle's Glicko rating is mapped to a 1–5 difficulty level.</p>
 *
 * <p>Runs on a background thread only (network). Requires INTERNET permission.</p>
 */
public final class LichessApi {

    private static final String DAILY = "https://lichess.org/api/puzzle/daily";
    private static final String NEXT = "https://lichess.org/api/puzzle/next";

    /** Difficulty values accepted by /api/puzzle/next. */
    public static final String[] DIFFICULTIES =
            {"easiest", "easier", "normal", "harder", "hardest"};

    public static class RemotePuzzle {
        public final String id;
        public final String title;
        public final int level;
        public final String fen;
        public final String solutionUci;
        RemotePuzzle(String id, String title, int level, String fen, String solutionUci) {
            this.id = id;
            this.title = title;
            this.level = level;
            this.fen = fen;
            this.solutionUci = solutionUci;
        }
    }

    private LichessApi() { }

    /** Fetches up to {@code count} distinct puzzles at the given difficulty. */
    public static List<RemotePuzzle> fetchBatch(String difficulty, int count) {
        List<RemotePuzzle> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        RemotePuzzle daily = parse(httpGet(DAILY));
        if (daily != null && seen.add(daily.id)) out.add(daily);

        String url = difficulty != null ? NEXT + "?difficulty=" + difficulty : NEXT;
        for (int i = 0; i < count; i++) {
            RemotePuzzle p = parse(httpGet(url));
            if (p != null && seen.add(p.id)) out.add(p);
        }
        return out;
    }

    private static RemotePuzzle parse(String json) {
        if (json == null) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject game = root.getJSONObject("game");
            JSONObject puzzle = root.getJSONObject("puzzle");

            String pgn = game.optString("pgn", "").trim();
            String id = puzzle.optString("id", "");
            int rating = puzzle.optInt("rating", 1500);
            int initialPly = puzzle.optInt("initialPly", 0);

            JSONArray sol = puzzle.getJSONArray("solution");
            if (pgn.isEmpty() || sol.length() == 0) return null;
            List<String> solution = new ArrayList<>();
            for (int i = 0; i < sol.length(); i++) solution.add(sol.getString(i));

            String fen = reconstructFen(pgn, initialPly, solution.get(0));
            if (fen == null) return null;

            String title = "Lichess • " + rating;
            return new RemotePuzzle(id, title, levelForRating(rating),
                    fen, String.join(" ", solution));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Replays the game to the puzzle position. Lichess sets {@code initialPly} to
     * the number of plies before the puzzle; we replay that many, then—if the
     * first solution move is not for the side to move—advance one extra ply so
     * the position always has the solver on move (robust to off-by-one).
     */
    private static String reconstructFen(String pgn, int initialPly, String firstSolution) {
        String[] tokens = pgn.split("\\s+");
        ChessBoard board = ChessBoard.fromFen(PgnImporter.START_FEN);

        int ply = 0;
        for (; ply < initialPly && ply < tokens.length; ply++) {
            String uci = PgnImporter.sanToUci(board, tokens[ply]);
            if (uci == null) return null;
            board.applyUci(uci);
        }

        if (!ownsFromSquare(board, firstSolution) && ply < tokens.length) {
            String uci = PgnImporter.sanToUci(board, tokens[ply]);
            if (uci != null) board.applyUci(uci);
        }
        return board.toFen();
    }

    private static boolean ownsFromSquare(ChessBoard board, String uci) {
        if (uci == null || uci.length() < 2) return false;
        int fromCol = uci.charAt(0) - 'a';
        int fromRow = 8 - (uci.charAt(1) - '0');
        if (fromRow < 0 || fromRow > 7 || fromCol < 0 || fromCol > 7) return false;
        char p = board.pieceAt(fromRow, fromCol);
        return (board.isWhiteToMove() && ChessBoard.isWhite(p))
                || (!board.isWhiteToMove() && ChessBoard.isBlack(p));
    }

    private static int levelForRating(int rating) {
        if (rating < 1200) return 1;
        if (rating < 1500) return 2;
        if (rating < 1800) return 3;
        if (rating < 2100) return 4;
        return 5;
    }

    private static String httpGet(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "PTChessClub/1.0");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            int code = conn.getResponseCode();
            if (code != 200) return null;
            try (InputStream in = conn.getInputStream()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
