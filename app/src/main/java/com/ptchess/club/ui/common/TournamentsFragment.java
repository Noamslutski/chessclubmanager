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
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.TournamentResult;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;

import java.util.List;

public class TournamentsFragment extends Fragment {

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
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.tournaments_title);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        emptyView.setText(R.string.no_results);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        User user = ((MainActivity) requireActivity()).getCurrentUser();
        // Staff and parents see whose result each row is; a student sees only their own.
        boolean showChild = user.role != Role.CHILD;

        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<TournamentResult> results = repo.getResultsForUser(user);
            Async.main(() -> {
                if (!isAdded()) return;
                recycler.setAdapter(new ResultAdapter(results, showChild));
                emptyView.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }
}
