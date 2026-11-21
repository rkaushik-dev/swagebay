package io.github.rkaushikdev.swagebay.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The service's own route: is it up, and which version is it. */
@RestController
@Tag(name = "service")
public class ServiceRoutes {

    /** {@code {"status": "ok", "version": "1.0.0"}}. Does not touch the database. */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("version", SwagebayApplication.VERSION);
        return body;
    }
}
