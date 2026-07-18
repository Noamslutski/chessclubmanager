package com.ptchess.club.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.firebase.FirebaseContent;
import com.ptchess.club.data.model.Group;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class GroupsFragment extends Fragment {

    private RecyclerView recycler;
    private TextView emptyView;
    private User user;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        user = ((MainActivity) requireActivity()).getCurrentUser();
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.my_groups);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        emptyView.setText(R.string.no_groups);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        MaterialButton add = view.findViewById(R.id.btnAction);
        if (user.role == Role.ADMIN) {
            add.setVisibility(View.VISIBLE);
            add.setText(R.string.add_group);
            add.setOnClickListener(v -> showAddGroup());
        }

        load();
    }

    private void load() {
        if (FirebaseContent.enabled(requireContext())) {
            FirebaseContent.resolveParentKids(requireContext(), user, kids ->
                    FirebaseContent.fetchGroups(user, kids, new FirebaseContent.GroupsCb() {
                        @Override public void ok(List<Group> groups) { render(groups); }
                        @Override public void fail() { loadLocal(); }
                    }));
        } else {
            loadLocal();
        }
    }

    private void loadLocal() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<Group> groups = repo.getGroupsForUser(user);
            Async.main(() -> render(groups));
        });
    }

    private void render(List<Group> groups) {
        if (!isAdded()) return;
        recycler.setAdapter(new GroupAdapter(groups, this::showGroup));
        emptyView.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showGroup(Group g) {
        String[] weekdays = getResources().getStringArray(R.array.weekdays);
        String day = (g.dayIndex >= 0 && g.dayIndex < weekdays.length) ? weekdays[g.dayIndex] : "";
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.group_tutor, g.tutorName)).append('\n');
        sb.append(getString(R.string.group_when, day, g.time)).append("\n\n");

        boolean staff = user.role == Role.ADMIN || user.role == Role.TUTOR;
        if (!staff) {
            dialog(g, sb.toString());
            return;
        }
        if (g.cloudId != null) {
            // Cloud group — member names are denormalised on the document.
            for (String n : g.memberNames) sb.append("• ").append(n).append('\n');
            dialog(g, sb.toString());
        } else {
            ClubRepository repo = ClubRepository.getInstance(requireContext());
            Async.io(() -> {
                List<User> members = repo.getGroupMembers(g.id);
                StringBuilder body = new StringBuilder(sb);
                for (User m : members) body.append("• ").append(m.fullName).append('\n');
                Async.main(() -> dialog(g, body.toString()));
            });
        }
    }

    private void dialog(Group g, String body) {
        if (!isAdded()) return;
        androidx.appcompat.app.AlertDialog.Builder b =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(g.name)
                        .setMessage(body)
                        .setPositiveButton(R.string.close, null);
        // Admins can add students to the group.
        if (user.role == Role.ADMIN) {
            b.setNeutralButton(R.string.add_member, (d, w) -> showAddMembers(g));
        }
        b.show();
    }

    // ---------------------------------------------------------- add group

    private void showAddGroup() {
        if (FirebaseContent.enabled(requireContext())) {
            FirebaseContent.clubUsersByRole(user.clubId, Role.TUTOR, new FirebaseContent.UsersCb() {
                @Override public void ok(List<User> tutors) { onTutorsLoaded(tutors); }
                @Override public void fail() { loadTutorsLocal(); }
            });
        } else {
            loadTutorsLocal();
        }
    }

    private void loadTutorsLocal() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<User> tutors = repo.getClubUsersByRole(user.clubId, Role.TUTOR);
            Async.main(() -> onTutorsLoaded(tutors));
        });
    }

    private void onTutorsLoaded(List<User> tutors) {
        if (!isAdded()) return;
        if (tutors.isEmpty()) {
            UiUtils.toast(requireContext(), R.string.no_tutors);
            return;
        }
        buildAddGroupDialog(tutors);
    }

    private void buildAddGroupDialog(List<User> tutors) {
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_group, null, false);
        EditText nameField = form.findViewById(R.id.groupName);
        EditText timeField = form.findViewById(R.id.groupTime);
        Spinner daySpinner = form.findViewById(R.id.groupDay);
        Spinner tutorSpinner = form.findViewById(R.id.groupTutor);

        daySpinner.setAdapter(spinner(getResources().getStringArray(R.array.weekdays)));
        String[] tutorNames = new String[tutors.size()];
        for (int i = 0; i < tutors.size(); i++) tutorNames[i] = tutors.get(i).fullName;
        tutorSpinner.setAdapter(spinner(tutorNames));

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_group)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = nameField.getText().toString().trim();
                    if (name.isEmpty()) return;
                    int dayIndex = daySpinner.getSelectedItemPosition();
                    String time = timeField.getText().toString().trim();
                    User tutor = tutors.get(tutorSpinner.getSelectedItemPosition());
                    if (FirebaseContent.enabled(requireContext())) {
                        FirebaseContent.addGroup(user.clubId, name, dayIndex, time,
                                tutor.email, tutor.fullName, this::load);
                    } else {
                        ClubRepository repo = ClubRepository.getInstance(requireContext());
                        Async.io(() -> {
                            repo.addGroup(user.clubId, name, dayIndex, time, tutor.id);
                            Async.main(this::load);
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------- add members

    private void showAddMembers(Group g) {
        if (g.cloudId != null) {
            FirebaseContent.clubUsersByRole(user.clubId, Role.CHILD, new FirebaseContent.UsersCb() {
                @Override public void ok(List<User> children) { pickMembers(g, children); }
                @Override public void fail() { loadChildrenLocal(g); }
            });
        } else {
            loadChildrenLocal(g);
        }
    }

    private void loadChildrenLocal(Group g) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<User> children = repo.getClubUsersByRole(user.clubId, Role.CHILD);
            Async.main(() -> pickMembers(g, children));
        });
    }

    private void pickMembers(Group g, List<User> children) {
        if (!isAdded()) return;
        if (children.isEmpty()) {
            UiUtils.toast(requireContext(), R.string.no_results);
            return;
        }
        String[] names = new String[children.size()];
        boolean[] checked = new boolean[children.size()];
        for (int i = 0; i < children.size(); i++) names[i] = children.get(i).fullName;

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_member)
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.save, (d, w) -> saveMembers(g, children, checked))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveMembers(Group g, List<User> children, boolean[] checked) {
        if (g.cloudId != null) {
            List<String> emails = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < children.size(); i++) {
                if (checked[i]) {
                    emails.add(children.get(i).email);
                    names.add(children.get(i).fullName);
                }
            }
            if (emails.isEmpty()) return;
            FirebaseContent.addGroupMembers(user.clubId, g.cloudId, emails, names, () -> {
                if (!isAdded()) return;
                UiUtils.toast(requireContext(), getString(R.string.members_added, emails.size()));
                load();
            });
        } else {
            ClubRepository repo = ClubRepository.getInstance(requireContext());
            Async.io(() -> {
                int count = 0;
                for (int i = 0; i < children.size(); i++) {
                    if (checked[i]) {
                        repo.addGroupMember(g.id, children.get(i).id);
                        count++;
                    }
                }
                int finalCount = count;
                Async.main(() -> {
                    if (!isAdded()) return;
                    UiUtils.toast(requireContext(), getString(R.string.members_added, finalCount));
                    load();
                });
            });
        }
    }

    private ArrayAdapter<String> spinner(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }
}
