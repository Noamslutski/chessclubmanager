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

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.ui.common.UserAdapter;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.util.List;

/** Admin center: approve/reject pending registrations and change user roles. */
public class AdminFragment extends Fragment {

    private RecyclerView recycler;
    private TextView emptyView;
    private boolean pendingMode = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggleGroup);
        toggle.check(R.id.btnPending);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            pendingMode = checkedId == R.id.btnPending;
            load();
        });

        load();
    }

    private void load() {
        emptyView.setText(pendingMode ? R.string.no_pending : R.string.manage_users);
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        long clubId = ((MainActivity) requireActivity()).getCurrentUser().clubId;
        Async.io(() -> {
            List<User> users = pendingMode
                    ? repo.getPendingUsers(clubId) : repo.getAllUsers(clubId);
            Async.main(() -> {
                if (!isAdded()) return;
                recycler.setAdapter(new UserAdapter(users, this::bindActions));
                emptyView.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void bindActions(User user, com.google.android.material.button.MaterialButton primary,
                             com.google.android.material.button.MaterialButton secondary) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        if (pendingMode) {
            primary.setVisibility(View.VISIBLE);
            primary.setText(R.string.approve);
            primary.setOnClickListener(v -> Async.io(() -> {
                repo.approveUser(user.id);
                Async.main(this::load);
            }));

            secondary.setVisibility(View.VISIBLE);
            secondary.setText(R.string.reject);
            secondary.setOnClickListener(v -> Async.io(() -> {
                repo.rejectUser(user.id);
                Async.main(this::load);
            }));
        } else {
            primary.setVisibility(View.VISIBLE);
            primary.setText(R.string.set_role);
            primary.setOnClickListener(v -> showRolePicker(user));
            secondary.setVisibility(View.GONE);
        }
    }

    private void showRolePicker(User user) {
        Role[] roles = {Role.CHILD, Role.TUTOR, Role.PARENT, Role.ADMIN};
        String[] labels = new String[roles.length];
        int current = 0;
        for (int i = 0; i < roles.length; i++) {
            labels[i] = getString(roles[i].displayRes);
            if (roles[i] == user.role) current = i;
        }
        final int[] choice = {current};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.set_role)
                .setSingleChoiceItems(labels, current, (d, which) -> choice[0] = which)
                .setPositiveButton(R.string.save, (d, w) -> {
                    Role newRole = roles[choice[0]];
                    ClubRepository repo = ClubRepository.getInstance(requireContext());
                    Async.io(() -> {
                        repo.setRole(user.id, newRole);
                        Async.main(() -> {
                            UiUtils.toast(requireContext(), getString(newRole.displayRes));
                            load();
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
