package com.ptchess.club.chess;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws an interactive 8x8 board with Unicode pieces. White is always at the
 * bottom. Tapping an own piece highlights its pseudo-legal targets; tapping a
 * target emits a UCI move to {@link OnMoveListener}. The hosting fragment
 * decides whether the move is correct for the current puzzle.
 */
public class ChessBoardView extends View {

    public interface OnMoveListener {
        void onMove(String uci);
    }

    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lastPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteBody = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whiteEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackBody = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ChessBoard board;
    private int selRow = -1, selCol = -1;
    private List<int[]> targets = new ArrayList<>();
    private int lastFromRow = -1, lastFromCol = -1, lastToRow = -1, lastToCol = -1;
    private boolean interactive = true;

    @Nullable private OnMoveListener listener;

    public ChessBoardView(Context c) { super(c); init(); }
    public ChessBoardView(Context c, AttributeSet a) { super(c, a); init(); }
    public ChessBoardView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        lightPaint.setColor(0xFFE9EDF4);
        darkPaint.setColor(0xFF3D5A96);
        selPaint.setColor(0x88E7B04A);
        lastPaint.setColor(0x552B6EE0);
        dotPaint.setColor(0x992B6EE0);

        // Pieces are drawn as a matched pair of glyphs: a solid body glyph plus
        // the outline glyph on top (crisp contour + internal detail), with a soft
        // drop shadow. This reads far cleaner than a single glyph with a stroke.
        pieceShadow.setColor(0x4D000000);
        whiteBody.setColor(0xFFF7FAFF);   // near-white fill
        whiteEdge.setColor(0xFF12263F);   // dark contour + detail
        blackBody.setColor(0xFF17253E);   // deep navy fill (softer than pure black)
        blackEdge.setColor(0xFFE7EEF8);   // light contour + detail

        for (Paint p : new Paint[]{pieceShadow, whiteBody, whiteEdge, blackBody, blackEdge}) {
            p.setTextAlign(Paint.Align.CENTER);
        }
        coordPaint.setColor(0x66FFFFFF);
        coordPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void setOnMoveListener(OnMoveListener l) {
        this.listener = l;
    }

    public void setBoard(ChessBoard board) {
        this.board = board;
        clearSelection();
        invalidate();
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public void setLastMove(int fromRow, int fromCol, int toRow, int toCol) {
        lastFromRow = fromRow; lastFromCol = fromCol; lastToRow = toRow; lastToCol = toCol;
        invalidate();
    }

    public void highlightSquare(int row, int col) {
        selRow = row; selCol = col; targets = new ArrayList<>();
        invalidate();
    }

    public void clearSelection() {
        selRow = selCol = -1;
        targets = new ArrayList<>();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        int size = Math.min(w, h == 0 ? w : h);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (board == null) return;
        float cell = getWidth() / 8f;
        coordPaint.setTextSize(cell * 0.22f);

        for (int r = 0; r < 8; r++) {
            for (int col = 0; col < 8; col++) {
                float x = col * cell, y = r * cell;
                boolean light = (r + col) % 2 == 0;
                canvas.drawRect(x, y, x + cell, y + cell, light ? lightPaint : darkPaint);

                if ((r == lastFromRow && col == lastFromCol)
                        || (r == lastToRow && col == lastToCol)) {
                    canvas.drawRect(x, y, x + cell, y + cell, lastPaint);
                }
                if (r == selRow && col == selCol) {
                    canvas.drawRect(x, y, x + cell, y + cell, selPaint);
                }
            }
        }

        // Target dots
        for (int[] t : targets) {
            float cx = t[1] * cell + cell / 2f;
            float cy = t[0] * cell + cell / 2f;
            char occupant = board.pieceAt(t[0], t[1]);
            if (occupant == '.') {
                canvas.drawCircle(cx, cy, cell * 0.14f, dotPaint);
            } else {
                dotPaint.setStyle(Paint.Style.STROKE);
                dotPaint.setStrokeWidth(cell * 0.08f);
                canvas.drawCircle(cx, cy, cell * 0.42f, dotPaint);
                dotPaint.setStyle(Paint.Style.FILL);
            }
        }

        // Pieces
        float textSize = cell * 0.86f;
        for (Paint p : new Paint[]{pieceShadow, whiteBody, whiteEdge, blackBody, blackEdge}) {
            p.setTextSize(textSize);
        }
        Paint.FontMetrics fm = whiteBody.getFontMetrics();
        float baselineOffset = (fm.descent + fm.ascent) / 2f;
        float shadowDx = cell * 0.015f;
        float shadowDy = cell * 0.03f;

        for (int r = 0; r < 8; r++) {
            for (int col = 0; col < 8; col++) {
                char p = board.pieceAt(r, col);
                if (p == '.') continue;
                String solid = solidGlyph(p);
                String outline = outlineGlyph(p);
                float cx = col * cell + cell / 2f;
                float cy = r * cell + cell / 2f - baselineOffset;
                boolean white = ChessBoard.isWhite(p);
                // soft shadow, then the solid body, then the outline+detail on top
                canvas.drawText(solid, cx + shadowDx, cy + shadowDy, pieceShadow);
                canvas.drawText(solid, cx, cy, white ? whiteBody : blackBody);
                canvas.drawText(outline, cx, cy, white ? whiteEdge : blackEdge);
            }
        }

        // File/rank coordinates for orientation
        for (int i = 0; i < 8; i++) {
            canvas.drawText(String.valueOf((char) ('a' + i)),
                    i * cell + cell * 0.06f, getHeight() - cell * 0.06f, coordPaint);
            canvas.drawText(String.valueOf(8 - i),
                    getWidth() - cell * 0.20f, i * cell + cell * 0.28f, coordPaint);
        }
    }

    /** Solid (filled) glyph — the piece body silhouette (U+265A..265F). */
    private String solidGlyph(char p) {
        switch (Character.toUpperCase(p)) {
            case 'K': return "♚";
            case 'Q': return "♛";
            case 'R': return "♜";
            case 'B': return "♝";
            case 'N': return "♞";
            case 'P': return "♟";
            default: return "";
        }
    }

    /** Outline glyph — same piece drawn as contour + internal detail (U+2654..2659). */
    private String outlineGlyph(char p) {
        switch (Character.toUpperCase(p)) {
            case 'K': return "♔";
            case 'Q': return "♕";
            case 'R': return "♖";
            case 'B': return "♗";
            case 'N': return "♘";
            case 'P': return "♙";
            default: return "";
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!interactive || board == null) return false;
        if (event.getAction() != MotionEvent.ACTION_DOWN) return true;

        float cell = getWidth() / 8f;
        int col = (int) (event.getX() / cell);
        int row = (int) (event.getY() / cell);
        if (row < 0 || row > 7 || col < 0 || col > 7) return true;

        // Tapping a highlighted target completes a move.
        for (int[] t : targets) {
            if (t[0] == row && t[1] == col) {
                String uci = ChessBoard.toUci(selRow, selCol, row, col);
                clearSelection();
                invalidate();
                if (listener != null) listener.onMove(uci);
                return true;
            }
        }

        char p = board.pieceAt(row, col);
        boolean own = (board.isWhiteToMove() && ChessBoard.isWhite(p))
                || (!board.isWhiteToMove() && ChessBoard.isBlack(p));
        if (own) {
            selRow = row;
            selCol = col;
            targets = board.legalTargets(row, col);
        } else {
            clearSelection();
        }
        invalidate();
        return true;
    }
}
