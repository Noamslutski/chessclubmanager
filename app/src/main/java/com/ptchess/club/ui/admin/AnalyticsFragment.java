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
import com.ptchess.club.data.gamification.ActivityLog;
import com.ptchess.club.data.gamification.Gamification;
import com.ptchess.club.data.model.Activity;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Admin club analytics: engagement totals and an XP leaderboard. */
public class AnalyticsFragment extends Fragment {

    private User user;
    private TextView statMembers, statActive, statPuzzles, statAssignments, statTournaments, txtEmpty;
    private RecyclerView recycler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_analytics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        user = ((MainActivity) requireActivity()).getCurrentUser();
        statMembers = view.findViewById(R.id.statMembers);
        statActive = view.findViewById(R.id.statActive);
        statPuzzles = view.findViewById(R.id.statPuzzlesSolved);
        statAssignments = view.findViewById(R.id.statAssignmentsDone);
        statTournaments = view.findViewById(R.id.statTournamentsPlayed);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        recycler = view.findViewById(R.id.recyclerLeaderboard);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        load();
    }

    private void load() {
        long clubId = user.clubId;
        ActivityLog.clubNames(requireContext(), clubId, names -> {
            if (!isAdded()) return;
            ActivityLog.forClub(requireContext(), clubId, activities -> {
                if (!isAdded()) return;
                render(activities, names);
            });
        });
    }

    private void render(List<Activity> all, Map<String, String> names) {
        Gamification.Stats agg = Gamification.summarize(all, LocalDate.now());
        statMembers.setText(String.valueOf(names.size()));
        statActive.setText(String.valueOf(Gamification.activeSince(all, LocalDate.now().minusDays(7))));
        statPuzzles.setText(String.valueOf(agg.puzzles));
        statAssignments.setText(String.valueOf(agg.assignments));
        statTournaments.setText(String.valueOf(agg.tournaments));

        List<Gamification.Ranked> board = Gamification.leaderboard(all, names);
        recycler.setAdapter(new LeaderboardAdapter(board));
        txtEmpty.setVisibility(board.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
