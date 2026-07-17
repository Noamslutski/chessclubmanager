package com.ptchess.club.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.Club;
import com.ptchess.club.security.InputValidator;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class RegisterFragment extends Fragment {

    private TextInputEditText name, email, phone, password, confirm, childEmail, childPassword, newClub;
    private TextInputLayout newClubLayout;
    private Spinner clubSpinner;
    private MaterialButton btnRegister;

    private final List<Club> clubs = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        name = view.findViewById(R.id.inputName);
        email = view.findViewById(R.id.inputEmail);
        phone = view.findViewById(R.id.inputPhone);
        password = view.findViewById(R.id.inputPassword);
        confirm = view.findViewById(R.id.inputConfirm);
        childEmail = view.findViewById(R.id.inputChildEmail);
        childPassword = view.findViewById(R.id.inputChildPassword);
        newClub = view.findViewById(R.id.inputNewClub);
        newClubLayout = view.findViewById(R.id.newClubLayout);
        clubSpinner = view.findViewById(R.id.clubSpinner);
        btnRegister = view.findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> attemptRegister());
        view.findViewById(R.id.linkLogin).setOnClickListener(v -> auth().showLogin());

        // Position 0 = "Create a new club"; the rest are existing clubs.
        clubSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                newClubLayout.setVisibility(pos == 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        loadClubs();
    }

    private void loadClubs() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<Club> loaded = repo.getClubs();
            Async.main(() -> {
                if (!isAdded()) return;
                clubs.clear();
                clubs.addAll(loaded);
                List<String> labels = new ArrayList<>();
                labels.add(getString(R.string.create_new_club));
                for (Club c : clubs) labels.add(c.name);
                ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_item, labels);
                a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                clubSpinner.setAdapter(a);
            });
        });
    }

    private void attemptRegister() {
        String fullName = text(name);
        String mail = text(email);
        String pass = text(password);
        String conf = text(confirm);
        String ph = text(phone);
        String cMail = text(childEmail);
        String cPass = text(childPassword);

        if (!InputValidator.isValidName(fullName)) {
            UiUtils.toast(requireContext(), R.string.err_required);
            return;
        }
        if (!InputValidator.isValidEmail(InputValidator.normalizeEmail(mail))) {
            UiUtils.toast(requireContext(), R.string.err_email_invalid);
            return;
        }
        if (!InputValidator.isStrongPassword(pass)) {
            UiUtils.toast(requireContext(), R.string.err_password_weak);
            return;
        }
        if (!pass.equals(conf)) {
            UiUtils.toast(requireContext(), R.string.err_passwords_mismatch);
            return;
        }

        // Resolve club choice (ignored when linking to a child — that inherits the child's club).
        int pos = clubSpinner.getSelectedItemPosition();
        long joinClubId = 0;
        String newClubName = null;
        boolean linkingChild = !cMail.isEmpty();
        if (!linkingChild) {
            if (pos <= 0) {
                newClubName = text(newClub);
                if (newClubName.isEmpty()) {
                    UiUtils.toast(requireContext(), R.string.err_club_required);
                    return;
                }
            } else if (pos - 1 < clubs.size()) {
                joinClubId = clubs.get(pos - 1).id;
            }
        }

        final long joinId = joinClubId;
        final String createName = newClubName;
        btnRegister.setEnabled(false);
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            ClubRepository.RegisterResult result =
                    repo.register(fullName, mail, pass, ph, cMail, cPass, joinId, createName);
            Async.main(() -> handleResult(result));
        });
    }

    private void handleResult(ClubRepository.RegisterResult result) {
        if (!isAdded()) return;
        btnRegister.setEnabled(true);
        switch (result.status) {
            case SUCCESS_PENDING:
                UiUtils.toast(requireContext(), R.string.msg_register_pending);
                auth().showLogin();
                break;
            case SUCCESS_AUTO_APPROVED:
                UiUtils.toast(requireContext(), R.string.msg_auto_approved);
                auth().showLogin();
                break;
            case SUCCESS_PARENT:
                UiUtils.toast(requireContext(),
                        getString(R.string.msg_parent_linked, result.linkedChildName));
                auth().showLogin();
                break;
            case SUCCESS_CLUB_CREATED:
                UiUtils.toast(requireContext(),
                        getString(R.string.msg_club_created, result.linkedChildName));
                auth().showLogin();
                break;
            case EMAIL_EXISTS:
                UiUtils.toast(requireContext(), R.string.err_email_exists);
                break;
            case INVALID:
                UiUtils.toast(requireContext(), R.string.err_password_weak);
                break;
        }
    }

    private AuthActivity auth() {
        return (AuthActivity) requireActivity();
    }

    private String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}
