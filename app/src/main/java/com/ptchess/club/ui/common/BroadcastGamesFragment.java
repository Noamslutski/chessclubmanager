package com.ptchess.club.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ptchess.club.R;
import com.ptchess.club.chess.PgnImporter;
import com.ptchess.club.data.remote.LichessBroadcast;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;

import java.util.ArrayList;
import java.util.List;

/** Lists the games of a broadcast round; tapping opens the live game viewer. */
public class BroadcastGamesFragment extends Fragment {

    public static final String ARG_ROUND_ID = "round_id";
    public static final String ARG_TITLE = "title";

    private RecyclerView recycler;
    private TextView emptyView;
    private View loadingBar;
    private String roundId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        roundId = getArguments() != null ? getArguments().getString(ARG_ROUND_ID) : null;
        String title = getArguments() != null ? getArguments().getString(ARG_TITLE) : "";
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.watch_select_game);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        loadingBar = view.findViewById(R.id.loadingBar);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        load(title);
    }

    private void load(String title) {
        loadingBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        Async.io(() -> {
            String pgn = roundId != null ? LichessBroadcast.roundPgn(roundId) : null;
            List<PgnImporter.GameRecord> games = PgnImporter.parseGames(pgn);
            Async.main(() -> {
                if (!isAdded()) return;
                loadingBar.setVisibility(View.GONE);
                List<MoreAdapter.MoreItem> items = new ArrayList<>();
                MainActivity host = (MainActivity) requireActivity();
                for (int i = 0; i < games.size(); i++) {
                    PgnImporter.GameRecord g = games.get(i);
                    String board = g.board.isEmpty() ? "" : getString(R.string.board_number, g.board) + " · ";
                    String result = "*".equals(g.result) ? "" : "  " + g.result;
                    String label = board + g.white + " – " + g.black + result;
                    final int index = i;
                    items.add(new MoreAdapter.MoreItem(R.drawable.ic_watch, label,
                            () -> openViewer(host, index, title)));
                }
                recycler.setAdapter(new MoreAdapter(items));
                emptyView.setText(R.string.watch_no_games);
                emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void openViewer(MainActivity host, int gameIndex, String title) {
        GameViewerFragment f = new GameViewerFragment();
        Bundle args = new Bundle();
        args.putString(GameViewerFragment.ARG_ROUND_ID, roundId);
        args.putInt(GameViewerFragment.ARG_GAME_INDEX, gameIndex);
        f.setArguments(args);
        host.openSection(f, title);
    }
}
