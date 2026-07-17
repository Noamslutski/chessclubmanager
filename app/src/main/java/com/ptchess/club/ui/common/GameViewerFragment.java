package com.ptchess.club.ui.common;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ptchess.club.R;
import com.ptchess.club.chess.ChessBoard;
import com.ptchess.club.chess.ChessBoardView;
import com.ptchess.club.chess.PgnImporter;
import com.ptchess.club.data.remote.LichessBroadcast;
import com.ptchess.club.util.Async;

import java.util.List;

/**
 * Watches a single broadcast game: a read-only board with move navigation,
 * player info, clocks, a highlighted move list, and a periodic live refresh
 * that follows the game to its latest position while the user is "Live".
 */
public class GameViewerFragment extends Fragment {

    public static final String ARG_ROUND_ID = "round_id";
    public static final String ARG_GAME_INDEX = "game_index";
    private static final long REFRESH_MS = 6_000;

    private ChessBoardView board;
    private TextView txtWhite, txtBlack, txtWhiteClock, txtBlackClock, txtMoveInfo, txtMoves, txtStatus;

    private String roundId;
    private int gameIndex;
    private PgnImporter.GameRecord game;
    private int ply;
    private boolean following = true;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::refreshLive;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_game_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        roundId = getArguments() != null ? getArguments().getString(ARG_ROUND_ID) : null;
        gameIndex = getArguments() != null ? getArguments().getInt(ARG_GAME_INDEX, 0) : 0;

        board = v.findViewById(R.id.boardView);
        txtWhite = v.findViewById(R.id.txtWhite);
        txtBlack = v.findViewById(R.id.txtBlack);
        txtWhiteClock = v.findViewById(R.id.txtWhiteClock);
        txtBlackClock = v.findViewById(R.id.txtBlackClock);
        txtMoveInfo = v.findViewById(R.id.txtMoveInfo);
        txtMoves = v.findViewById(R.id.txtMoves);
        txtStatus = v.findViewById(R.id.txtViewerStatus);
        board.setInteractive(false);

        v.findViewById(R.id.btnStart).setOnClickListener(x -> { following = false; ply = 0; render(); });
        v.findViewById(R.id.btnPrev).setOnClickListener(x -> { following = false; if (ply > 0) ply--; render(); });
        v.findViewById(R.id.btnNext).setOnClickListener(x -> {
            if (game != null && ply < game.movesUci.size()) ply++;
            following = game != null && ply >= game.movesUci.size();
            render();
        });
        v.findViewById(R.id.btnLive).setOnClickListener(x -> {
            following = true;
            if (game != null) ply = game.movesUci.size();
            render();
        });

        txtStatus.setText(R.string.watch_connecting);
        fetch(true);
    }

    private void fetch(boolean initial) {
        Async.io(() -> {
            String pgn = roundId != null ? LichessBroadcast.roundPgn(roundId) : null;
            List<PgnImporter.GameRecord> games = PgnImporter.parseGames(pgn);
            Async.main(() -> {
                if (!isAdded()) return;
                if (gameIndex < games.size()) {
                    game = games.get(gameIndex);
                    if (initial || following) ply = game.movesUci.size();
                    ply = Math.min(ply, game.movesUci.size());
                    txtStatus.setText("");
                    render();
                } else if (initial) {
                    txtStatus.setText(R.string.watch_failed);
                }
                handler.removeCallbacks(poll);
                handler.postDelayed(poll, REFRESH_MS);
            });
        });
    }

    private void refreshLive() {
        fetch(false);
    }

    private void render() {
        if (game == null) return;

        ChessBoard b = ChessBoard.fromFen(game.fen);
        for (int i = 0; i < ply && i < game.movesUci.size(); i++) {
            b.applyUci(game.movesUci.get(i));
        }
        board.setBoard(b);
        if (ply > 0) {
            String lm = game.movesUci.get(ply - 1);
            board.setLastMove(8 - (lm.charAt(1) - '0'), lm.charAt(0) - 'a',
                    8 - (lm.charAt(3) - '0'), lm.charAt(2) - 'a');
        } else {
            board.setLastMove(-1, -1, -1, -1);
        }

        txtWhite.setText("♔ " + playerLabel(game.whiteTitle, game.white, game.whiteElo));
        txtBlack.setText("♚ " + playerLabel(game.blackTitle, game.black, game.blackElo));
        txtWhiteClock.setText(game.whiteClock.isEmpty() ? "—" : game.whiteClock);
        txtBlackClock.setText(game.blackClock.isEmpty() ? "—" : game.blackClock);

        int total = game.movesUci.size();
        txtMoveInfo.setText(ply == 0
                ? getString(R.string.viewer_start_position)
                : getString(R.string.viewer_move, ply, total));

        txtMoves.setText(buildMoveList());
    }

    private CharSequence buildMoveList() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        List<String> sans = game.movesSan;
        int gold = ContextCompat.getColor(requireContext(), R.color.gold_accent);
        for (int i = 0; i < sans.size(); i++) {
            if (i % 2 == 0) sb.append((i / 2 + 1) + ". ");
            int start = sb.length();
            sb.append(sans.get(i));
            if (i == ply - 1) {
                sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), 0);
                sb.setSpan(new ForegroundColorSpan(gold), start, sb.length(), 0);
            }
            sb.append("  ");
        }
        return sb;
    }

    private String playerLabel(String title, String name, String elo) {
        StringBuilder s = new StringBuilder();
        if (title != null && !title.isEmpty()) s.append(title).append(' ');
        s.append(name);
        if (elo != null && !elo.isEmpty()) s.append(" (").append(elo).append(')');
        return s.toString();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(poll);
    }
}
