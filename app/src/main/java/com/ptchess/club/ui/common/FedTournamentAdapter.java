package com.ptchess.club.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.ptchess.club.R;
import com.ptchess.club.data.remote.FederationApi.FedTournament;

import java.util.List;

/** Federation tournaments the player is eligible for, with a Register action. */
public class FedTournamentAdapter extends RecyclerView.Adapter<FedTournamentAdapter.VH> {

    public interface OnRegister {
        void onRegister(String link);
    }

    private final List<FedTournament> items;
    private final OnRegister onRegister;

    public FedTournamentAdapter(List<FedTournament> items, OnRegister onRegister) {
        this.items = items;
        this.onRegister = onRegister;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_fed_tournament, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FedTournament t = items.get(position);
        Context ctx = h.itemView.getContext();
        h.name.setText(t.name);

        h.chips.removeAllViews();
        addChip(h.chips, t.date);
        addChip(h.chips, t.location);
        addChip(h.chips, t.timeControl);
        if (t.places >= 0) addChip(h.chips, ctx.getString(R.string.places_left, t.places));

        String rating = ratingRequirement(ctx, t);
        h.rating.setVisibility(rating.isEmpty() ? View.GONE : View.VISIBLE);
        h.rating.setText(rating);

        StringBuilder info = new StringBuilder();
        if (!t.organizer.isEmpty()) info.append(t.organizer);
        if (!t.info.isEmpty()) {
            if (info.length() > 0) info.append(" · ");
            info.append(t.info);
        }
        h.info.setVisibility(info.length() == 0 ? View.GONE : View.VISIBLE);
        h.info.setText(info.toString());

        boolean hasLink = t.link != null && !t.link.isEmpty();
        h.register.setVisibility(hasLink ? View.VISIBLE : View.GONE);
        h.register.setOnClickListener(v -> onRegister.onRegister(t.link));
    }

    private String ratingRequirement(Context ctx, FedTournament t) {
        if (t.minRating > 0 && t.maxRating > 0) {
            return ctx.getString(R.string.rating_range_req, t.minRating, t.maxRating);
        }
        if (t.maxRating > 0) return ctx.getString(R.string.rating_max_req, t.maxRating);
        if (t.minRating > 0) return ctx.getString(R.string.rating_min_req, t.minRating);
        return "";
    }

    private void addChip(ChipGroup group, String text) {
        if (text == null || text.isEmpty()) return;
        Chip chip = new Chip(group.getContext());
        chip.setText(text);
        chip.setClickable(false);
        chip.setChipBackgroundColorResource(R.color.surface_700);
        chip.setTextColor(group.getContext().getColor(R.color.blue_light));
        chip.setEnsureMinTouchTargetSize(false);
        group.addView(chip);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, rating, info;
        final ChipGroup chips;
        final MaterialButton register;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.txtName);
            rating = v.findViewById(R.id.txtRating);
            info = v.findViewById(R.id.txtInfo);
            chips = v.findViewById(R.id.chips);
            register = v.findViewById(R.id.btnRegister);
        }
    }
}
