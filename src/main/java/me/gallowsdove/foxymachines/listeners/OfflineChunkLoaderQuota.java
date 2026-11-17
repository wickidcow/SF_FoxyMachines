package me.gallowsdove.foxymachines.listeners;

/**
 * Cumulative offline-break ledger: journal totals are never erased on login.
 *
 * Both the player quota and the last-applied journal total are written to the
 * same player PDC, so saving/reloading that player cannot apply an already
 * acknowledged decrement a second time.
 */
final class OfflineChunkLoaderQuota {

    private OfflineChunkLoaderQuota() {}

    record Adjustment(int remaining, int appliedTotal) {}

    static int nextJournalTotal(int currentTotal) {
        if (currentTotal < 0) {
            throw new IllegalArgumentException("Negative offline loader journal total");
        }
        return Math.addExact(currentTotal, 1);
    }

    static Adjustment reconcile(int remainingQuota, int cumulativeJournal, int alreadyApplied) {
        if (remainingQuota < 0 || cumulativeJournal < 0 || alreadyApplied < 0
                || alreadyApplied > cumulativeJournal) {
            throw new IllegalArgumentException("Conflicting offline loader quota counters");
        }
        int unapplied = cumulativeJournal - alreadyApplied;
        return new Adjustment(Math.max(0, remainingQuota - unapplied), cumulativeJournal);
    }
}
