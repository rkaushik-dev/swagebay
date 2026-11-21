package io.github.rkaushikdev.swagebay.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * A test of the service's routes: the whole application, every router mounted, over the suite's
 * database, driven through MockMvc with no server. The API document is served too, which a slice
 * would leave out. Every class carrying this shares one application, so it starts once per run.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(classes = SwagebayApplication.class)
@AutoConfigureMockMvc
@Import(TestService.class)
public @interface RouteTest {}
