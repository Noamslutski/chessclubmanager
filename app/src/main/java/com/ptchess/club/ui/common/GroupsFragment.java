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
import com.ptchess.club.data.model.Group;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;

import java.util.List;

public class GroupsFragment extends Fragment {

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
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.my_groups);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        emptyView.setText(R.string.no_groups);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        User user = ((MainActivity) requireActivity()).getCurrentUser();
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<Group> groups = repo.getGroupsForUser(user);
            Async.main(() -> {
                if (!isAdded()) return;
                recycler.setAdapter(new GroupAdapter(groups, g -> showGroup(g, user)));
                emptyView.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showGroup(Group g, User viewer) {
        String[] weekdays = getResources().getStringArray(R.array.weekdays);
        String day = (g.dayIndex >= 0 && g.dayIndex < weekdays.length) ? weekdays[g.dayIndex] : "";
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.group_tutor, g.tutorName)).append('\n');
        sb.append(getString(R.string.group_when, day, g.time)).append("\n\n");

        // Only staff see the full member roster.
        if (viewer.role == Role.ADMIN || viewer.role == Role.TUTOR) {
            ClubRepository repo = ClubRepository.getInstance(requireContext());
            Async.io(() -> {
                List<User> members = repo.getGroupMembers(g.id);
                StringBuilder body = new StringBuilder(sb);
                for (User m : members) body.append("• ").append(m.fullName).append('\n');
                Async.main(() -> dialog(g.name, body.toString()));
            });
        } else {
            dialog(g.name, sb.toString());
        }
    }

    private void dialog(String title, String body) {
        if (!isAdded()) return;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
