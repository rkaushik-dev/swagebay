package io.github.rkaushikdev.swagebay.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing wrong with a request, as a {@code 422} lists it under {@code detail}.
 *
 * <pre>{@code
 * {"type": "string_too_short", "loc": ["body", "account"],
 *  "msg": "String should have at least 1 character", "input": "", "ctx": {"min_length": 1}}
 * }</pre>
 *
 * @param type what kind of problem, a stable name a client can switch on
 * @param loc where it is: {@code body}, {@code query} or {@code path}, then the field names and
 *     list positions down to the value
 * @param msg the sentence a person reads
 * @param input the value that was refused, or for a missing field the object it is missing from;
 *     null when there was nothing
 * @param ctx the limits the value broke, when the problem has any
 */
public record Problem(String type, List<Object> loc, String msg, Object input, Optional<Map<String, Object>> ctx) {

    public Problem {
        Objects.requireNonNull(type, "type");
        loc = List.copyOf(loc);
        Objects.requireNonNull(msg, "msg");
        ctx = ctx.map(limits -> Collections.unmodifiableMap(new LinkedHashMap<>(limits)));
    }

    /** A problem with no limits to report. */
    public Problem(String type, List<Object> loc, String msg, Object input) {
        this(type, loc, msg, input, Optional.empty());
    }

    /** A problem with the limits the value broke. */
    public Problem(String type, List<Object> loc, String msg, Object input, Map<String, Object> ctx) {
        this(type, loc, msg, input, Optional.of(ctx));
    }

    /** The same problem found inside {@code where}: its location put after {@code where}'s. */
    public Problem under(List<Object> where) {
        List<Object> full = new ArrayList<>(where);
        full.addAll(loc);
        return new Problem(type, full, msg, input, ctx);
    }

    /** The entry as it is written in a response, the limits left out when there are none. */
    public Map<String, Object> asMap() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.put("loc", loc);
        entry.put("msg", msg);
        entry.put("input", input);
        ctx.ifPresent(limits -> entry.put("ctx", limits));
        return entry;
    }
}
