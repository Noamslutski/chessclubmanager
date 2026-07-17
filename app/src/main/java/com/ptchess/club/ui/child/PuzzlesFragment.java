package com.ptchess.club.ui.child;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.ptchess.club.R;
import com.ptchess.club.chess.ChessBoard;
import com.ptchess.club.chess.ChessBoardView;
import com.ptchess.club.chess.PgnImporter;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.firebase.PuzzleCloud;
import com.ptchess.club.data.model.Puzzle;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.data.remote.LichessApi;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class PuzzlesFragment extends Fragment {

    private ChessBoardView boardView;
    private TextView title, level, sideToMove, status;
    private ChipGroup chipLevels;

    private List<Puzzle> allPuzzles = new ArrayList<>();
    private List<Puzzle> puzzles = new ArrayList<>();
    private int currentLevel = 0; // 0 = all levels
    private int index = 0;
    private boolean autoLoadTried = false;

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
        chipLevels = view.findViewById(R.id.chipLevels);

        boardView.setOnMoveListener(this::onUserMove);
        view.findViewById(R.id.btnHint).setOnClickListener(v -> showHint());
        view.findViewById(R.id.btnReset).setOnClickListener(v -> loadPuzzle(index));
        view.findViewById(R.id.btnNext).setOnClickListener(v -> nextPuzzle());
        view.findViewById(R.id.btnLichess).setOnClickListener(v -> showLichessDialog());

        MaterialButton importBtn = view.findViewById(R.id.btnImportPgn);
        User user = ((MainActivity) requireActivity()).getCurrentUser();
        if (user.role == Role.TUTOR || user.role == Role.ADMIN) {
            importBtn.setVisibility(View.VISIBLE);
            importBtn.setOnClickListener(v -> showStaffOptions());
        }

        pgnPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onPgnPicked);

        loadPuzzles();
    }

    private void loadPuzzles() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        long clubId = ((MainActivity) requireActivity()).getCurrentUser().clubId;
        Async.io(() -> {
            List<Puzzle> loaded = repo.getPuzzles(clubId);
            Async.main(() -> {
                if (!isAdded()) return;
                // No local puzzles yet → pull real puzzles from Lichess automatically.
                if (loaded.isEmpty() && !autoLoadTried) {
                    autoLoadTried = true;
                    autoLoadFromLichess();
                    return;
                }
                allPuzzles = loaded;
                rebuildLevelChips();
                applyFilter(currentLevel);
                if (loaded.isEmpty()) {
                    status.setTextColor(getResources().getColor(R.color.text_muted, null));
                    status.setText(R.string.lichess_failed);
                }
            });
        });
    }

    /**
     * First-run population. Prefers the shared Firestore pool (kept fresh by the
     * scheduled Cloud Function); if Firebase isn't configured or the pool is
     * empty, falls back to fetching from Lichess directly.
     */
    private void autoLoadFromLichess() {
        status.setTextColor(getResources().getColor(R.color.text_muted, null));
        status.setText(R.string.lichess_loading);
        User user = ((MainActivity) requireActivity()).getCurrentUser();
        ClubRepository repo = ClubRepository.getInstance(requireContext());

        PuzzleCloud.fetch(requireContext(), cloud -> {
            if (!isAdded()) return;
            if (!cloud.isEmpty()) {
                Async.io(() -> {
                    for (Puzzle p : cloud) {
                        repo.addPuzzle(user.clubId, p.title, p.level, p.fen, p.solutionUci, user.id);
                    }
                    Async.main(() -> { if (isAdded()) loadPuzzles(); });
                });
            } else {
                fetchFromLichessDirect(user, repo);
            }
        });
    }

    private void fetchFromLichessDirect(User user, ClubRepository repo) {
        Async.io(() -> {
            for (String difficulty : new String[]{"easier", "normal", "harder"}) {
                for (LichessApi.RemotePuzzle rp : LichessApi.fetchBatch(difficulty, 4)) {
                    if (rp.solutionUci == null || rp.solutionUci.isEmpty()) continue;
                    repo.addPuzzle(user.clubId, rp.title, rp.level, rp.fen, rp.solutionUci, user.id);
                }
            }
            Async.main(() -> { if (isAdded()) loadPuzzles(); });
        });
    }

    /** Rebuilds the level filter chips from the levels present in the pool. */
    private void rebuildLevelChips() {
        chipLevels.setOnCheckedStateChangeListener(null);
        chipLevels.removeAllViews();
        addLevelChip(getString(R.string.level_all), 0);

        TreeSet<Integer> levels = new TreeSet<>();
        for (Puzzle p : allPuzzles) levels.add(p.level);
        for (int lvl : levels) addLevelChip(getString(R.string.puzzle_level, lvl), lvl);

        if (currentLevel != 0 && !levels.contains(currentLevel)) currentLevel = 0;
        checkChipForLevel(currentLevel);

        chipLevels.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip c = group.findViewById(checkedIds.get(0));
            if (c != null && c.getTag() != null) applyFilter((int) c.getTag());
        });
    }

    private void addLevelChip(String text, int levelValue) {
        Chip chip = (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.item_level_chip, chipLevels, false);
        chip.setText(text);
        chip.setTag(levelValue);
        chip.setId(View.generateViewId());
        chipLevels.addView(chip);
    }

    private void checkChipForLevel(int levelValue) {
        for (int i = 0; i < chipLevels.getChildCount(); i++) {
            Chip c = (Chip) chipLevels.getChildAt(i);
            if (c.getTag() != null && (int) c.getTag() == levelValue) {
                c.setChecked(true);
                return;
            }
        }
    }

    private void applyFilter(int levelValue) {
        currentLevel = levelValue;
        puzzles = new ArrayList<>();
        for (Puzzle p : allPuzzles) {
            if (levelValue == 0 || p.level == levelValue) puzzles.add(p);
        }
        index = 0;
        if (puzzles.isEmpty()) {
            status.setTextColor(getResources().getColor(R.color.text_muted, null));
            status.setText(R.string.no_results);
        } else {
            loadPuzzle(0);
        }
    }

    private void showLichessDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.choose_difficulty)
                .setItems(getResources().getStringArray(R.array.lichess_difficulties),
                        (d, which) -> importFromLichess(LichessApi.DIFFICULTIES[which]))
                .show();
    }

    private void importFromLichess(String difficulty) {
        UiUtils.toast(requireContext(), R.string.lichess_loading);
        User user = ((MainActivity) requireActivity()).getCurrentUser();
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<LichessApi.RemotePuzzle> remote = LichessApi.fetchBatch(difficulty, 8);
            int added = 0;
            for (LichessApi.RemotePuzzle rp : remote) {
                if (rp.solutionUci == null || rp.solutionUci.isEmpty()) continue;
                repo.addPuzzle(user.clubId, rp.title, rp.level, rp.fen, rp.solutionUci, user.id);
                added++;
            }
            int finalAdded = added;
            Async.main(() -> {
                if (!isAdded()) return;
                if (finalAdded == 0) {
                    UiUtils.toast(requireContext(), R.string.lichess_failed);
                } else {
                    UiUtils.toast(requireContext(),
                            getString(R.string.lichess_added, finalAdded));
                    loadPuzzles();
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

    private void showStaffOptions() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_puzzle)
                .setItems(new CharSequence[]{
                        getString(R.string.import_pgn),
                        getString(R.string.puzzle_add_manual)}, (d, which) -> {
                    if (which == 0) pgnPicker.launch(new String[]{"*/*"});
                    else showAddPuzzleManual();
                })
                .show();
    }

    private void showAddPuzzleManual() {
        User user = ((MainActivity) requireActivity()).getCurrentUser();
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_puzzle, null, false);
        EditText title = form.findViewById(R.id.puzzleTitle);
        EditText levelField = form.findViewById(R.id.puzzleLevel);
        EditText fenField = form.findViewById(R.id.puzzleFen);
        EditText solutionField = form.findViewById(R.id.puzzleSolution);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_puzzle)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String t = title.getText().toString().trim();
                    String fen = fenField.getText().toString().trim();
                    String sol = solutionField.getText().toString().trim();
                    int lvl = clampLevel(levelField.getText().toString());
                    if (fen.isEmpty() || sol.isEmpty() || !firstMoveLegal(fen, sol)) {
                        UiUtils.toast(requireContext(), R.string.puzzle_invalid);
                        return;
                    }
                    ClubRepository repo = ClubRepository.getInstance(requireContext());
                    Async.io(() -> {
                        repo.addPuzzle(user.clubId,
                                t.isEmpty() ? getString(R.string.puzzles_title) : t,
                                lvl, fen, sol, user.id);
                        Async.main(this::loadPuzzles);
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int clampLevel(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(1, Math.min(3, v));
        } catch (Exception e) {
            return 2;
        }
    }

    /** Validates that the first solution move is legal for the given FEN. */
    private boolean firstMoveLegal(String fen, String solution) {
        try {
            String first = solution.trim().split("\\s+")[0];
            ChessBoard b = ChessBoard.fromFen(fen);
            int fromCol = first.charAt(0) - 'a';
            int fromRow = 8 - (first.charAt(1) - '0');
            int toCol = first.charAt(2) - 'a';
            int toRow = 8 - (first.charAt(3) - '0');
            for (int[] target : b.legalTargets(fromRow, fromCol)) {
                if (target[0] == toRow && target[1] == toCol) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
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
                repo.addPuzzle(user.clubId, pp.title, 2, pp.fen, pp.solutionUci, user.id);
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
