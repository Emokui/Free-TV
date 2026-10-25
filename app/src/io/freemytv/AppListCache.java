package io.freemytv;

/** Main-thread cache policy; results are accepted only for the shelf/language/packages they were loaded for. */
final class AppListCache {
    static final long MAX_AGE_MS = 15 * 60_000L;
    private long generation, loadedGeneration, loadedAt;
    private int loadedShelf;
    private String loadedLanguage;
    private Request pending;
    private boolean loaded;

    static final class Request {
        final long generation;
        final int shelf;
        final String language;
        Request(long generation, int shelf, String language) {
            this.generation = generation;
            this.shelf = shelf;
            this.language = language;
        }
    }

    void invalidate() { generation++; }

    boolean needsReload(long now, int shelf, String language) {
        return pending == null && (!loaded || generation != loadedGeneration || shelf != loadedShelf
                || !language.equals(loadedLanguage) || now - loadedAt >= MAX_AGE_MS);
    }

    Request begin(long now, int shelf, String language) {
        if (!needsReload(now, shelf, language)) return null;
        return pending = new Request(generation, shelf, language);
    }

    boolean complete(Request request, long now, int shelf, String language) {
        if (request != pending) return false;
        pending = null;
        if (request.generation != generation || request.shelf != shelf || !request.language.equals(language)) return false;
        loadedGeneration = generation;
        loadedShelf = shelf;
        loadedLanguage = language;
        loadedAt = now;
        loaded = true;
        return true;
    }

    void fail(Request request) { if (pending == request) pending = null; }

    static boolean withinBurst(long previous, long now) { return previous != 0 && now - previous < 1500; }
}
