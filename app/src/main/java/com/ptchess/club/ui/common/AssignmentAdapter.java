package com.ptchess.club.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.model.Assignment;

import java.util.List;

public class AssignmentAdapter extends RecyclerView.Adapter<AssignmentAdapter.VH> {

    public interface OnAssignmentAction {
        void onToggleDone(Assignment a);
        void onOpenAttachment(Assignment a);
    }

    private final List<Assignment> items;
    private final boolean canComplete;
    private final OnAssignmentAction action;

    public AssignmentAdapter(List<Assignment> items, boolean canComplete, OnAssignmentAction action) {
        this.items = items;
        this.canComplete = canComplete;
        this.action = action;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_assignment, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Assignment a = items.get(position);
        h.title.setText(a.title);
        h.group.setText(a.groupName);
        h.desc.setText(a.description);
        if (a.dueDate != null && !a.dueDate.isEmpty()) {
            h.due.setVisibility(View.VISIBLE);
            h.due.setText(h.itemView.getContext().getString(R.string.assignment_due, a.dueDate));
        } else {
            h.due.setVisibility(View.GONE);
        }

        boolean hasFile = a.fileUri != null && !a.fileUri.isEmpty();
        h.attachment.setVisibility(hasFile ? View.VISIBLE : View.GONE);
        h.attachment.setOnClickListener(v -> action.onOpenAttachment(a));

        if (canComplete) {
            h.done.setVisibility(View.VISIBLE);
            h.done.setText(a.completed
                    ? R.string.assignment_completed : R.string.assignment_done);
            h.done.setEnabled(true);
            h.done.setOnClickListener(v -> action.onToggleDone(a));
        } else {
            h.done.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, group, desc, due;
        final MaterialButton attachment, done;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.txtAssignTitle);
            group = v.findViewById(R.id.txtAssignGroup);
            desc = v.findViewById(R.id.txtAssignDesc);
            due = v.findViewById(R.id.txtAssignDue);
            attachment = v.findViewById(R.id.btnAttachment);
            done = v.findViewById(R.id.btnDone);
        }
    }
}
