package com.slavacom.vkmidsprint.service;

import com.slavacom.vkmidsprint.service.tarantool.LuaExpression;
import io.tarantool.client.TarantoolClient;
import io.tarantool.mapping.TarantoolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyValueServiceTest {

    @Mock
    private TarantoolClient boxClient;

    private KeyValueService service;

    @BeforeEach
    void setUp() {
        service = new KeyValueService(boxClient);
    }

    @Test
    void putUsesReplaceExpression() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        when(boxClient.eval(eq(LuaExpression.replace()), anyList()))
                .thenReturn(CompletableFuture.completedFuture(response));

        byte[] value = new byte[]{1, 2, 3};
        service.put("k1", value);

            verify(boxClient).eval(eq(LuaExpression.replace()), argThat((List<?> input) -> {
            if (input.size() != 2 || !"k1".equals(input.getFirst())) {
                return false;
            }
            if (!(input.get(1) instanceof byte[] bytes)) {
                return false;
            }
            assertArrayEquals(value, bytes);
            return true;
        }));
    }

    @Test
    void getUsesSelectExpression() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        when(boxClient.eval(eq(LuaExpression.selectByKey()), anyList()))
                .thenReturn(CompletableFuture.completedFuture(response));

        service.get("k2");

        verify(boxClient).eval(eq(LuaExpression.selectByKey()), eq(List.of("k2")));
    }

    @Test
    void rangeUsesRangeExpression() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        when(boxClient.eval(eq(LuaExpression.rangeByKey()), anyList()))
                .thenReturn(CompletableFuture.completedFuture(response));

        service.range("a", "z");

        verify(boxClient).eval(eq(LuaExpression.rangeByKey()), eq(List.of("a", "z")));
    }

    @Test
    void putPropagatesClientErrors() {
        when(boxClient.eval(eq(LuaExpression.replace()), anyList()))
                .thenThrow(new RuntimeException("tarantool down"));

        assertThrows(Exception.class, () -> service.put("key", new byte[]{9}));
    }

    @SuppressWarnings("unchecked")
    private TarantoolResponse<List<?>> tarantoolResponseMock() {
        return (TarantoolResponse<List<?>>) mock(TarantoolResponse.class);
    }
}

