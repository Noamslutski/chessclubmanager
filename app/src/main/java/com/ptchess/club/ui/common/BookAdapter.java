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
import com.ptchess.club.data.model.Book;

import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.VH> {

    public interface OnBookAction {
        void onOpen(Book book);
        void onPrint(Book book);
    }

    private final List<Book> items;
    private final OnBookAction action;

    public BookAdapter(List<Book> items, OnBookAction action) {
        this.items = items;
        this.action = action;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_book, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Book b = items.get(position);
        Context ctx = h.itemView.getContext();
        h.title.setText(b.title);
        h.author.setText(b.author);

        h.chips.removeAllViews();
        addChip(h.chips, LabelMapper.category(ctx, b.category));
        addChip(h.chips, LabelMapper.side(ctx, b.side));
        addChip(h.chips, b.ratingMin + "–" + b.ratingMax);
        if (b.language != null && !b.language.isEmpty()) addChip(h.chips, b.language);

        h.open.setOnClickListener(v -> action.onOpen(b));
        h.print.setOnClickListener(v -> action.onPrint(b));
    }

    private void addChip(ChipGroup group, String text) {
        Chip chip = new Chip(group.getContext());
        chip.setText(text);
        chip.setClickable(false);
        chip.setChipBackgroundColorResource(R.color.surface_700);
        chip.setTextColor(group.getContext().getColor(R.color.blue_light));
        chip.setChipStrokeColorResource(R.color.stroke_600);
        chip.setChipStrokeWidth(1f);
        chip.setEnsureMinTouchTargetSize(false);
        group.addView(chip);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title, author;
        final ChipGroup chips;
        final MaterialButton open, print;
        VH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.txtBookTitle);
            author = v.findViewById(R.id.txtBookAuthor);
            chips = v.findViewById(R.id.chipGroup);
            open = v.findViewById(R.id.btnOpen);
            print = v.findViewById(R.id.btnPrint);
        }
    }
}
