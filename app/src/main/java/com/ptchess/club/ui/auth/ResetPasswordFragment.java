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
import com.ptchess.club.util.UiUtils;

public class ResetPasswordFragment extends Fragment {

    private TextInputEditText inputEmail;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reset, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        inputEmail = view.findViewById(R.id.inputEmail);
        MaterialButton btnReset = view.findViewById(R.id.btnReset);

        btnReset.setOnClickListener(v -> {
            String email = inputEmail.getText() == null ? "" : inputEmail.getText().toString().trim();
            if (!InputValidator.isValidEmail(InputValidator.normalizeEmail(email))) {
                UiUtils.toast(requireContext(), R.string.err_email_invalid);
                return;
            }
            // The same neutral message is shown whether or not the email exists,
            // so the screen never reveals which addresses are registered.
            ClubRepository.getInstance(requireContext()).requestPasswordReset(email);
            UiUtils.toast(requireContext(), R.string.msg_reset_sent);
            ((AuthActivity) requireActivity()).showLogin();
        });

        view.findViewById(R.id.linkBack).setOnClickListener(v ->
                ((AuthActivity) requireActivity()).showLogin());
    }
}
