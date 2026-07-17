package com.ptchess.club.chess;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws crisp vector chess pieces (flat silhouette style) instead of relying on
 * device font glyphs. Each piece is defined once as a unit path in a 0..100 box
 * and rendered with a soft drop shadow, a contrasting outline, and the body
 * fill. Reusable static scratch objects keep {@code onDraw} allocation-free.
 */
public final class ChessPieceRenderer {

    private static final Map<Character, Path> UNIT = new HashMap<>();
    private static final Path SCRATCH = new Path();
    private static final Matrix BASE = new Matrix();
    private static final Matrix TMP = new Matrix();
    private static final float[] PT = new float[2];

    private ChessPieceRenderer() { }

    public static void draw(Canvas canvas, char piece, float x, float y, float cell,
                            Paint body, Paint edge, Paint shadow) {
        Path unit = unitPath(Character.toUpperCase(piece));
        if (unit == null) return;

        float content = cell * 0.90f;
        float pad = (cell - content) / 2f;
        float s = content / 100f;
        BASE.setScale(s, s);
        BASE.postTranslate(x + pad, y + pad);

        // shadow (offset down-right)
        TMP.set(BASE);
        TMP.postTranslate(cell * 0.015f, cell * 0.03f);
        unit.transform(TMP, SCRATCH);
        canvas.drawPath(SCRATCH, shadow);

        // outline: same silhouette scaled up slightly about the piece centre
        float cx = x + cell / 2f, cy = y + cell / 2f;
        TMP.set(BASE);
        TMP.postScale(1.07f, 1.07f, cx, cy);
        unit.transform(TMP, SCRATCH);
        canvas.drawPath(SCRATCH, edge);

        // body fill
        unit.transform(BASE, SCRATCH);
        canvas.drawPath(SCRATCH, body);

        // knight eye in the contrast colour
        if (Character.toUpperCase(piece) == 'N') {
            PT[0] = 41f; PT[1] = 38f;
            BASE.mapPoints(PT);
            canvas.drawCircle(PT[0], PT[1], cell * 0.028f, edge);
        }
    }

    private static Path unitPath(char type) {
        Path p = UNIT.get(type);
        if (p != null) return p;
        p = new Path();
        switch (type) {
            case 'P': pawn(p); break;
            case 'R': rook(p); break;
            case 'N': knight(p); break;
            case 'B': bishop(p); break;
            case 'Q': queen(p); break;
            case 'K': king(p); break;
            default: return null;
        }
        UNIT.put(type, p);
        return p;
    }

    private static void base(Path p) {
        p.moveTo(24, 94);
        p.lineTo(76, 94);
        p.lineTo(70, 86);
        p.lineTo(30, 86);
        p.close();
        p.addRect(30, 80, 70, 85, Path.Direction.CW);
    }

    private static void pawn(Path p) {
        p.addCircle(50, 30, 13, Path.Direction.CW);
        p.moveTo(40, 40);
        p.cubicTo(32, 52, 32, 66, 34, 76);
        p.lineTo(66, 76);
        p.cubicTo(68, 66, 68, 52, 60, 40);
        p.close();
        p.addRect(41, 42, 59, 47, Path.Direction.CW);
        base(p);
    }

    private static void rook(Path p) {
        p.moveTo(30, 18);
        p.lineTo(38, 18); p.lineTo(38, 25); p.lineTo(46, 25); p.lineTo(46, 18);
        p.lineTo(54, 18); p.lineTo(54, 25); p.lineTo(62, 25); p.lineTo(62, 18);
        p.lineTo(70, 18); p.lineTo(70, 34); p.lineTo(30, 34);
        p.close();
        p.moveTo(35, 34);
        p.lineTo(65, 34);
        p.lineTo(61, 76);
        p.lineTo(39, 76);
        p.close();
        p.addRect(35, 46, 65, 51, Path.Direction.CW);
        base(p);
    }

    private static void bishop(Path p) {
        p.addCircle(50, 14, 4.5f, Path.Direction.CW);
        p.moveTo(50, 18);
        p.cubicTo(61, 26, 61, 40, 50, 48);
        p.cubicTo(39, 40, 39, 26, 50, 18);
        p.close();
        p.moveTo(41, 48);
        p.cubicTo(35, 58, 34, 68, 35, 76);
        p.lineTo(65, 76);
        p.cubicTo(66, 68, 65, 58, 59, 48);
        p.close();
        p.addRect(41, 47, 59, 52, Path.Direction.CW);
        base(p);
    }

    private static void queen(Path p) {
        p.moveTo(34, 48);
        p.lineTo(30, 28);
        p.lineTo(42, 42);
        p.lineTo(50, 24);
        p.lineTo(58, 42);
        p.lineTo(70, 28);
        p.lineTo(66, 48);
        p.close();
        p.addCircle(30, 26, 4, Path.Direction.CW);
        p.addCircle(50, 21, 4.5f, Path.Direction.CW);
        p.addCircle(70, 26, 4, Path.Direction.CW);
        p.moveTo(37, 50);
        p.cubicTo(33, 60, 33, 70, 35, 76);
        p.lineTo(65, 76);
        p.cubicTo(67, 70, 67, 60, 63, 50);
        p.close();
        p.addRect(35, 49, 65, 54, Path.Direction.CW);
        base(p);
    }

    private static void king(Path p) {
        p.addRect(47, 12, 53, 30, Path.Direction.CW);   // cross vertical
        p.addRect(42, 17, 58, 23, Path.Direction.CW);   // cross horizontal
        p.moveTo(37, 48);
        p.cubicTo(37, 34, 45, 30, 50, 30);
        p.cubicTo(55, 30, 63, 34, 63, 48);
        p.close();
        p.moveTo(37, 48);
        p.lineTo(63, 48);
        p.cubicTo(67, 60, 66, 70, 62, 76);
        p.lineTo(38, 76);
        p.cubicTo(34, 70, 33, 60, 37, 48);
        p.close();
        p.addRect(36, 54, 64, 59, Path.Direction.CW);
        base(p);
    }

    private static void knight(Path p) {
        p.moveTo(32, 84);
        p.lineTo(33, 62);
        p.cubicTo(31, 54, 35, 49, 43, 47);
        p.lineTo(35, 43);
        p.lineTo(29, 45);
        p.lineTo(31, 39);
        p.lineTo(43, 33);
        p.lineTo(41, 29);
        p.lineTo(47, 29);
        p.lineTo(50, 24);
        p.lineTo(47, 15);
        p.lineTo(55, 23);
        p.lineTo(61, 17);
        p.cubicTo(70, 26, 71, 40, 69, 52);
        p.lineTo(70, 84);
        p.close();
        base(p);
    }
}
