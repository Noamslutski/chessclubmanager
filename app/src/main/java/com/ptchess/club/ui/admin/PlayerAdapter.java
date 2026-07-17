package com.ptchess.club.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;

import java.util.List;

/** Renders both search results (with an Add button) and the saved roster. */
public class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.VH> {

    public static class Row {
        public final String name;
        public final String subtitle;
        public final boolean addable;
        public final Runnable onAdd;
        public Row(String name, String subtitle, boolean addable, Runnable onAdd) {
            this.name = name;
            this.subtitle = subtitle;
            this.addable = addable;
            this.onAdd = onAdd;
        }
    }

    private final List<Row> rows;

    public PlayerAdapter(List<Row> rows) {
        this.rows = rows;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_player, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = rows.get(position);
        h.name.setText(r.name);
        h.meta.setText(r.subtitle);
        if (r.addable) {
            h.add.setVisibility(View.VISIBLE);
            h.add.setOnClickListener(v -> {
                if (r.onAdd != null) r.onAdd.run();
            });
        } else {
            h.add.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, meta;
        final MaterialButton add;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.txtPlayerName);
            meta = v.findViewById(R.id.txtPlayerMeta);
            add = v.findViewById(R.id.btnAddPlayer);
        }
    }
}
