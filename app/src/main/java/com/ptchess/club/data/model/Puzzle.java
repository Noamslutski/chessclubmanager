package com.ptchess.club.data.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Puzzle {
    public long id;
    public String title;
    public int level;
    public String fen;
    /** Solution as a space-separated list of UCI moves, e.g. "e2e4 e7e5". */
    public String solutionUci;

    public Puzzle(long id, String title, int level, String fen, String solutionUci) {
        this.id = id;
        this.title = title;
        this.level = level;
        this.fen = fen;
        this.solutionUci = solutionUci;
    }

    public List<String> solutionMoves() {
        if (solutionUci == null || solutionUci.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(solutionUci.trim().split("\\s+")));
    }
}
