package com.slavacom.vkmidsprint.service.tarantool;

import io.tarantool.mapping.TarantoolResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;

class TarantoolResponseParserTest {

    @Test
    void getFirstTupleUnwrapsEvalNestedShape() {
        List<?> tuples = List.of(List.of(List.of("k1", "v1")));

        List<?> firstTuple = TarantoolResponseParser.getFirstTuple(tuples);

        assertEquals("k1", firstTuple.get(0));
        assertEquals("v1", firstTuple.get(1));
    }

    @Test
    void normalizeTuplesUnwrapsSingleWrapper() {
        List<?> tuples = List.of(List.of(List.of("a", 1), List.of("b", 2)));

        List<?> normalized = TarantoolResponseParser.normalizeTuples(tuples);

        assertEquals(2, normalized.size());
    }

    @Test
    void extractCountSupportsNumber() {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        doReturn((List<?>) List.of(7L)).when(response).get();

        int count = TarantoolResponseParser.extractCount(response);

        assertEquals(7, count);
    }

    @Test
    void asTupleReturnsEmptyForNonList() {
        List<?> tuple = TarantoolResponseParser.asTuple("not-a-tuple");

        assertTrue(tuple.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private TarantoolResponse<List<?>> tarantoolResponseMock() {
        return (TarantoolResponse<List<?>>) mock(TarantoolResponse.class);
    }
}



