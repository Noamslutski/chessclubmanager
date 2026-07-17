package com.ptchess.club.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.model.Club;

import java.util.List;

public class ClubAdapter extends RecyclerView.Adapter<ClubAdapter.VH> {

    public interface OnToggleVerify {
        void onToggle(Club club);
    }

    private final List<Club> clubs;
    private final OnToggleVerify listener;

    public ClubAdapter(List<Club> clubs, OnToggleVerify listener) {
        this.clubs = clubs;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_club, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Club c = clubs.get(position);
        h.name.setText(c.name);
        h.owner.setText(c.ownerName);
        h.status.setText(c.verified ? R.string.club_verified : R.string.club_unverified);
        h.verify.setText(c.verified ? R.string.unverify : R.string.verify);
        h.verify.setOnClickListener(v -> {
            if (listener != null) listener.onToggle(c);
        });
    }

    @Override
    public int getItemCount() {
        return clubs.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, owner, status;
        final MaterialButton verify;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.txtClubName);
            owner = v.findViewById(R.id.txtClubOwner);
            status = v.findViewById(R.id.txtClubStatus);
            verify = v.findViewById(R.id.btnVerify);
        }
    }
}
