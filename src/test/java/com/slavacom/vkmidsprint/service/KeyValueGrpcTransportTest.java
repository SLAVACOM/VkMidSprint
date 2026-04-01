package com.slavacom.vkmidsprint.service;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.tarantool.mapping.TarantoolResponse;
import kv.KeyValueServiceGrpc;
import kv.Keyvalue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyValueGrpcTransportTest {

    @Mock
    private KeyValueService keyValueService;

    private Server server;
    private ManagedChannel channel;
    private KeyValueServiceGrpc.KeyValueServiceBlockingStub blockingStub;

    @BeforeEach
    void setUp() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new KeyValueGrpcService(keyValueService))
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        blockingStub = KeyValueServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    void putRpcWorksOverGrpcTransport() throws Exception {
        Keyvalue.PutResponse response = blockingStub.put(
                Keyvalue.PutRequest.newBuilder()
                        .setKey("grpc-put")
                        .setValue(ByteString.copyFromUtf8("value"))
                        .build()
        );

        assertTrue(response.getSuccess());
    }

    @Test
    void getStringRpcWorksOverGrpcTransport() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        doReturn((List<?>) List.of(List.of("grpc-key", "hello".getBytes()))).when(response).get();
        when(keyValueService.get("grpc-key")).thenReturn(response);

        Keyvalue.GetStringResponse getResponse = blockingStub.getString(
                Keyvalue.GetRequest.newBuilder().setKey("grpc-key").build()
        );

        assertTrue(getResponse.getFound());
        assertEquals("hello", getResponse.getValue());
    }

    @Test
    void rangeRpcStreamsResultsOverGrpcTransport() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        doReturn((List<?>) List.of(List.of(
                List.of("a", "1".getBytes()),
                List.of("b", "2".getBytes())
        ))).when(response).get();
        when(keyValueService.range("a", "z")).thenReturn(response);

        Iterator<Keyvalue.RangeResponse> stream = blockingStub.range(
                Keyvalue.RangeRequest.newBuilder().setKeySince("a").setKeyTo("z").build()
        );

        Keyvalue.RangeResponse first = stream.next();
        Keyvalue.RangeResponse second = stream.next();

        assertEquals("a", first.getKey());
        assertEquals("1", first.getValue().toStringUtf8());
        assertEquals("b", second.getKey());
    }

    @Test
    void rangeRpcReturnsInvalidArgumentForWrongBounds() {
        try {
            blockingStub.range(
                    Keyvalue.RangeRequest.newBuilder().setKeySince("z").setKeyTo("a").build()
            ).hasNext();
            fail("Expected INVALID_ARGUMENT");
        } catch (StatusRuntimeException e) {
            assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
        }
    }

    @SuppressWarnings("unchecked")
    private TarantoolResponse<List<?>> tarantoolResponseMock() {
        return (TarantoolResponse<List<?>>) mock(TarantoolResponse.class);
    }
}

