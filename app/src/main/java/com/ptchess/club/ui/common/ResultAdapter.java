package com.ptchess.club.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ptchess.club.R;
import com.ptchess.club.data.model.TournamentResult;

import java.text.DecimalFormat;
import java.util.List;

public class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {

    private static final DecimalFormat PTS = new DecimalFormat("0.#");

    private final List<TournamentResult> items;
    private final boolean showChild;

    public ResultAdapter(List<TournamentResult> items, boolean showChild) {
        this.items = items;
        this.showChild = showChild;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_result, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        TournamentResult r = items.get(position);
        h.tournament.setText(r.tournamentName);
        if (showChild) {
            h.child.setVisibility(View.VISIBLE);
            h.child.setText(r.childName);
        } else {
            h.child.setVisibility(View.GONE);
        }
        h.points.setText(h.itemView.getContext()
                .getString(R.string.tournament_points, PTS.format(r.points), r.games));
        h.standing.setText(h.itemView.getContext()
                .getString(R.string.tournament_standing, r.standing));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tournament, child, points, standing;
        VH(@NonNull View v) {
            super(v);
            tournament = v.findViewById(R.id.txtTournament);
            child = v.findViewById(R.id.txtChild);
            points = v.findViewById(R.id.txtPoints);
            standing = v.findViewById(R.id.txtStanding);
        }
    }
}
