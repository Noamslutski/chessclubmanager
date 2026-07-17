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

/**
 * Generic user row. The hosting screen supplies an {@link ActionBinder} to
 * configure the two action buttons (approve/reject, set role, contact, …) so
 * the same layout serves admin, parent and group-member lists.
 */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {

    public interface ActionBinder {
        void bind(User user, MaterialButton primary, MaterialButton secondary);
    }

    private final List<User> items;
    private final ActionBinder binder;

    public UserAdapter(List<User> items, ActionBinder binder) {
        this.items = items;
        this.binder = binder;
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
        User u = items.get(position);
        h.name.setText(u.fullName);
        h.email.setText(u.email);
        h.role.setText(h.itemView.getContext().getString(u.role.displayRes));

        // Reset then let the binder decide.
        h.primary.setVisibility(View.GONE);
        h.secondary.setVisibility(View.GONE);
        h.primary.setOnClickListener(null);
        h.secondary.setOnClickListener(null);
        if (binder != null) binder.bind(u, h.primary, h.secondary);

        boolean anyVisible = h.primary.getVisibility() == View.VISIBLE
                || h.secondary.getVisibility() == View.VISIBLE;
        h.actionRow.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, email, role;
        final MaterialButton primary, secondary;
        final View actionRow;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.txtUserName);
            email = v.findViewById(R.id.txtUserEmail);
            role = v.findViewById(R.id.txtUserRole);
            primary = v.findViewById(R.id.btnPrimary);
            secondary = v.findViewById(R.id.btnSecondary);
            actionRow = v.findViewById(R.id.actionRow);
        }
    }
}
