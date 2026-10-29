package com.erpschool.common.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;

public class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = p.getValueAsString();
        try {
            return Instants.parse(raw);
        } catch (IllegalArgumentException ex) {
            throw ctxt.weirdStringException(raw, Instant.class, ex.getMessage());
        }
    }
}
