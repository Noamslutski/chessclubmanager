package com.ptchess.club.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ptchess.club.R;
import com.ptchess.club.data.model.Group;

import java.util.List;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.VH> {

    public interface OnGroupClick {
        void onClick(Group group);
    }

    private final List<Group> items;
    private final OnGroupClick click;

    public GroupAdapter(List<Group> items, OnGroupClick click) {
        this.items = items;
        this.click = click;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Group g = items.get(position);
        h.name.setText(g.name);
        h.tutor.setText(h.itemView.getContext().getString(R.string.group_tutor, g.tutorName));

        String[] weekdays = h.itemView.getResources().getStringArray(R.array.weekdays);
        String day = (g.dayIndex >= 0 && g.dayIndex < weekdays.length)
                ? weekdays[g.dayIndex] : "";
        h.when.setText(h.itemView.getContext().getString(R.string.group_when, day, g.time));
        h.members.setText(h.itemView.getContext().getString(R.string.group_members, g.memberCount));

        h.itemView.setOnClickListener(v -> {
            if (click != null) click.onClick(g);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, tutor, when, members;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.txtGroupName);
            tutor = v.findViewById(R.id.txtTutor);
            when = v.findViewById(R.id.txtWhen);
            members = v.findViewById(R.id.txtMembers);
        }
    }
}
