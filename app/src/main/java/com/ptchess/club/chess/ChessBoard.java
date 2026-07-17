package com.ptchess.club.chess;

import java.util.ArrayList;
import java.util.List;

/**
 * A lightweight chess position used by the puzzle trainer.
 *
 * <p>Board layout: {@code squares[row][col]}, row 0 = rank 8 (top of the view),
 * row 7 = rank 1, col 0 = file a. Pieces are FEN letters (uppercase = White,
 * lowercase = Black); {@code '.'} is empty.</p>
 *
 * <p>It generates pseudo-legal moves (enough for a training board and to convert
 * SAN from PGN) and applies UCI moves including castling, promotion and en
 * passant. Full check/pin filtering is intentionally omitted — puzzle
 * correctness is decided by comparing the played move to the stored solution.</p>
 */
public class ChessBoard {

    private final char[][] squares = new char[8][8];
    private boolean whiteToMove = true;
    private String castling = "KQkq";
    private int epRow = -1, epCol = -1; // en-passant target square

    public static ChessBoard fromFen(String fen) {
        ChessBoard b = new ChessBoard();
        for (char[] row : b.squares) java.util.Arrays.fill(row, '.');
        String[] parts = fen.trim().split("\\s+");
        String placement = parts[0];
        int row = 0, col = 0;
        for (int i = 0; i < placement.length(); i++) {
            char ch = placement.charAt(i);
            if (ch == '/') {
                row++;
                col = 0;
            } else if (Character.isDigit(ch)) {
                col += (ch - '0');
            } else if (row < 8 && col < 8) {
                b.squares[row][col++] = ch;
            }
        }
        b.whiteToMove = parts.length < 2 || parts[1].equals("w");
        b.castling = parts.length >= 3 ? parts[2] : "KQkq";
        if (parts.length >= 4 && !parts[3].equals("-") && parts[3].length() == 2) {
            b.epCol = parts[3].charAt(0) - 'a';
            b.epRow = 8 - (parts[3].charAt(1) - '0');
        }
        return b;
    }

    public char pieceAt(int row, int col) {
        return squares[row][col];
    }

