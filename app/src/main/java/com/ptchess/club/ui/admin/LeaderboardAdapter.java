package com.ptchess.club.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.ptchess.club.R;
import com.ptchess.club.data.gamification.Gamification;

import java.util.List;

/** Club XP leaderboard rows; the top three ranks are highlighted in gold. */
public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.VH> {

    private final List<Gamification.Ranked> rows;

    public LeaderboardAdapter(List<Gamification.Ranked> rows) {
        this.rows = rows;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Gamification.Ranked r = rows.get(position);
        int rank = position + 1;
        h.rank.setText(h.itemView.getContext().getString(R.string.gam_rank, rank));
        h.rank.setTextColor(ContextCompat.getColor(h.itemView.getContext(),
                rank <= 3 ? R.color.gold_accent : R.color.text_muted));
        h.name.setText(r.name);
        h.sub.setText(h.itemView.getContext().getString(R.string.gam_level_xp, r.level, r.xp));
        h.stats.setText("🧩 " + r.puzzles + "   ✅ " + r.assignments);
    }

    @Override
    public int getItemCount() {
        return rows == null ? 0 : rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView rank, name, sub, stats;
        VH(@NonNull View v) {
            super(v);
            rank = v.findViewById(R.id.txtRank);
            name = v.findViewById(R.id.txtName);
            sub = v.findViewById(R.id.txtSub);
            stats = v.findViewById(R.id.txtStats);
        }
    }
}
