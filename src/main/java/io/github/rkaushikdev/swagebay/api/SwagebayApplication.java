package io.github.rkaushikdev.swagebay.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.swagebay.Swagebay;
import io.github.rkaushikdev.swagebay.api.routes.NonconformanceRoutes;
import io.github.rkaushikdev.swagebay.persistence.Database;
import io.github.rkaushikdev.swagebay.util.Times;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The HTTP service, and the one dependency everything else hangs off: the {@link Database}.
 *
 * <p>The database is handed to the application rather than found by it. That is the difference
 * between a test that can stand up two applications against two databases and a suite that has to
 * run against one, and the second kind stops being runnable at about forty tests. Units of work are
 * per request, through {@link Work}; nothing caches a domain object between requests.
 *
 * <p>Nothing is found by scanning the class path. The service is the error handling in {@link
 * Errors}, the health route, and the routers listed in {@link Routers}, which is the one place a
 * router is mounted. Spring Boot's own data source, JPA and Flyway set up are switched off: with H2
 * on the class path and no data source of its own, Boot would otherwise build an embedded database
 * and migrate that. The service never builds or migrates a schema; the command line does.
 *
 * <p>There is no authentication here. That is done by the reverse proxy in front of the service,
 * which is where the client certificates live, and a second half implemented copy of it in here
 * would be worse than having none.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(
        exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
        })
@Import({Errors.class, ServiceRoutes.class, SwagebayApplication.Routers.class})
public class SwagebayApplication implements WebMvcConfigurer {

    /** The service's name, as the API document gives it. */
    public static final String TITLE = "swagebay";

    /** What the service is for, as the API document gives it. */
    public static final String SUMMARY = "radiographic technique planning and image quality qualification";

    /** The version the health route and the API document report. */
    public static final String VERSION = Swagebay.VERSION;

    private static final String DATABASE = "database";

    /** Every router the service mounts. A new router is mounted by adding it here, and nowhere else. */
    @Import({NonconformanceRoutes.class})
    static final class Routers {}

    /**
     * One application over one database, which somebody else opened, migrated and will close.
     *
     * @param args Spring Boot arguments, {@code --server.port=0} for instance
     */
    public static ConfigurableApplicationContext run(Database database, String... args) {
        return run(database, List.of(), args);
    }

    /**
     * One application over one database with more routes beside its own: the ones a test adds, for
     * instance.
     */
    public static ConfigurableApplicationContext run(Database database, List<Class<?>> routes, String... args) {
        return start(context -> context.getBeanFactory().registerSingleton(DATABASE, database), routes, args);
    }

    /**
     * The service as deployed: over the database at {@code url}, which the application opens when it
     * starts and closes when it stops. The schema has to be there already.
     */
    public static ConfigurableApplicationContext runDefault(String url, String... args) {
        return start(
                context -> context.registerBean(DATABASE, Database.class, () -> Database.open(url)), List.of(), args);
    }

    /** Serve the database at {@link Database#DEFAULT_URL}. */
    public static void main(String[] args) {
        runDefault(Database.DEFAULT_URL, args);
    }

    private static ConfigurableApplicationContext start(
            ApplicationContextInitializer<GenericApplicationContext> database, List<Class<?>> routes, String... args) {
        List<Class<?>> sources = new ArrayList<>();
        sources.add(SwagebayApplication.class);
        sources.addAll(routes);
        return new SpringApplicationBuilder(sources.toArray(Class<?>[]::new))
                .initializers(database)
                .run(args);
    }

    @Bean
    Work work(Database database) {
        return new Work(database);
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer json() {
        return Json::configure;
    }

    /** The API document's shapes are named the way the service writes them, in snake case. */
    @Bean
    ModelResolver shapes(ObjectMapper mapper) {
        return new ModelResolver(mapper);
    }

    @Bean
    OpenAPI document() {
        return new OpenAPI().info(new Info().title(TITLE).summary(SUMMARY).version(VERSION));
    }

    /** Days and moments in a query or a path read the way they do in a body. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, LocalDate.class, LocalDate::parse);
        registry.addConverter(String.class, LocalDateTime.class, Times::parse);
    }
}
