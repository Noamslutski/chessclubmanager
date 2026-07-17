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
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.ptchess.club.R;
import com.ptchess.club.data.ClubRepository;
import com.ptchess.club.data.model.Role;
import com.ptchess.club.data.model.TournamentResult;
import com.ptchess.club.data.model.User;
import com.ptchess.club.data.remote.FederationApi;
import com.ptchess.club.ui.MainActivity;
import com.ptchess.club.util.Async;
import com.ptchess.club.util.UiUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Two modes: the player's own tournament <b>results</b>, and <b>registration</b>
 * to real federation tournaments filtered by the player's rating (ineligible
 * ones are hidden). Staff can import results from a CSV in results mode.
 */
public class TournamentsFragment extends Fragment {

    private RecyclerView recycler;
    private TextView emptyView, note;
    private View loadingBar;
    private MaterialButton importBtn;
    private User user;
    private boolean resultsMode = true;

    private ActivityResultLauncher<String[]> csvPicker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tournaments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        user = ((MainActivity) requireActivity()).getCurrentUser();
        recycler = view.findViewById(R.id.recycler);
        emptyView = view.findViewById(R.id.emptyView);
        note = view.findViewById(R.id.txtNote);
        loadingBar = view.findViewById(R.id.loadingBar);
        importBtn = view.findViewById(R.id.btnAction);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        boolean staff = user.role == Role.TUTOR || user.role == Role.ADMIN;
        importBtn.setOnClickListener(v -> showImportInfo());

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggleGroup);
        toggle.check(R.id.btnResults);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            resultsMode = checkedId == R.id.btnResults;
            importBtn.setVisibility(resultsMode && staff ? View.VISIBLE : View.GONE);
            reload();
        });
        importBtn.setVisibility(staff ? View.VISIBLE : View.GONE);

        csvPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onCsvPicked);

        reload();
    }

    private void reload() {
        if (resultsMode) loadResults();
        else loadFederation();
    }

    // ------------------------------------------------------------ results

    private void loadResults() {
        note.setVisibility(View.GONE);
        loadingBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        boolean showChild = user.role != Role.CHILD;
        ClubRepository repo = ClubRepository.getInstance(requireContext());
        Async.io(() -> {
            List<TournamentResult> results = repo.getResultsForUser(user);
            Async.main(() -> {
                if (!isAdded()) return;
                loadingBar.setVisibility(View.GONE);
                recycler.setAdapter(new ResultAdapter(results, showChild));
                emptyView.setText(R.string.no_results);
                emptyView.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    // ------------------------------------------------------------ federation

    private void loadFederation() {
        loadingBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        note.setVisibility(View.VISIBLE);
        note.setText(user.rating > 0
                ? getString(R.string.fed_note_rating, user.rating)
                : getString(R.string.fed_note_no_rating));

        if (!FederationApi.hasApiKey()) {
            loadingBar.setVisibility(View.GONE);
            recycler.setAdapter(null);
            emptyView.setText(R.string.fed_no_key);
            emptyView.setVisibility(View.VISIBLE);
            return;
        }

        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        Async.io(() -> {
            List<FederationApi.FedTournament> all = FederationApi.listTournaments(year, month);
            List<FederationApi.FedTournament> eligible = new ArrayList<>();
            for (FederationApi.FedTournament t : all) {
                if (t.eligible(user.rating)) eligible.add(t); // hide ones the player can't enter
            }
            Async.main(() -> {
                if (!isAdded()) return;
                loadingBar.setVisibility(View.GONE);
                recycler.setAdapter(new FedTournamentAdapter(eligible, this::openLink));
                emptyView.setText(R.string.fed_none);
                emptyView.setVisibility(eligible.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void openLink(String link) {
        String url = link.startsWith("http") ? link : "https://" + link;
        UiUtils.openUri(requireContext(), url, null);
    }

    // ------------------------------------------------------------ CSV import

    private void showImportInfo() {
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
        long clubId = user.clubId;
        Async.io(() -> {
            String csv = readText(uri);
            int added = repo.importResultsCsv(clubId, csv);
            Async.main(() -> {
                if (!isAdded()) return;
                UiUtils.toast(requireContext(), "CSV: +" + added);
                if (resultsMode) loadResults();
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
