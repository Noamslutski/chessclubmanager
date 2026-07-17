package com.ptchess.club.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.LocaleHelper;
import com.ptchess.club.util.UiUtils;

public class SettingsFragment extends Fragment {

    private User user;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        user = ((MainActivity) requireActivity()).getCurrentUser();

        ((TextView) view.findViewById(R.id.txtAccountName)).setText(user.fullName);
        ((TextView) view.findViewById(R.id.txtAccountEmail)).setText(user.email);

        RadioGroup group = view.findViewById(R.id.languageGroup);
        RadioButton hebrew = view.findViewById(R.id.radioHebrew);
        RadioButton english = view.findViewById(R.id.radioEnglish);

        boolean isHebrew = LocaleHelper.isHebrew(requireContext());
        hebrew.setChecked(isHebrew);
        english.setChecked(!isHebrew);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            String lang = checkedId == R.id.radioEnglish
                    ? LocaleHelper.LANG_ENGLISH : LocaleHelper.LANG_HEBREW;
            if (!lang.equals(LocaleHelper.getLanguage(requireContext()))) {
                LocaleHelper.setLanguage(requireContext(), lang);
                requireActivity().recreate();
            }
        });

        view.findViewById(R.id.btnChangePassword).setOnClickListener(v -> showChangePassword());
        MaterialButton logout = view.findViewById(R.id.btnLogout);
        logout.setOnClickListener(v -> ((MainActivity) requireActivity()).logout());
    }

    private void showChangePassword() {
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_change_password, null, false);
        EditText current = form.findViewById(R.id.currentPassword);
        EditText next = form.findViewById(R.id.newPassword);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.change_password)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String cur = current.getText().toString();
                    String nw = next.getText().toString();
                    ClubRepository repo = ClubRepository.getInstance(requireContext());
                    Async.io(() -> {
                        boolean ok = repo.changePassword(user.id, cur, nw);
                        Async.main(() -> {
                            if (!isAdded()) return;
                            UiUtils.toast(requireContext(),
                                    ok ? R.string.save : R.string.err_password_weak);
                        });
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
