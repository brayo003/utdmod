package com.utdmod.research;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class PerturbationRecorder {
    private static final int MAX_HISTORY = 2048;
    private static final Deque<SubstratePerturbation> history = new ArrayDeque<>();

    private PerturbationRecorder() {}

    public static synchronized void record(SubstratePerturbation perturbation) {
        if (perturbation == null || !ResearchConfig.isRecordingEnabled()) return;
        if (history.size() >= MAX_HISTORY) {
            history.removeFirst();
        }
        history.addLast(perturbation);
    }

    public static synchronized List<SubstratePerturbation> snapshot() {
        return new ArrayList<>(history);
    }

    public static synchronized int count() {
        return history.size();
    }

    public static synchronized void dumpRecent(int maxEntries) {
        int count = Math.min(maxEntries, history.size());
        System.out.printf("[RESEARCH] Dumping %d/%d recorded perturbations:%n", count, history.size());
        int index = 0;
        for (SubstratePerturbation p : history) {
            if (index++ >= history.size() - count) {
                System.out.println(p.toString());
            }
        }
    }
}
