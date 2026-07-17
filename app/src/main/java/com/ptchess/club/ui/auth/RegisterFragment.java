package com.ptchess.club.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.security.InputValidator;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

public class RegisterFragment extends Fragment {

    private TextInputEditText name, email, phone, password, confirm, childEmail, childPassword;
    private MaterialButton btnRegister;

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
        btnRegister = view.findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> attemptRegister());
        view.findViewById(R.id.linkLogin).setOnClickListener(v -> auth().showLogin());
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

        btnRegister.setEnabled(false);
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            ClubRepository.RegisterResult result =
                    repo.register(fullName, mail, pass, ph, cMail, cPass);
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
            case SUCCESS_PARENT:
                UiUtils.toast(requireContext(),
                        getString(R.string.msg_parent_linked, result.linkedChildName));
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
