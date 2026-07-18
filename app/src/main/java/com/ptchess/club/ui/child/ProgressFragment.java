package com.ptchess.club.ui.child;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.ptchess.club.R;
import com.ptchess.club.data.gamification.ActivityLog;
import com.ptchess.club.data.gamification.Gamification;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.ui.common.BadgeAdapter;

import java.time.LocalDate;

/** Student progress: level, XP progress, streak, activity counts and badges. */
public class ProgressFragment extends Fragment {

    private User user;
    private TextView txtLevel, txtXp, txtStreak, statPuzzles, statAssignments, statTournaments, txtEmpty;
    private LinearProgressIndicator xpBar;
    private RecyclerView recyclerBadges;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_progress, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        user = ((MainActivity) requireActivity()).getCurrentUser();
        txtLevel = view.findViewById(R.id.txtLevel);
        txtXp = view.findViewById(R.id.txtXp);
        txtStreak = view.findViewById(R.id.txtStreak);
        statPuzzles = view.findViewById(R.id.statPuzzles);
        statAssignments = view.findViewById(R.id.statAssignments);
        statTournaments = view.findViewById(R.id.statTournaments);
        txtEmpty = view.findViewById(R.id.txtEmpty);
        xpBar = view.findViewById(R.id.xpBar);
        recyclerBadges = view.findViewById(R.id.recyclerBadges);
        recyclerBadges.setLayoutManager(new GridLayoutManager(requireContext(), 3));

        load();
    }

    private void load() {
        ActivityLog.forUser(requireContext(), user, activities -> {
            if (!isAdded()) return;
            render(Gamification.summarize(activities, LocalDate.now()));
        });
    }

    private void render(Gamification.Stats s) {
        txtLevel.setText(getString(R.string.gam_level, s.level));
        xpBar.setMax(s.xpForLevel);
        xpBar.setProgress(s.xpIntoLevel);
        txtXp.setText(getString(R.string.gam_xp_progress, s.xpIntoLevel, s.xpForLevel));
        txtStreak.setText("🔥 " + getString(R.string.gam_streak_days, s.currentStreak));
        statPuzzles.setText(String.valueOf(s.puzzles));
        statAssignments.setText(String.valueOf(s.assignments));
        statTournaments.setText(String.valueOf(s.tournaments));
        recyclerBadges.setAdapter(new BadgeAdapter(s.badges));
        txtEmpty.setVisibility(s.xp == 0 ? View.VISIBLE : View.GONE);
    }
}
