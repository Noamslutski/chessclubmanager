package com.ptchess.club.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ptchess.club.R;
import com.ptchess.club.data.model.NewsItem;

import java.util.List;

public class NewsAdapter extends RecyclerView.Adapter<NewsAdapter.VH> {

    private final List<NewsItem> items;

    public NewsAdapter(List<NewsItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_news, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        NewsItem n = items.get(position);
        h.title.setText(n.title);
        h.body.setText(n.body);
        String meta = n.authorName == null || n.authorName.isEmpty()
                ? n.date : n.authorName + " · " + n.date;
        h.meta.setText(meta);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, body, meta;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.txtTitle);
            body = v.findViewById(R.id.txtBody);
            meta = v.findViewById(R.id.txtMeta);
        }
    }
}
