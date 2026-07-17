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
import com.ptchess.club.data.model.AuthResult;
import com.ptchess.club.security.InputValidator;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

public class LoginFragment extends Fragment {

    private TextInputEditText inputEmail, inputPassword;
    private MaterialButton btnLogin;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        inputEmail = view.findViewById(R.id.inputEmail);
        inputPassword = view.findViewById(R.id.inputPassword);
        btnLogin = view.findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v -> attemptLogin());
        view.findViewById(R.id.linkRegister).setOnClickListener(v -> auth().showRegister());
        view.findViewById(R.id.linkForgot).setOnClickListener(v -> auth().showReset());
    }

    private void attemptLogin() {
        String email = text(inputEmail);
        String password = text(inputPassword);

        if (!InputValidator.isValidEmail(InputValidator.normalizeEmail(email))) {
            UiUtils.toast(requireContext(), R.string.err_email_invalid);
            return;
        }
        if (password.isEmpty()) {
            UiUtils.toast(requireContext(), R.string.err_required);
            return;
        }

        btnLogin.setEnabled(false);
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            AuthResult result = repo.authenticate(email, password);
            Async.main(() -> handleResult(result));
        });
    }

    private void handleResult(AuthResult result) {
        if (!isAdded()) return;
        btnLogin.setEnabled(true);
        switch (result.status) {
            case SUCCESS:
                auth().onAuthSuccess(result.user.id);
                break;
            case WRONG_CREDENTIALS:
                UiUtils.toast(requireContext(), R.string.err_login_failed);
                break;
            case PENDING_APPROVAL:
                UiUtils.toast(requireContext(), R.string.err_account_pending);
                break;
            case REJECTED:
                UiUtils.toast(requireContext(), R.string.err_account_rejected);
                break;
            case LOCKED:
                UiUtils.toast(requireContext(),
                        getString(R.string.err_too_many_attempts, result.lockMinutes));
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
