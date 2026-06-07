package com.seasonplan.lifecycle.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

final class JdbcJson {
    private JdbcJson() {
    }

    static String write(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Could not serialize JSON value", error);
        }
    }

    static Map<String, String> readStringMap(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Could not deserialize string map", error);
        }
    }

    static Map<String, Double> readDoubleMap(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Could not deserialize numeric map", error);
        }
    }
}
