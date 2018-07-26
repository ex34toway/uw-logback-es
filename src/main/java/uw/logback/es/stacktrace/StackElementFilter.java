package uw.logback.es.stacktrace;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Component in charge of accepting or rejecting {@link StackTraceElement elements} when computing a stack trace hash
 */
public abstract class StackElementFilter {
    /**
     * Tests whether or not the specified {@link StackTraceElement} should be
     * accepted when computing a stack hash.
     *
     * @param element The {@link StackTraceElement} to be tested
     * @return <code>true</code> if and only if <code>element</code>
     * should be accepted
     */
    public abstract boolean accept(StackTraceElement element);

    /**
     * Creates a {@link StackElementFilter} that accepts any stack trace elements
     */
    public static final StackElementFilter any() {
        return new StackElementFilter() {
            @Override
            public boolean accept(StackTraceElement element) {
                return true;
            }
        };
    }

    /**
     * Creates a {@link StackElementFilter} that accepts all stack trace elements with a non {@code null}
     * {@code {@link StackTraceElement#getFileName()} filename} and positive {@link StackTraceElement#getLineNumber()} line number}
     */
    public static final StackElementFilter withSourceInfo() {
        return new StackElementFilter() {
            @Override
            public boolean accept(StackTraceElement element) {
                return element.getFileName() != null && element.getLineNumber() >= 0;
            }
        };
    }

    /**
     * Creates a {@link StackElementFilter} by exclusion {@link Pattern patterns}
     *
     * @param excludes regular expressions matching {@link StackTraceElement} to filter out
     * @return the filter
     */
    public static final StackElementFilter byPattern(final List<Pattern> excludes) {
        return new StackElementFilter() {
            @Override
            public boolean accept(StackTraceElement element) {
                if (!excludes.isEmpty()) {
                    String classNameAndMethod = element.getClassName() + "." + element.getMethodName();
                    for (Pattern exclusionPattern : excludes) {
                        if (exclusionPattern.matcher(classNameAndMethod).find()) {
                            return false;
                        }
                    }
                }
                return true;
            }
        };
    }
}
