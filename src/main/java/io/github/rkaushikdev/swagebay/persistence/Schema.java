package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.persistence.models.Models;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The naming convention every constraint gets, and the tables the mapping declares.
 *
 * <p>Named constraints are not a nicety. A migration that changes a constraint drops it by name,
 * and on a database where the names were left to the engine those names differ from one engine to
 * the next, so a migration that runs in the test suite fails on the server. Every constraint and
 * index in the migrations and on the entities carries the name this convention gives it, from the
 * first migration onward, and the tests hold both to it.
 *
 * <p>Every table is keyed on an integer surrogate (see {@link Keyed}), and the domain identifier
 * is a unique column beside it.
 */
public final class Schema {

    /**
     * The name of each kind of constraint, by its short code: {@code pk} primary key, {@code uq}
     * unique, {@code ck} check, {@code fk} foreign key and {@code ix} index. A unique constraint, a
     * foreign key and an index are named for their first column.
     */
    public static final Map<String, String> NAMING_CONVENTION = convention();

    private Schema() {}

    private static Map<String, String> convention() {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.put("ix", "ix_{table}_{column}");
        templates.put("uq", "uq_{table}_{column}");
        templates.put("ck", "ck_{table}_{name}");
        templates.put("fk", "fk_{table}_{column}_{referred_table}");
        templates.put("pk", "pk_{table}");
        return Collections.unmodifiableMap(templates);
    }

    /** The name of the primary key of {@code table}. */
    public static String primaryKey(String table) {
        return name("pk", Map.of("table", table));
    }

    /** The name of a unique constraint on {@code table} whose first column is {@code column}. */
    public static String unique(String table, String column) {
        return name("uq", Map.of("table", table, "column", column));
    }

    /** The name of the check constraint on {@code table} that its declaration calls {@code name}. */
    public static String check(String table, String name) {
        return name("ck", Map.of("table", table, "name", name));
    }

    /** The name of the foreign key from {@code column} of {@code table} to {@code referred}. */
    public static String foreignKey(String table, String column, String referred) {
        return name("fk", Map.of("table", table, "column", column, "referred_table", referred));
    }

    /** The name of an index on {@code table} whose first column is {@code column}. */
    public static String index(String table, String column) {
        return name("ix", Map.of("table", table, "column", column));
    }

    /** Every table the mapping declares, sorted. */
    public static List<String> tableNames() {
        return Models.ENTITIES.stream()
                .map(entity -> entity.getAnnotation(Table.class).name())
                .sorted()
                .toList();
    }

    private static String name(String kind, Map<String, String> parts) {
        String named = NAMING_CONVENTION.get(kind);
        for (Map.Entry<String, String> part : parts.entrySet()) {
            named = named.replace("{" + part.getKey() + "}", part.getValue());
        }
        return named;
    }
}
