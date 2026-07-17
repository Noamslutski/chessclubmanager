package com.ptchess.club.data.firebase;

import android.content.Context;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ptchess.club.data.model.Puzzle;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the shared Firestore {@code puzzles} pool that the scheduled
 * {@code ingestLichessPuzzles} Cloud Function keeps topped up from Lichess.
 * Guarded: when Firebase is not configured it returns an empty list and the
 * caller falls back to fetching from Lichess directly.
 */
public final class PuzzleCloud {

    public interface Callback {
        void onResult(List<Puzzle> puzzles);
    }

    private PuzzleCloud() { }

    public static void fetch(Context context, Callback callback) {
        if (!FirebaseServices.isAvailable(context)) {
            callback.onResult(new ArrayList<>());
            return;
        }
        try {
            FirebaseFirestore.getInstance()
                    .collection("puzzles")
                    .limit(80)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        List<Puzzle> out = new ArrayList<>();
                        long id = 1;
                        for (QueryDocumentSnapshot doc : snapshot) {
                            String fen = doc.getString("fen");
                            String solution = doc.getString("solutionUci");
                            if (fen == null || solution == null || solution.isEmpty()) continue;
                            Long level = doc.getLong("level");
                            String title = doc.getString("title");
                            out.add(new Puzzle(id++, title != null ? title : "Lichess",
                                    level != null ? level.intValue() : 2, fen, solution));
                        }
                        callback.onResult(out);
                    })
                    .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
        } catch (Throwable t) {
            callback.onResult(new ArrayList<>());
        }
    }
}
