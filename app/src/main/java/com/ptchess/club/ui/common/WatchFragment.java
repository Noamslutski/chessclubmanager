package com.ptchess.club.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ptchess.club.R;
import com.ptchess.club.chess.ChessBoard;
import com.ptchess.club.chess.ChessBoardView;
import com.ptchess.club.data.remote.LichessTv;
import com.ptchess.club.util.Async;

/** Watch the current featured Lichess TV game live (read-only board). */
public class WatchFragment extends Fragment {

    private ChessBoardView board;
    private TextView whiteName, blackName, status;
    private LichessTv tv;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_watch, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        board = view.findViewById(R.id.boardView);
        whiteName = view.findViewById(R.id.txtWhiteName);
        blackName = view.findViewById(R.id.txtBlackName);
        status = view.findViewById(R.id.txtWatchStatus);

        board.setInteractive(false);
        board.setBoard(ChessBoard.fromFen(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
        status.setText(R.string.watch_connecting);

        startStream();
    }

    private void startStream() {
        tv = new LichessTv();
        Async.io(() -> tv.stream(new LichessTv.Listener() {
            @Override
            public void onPosition(String fen, String lm, String white, String black) {
                Async.main(() -> {
                    if (!isAdded()) return;
                    board.setBoard(ChessBoard.fromFen(fen));
                    if (lm != null && lm.length() >= 4) {
                        int fromRow = 8 - (lm.charAt(1) - '0');
                        int fromCol = lm.charAt(0) - 'a';
                        int toRow = 8 - (lm.charAt(3) - '0');
                        int toCol = lm.charAt(2) - 'a';
                        board.setLastMove(fromRow, fromCol, toRow, toCol);
                    }
                    whiteName.setText("♔ " + white);
                    blackName.setText("♚ " + black);
                    status.setText("");
                });
            }

            @Override
            public void onError() {
                Async.main(() -> {
                    if (isAdded()) status.setText(R.string.watch_failed);
                });
            }
        }));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tv != null) tv.stop();
    }
}
