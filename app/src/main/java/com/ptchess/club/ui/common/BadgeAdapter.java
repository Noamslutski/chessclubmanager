package com.ptchess.club.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ptchess.club.R;
import com.ptchess.club.data.gamification.Gamification;

import java.util.List;

/** Grid of earned + locked badges; locked ones are dimmed and show progress. */
public class BadgeAdapter extends RecyclerView.Adapter<BadgeAdapter.VH> {

    private final List<Gamification.Badge> badges;

    public BadgeAdapter(List<Gamification.Badge> badges) {
        this.badges = badges;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_badge, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Gamification.Badge b = badges.get(position);
        h.emoji.setText(BadgeLabels.emoji(b.id));
        h.title.setText(BadgeLabels.title(h.itemView.getContext(), b.id, b.target));
        h.progress.setText(h.itemView.getContext().getString(
                R.string.gam_badge_progress, Math.min(b.current, b.target), b.target));
        // Earned badges are bright; locked ones are dimmed.
        h.itemView.setAlpha(b.earned ? 1f : 0.45f);
    }

    @Override
    public int getItemCount() {
        return badges == null ? 0 : badges.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView emoji, title, progress;
        VH(@NonNull View v) {
            super(v);
            emoji = v.findViewById(R.id.txtBadgeEmoji);
            title = v.findViewById(R.id.txtBadgeTitle);
            progress = v.findViewById(R.id.txtBadgeProgress);
        }
    }
}
