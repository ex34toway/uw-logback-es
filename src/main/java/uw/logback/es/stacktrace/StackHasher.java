package uw.logback.es.stacktrace;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Utility class that generates a hash from any Java {@link Throwable error}
 *
 * @author Pierre Smeyers
 */
public class StackHasher {

    private final StackElementFilter filter;

    /**
     * Constructs a {@link StackHasher} with the given filter
     *
     * @param filter filter
     */
    public StackHasher(StackElementFilter filter) {
        this.filter = filter;
    }

    /**
     * Constructs a {@link StackHasher} using {@link StackElementFilter#withSourceInfo()} filter
     */
    public StackHasher() {
        this(StackElementFilter.withSourceInfo());
    }

    /**
     * Generates a Hexadecimal hash for the given error stack
     * <p>
     * Two errors with the same stack hash are most probably same errors
     */
    public String hexHash(Throwable error) {
        // compute topmost error hash, but don't queue the complete hashes chain
        return toHex(hash(error, null));
    }

    /**
     * Generates and returns Hexadecimal hashes for the error stack and each ancestor {@link Throwable#getCause() cause}
     * <p>
     * The first queue element is the stack hash for the topmost error, the next one (if any) is it's direct
     * {@link Throwable#getCause() cause} hash, and so on...
     */
    public Deque<String> hexHashes(Throwable error) {
        Deque<String> hexHashes = new ArrayDeque<String>();
        hash(error, hexHashes);
        return hexHashes;
    }

    /**
     * Generates a hash (int) of the given error stack
     * <p>
     * Two errors with the same stack hash are most probably same errors
     */
    int hash(Throwable error, Deque<String> hexHashes) {
        int hash = 0;

        // compute parent error hash
        if (error.getCause() != null && error.getCause() != error) {
            // has parent error
            hash = hash(error.getCause(), hexHashes);
        }

        // then this error hash
        // hash error classname
        hash = 31 * hash + error.getClass().getName().hashCode();
        // hash stacktrace
        for (StackTraceElement element : error.getStackTrace()) {
            if (filter.accept(element)) {
                hash = 31 * hash + hash(element);
            }
        }

        // push hexadecimal representation of hash
        if(hexHashes != null) {
            hexHashes.push(toHex(hash));
        }

        return hash;
    }

    String toHex(int hash) {
        return String.format("%08x", hash);
    }

    int hash(StackTraceElement element) {
        int result = element.getClassName().hashCode();
        result = 31 * result + element.getMethodName().hashCode();
        // let's assume filename is not necessary
        result = 31 * result + element.getLineNumber();
        return result;
    }
}
