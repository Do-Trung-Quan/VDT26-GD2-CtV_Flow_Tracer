package com.ctv.flowtracer.core.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SchemaValidatorTest {

    private static final String SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "required": ["code", "items"],
              "additionalProperties": false,
              "properties": {
                "code": { "type": "string", "minLength": 1 },
                "items": { "type": "array", "items": { "$ref": "#/$defs/Item" } }
              },
              "$defs": { "Item": { "type": "object", "required": ["qty"], "properties": { "qty": { "type": "integer", "minimum": 1 } } } }
            }
            """;

    private final SchemaValidator validator = new SchemaValidator();

    @Test
    void validJsonHasNoErrors() {
        assertThat(validator.validate(SCHEMA, "{\"code\":\"A\",\"items\":[{\"qty\":2}]}")).isEmpty();
    }

    @Test
    void reportsEveryViolationWithLocation() {
        var errors = validator.validate(SCHEMA, "{\"code\":\"\",\"items\":[{\"qty\":0}],\"extra\":1}");

        assertThat(errors).hasSize(3);
        assertThat(String.join("\n", errors)).contains("/code: ").contains("/items/0/qty: ").contains("extra");
    }

    @Test
    void malformedJsonIsAnError() {
        assertThat(validator.validate(SCHEMA, "{not json")).singleElement().asString().startsWith("Không phải JSON hợp lệ");
    }

    @Test
    void brokenSchemaIsRejected() {
        assertThatThrownBy(() -> validator.validate("{ not a schema", "{}")).isInstanceOf(IllegalArgumentException.class);
    }
}
