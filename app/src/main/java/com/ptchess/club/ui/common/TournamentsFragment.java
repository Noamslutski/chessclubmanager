package com.ptchess.club.ui.common;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.TournamentResult;
import com.ptchess.club.data.model.User;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class TournamentsFragment extends Fragment {

    private RecyclerView recycler;
    private TextView emptyView;
    private User user;

    private ActivityResultLauncher<String[]> csvPicker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        user = ((MainActivity) requireActivity()).getCurrentUser();
        ((TextView) view.findViewById(R.id.listTitle)).setText(R.string.tournaments_title);
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        emptyView.setText(R.string.no_results);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        MaterialButton importBtn = view.findViewById(R.id.btnAction);
        if (user.role == Role.TUTOR || user.role == Role.ADMIN) {
            importBtn.setVisibility(View.VISIBLE);
            importBtn.setText(R.string.import_results);
            importBtn.setOnClickListener(v -> showImportInfo());
        }

        csvPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onCsvPicked);

        load();
    }

    private void load() {
        // Staff and parents see whose result each row is; a student sees only their own.
        boolean showChild = user.role != Role.CHILD;
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<TournamentResult> results = repo.getResultsForUser(user);
            Async.main(() -> {
                if (!isAdded()) return;
                recycler.setAdapter(new ResultAdapter(results, showChild));
                emptyView.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void showImportInfo() {
        // Explain the expected file format (like a chess-results export) then pick.
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_results)
                .setMessage("CSV: child_email, tournament, date, points, games, standing")
                .setPositiveButton(R.string.ok, (d, w) ->
                        csvPicker.launch(new String[]{"text/csv", "text/comma-separated-values",
                                "text/plain", "*/*"}))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void onCsvPicked(@Nullable Uri uri) {
        if (uri == null) return;
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            String csv = readText(uri);
            int added = repo.importResultsCsv(csv);
            Async.main(() -> {
                if (!isAdded()) return;
                UiUtils.toast(requireContext(), "CSV: +" + added);
                load();
            });
        });
    }

    private String readText(Uri uri) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null) return "";
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                if (sb.length() > 500_000) break;
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString();
    }
}
