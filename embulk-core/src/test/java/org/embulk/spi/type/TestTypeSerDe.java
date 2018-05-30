package org.embulk.spi.type;

import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.embulk.EmbulkTestRuntime;
import org.junit.Rule;
import org.junit.Test;

public class TestTypeSerDe {
    @Rule
    public EmbulkTestRuntime runtime = new EmbulkTestRuntime();

    private static class HasType {
        private Type type;
        // TODO test TimestampType

        @JsonCreator
        public HasType(
                @JsonProperty("type") Type type) {
            this.type = type;
        }

        @JsonProperty("type")
        public Type getType() {
            return type;
        }
    }

    @Test
    public void testGetType() {
        HasType type = new HasType(StringType.STRING);
        String json = runtime.getModelManager().writeObject(type);
        HasType decoded = runtime.getModelManager().readObject(HasType.class, json);
        assertTrue(StringType.STRING == decoded.getType());
    }
}
