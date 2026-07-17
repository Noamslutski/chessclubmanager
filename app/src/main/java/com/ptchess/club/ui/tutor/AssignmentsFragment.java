package com.ptchess.club.ui.tutor;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.firebase.FirebaseFiles;
import com.ptchess.club.data.model.Assignment;
import com.ptchess.club.data.model.Group;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.ui.common.AssignmentAdapter;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.util.List;

public class AssignmentsFragment extends Fragment implements AssignmentAdapter.OnAssignmentAction {

    private RecyclerView recycler;
    private TextView emptyView;
    private User user;

    private ActivityResultLauncher<String[]> filePicker;
    private String pickedUri;
    private TextView pickedLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        user = ((MainActivity) requireActivity()).getCurrentUser();
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.assignments_title);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        emptyView.setText(R.string.no_results);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        MaterialButton add = view.findViewById(R.id.btnAction);
        if (user.role == Role.TUTOR || user.role == Role.ADMIN) {
            add.setVisibility(View.VISIBLE);
            add.setText(R.string.new_assignment);
            add.setOnClickListener(v -> showAddAssignment());
        }

        filePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onFilePicked);

        load();
    }

    private void load() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<Assignment> list = repo.getAssignmentsForUser(user);
            Async.main(() -> {
                if (!isAdded()) return;
                recycler.setAdapter(new AssignmentAdapter(list, user.role == Role.CHILD, this));
                emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    public void onToggleDone(Assignment a) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        boolean newState = !a.completed;
        Async.io(() -> {
            repo.markAssignmentDone(a.id, user.id, newState);
            Async.main(this::load);
        });
    }

    @Override
    public void onOpenAttachment(Assignment a) {
        UiUtils.openUri(requireContext(), a.fileUri, null);
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        pickedUri = uri.toString();
        if (pickedLabel != null) pickedLabel.setText(pickedUri);
    }

    private void showAddAssignment() {
        pickedUri = null;
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<Group> groups = repo.getGroupsForUser(user);
            Async.main(() -> {
                if (!isAdded()) return;
                if (groups.isEmpty()) {
                    UiUtils.toast(requireContext(), R.string.no_groups);
                    return;
                }
                buildAddDialog(groups);
            });
        });
    }

    private void buildAddDialog(List<Group> groups) {
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_assignment, null, false);
        Spinner groupSpinner = form.findViewById(R.id.assignGroup);
        EditText title = form.findViewById(R.id.assignTitle);
        EditText desc = form.findViewById(R.id.assignDesc);
        EditText due = form.findViewById(R.id.assignDue);
        pickedLabel = form.findViewById(R.id.txtChosenFile);

        String[] names = new String[groups.size()];
        for (int i = 0; i < groups.size(); i++) names[i] = groups.get(i).name;
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        groupSpinner.setAdapter(a);

        form.findViewById(R.id.btnChooseFile).setOnClickListener(v ->
                filePicker.launch(new String[]{"application/pdf", "text/plain",
                        "application/x-chess-pgn", "*/*"}));

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.new_assignment)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String t = title.getText().toString().trim();
                    if (t.isEmpty()) return;
                    Group g = groups.get(groupSpinner.getSelectedItemPosition());
                    String description = desc.getText().toString().trim();
                    String dueDate = due.getText().toString().trim();
                    // Upload the attachment to Storage first (if Firebase is on), then save the
                    // assignment with the resulting URL; without Firebase the local URI is kept.
                    resolveUpload(pickedUri, fileUrl -> {
                        ClubRepository repo = ClubRepository.getInstance(requireContext());
                        Async.io(() -> {
                            repo.addAssignment(user.clubId, g.id, t, description, fileUrl, dueDate, user.id);
                            Async.main(this::load);
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Uploads the picked attachment to Storage when Firebase is on; otherwise passes the local URI through. */
    private void resolveUpload(String pickedFile, java.util.function.Consumer<String> then) {
        if (pickedFile == null || !FirebaseFiles.enabled(requireContext())) {
            then.accept(pickedFile);
            return;
        }
        FirebaseFiles.upload(requireContext(), FirebaseFiles.ASSIGNMENTS, user.clubId, Uri.parse(pickedFile),
                new FirebaseFiles.UploadCb() {
                    @Override public void onSuccess(String url) { then.accept(url); }
                    @Override public void onError(String message) { then.accept(pickedFile); }
                });
    }
}
