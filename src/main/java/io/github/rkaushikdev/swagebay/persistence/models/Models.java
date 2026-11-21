package io.github.rkaushikdev.swagebay.persistence.models;

import java.util.List;

/**
 * Every table in the schema, gathered so the mapping is complete.
 *
 * <p>The database is built from this list and from nothing else: no class path is scanned, so a
 * schema never ends up holding whatever happened to be found first, and a row class that is not on
 * the list is not mapped at all. Anything that builds or validates the schema goes through here.
 *
 * <p>The rows share a few habits. A column that may be empty is read as an {@code Optional} and
 * cleared by setting {@code null}. A required number with no default is held boxed, so a row that
 * never had it set is refused by the schema rather than written as zero. The row a child hangs off
 * is set when the child is added to it, and reads as empty until then. A parent's children are read
 * only from outside: a child is added through its parent, which keeps both sides in step, and is
 * written, and deleted, with it. A device's trips are the one list that is only ever read.
 */
public final class Models {

    /** The entity classes, one per table. */
    public static final List<Class<?>> ENTITIES = List.of(
            AuditRow.class,
            CounterRow.class,
            InspectionRow.class,
            DefectRow.class,
            NonconformanceRow.class,
            MaterialRow.class,
            IsotopeRow.class,
            FilmRow.class,
            IncidentRow.class,
            CalibrationRow.class,
            FiledRow.class,
            LotRow.class,
            QualificationRow.class,
            ControlStripRow.class,
            SurveyRow.class,
            SurveyPointRow.class,
            DoseEntryRow.class,
            DosimeterRow.class,
            ExchangeRow.class,
            UseRow.class,
            SourceRow.class,
            LeakTestRow.class,
            DeviceRow.class,
            TripRow.class,
            SightingRow.class,
            StoredImageRow.class,
            JobRow.class,
            WeldRow.class,
            RadiographRow.class,
            IndicationRow.class);

    private Models() {}
}
