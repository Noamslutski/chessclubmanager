package com.ptchess.club.ui.admin;

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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.Player;
import com.ptchess.club.data.model.User;
import com.ptchess.club.data.remote.ParseBotApi;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Verified club owners can search the federation players database and add
 * players to their club roster (deduped per club). See the README for why the
 * true "no duplicates across owners" + super-admin approval flow needs a backend.
 */
public class PlayersFragment extends Fragment {

    private RecyclerView recycler;
    private TextView emptyView, note;
    private TextInputEditText search;
    private View searchRow;
    private User user;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_players, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        user = ((MainActivity) requireActivity()).getCurrentUser();
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        note = view.findViewById(R.id.txtPlayersNote);
        search = view.findViewById(R.id.inputSearch);
        searchRow = view.findViewById(R.id.searchRow);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        MaterialButton searchBtn = view.findViewById(R.id.btnSearch);
        searchBtn.setOnClickListener(v -> runSearch());

        gateAndLoad();
    }

    /** Only verified-club admins (or the super admin) may import players. */
    private void gateAndLoad() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            boolean allowed = ClubRepository.isSuperAdmin(user) || repo.isClubVerified(user.clubId);
            boolean hasKey = ParseBotApi.hasApiKey();
            List<Player> roster = repo.getPlayers(user.clubId);
            Async.main(() -> {
                if (!isAdded()) return;
                if (!allowed) {
                    searchRow.setVisibility(View.GONE);
                    note.setVisibility(View.VISIBLE);
                    note.setText(R.string.players_not_verified);
                } else if (!hasKey) {
                    note.setVisibility(View.VISIBLE);
                    note.setText(R.string.players_no_key);
                }
                showRoster(roster);
            });
        });
    }

    private void showRoster(List<Player> roster) {
        List<PlayerAdapter.Row> rows = new ArrayList<>();
        for (Player p : roster) {
            rows.add(new PlayerAdapter.Row(p.fullName, meta(p.rating, p.federation), false, null));
        }
        recycler.setAdapter(new PlayerAdapter(rows));
        emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        emptyView.setText(R.string.players_none);
    }

    private void runSearch() {
        String name = search.getText() == null ? "" : search.getText().toString().trim();
        if (name.isEmpty()) return;
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<ParseBotApi.FoundPlayer> found = ParseBotApi.searchPlayers(name);
            Async.main(() -> {
                if (!isAdded()) return;
                List<PlayerAdapter.Row> rows = new ArrayList<>();
                for (ParseBotApi.FoundPlayer fp : found) {
                    rows.add(new PlayerAdapter.Row(fp.name, meta(fp.rating, fp.federation),
                            true, () -> addPlayer(fp)));
                }
                recycler.setAdapter(new PlayerAdapter(rows));
                emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                emptyView.setText(R.string.players_none);
            });
        });
    }

    private void addPlayer(ParseBotApi.FoundPlayer fp) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            boolean added = repo.addPlayer(user.clubId, fp.externalId, fp.name, fp.rating, fp.federation);
            Async.main(() -> {
                if (!isAdded()) return;
                UiUtils.toast(requireContext(), added
                        ? getString(R.string.player_added, fp.name)
                        : getString(R.string.player_exists));
            });
        });
    }

    private String meta(int rating, String fed) {
        String r = rating > 0 ? String.valueOf(rating) : "—";
        return (fed == null || fed.isEmpty()) ? r : r + " · " + fed;
    }
}
