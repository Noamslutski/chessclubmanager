package com.ptchess.club.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.NewsItem;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;

import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView recycler;
    private TextView emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        User user = ((MainActivity) requireActivity()).getCurrentUser();

        TextView welcome = view.findViewById(R.id.txtWelcome);
        TextView role = view.findViewById(R.id.txtRole);
        welcome.setText(getString(R.string.home_welcome, firstName(user.fullName)));
        role.setText(getString(user.role.displayRes));

        recycler = view.findViewById(R.id.recyclerNews);
        emptyView = view.findViewById(R.id.emptyNews);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        MaterialButton addNews = view.findViewById(R.id.btnAddNews);
        if (user.role == Role.ADMIN) {
            addNews.setVisibility(View.VISIBLE);
            addNews.setOnClickListener(v -> showAddNews(user));
        }

        loadNews();
    }

    private void loadNews() {
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<NewsItem> news = repo.getNews();
            Async.main(() -> {
                if (!isAdded()) return;
                recycler.setAdapter(new NewsAdapter(news));
                emptyView.setVisibility(news.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showAddNews(User user) {
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_two_fields, null, false);
        EditText title = form.findViewById(R.id.field1);
        EditText body = form.findViewById(R.id.field2);
        title.setHint(getString(R.string.add_news));
        body.setHint(getString(R.string.home_news));
        body.setMinLines(3);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.add_news)
                .setView(form)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String t = title.getText().toString().trim();
                    String b = body.getText().toString().trim();
                    if (t.isEmpty()) return;
                    ClubRepository repo = ClubRepository.getInstance(requireContext());
                    Async.io(() -> {
                        repo.addNews(t, b, user.id);
                        Async.main(this::loadNews);
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String firstName(String fullName) {
        if (fullName == null) return "";
        int space = fullName.indexOf(' ');
        return space > 0 ? fullName.substring(0, space) : fullName;
    }
}
