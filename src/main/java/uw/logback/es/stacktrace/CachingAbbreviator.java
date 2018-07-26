package uw.logback.es.stacktrace;

import ch.qos.logback.classic.pattern.Abbreviator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * CachingAbbreviator
 *
 * @author liliang
 * @since 2018-07-26
 */
public class CachingAbbreviator implements Abbreviator {

    private final Abbreviator delegate;

    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<String, String>();

    public CachingAbbreviator(Abbreviator delegate) {
        super();
        this.delegate = delegate;
    }

    @Override
    public String abbreviate(String in) {
        String abbreviation = cache.get(in);
        if (abbreviation == null) {
            abbreviation = delegate.abbreviate(in);
            cache.putIfAbsent(in, abbreviation);
        }
        return abbreviation;
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        cache.clear();
    }
}
