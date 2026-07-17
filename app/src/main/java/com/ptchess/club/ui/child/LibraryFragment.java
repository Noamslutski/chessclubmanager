package com.ptchess.club.ui.child;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.Book;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.ui.common.BookAdapter;
import com.ptchess.club.ui.common.LabelMapper;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.util.List;

public class LibraryFragment extends Fragment implements BookAdapter.OnBookAction {

    private RecyclerView recycler;
    private TextView emptyView;

    private ActivityResultLauncher<String[]> filePicker;
    private String pickedUri;
    private TextView pickedLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.library_title);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        emptyView.setText(R.string.no_results);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        User user = ((MainActivity) requireActivity()).getCurrentUser();
        MaterialButton add = view.findViewById(R.id.btnAction);
        if (user.role == Role.TUTOR || user.role == Role.ADMIN) {
            add.setVisibility(View.VISIBLE);
            add.setText(R.string.add_book);
            add.setOnClickListener(v -> showAddBook(user));
        }

        filePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onFilePicked);

        load();
    }

    private void load() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<Book> books = repo.getBooks();
            Async.main(() -> {
                if (!isAdded()) return;
                recycler.setAdapter(new BookAdapter(books, this));
                emptyView.setVisibility(books.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    public void onOpen(Book book) {
        UiUtils.openUri(requireContext(), book.fileUri, "application/pdf");
    }

    @Override
    public void onPrint(Book book) {
        UiUtils.printDocument(requireContext(), book.title, book.fileUri);
    }

    private void onFilePicked(@Nullable Uri uri) {
        if (uri == null) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        pickedUri = uri.toString();
        if (pickedLabel != null) pickedLabel.setText(pickedUri);
    }

    private void showAddBook(User user) {
        pickedUri = null;
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_book, null, false);

        EditText title = form.findViewById(R.id.bookTitle);
        EditText author = form.findViewById(R.id.bookAuthor);
        EditText language = form.findViewById(R.id.bookLanguage);
        Spinner category = form.findViewById(R.id.bookCategory);
        Spinner side = form.findViewById(R.id.bookSide);
        EditText rMin = form.findViewById(R.id.ratingMin);
        EditText rMax = form.findViewById(R.id.ratingMax);
        pickedLabel = form.findViewById(R.id.txtChosenFile);

        category.setAdapter(spinner(labelsFor(LabelMapper.CATEGORY_KEYS, true)));
        side.setAdapter(spinner(labelsFor(LabelMapper.SIDE_KEYS, false)));

        form.findViewById(R.id.btnChooseFile).setOnClickListener(v ->
                filePicker.launch(new String[]{"application/pdf"}));

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_book)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String t = title.getText().toString().trim();
                    if (t.isEmpty()) return;
                    String categoryKey = LabelMapper.CATEGORY_KEYS[category.getSelectedItemPosition()];
                    String sideKey = LabelMapper.SIDE_KEYS[side.getSelectedItemPosition()];
                    int min = parseInt(rMin.getText().toString(), 0);
                    int max = parseInt(rMax.getText().toString(), 3000);
                    String uri = pickedUri;
                    ClubRepository repo = ClubRepository.getInstance(requireContext());
                    Async.io(() -> {
                        repo.addBook(t, author.getText().toString().trim(),
                                language.getText().toString().trim(), categoryKey, sideKey,
                                min, max, uri, user.id);
                        Async.main(this::load);
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private ArrayAdapter<String> spinner(String[] labels) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private String[] labelsFor(String[] keys, boolean category) {
        String[] out = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            out[i] = category ? LabelMapper.category(requireContext(), keys[i])
                    : LabelMapper.side(requireContext(), keys[i]);
        }
        return out;
    }

    private int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
