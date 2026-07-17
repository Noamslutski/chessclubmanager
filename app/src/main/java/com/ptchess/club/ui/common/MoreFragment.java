package com.ptchess.club.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ptchess.club.R;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.ui.admin.AdminFragment;
import com.ptchess.club.ui.admin.PlayersFragment;
import com.ptchess.club.ui.admin.VerifyClubsFragment;
import com.ptchess.club.ui.parent.ChildrenFragment;
import com.ptchess.club.ui.tutor.AssignmentsFragment;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.util.Async;

import java.util.ArrayList;
import java.util.List;

public class MoreFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_more, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RecyclerView recycler = view.findViewById(R.id.recyclerMore);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        MainActivity host = (MainActivity) requireActivity();
        User user = host.getCurrentUser();
        List<MoreAdapter.MoreItem> items = new ArrayList<>();

        items.add(new MoreAdapter.MoreItem(R.drawable.ic_groups,
                getString(R.string.nav_groups),
                () -> host.openSection(new GroupsFragment(), getString(R.string.nav_groups))));

        items.add(new MoreAdapter.MoreItem(R.drawable.ic_assignment,
                getString(R.string.nav_assignments),
                () -> host.openSection(new AssignmentsFragment(), getString(R.string.nav_assignments))));

        items.add(new MoreAdapter.MoreItem(R.drawable.ic_tournament,
                getString(R.string.nav_tournaments),
                () -> host.openSection(new TournamentsFragment(), getString(R.string.nav_tournaments))));

        if (user.role == Role.PARENT) {
            items.add(new MoreAdapter.MoreItem(R.drawable.ic_groups,
                    getString(R.string.nav_children),
                    () -> host.openSection(new ChildrenFragment(), getString(R.string.nav_children))));
        }

        if (user.role == Role.ADMIN) {
            items.add(new MoreAdapter.MoreItem(R.drawable.ic_admin,
                    getString(R.string.nav_admin),
                    () -> host.openSection(new AdminFragment(), getString(R.string.nav_admin))));
        }

        items.add(new MoreAdapter.MoreItem(R.drawable.ic_settings,
                getString(R.string.nav_settings),
                () -> host.openSection(new SettingsFragment(), getString(R.string.nav_settings))));

        MoreAdapter adapter = new MoreAdapter(items);
        recycler.setAdapter(adapter);

        // Verified-club admins get "Import players"; the super admin also gets
        // "Verify clubs". Both depend on DB state, so they are resolved async.
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        boolean superAdmin = ClubRepository.isSuperAdmin(user);
        Async.io(() -> {
            boolean verified = repo.isClubVerified(user.clubId);
            Async.main(() -> {
                if (!isAdded()) return;
                int insertAt = Math.max(0, items.size() - 1); // before Settings
                if (user.role == Role.ADMIN && (verified || superAdmin)) {
                    items.add(insertAt, new MoreAdapter.MoreItem(R.drawable.ic_groups,
                            getString(R.string.nav_players),
                            () -> host.openSection(new PlayersFragment(),
                                    getString(R.string.nav_players))));
                }
                if (superAdmin) {
                    items.add(new MoreAdapter.MoreItem(R.drawable.ic_admin,
                            getString(R.string.nav_verify_clubs),
                            () -> host.openSection(new VerifyClubsFragment(),
                                    getString(R.string.nav_verify_clubs))));
                }
                adapter.notifyDataSetChanged();
            });
        });
    }
}
