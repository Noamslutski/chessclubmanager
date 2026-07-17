package com.ptchess.club.ui.common;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.firebase.FirebaseFiles;
import com.ptchess.club.data.model.Club;
import com.ptchess.club.data.model.Group;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.util.List;

/** Public club profile: banner, logo, about, address, hall, contact, weekly schedule. */
public class ClubProfileFragment extends Fragment {

    private View root;
    private User user;
    private Club club;

    private ActivityResultLauncher<String[]> logoPicker;
    private ActivityResultLauncher<String[]> bannerPicker;
    private String pickedLogo, pickedBanner;
    private MaterialButton logoBtnRef, bannerBtnRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_club_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        user = ((MainActivity) requireActivity()).getCurrentUser();
        logoPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            pickedLogo = persist(uri);
            if (logoBtnRef != null && pickedLogo != null) logoBtnRef.setText("✓ " + getString(R.string.choose_logo));
        });
        bannerPicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            pickedBanner = persist(uri);
            if (bannerBtnRef != null && pickedBanner != null) bannerBtnRef.setText("✓ " + getString(R.string.choose_banner));
        });
        load();
    }

    private void load() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            Club c = repo.getClub(user.clubId);
            List<Group> groups = repo.getClubGroups(user.clubId);
            Async.main(() -> {
                if (!isAdded() || c == null) return;
                club = c;
                bind(groups);
            });
        });
    }

    private void bind(List<Group> groups) {
        ((TextView) root.findViewById(R.id.txtClubName)).setText(club.name);
        TextView verified = root.findViewById(R.id.txtVerified);
        verified.setText(club.verified ? R.string.club_verified : R.string.club_unverified);

        setTextOrHide(R.id.txtDescription, club.description);
        setTextOrHide(R.id.txtAddress, club.address.isEmpty() ? ""
                : getString(R.string.club_address_label) + ": " + club.address);
        setTextOrHide(R.id.txtHall, club.hall.isEmpty() ? ""
                : getString(R.string.club_hall_label) + ": " + club.hall);

        MaterialButton call = root.findViewById(R.id.btnCall);
        if (!club.contactPhone.isEmpty()) {
            call.setVisibility(View.VISIBLE);
            call.setOnClickListener(v -> UiUtils.dial(requireContext(), club.contactPhone));
        } else {
            call.setVisibility(View.GONE);
        }

        showImage(R.id.imgLogo, club.logoUri, true);
        showImage(R.id.imgBanner, club.bannerUri, false);

        buildSchedule(groups);

        MaterialButton edit = root.findViewById(R.id.btnEditProfile);
        if (user.role == Role.ADMIN) {
            edit.setVisibility(View.VISIBLE);
            edit.setOnClickListener(v -> showEditDialog());
        }
    }

    private void buildSchedule(List<Group> groups) {
        LinearLayout container = root.findViewById(R.id.scheduleContainer);
        container.removeAllViews();
        String[] weekdays = getResources().getStringArray(R.array.weekdays);
        if (groups.isEmpty()) {
            TextView tv = new TextView(requireContext());
            tv.setText(R.string.no_schedule);
            tv.setTextColor(getResources().getColor(R.color.text_muted, null));
            container.addView(tv);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Group g : groups) {
            View row = inflater.inflate(R.layout.item_group, container, false);
            ((TextView) row.findViewById(R.id.txtGroupName)).setText(g.name);
            ((TextView) row.findViewById(R.id.txtTutor))
                    .setText(getString(R.string.group_tutor, g.tutorName));
            String day = (g.dayIndex >= 0 && g.dayIndex < weekdays.length) ? weekdays[g.dayIndex] : "";
            ((TextView) row.findViewById(R.id.txtWhen))
                    .setText(getString(R.string.group_when, day, g.time));
            ((TextView) row.findViewById(R.id.txtMembers))
                    .setText(getString(R.string.group_members, g.memberCount));
            container.addView(row);
        }
    }

    private void showEditDialog() {
        pickedLogo = null;
        pickedBanner = null;
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_club, null, false);
        EditText desc = form.findViewById(R.id.editDescription);
        EditText address = form.findViewById(R.id.editAddress);
        EditText contact = form.findViewById(R.id.editContact);
        EditText hall = form.findViewById(R.id.editHall);
        desc.setText(club.description);
        address.setText(club.address);
        contact.setText(club.contactPhone);
        hall.setText(club.hall);

        logoBtnRef = form.findViewById(R.id.btnChooseLogo);
        bannerBtnRef = form.findViewById(R.id.btnChooseBanner);
        logoBtnRef.setOnClickListener(v -> logoPicker.launch(new String[]{"image/*"}));
        bannerBtnRef.setOnClickListener(v -> bannerPicker.launch(new String[]{"image/*"}));

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_profile)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String dd = desc.getText().toString();
                    String aa = address.getText().toString();
                    String cc = contact.getText().toString();
                    String hh = hall.getText().toString();
                    // Upload any newly picked images to Storage first (if Firebase is on),
                    // then persist the resulting URLs; nulls leave the existing images.
                    resolveUpload(pickedLogo, logoUrl ->
                            resolveUpload(pickedBanner, bannerUrl ->
                                    persistProfile(dd, aa, cc, hh, logoUrl, bannerUrl)));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Uploads a picked image to Storage when Firebase is on; otherwise passes the local URI through. */
    private void resolveUpload(String pickedUri, java.util.function.Consumer<String> then) {
        if (pickedUri == null) {
            then.accept(null);
            return;
        }
        if (!FirebaseFiles.enabled(requireContext())) {
            then.accept(pickedUri);
            return;
        }
        FirebaseFiles.upload(requireContext(), FirebaseFiles.LOGOS, club.id, Uri.parse(pickedUri),
                new FirebaseFiles.UploadCb() {
                    @Override public void onSuccess(String url) { then.accept(url); }
                    @Override public void onError(String message) { then.accept(pickedUri); }
                });
    }

    private void persistProfile(String dd, String aa, String cc, String hh,
                                String logoUrl, String bannerUrl) {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            repo.updateClubProfile(club.id, dd, aa, cc, hh, logoUrl, bannerUrl);
            Async.main(() -> {
                if (!isAdded()) return;
                UiUtils.toast(requireContext(), R.string.profile_saved);
                load();
            });
        });
    }

    // ---- helpers ----

    private void setTextOrHide(int id, String text) {
        TextView tv = root.findViewById(id);
        if (text == null || text.isEmpty()) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
        }
    }

    private void showImage(int id, String uri, boolean isLogo) {
        ImageView iv = root.findViewById(id);
        if (uri == null || uri.isEmpty()) {
            if (!isLogo) iv.setVisibility(View.GONE);
            return;
        }
        iv.setVisibility(View.VISIBLE);
        UiUtils.loadImage(iv, uri); // handles both local content:// and remote Storage URLs
    }

    private String persist(@Nullable Uri uri) {
        if (uri == null) return null;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        return uri.toString();
    }
}
