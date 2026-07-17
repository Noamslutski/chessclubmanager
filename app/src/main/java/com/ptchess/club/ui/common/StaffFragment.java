package com.ptchess.club.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.util.List;

/** Public directory of the club's coaches, tutors and admins (everyone can view). */
public class StaffFragment extends Fragment {

    private RecyclerView recycler;
    private TextView emptyView;
    private View loadingBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.staff_title);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        loadingBar = view.findViewById(R.id.loadingBar);
        emptyView.setText(R.string.no_staff);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        User user = ((MainActivity) requireActivity()).getCurrentUser();
        loadingBar.setVisibility(View.VISIBLE);
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<User> staff = repo.getClubStaff(user.clubId);
            Async.main(() -> {
                if (!isAdded()) return;
                loadingBar.setVisibility(View.GONE);
                recycler.setAdapter(new StaffAdapter(staff, this::showContact));
                emptyView.setVisibility(staff.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showContact(User u) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(u.fullName)
                .setItems(new CharSequence[]{
                        getString(R.string.call), getString(R.string.whatsapp)}, (d, which) -> {
                    if (which == 0) UiUtils.dial(requireContext(), u.phone);
                    else UiUtils.whatsapp(requireContext(), u.phone);
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }
}
