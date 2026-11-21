package io.github.rkaushikdev.swagebay.persistence;

import io.github.rkaushikdev.swagebay.persistence.models.Models;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.Check;

/** What the entities declare about their tables, read off their annotations. */
final class Mapping {

    /** One foreign key: the column it is on, its name, and the table it refers to. */
    record ForeignKey(String column, String name, String referred) {}

    private Mapping() {}

    /** Every entity class, one per table. */
    static List<Class<?>> entities() {
        return Models.ENTITIES;
    }

    static String table(Class<?> entity) {
        return entity.getAnnotation(Table.class).name();
    }

    static List<UniqueConstraint> uniques(Class<?> entity) {
        return List.of(entity.getAnnotation(Table.class).uniqueConstraints());
    }

    static List<Index> indexes(Class<?> entity) {
        return List.of(entity.getAnnotation(Table.class).indexes());
    }

    static List<Check> checks(Class<?> entity) {
        return List.of(entity.getAnnotationsByType(Check.class));
    }

    static List<ForeignKey> foreignKeys(Class<?> entity) {
        return Arrays.stream(entity.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(JoinColumn.class))
                .map(Mapping::foreignKey)
                .toList();
    }

    private static ForeignKey foreignKey(Field field) {
        JoinColumn column = field.getAnnotation(JoinColumn.class);
        return new ForeignKey(column.name(), column.foreignKey().name(), table(field.getType()));
    }
}
