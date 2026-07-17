package com.ptchess.club.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal PGN reader that turns games into puzzles. Each game becomes a puzzle
 * whose starting position is the [FEN] tag (or the initial position) and whose
 * solution is the main line converted to UCI. SAN is resolved against
 * {@link ChessBoard}'s pseudo-legal move generator.
 *
 * <p>Scope: main line only; variations, comments and NAGs are skipped. Good
 * enough to import tactics exported from Lichess or ChessBase.</p>
 */
public final class PgnImporter {

    public static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public static class ParsedPuzzle {
        public final String title;
        public final String fen;
        public final String solutionUci;
        public ParsedPuzzle(String title, String fen, String solutionUci) {
            this.title = title;
            this.fen = fen;
            this.solutionUci = solutionUci;
        }
    }

    private static final Pattern TAG = Pattern.compile("\\[(\\w+)\\s+\"([^\"]*)\"\\]");

    private PgnImporter() { }

    public static List<ParsedPuzzle> parse(String pgnText) {
        List<ParsedPuzzle> puzzles = new ArrayList<>();
        if (pgnText == null || pgnText.trim().isEmpty()) return puzzles;

        // Split into individual games (each starts with an [Event ...] tag).
        String[] games = pgnText.trim().split("(?=\\[Event )");
        for (String game : games) {
            ParsedPuzzle p = parseGame(game);
            if (p != null && !p.solutionUci.isEmpty()) puzzles.add(p);
        }
        return puzzles;
    }

    private static ParsedPuzzle parseGame(String game) {
        String fen = START_FEN;
        String white = null, event = null;
        StringBuilder movetext = new StringBuilder();

        for (String line : game.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[")) {
                Matcher m = TAG.matcher(trimmed);
                if (m.find()) {
                    String key = m.group(1);
                    String val = m.group(2);
                    if ("FEN".equalsIgnoreCase(key)) fen = val;
                    else if ("White".equalsIgnoreCase(key)) white = val;
                    else if ("Event".equalsIgnoreCase(key)) event = val;
                }
            } else if (!trimmed.isEmpty()) {
                movetext.append(trimmed).append(' ');
            }
        }

        List<String> sanMoves = tokenizeMoves(movetext.toString());
        String uci = sanLineToUci(fen, sanMoves);
        String title = event != null ? event : (white != null ? white : "PGN");
        return new ParsedPuzzle(title, fen, uci);
    }

    private static List<String> tokenizeMoves(String movetext) {
        // Remove comments {...}, variations (...), NAGs $n and result markers.
        String cleaned = movetext
                .replaceAll("\\{[^}]*\\}", " ")
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\$\\d+", " ")
                .replaceAll("\\b\\d+\\.(\\.\\.)?", " ")   // move numbers 1. / 1...
                .replaceAll("(1-0|0-1|1/2-1/2|\\*)", " ");

        List<String> moves = new ArrayList<>();
        for (String tok : cleaned.trim().split("\\s+")) {
            if (!tok.isEmpty()) moves.add(tok);
        }
        return moves;
    }

    private static String sanLineToUci(String fen, List<String> sanMoves) {
        ChessBoard board = ChessBoard.fromFen(fen);
        StringBuilder sb = new StringBuilder();
        int plies = 0;
        for (String san : sanMoves) {
            if (plies++ >= 40) break; // cap solution length
            String uci = sanToUci(board, san);
            if (uci == null) break;   // unparseable move ends the line
            if (sb.length() > 0) sb.append(' ');
            sb.append(uci);
            board.applyUci(uci);
        }
        return sb.toString();
    }

    /** Resolves one SAN move against the current position, returning UCI or null. */
    static String sanToUci(ChessBoard board, String san) {
        if (san == null) return null;
        String s = san.replaceAll("[+#!?]", "").trim();
        if (s.isEmpty()) return null;

        boolean white = board.isWhiteToMove();
        int homeRow = white ? 7 : 0;

        // Castling
        if (s.equals("O-O") || s.equals("0-0")) {
            return ChessBoard.toUci(homeRow, 4, homeRow, 6);
        }
        if (s.equals("O-O-O") || s.equals("0-0-0")) {
            return ChessBoard.toUci(homeRow, 4, homeRow, 2);
        }

        // Promotion suffix, e.g. e8=Q
        char promo = 0;
        int eq = s.indexOf('=');
        if (eq >= 0 && eq + 1 < s.length()) {
            promo = Character.toLowerCase(s.charAt(eq + 1));
            s = s.substring(0, eq);
        }

        s = s.replace("x", "");
        if (s.length() < 2) return null;

        String dest = s.substring(s.length() - 2);
        if (dest.charAt(0) < 'a' || dest.charAt(0) > 'h') return null;
        int destCol = dest.charAt(0) - 'a';
        int destRow = 8 - (dest.charAt(1) - '0');
        if (destRow < 0 || destRow > 7) return null;

        String prefix = s.substring(0, s.length() - 2);
        char pieceType = 'P';
        String disamb = "";
        if (!prefix.isEmpty() && "KQRBN".indexOf(prefix.charAt(0)) >= 0) {
            pieceType = prefix.charAt(0);
            disamb = prefix.substring(1);
        } else {
            disamb = prefix; // pawn capture file, e.g. "e" in exd5
        }

        Integer disFile = null, disRow = null;
        for (int i = 0; i < disamb.length(); i++) {
            char ch = disamb.charAt(i);
            if (ch >= 'a' && ch <= 'h') disFile = ch - 'a';
            else if (ch >= '1' && ch <= '8') disRow = 8 - (ch - '0');
        }

        // Find the piece of the right type whose legal targets include dest.
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                char p = board.pieceAt(r, c);
                if (p == '.') continue;
                boolean pieceIsWhite = ChessBoard.isWhite(p);
                if (pieceIsWhite != white) continue;
                if (Character.toUpperCase(p) != pieceType) continue;
                if (disFile != null && c != disFile) continue;
                if (disRow != null && r != disRow) continue;
                for (int[] t : board.legalTargets(r, c)) {
                    if (t[0] == destRow && t[1] == destCol) {
                        String uci = ChessBoard.toUci(r, c, destRow, destCol);
                        return promo != 0 ? uci + promo : uci;
                    }
                }
            }
        }
        return null;
    }
}
