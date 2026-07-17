package com.ptchess.club.ui.child;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.chess.ChessBoard;
import com.ptchess.club.chess.ChessBoardView;
import com.ptchess.club.chess.PgnImporter;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.Puzzle;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class PuzzlesFragment extends Fragment {

    private ChessBoardView boardView;
    private TextView title, level, sideToMove, status;

    private List<Puzzle> puzzles;
    private int index = 0;

    private ChessBoard board;
    private List<String> solution;
    private int solutionPos;
    private boolean solved;

    private ActivityResultLauncher<String[]> pgnPicker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_puzzles, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        boardView = view.findViewById(R.id.boardView);
        title = view.findViewById(R.id.txtPuzzleTitle);
        level = view.findViewById(R.id.txtLevel);
        sideToMove = view.findViewById(R.id.txtSideToMove);
        status = view.findViewById(R.id.txtStatus);

        boardView.setOnMoveListener(this::onUserMove);
        view.findViewById(R.id.btnHint).setOnClickListener(v -> showHint());
        view.findViewById(R.id.btnReset).setOnClickListener(v -> loadPuzzle(index));
        view.findViewById(R.id.btnNext).setOnClickListener(v -> nextPuzzle());

        MaterialButton importBtn = view.findViewById(R.id.btnImportPgn);
        User user = ((MainActivity) requireActivity()).getCurrentUser();
        if (user.role == Role.TUTOR || user.role == Role.ADMIN) {
            importBtn.setVisibility(View.VISIBLE);
            importBtn.setOnClickListener(v -> pgnPicker.launch(new String[]{"*/*"}));
        }

        pgnPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onPgnPicked);

        loadPuzzles();
    }

    private void loadPuzzles() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<Puzzle> loaded = repo.getPuzzles();
            Async.main(() -> {
                if (!isAdded()) return;
                puzzles = loaded;
                index = 0;
                if (puzzles.isEmpty()) {
                    status.setText(R.string.no_results);
                } else {
                    loadPuzzle(0);
                }
            });
        });
    }

    private void loadPuzzle(int i) {
        if (puzzles == null || puzzles.isEmpty()) return;
        index = ((i % puzzles.size()) + puzzles.size()) % puzzles.size();
        Puzzle p = puzzles.get(index);

        board = ChessBoard.fromFen(p.fen);
        solution = p.solutionMoves();
        solutionPos = 0;
        solved = false;

        title.setText(p.title != null && !p.title.isEmpty()
                ? p.title : getString(R.string.puzzles_title));
        level.setText(getString(R.string.puzzle_level, p.level));
        sideToMove.setText(board.isWhiteToMove()
                ? R.string.puzzle_white_to_move : R.string.puzzle_black_to_move);
        status.setText("");

        boardView.setInteractive(true);
        boardView.setBoard(board);
        boardView.setLastMove(-1, -1, -1, -1);
    }

    private void onUserMove(String uci) {
        if (solved || solution == null || solutionPos >= solution.size()) return;
        String expected = solution.get(solutionPos);

        if (!samePrimaryMove(uci, expected)) {
            status.setTextColor(getResources().getColor(R.color.danger, null));
            status.setText(R.string.puzzle_wrong);
            return;
        }

        applyMove(expected);
        solutionPos++;

        if (solutionPos >= solution.size()) {
            finishSolved();
            return;
        }

        status.setText("");
        // Opponent's reply from the solution line.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isAdded() || solved) return;
            if (solutionPos < solution.size()) {
                applyMove(solution.get(solutionPos));
                solutionPos++;
                if (solutionPos >= solution.size()) finishSolved();
            }
        }, 350);
    }

    private void applyMove(String uci) {
        int fromRow = 8 - (uci.charAt(1) - '0');
        int fromCol = uci.charAt(0) - 'a';
        int toRow = 8 - (uci.charAt(3) - '0');
        int toCol = uci.charAt(2) - 'a';
        board.applyUci(uci);
        boardView.setBoard(board);
        boardView.setLastMove(fromRow, fromCol, toRow, toCol);
        sideToMove.setText(board.isWhiteToMove()
                ? R.string.puzzle_white_to_move : R.string.puzzle_black_to_move);
    }

    private void finishSolved() {
        solved = true;
        boardView.setInteractive(false);
        status.setTextColor(getResources().getColor(R.color.success, null));
        status.setText(R.string.puzzle_correct);
    }

    private void showHint() {
        if (solved || solution == null || solutionPos >= solution.size()) return;
        String expected = solution.get(solutionPos);
        int fromRow = 8 - (expected.charAt(1) - '0');
        int fromCol = expected.charAt(0) - 'a';
        boardView.highlightSquare(fromRow, fromCol);
    }

    private void nextPuzzle() {
        if (puzzles != null && !puzzles.isEmpty()) loadPuzzle(index + 1);
    }

    /** Compares the first four UCI characters, ignoring any promotion suffix. */
    private boolean samePrimaryMove(String a, String b) {
        return a != null && b != null && a.length() >= 4 && b.length() >= 4
                && a.substring(0, 4).equals(b.substring(0, 4));
    }

    private void onPgnPicked(@Nullable Uri uri) {
        if (uri == null) return;
        User user = ((MainActivity) requireActivity()).getCurrentUser();
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            String pgn = readText(uri);
            List<PgnImporter.ParsedPuzzle> parsed = PgnImporter.parse(pgn);
            int added = 0;
            for (PgnImporter.ParsedPuzzle pp : parsed) {
                if (pp.solutionUci == null || pp.solutionUci.isEmpty()) continue;
                repo.addPuzzle(pp.title, 2, pp.fen, pp.solutionUci, user.id);
                added++;
            }
            int finalAdded = added;
            Async.main(() -> {
                if (!isAdded()) return;
                UiUtils.toast(requireContext(), "PGN: +" + finalAdded);
                loadPuzzles();
            });
        });
    }

    private String readText(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null) return "";
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 500_000) break; // guard against huge files
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
    }
}
