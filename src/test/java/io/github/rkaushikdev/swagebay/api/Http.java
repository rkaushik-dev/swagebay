package io.github.rkaushikdev.swagebay.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A small client over MockMvc for the API tests: send a request, get the status and the parsed body
 * back. A request the service does not answer, because a handler threw something that is not a
 * domain error, throws out of the call with that failure as its root cause.
 */
public final class Http {

    static final ObjectMapper JSON = Json.mapper();

    private final MockMvc mvc;

    public Http(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** {@code GET path}, with the query given as alternating names and values. */
    public Answer get(String path, String... query) {
        return send(withQuery(MockMvcRequestBuilders.get(path), query));
    }

    /** {@code POST path} with no body. */
    public Answer post(String path, String... query) {
        return send(withQuery(MockMvcRequestBuilders.post(path), query));
    }

    /** {@code POST path} with {@code body} written as JSON, and the query as alternating names and values. */
    public Answer postJson(String path, Object body, String... query) {
        return send(withQuery(MockMvcRequestBuilders.post(path), query)
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(body)));
    }

    /** Send a request built by hand. */
    public Answer send(MockHttpServletRequestBuilder request) {
        try {
            return new Answer(mvc.perform(request).andReturn());
        } catch (Exception failed) {
            throw new IllegalStateException("the request was not answered", failed);
        }
    }

    /** A JSON object as a map, to compare whole. */
    public static Map<String, Object> asMap(JsonNode tree) {
        return JSON.convertValue(tree, new TypeReference<Map<String, Object>>() {});
    }

    static String write(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static MockHttpServletRequestBuilder withQuery(MockHttpServletRequestBuilder request, String... query) {
        for (int at = 0; at + 1 < query.length; at += 2) {
            request.queryParam(query[at], query[at + 1]);
        }
        return request;
    }

    /** What came back. */
    public record Answer(MvcResult result) {

        public int status() {
            return result.getResponse().getStatus();
        }

        public String text() {
            return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        }

        public JsonNode json() {
            try {
                return JSON.readTree(text());
            } catch (JsonProcessingException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }
}
