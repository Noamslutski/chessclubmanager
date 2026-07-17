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
import com.ptchess.club.data.firebase.FirebaseAuthService;
import com.ptchess.club.data.firebase.FirebaseProfile;
import com.ptchess.club.data.model.AuthResult;
import com.ptchess.club.data.model.User;
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
        if (FirebaseAuthService.enabled(requireContext())) {
            FirebaseAuthService.signIn(email, password, new FirebaseAuthService.AuthCb() {
                @Override public void onSuccess(String uid) { resolveAndRoute(uid, email); }
                @Override public void onError(String message) {
                    if (!isAdded()) return;
                    // Fall back to local auth (seeded/offline accounts not in Firebase).
                    localLogin(email, password);
                }
            });
        } else {
            localLogin(email, password);
        }
    }

    /** After a Firebase sign-in, find (or hydrate) the local profile and route by status. */
    private void resolveAndRoute(String uid, String email) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            User local = repo.getUserByEmail(email);
            Async.main(() -> {
                if (!isAdded()) return;
                if (local != null) {
                    routeByStatus(local);
                    return;
                }
                // New device: rebuild the local mirror from the Firestore profile.
                FirebaseProfile.fetch(uid, profile -> {
                    if (!isAdded()) return;
                    if (profile == null) {
                        btnLogin.setEnabled(true);
                        UiUtils.toast(requireContext(), R.string.err_login_failed);
                        return;
                    }
                    Async.io(() -> {
                        long id = repo.upsertLocalUser(profile.fullName, email, profile.phone,
                                profile.role, profile.status, profile.clubId, profile.rating);
                        User hydrated = repo.getUserById(id);
                        Async.main(() -> routeByStatus(hydrated));
                    });
                });
            });
        });
    }

    private void routeByStatus(User u) {
        if (!isAdded() || u == null) return;
        btnLogin.setEnabled(true);
        if (User.STATUS_ACTIVE.equals(u.status)) {
            auth().onAuthSuccess(u.id);
        } else if (User.STATUS_PENDING.equals(u.status)) {
            FirebaseAuthService.signOut();
            UiUtils.toast(requireContext(), R.string.err_account_pending);
        } else {
            FirebaseAuthService.signOut();
            UiUtils.toast(requireContext(), R.string.err_account_rejected);
        }
    }

    /** Local-only fallback (used when Firebase isn't configured). */
    private void localLogin(String email, String password) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            AuthResult result = repo.authenticate(email, password);
            Async.main(() -> {
                if (!isAdded()) return;
                btnLogin.setEnabled(true);
                switch (result.status) {
                    case SUCCESS: auth().onAuthSuccess(result.user.id); break;
                    case WRONG_CREDENTIALS: UiUtils.toast(requireContext(), R.string.err_login_failed); break;
                    case PENDING_APPROVAL: UiUtils.toast(requireContext(), R.string.err_account_pending); break;
                    case REJECTED: UiUtils.toast(requireContext(), R.string.err_account_rejected); break;
                    case LOCKED:
                        UiUtils.toast(requireContext(),
                                getString(R.string.err_too_many_attempts, result.lockMinutes));
                        break;
                }
            });
        });
    }

    private AuthActivity auth() {
        return (AuthActivity) requireActivity();
    }

    private String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}
