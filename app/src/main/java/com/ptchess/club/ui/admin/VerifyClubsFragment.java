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

import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.Club;
import com.ptchess.club.util.Async;

import java.util.List;

/** Super-admin only: verify or unverify clubs (gates the player-import feature). */
public class VerifyClubsFragment extends Fragment {

    private RecyclerView recycler;
    private TextView emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.verify_clubs_title);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        emptyView.setText(R.string.no_clubs);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        load();
    }

    private void load() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<Club> clubs = repo.getClubs();
            Async.main(() -> {
                if (!isAdded()) return;
                recycler.setAdapter(new ClubAdapter(clubs, this::toggle));
                emptyView.setVisibility(clubs.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void toggle(Club club) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            repo.setClubVerified(club.id, !club.verified);
            Async.main(this::load);
        });
    }
}
