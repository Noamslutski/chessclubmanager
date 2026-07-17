package com.ptchess.club.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.model.User;

import java.util.List;

/** Public club-staff row: name, rank (role), rating, and a Contact action. */
public class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.VH> {

    public interface OnContact {
        void contact(User user);
    }

    private final List<User> staff;
    private final OnContact onContact;

    public StaffAdapter(List<User> staff, OnContact onContact) {
        this.staff = staff;
        this.onContact = onContact;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        User u = staff.get(position);
        h.name.setText(u.fullName);
        h.subtitle.setText(u.rating > 0
                ? h.itemView.getContext().getString(R.string.rating_label, u.rating) : "");
        h.role.setText(h.itemView.getContext().getString(u.role.displayRes));

        boolean canContact = u.phone != null && !u.phone.isEmpty();
        h.secondary.setVisibility(View.GONE);
        if (canContact) {
            h.actionRow.setVisibility(View.VISIBLE);
            h.primary.setVisibility(View.VISIBLE);
            h.primary.setText(R.string.contact);
            h.primary.setOnClickListener(v -> onContact.contact(u));
        } else {
            h.actionRow.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return staff.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, subtitle, role;
        final MaterialButton primary, secondary;
        final View actionRow;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.txtUserName);
            subtitle = v.findViewById(R.id.txtUserEmail);
            role = v.findViewById(R.id.txtUserRole);
            primary = v.findViewById(R.id.btnPrimary);
            secondary = v.findViewById(R.id.btnSecondary);
            actionRow = v.findViewById(R.id.actionRow);
        }
    }
}
