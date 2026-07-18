package com.ptchess.club.ui.parent;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.firebase.FirebaseAuthService;
import com.ptchess.club.data.firebase.FirebaseProfile;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.ui.common.UserAdapter;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.util.List;

public class ChildrenFragment extends Fragment {

    private RecyclerView recycler;
    private TextView emptyView;
    private User parent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        parent = ((MainActivity) requireActivity()).getCurrentUser();
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.my_children);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        emptyView.setText(R.string.no_results);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        MaterialButton add = view.findViewById(R.id.btnAction);
        add.setVisibility(View.VISIBLE);
        add.setText(R.string.add_child);
        add.setOnClickListener(v -> showAddChild());

        load();
    }

    private void load() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<User> kids = repo.getChildrenForParent(parent.id);
            Async.main(() -> {
                if (!isAdded()) return;
                recycler.setAdapter(new UserAdapter(kids, this::bindActions));
                emptyView.setVisibility(kids.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void bindActions(User child, MaterialButton primary, MaterialButton secondary) {
        primary.setVisibility(View.VISIBLE);
        primary.setText(R.string.contact_tutor);
        primary.setOnClickListener(v -> contactTutor(child));

        secondary.setVisibility(View.VISIBLE);
        secondary.setText(R.string.contact_admin);
        secondary.setOnClickListener(v -> contactAdmin());
    }

    private void contactTutor(User child) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            User tutor = repo.getPrimaryTutorForChild(child.id);
            Async.main(() -> showContact(tutor));
        });
    }

    private void contactAdmin() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            User admin = repo.getClubAdmin(parent.clubId);
            Async.main(() -> showContact(admin));
        });
    }

    private void showContact(User contact) {
        if (!isAdded()) return;
        if (contact == null || contact.phone == null || contact.phone.isEmpty()) {
            UiUtils.toast(requireContext(), "—");
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(contact.fullName)
                .setItems(new CharSequence[]{
                        getString(R.string.call), getString(R.string.whatsapp)}, (d, which) -> {
                    if (which == 0) UiUtils.dial(requireContext(), contact.phone);
                    else UiUtils.whatsapp(requireContext(), contact.phone);
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showAddChild() {
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_two_fields, null, false);
        EditText email = form.findViewById(R.id.field1);
        EditText password = form.findViewById(R.id.field2);
        email.setHint(getString(R.string.child_email));
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        password.setHint(getString(R.string.child_password));
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setMinLines(1);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_child)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String e = email.getText().toString().trim();
                    String p = password.getText().toString();
                    if (FirebaseAuthService.enabled(requireContext())) {
                        addChildFirebase(e, p);
                    } else {
                        addChildLocal(e, p);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void addChildLocal(String childEmail, String childPassword) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            String name = repo.addChildToParent(parent.id, childEmail, childPassword);
            Async.main(() -> {
                if (!isAdded()) return;
                if (name != null) {
                    UiUtils.toast(requireContext(), getString(R.string.msg_parent_linked, name));
                    load();
                } else {
                    UiUtils.toast(requireContext(), R.string.err_login_failed);
                }
            });
        });
    }

    /**
     * Under Firebase Auth the child's password lives in Firebase, so we verify with a
     * secondary session, record the link on the parent's cloud profile (so it follows
     * the parent across devices), and mirror it locally so this list shows the child.
     */
    private void addChildFirebase(String childEmail, String childPassword) {
        if (childEmail.isEmpty()) {
            UiUtils.toast(requireContext(), R.string.err_login_failed);
            return;
        }
        FirebaseAuthService.verifyCredentials(requireContext(), childEmail, childPassword,
                new FirebaseAuthService.AuthCb() {
                    @Override public void onSuccess(String childUid) {
                        FirebaseProfile.fetch(childUid, child -> {
                            if (!isAdded()) return;
                            if (child == null) {
                                UiUtils.toast(requireContext(), R.string.err_login_failed);
                                return;
                            }
                            String parentUid = FirebaseAuthService.currentUid();
                            if (parentUid != null) FirebaseProfile.addChildEmail(parentUid, childEmail);
                            ClubRepository repo = ClubRepository.getInstance(requireContext());
                            Async.io(() -> {
                                long childLocalId = repo.upsertLocalUser(child.fullName, childEmail,
                                        child.phone, child.role, child.status, child.clubId, child.rating);
                                repo.linkParentToChild(parent.id, childLocalId);
                                Async.main(() -> {
                                    if (!isAdded()) return;
                                    UiUtils.toast(requireContext(),
                                            getString(R.string.msg_parent_linked, child.fullName));
                                    load();
                                });
                            });
                        });
                    }
                    @Override public void onError(String message) {
                        if (isAdded()) UiUtils.toast(requireContext(), R.string.err_login_failed);
                    }
                });
    }
}
