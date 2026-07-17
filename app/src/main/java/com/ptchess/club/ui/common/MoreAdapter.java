package com.ptchess.club.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ptchess.club.R;

import java.util.List;

public class MoreAdapter extends RecyclerView.Adapter<MoreAdapter.VH> {

    public static class MoreItem {
        public final int iconRes;
        public final String title;
        public final Runnable action;
        public MoreItem(int iconRes, String title, Runnable action) {
            this.iconRes = iconRes;
            this.title = title;
            this.action = action;
        }
    }

    private final List<MoreItem> items;

    public MoreAdapter(List<MoreItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_more, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MoreItem item = items.get(position);
        h.icon.setImageResource(item.iconRes);
        h.title.setText(item.title);
        h.itemView.setOnClickListener(v -> {
            if (item.action != null) item.action.run();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        VH(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.imgMoreIcon);
            title = v.findViewById(R.id.txtMoreTitle);
        }
    }
}