    /** Serializes the current position to FEN (halfmove/fullmove fixed at "0 1"). */
    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int c = 0; c < 8; c++) {
                char p = squares[r][c];
                if (p == '.') {
                    empty++;
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(p);
                }
            }
            if (empty > 0) sb.append(empty);
            if (r < 7) sb.append('/');
        }
        sb.append(' ').append(whiteToMove ? 'w' : 'b');
        sb.append(' ').append(castling == null || castling.isEmpty() ? "-" : castling);
        sb.append(' ');
        if (epRow >= 0 && epCol >= 0) {
            sb.append((char) ('a' + epCol)).append(8 - epRow);
        } else {
            sb.append('-');
        }
        sb.append(" 0 1");
        return sb.toString();
    }

    public boolean isWhiteToMove() {
        return whiteToMove;
    }

    public static boolean isWhite(char p) {
        return p != '.' && Character.isUpperCase(p);
    }

    public static boolean isBlack(char p) {
        return p != '.' && Character.isLowerCase(p);
    }

    private boolean isOwn(char p) {
        return whiteToMove ? isWhite(p) : isBlack(p);
    }

    private boolean isEnemy(char p) {
        return whiteToMove ? isBlack(p) : isWhite(p);
    }

    private static boolean inBounds(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }

    /** Pseudo-legal destination squares for the piece on (row,col) of the side to move. */
    public List<int[]> legalTargets(int row, int col) {
        List<int[]> out = new ArrayList<>();
        char p = squares[row][col];
        if (p == '.' || !isOwn(p)) return out;
        char type = Character.toUpperCase(p);
        switch (type) {
            case 'P': pawnMoves(row, col, out); break;
            case 'N': stepMoves(row, col, KNIGHT, out); break;
            case 'K': stepMoves(row, col, KING, out); kingCastles(row, col, out); break;
            case 'B': slideMoves(row, col, BISHOP, out); break;
            case 'R': slideMoves(row, col, ROOK, out); break;
            case 'Q': slideMoves(row, col, BISHOP, out); slideMoves(row, col, ROOK, out); break;
        }
        return out;
    }

    private static final int[][] KNIGHT = {
            {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
    private static final int[][] KING = {
            {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
    private static final int[][] BISHOP = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final int[][] ROOK = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    private void stepMoves(int r, int c, int[][] deltas, List<int[]> out) {
        for (int[] d : deltas) {
            int nr = r + d[0], nc = c + d[1];
            if (inBounds(nr, nc) && !isOwn(squares[nr][nc])) out.add(new int[]{nr, nc});
        }
    }

    private void slideMoves(int r, int c, int[][] dirs, List<int[]> out) {
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (inBounds(nr, nc)) {
                char t = squares[nr][nc];
                if (t == '.') {
                    out.add(new int[]{nr, nc});
                } else {
                    if (isEnemy(t)) out.add(new int[]{nr, nc});
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
    }

    private void pawnMoves(int r, int c, List<int[]> out) {
        int dir = whiteToMove ? -1 : 1;
        int startRow = whiteToMove ? 6 : 1;
        int one = r + dir;
        if (inBounds(one, c) && squares[one][c] == '.') {
            out.add(new int[]{one, c});
            int two = r + 2 * dir;
            if (r == startRow && squares[two][c] == '.') out.add(new int[]{two, c});
        }
        for (int dc = -1; dc <= 1; dc += 2) {
            int nc = c + dc;
            if (!inBounds(one, nc)) continue;
            if (isEnemy(squares[one][nc]) || (one == epRow && nc == epCol)) {
                out.add(new int[]{one, nc});
            }
        }
    }

    private void kingCastles(int r, int c, List<int[]> out) {
        char king = squares[r][c];
        boolean white = isWhite(king);
        int homeRow = white ? 7 : 0;
        if (r != homeRow || c != 4) return;
        String ks = white ? "K" : "k";
        String qs = white ? "Q" : "q";
        if (castling.contains(ks)
                && squares[homeRow][5] == '.' && squares[homeRow][6] == '.'
                && squares[homeRow][7] == (white ? 'R' : 'r')) {
            out.add(new int[]{homeRow, 6});
        }
        if (castling.contains(qs)
                && squares[homeRow][1] == '.' && squares[homeRow][2] == '.'
                && squares[homeRow][3] == '.'
                && squares[homeRow][0] == (white ? 'R' : 'r')) {
            out.add(new int[]{homeRow, 2});
        }
    }

    /** Applies a UCI move (e.g. "e2e4", "e7e8q"). Assumes the move is legal. */
    public void applyUci(String uci) {
        if (uci == null || uci.length() < 4) return;
        int fromCol = uci.charAt(0) - 'a';
        int fromRow = 8 - (uci.charAt(1) - '0');
        int toCol = uci.charAt(2) - 'a';
        int toRow = 8 - (uci.charAt(3) - '0');
        char promo = uci.length() >= 5 ? uci.charAt(4) : 0;
        applyMove(fromRow, fromCol, toRow, toCol, promo);
    }

    public void applyMove(int fromRow, int fromCol, int toRow, int toCol, char promo) {
        char piece = squares[fromRow][fromCol];
        char type = Character.toUpperCase(piece);
        boolean white = isWhite(piece);

        // En passant capture: pawn moves diagonally onto the ep square.
        if (type == 'P' && toCol != fromCol && squares[toRow][toCol] == '.') {
            squares[fromRow][toCol] = '.';
        }

        // Move the piece.
        squares[toRow][toCol] = piece;
        squares[fromRow][fromCol] = '.';

        // Promotion (default to queen if unspecified).
        if (type == 'P' && (toRow == 0 || toRow == 7)) {
            char q = promo != 0 ? promo : 'q';
            squares[toRow][toCol] = white ? Character.toUpperCase(q) : Character.toLowerCase(q);
        }

        // Castling: move the rook too.
        if (type == 'K' && Math.abs(toCol - fromCol) == 2) {
            int homeRow = white ? 7 : 0;
            if (toCol == 6) { // king side
                squares[homeRow][5] = squares[homeRow][7];
                squares[homeRow][7] = '.';
            } else if (toCol == 2) { // queen side
                squares[homeRow][3] = squares[homeRow][0];
                squares[homeRow][0] = '.';
            }
        }

        updateCastlingRights(type, white, fromRow, fromCol);

        // New en-passant target after a double pawn push.
        epRow = epCol = -1;
        if (type == 'P' && Math.abs(toRow - fromRow) == 2) {
            epRow = (fromRow + toRow) / 2;
            epCol = fromCol;
        }

        whiteToMove = !whiteToMove;
    }

    private void updateCastlingRights(char type, boolean white, int fromRow, int fromCol) {
        if (type == 'K') {
            castling = castling.replace(white ? "K" : "k", "").replace(white ? "Q" : "q", "");
        } else if (type == 'R') {
            if (white && fromRow == 7 && fromCol == 0) castling = castling.replace("Q", "");
            if (white && fromRow == 7 && fromCol == 7) castling = castling.replace("K", "");
            if (!white && fromRow == 0 && fromCol == 0) castling = castling.replace("q", "");
            if (!white && fromRow == 0 && fromCol == 7) castling = castling.replace("k", "");
        }
        if (castling.isEmpty()) castling = "-";
    }

    // ---- check detection ----

    private int[] findKing(boolean white) {
        char king = white ? 'K' : 'k';
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (squares[r][c] == king) return new int[]{r, c};
            }
        }
        return null;
    }

    /** True if (row,col) is attacked by any piece of the given colour. */
    public boolean isSquareAttacked(int row, int col, boolean byWhite) {
        int pawnRow = byWhite ? row + 1 : row - 1;
        char pawn = byWhite ? 'P' : 'p';
        if (inBounds(pawnRow, col - 1) && squares[pawnRow][col - 1] == pawn) return true;
        if (inBounds(pawnRow, col + 1) && squares[pawnRow][col + 1] == pawn) return true;

        char knight = byWhite ? 'N' : 'n';
        for (int[] d : KNIGHT) {
            int r = row + d[0], c = col + d[1];
            if (inBounds(r, c) && squares[r][c] == knight) return true;
        }
        char king = byWhite ? 'K' : 'k';
        for (int[] d : KING) {
            int r = row + d[0], c = col + d[1];
            if (inBounds(r, c) && squares[r][c] == king) return true;
        }

        char bishop = byWhite ? 'B' : 'b';
        char rook = byWhite ? 'R' : 'r';
        char queen = byWhite ? 'Q' : 'q';
        for (int[] d : BISHOP) {
            int r = row + d[0], c = col + d[1];
            while (inBounds(r, c)) {
                char p = squares[r][c];
                if (p != '.') { if (p == bishop || p == queen) return true; break; }
                r += d[0]; c += d[1];
            }
        }
        for (int[] d : ROOK) {
            int r = row + d[0], c = col + d[1];
            while (inBounds(r, c)) {
                char p = squares[r][c];
                if (p != '.') { if (p == rook || p == queen) return true; break; }
                r += d[0]; c += d[1];
            }
        }
        return false;
    }

    public boolean isInCheck(boolean white) {
        int[] k = findKing(white);
        return k != null && isSquareAttacked(k[0], k[1], !white);
    }

    /** The square of the side-to-move king if it is in check, else null (for UI). */
    public int[] checkedKingSquare() {
        if (isInCheck(whiteToMove)) return findKing(whiteToMove);
        return null;
    }

    // ---- coordinate helpers ----

    public static String toUci(int fromRow, int fromCol, int toRow, int toCol) {
        return "" + (char) ('a' + fromCol) + (8 - fromRow)
                + (char) ('a' + toCol) + (8 - toRow);
    }
}
