package io.github.rkaushikdev.swagebay.api;

import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.persistence.UnitOfWork;
import java.util.function.Function;

/**
 * One unit of work per request, committed only if the handler returns.
 *
 * <p>A handler that throws leaves the unit of work by the exception route, so it rolls back and the
 * commit is never reached. That is the whole transactional contract of the service, and it is this
 * short because the unit of work already carries it.
 *
 * <p>Handlers do not commit. The commit happens before the handler's answer is written, which is why
 * this is a call around the handler's body and not an interceptor: a response body is written before
 * an interceptor's {@code postHandle} runs, so a commit there that failed would follow a 200. The
 * handler builds its response inside the call, while what it loaded is still attached.
 */
public final class Work {

    private final Database database;

    /** The template over {@code database}, which somebody else opened and will close. */
    public Work(Database database) {
        this.database = database;
    }

    /** Run {@code handler} in a fresh unit of work, commit, and answer what it answered. */
    public <T> T apply(Function<? super UnitOfWork, ? extends T> handler) {
        return database.transactionResult(handler);
    }
}
