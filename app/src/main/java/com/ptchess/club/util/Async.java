package com.ptchess.club.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal threading helper. Auth (PBKDF2) and DB access run on a background
 * pool; results are posted back to the main thread. Keeps the UI responsive
 * without pulling in a larger async framework.
 */
public final class Async {

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Async() { }

    public static void io(Runnable work) {
        IO.execute(work);
    }

    public static void main(Runnable work) {
        MAIN.post(work);
    }
}
