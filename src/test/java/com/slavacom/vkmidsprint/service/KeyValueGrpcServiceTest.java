package com.slavacom.vkmidsprint.service;

import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.tarantool.mapping.TarantoolResponse;
import kv.Keyvalue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyValueGrpcServiceTest {

    @Mock
    private KeyValueService keyValueService;

    private KeyValueGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new KeyValueGrpcService(keyValueService);
    }

    @Test
    void putReturnsSuccess() throws Exception {
        TestObserver<Keyvalue.PutResponse> observer = new TestObserver<>();

        grpcService.put(
                Keyvalue.PutRequest.newBuilder()
                        .setKey("k-put")
                        .setValue(ByteString.copyFromUtf8("payload"))
                        .build(),
                observer
        );

        assertTrue(observer.completed);
        assertNotNull(observer.value);
        assertTrue(observer.value.getSuccess());
    }

    @Test
    void putPassesErrorToObserver() throws Exception {
        doThrow(new RuntimeException("boom")).when(keyValueService).put("k-put-err", "x".getBytes());

        TestObserver<Keyvalue.PutResponse> observer = new TestObserver<>();
        grpcService.put(
                Keyvalue.PutRequest.newBuilder()
                        .setKey("k-put-err")
                        .setValue(ByteString.copyFromUtf8("x"))
                        .build(),
                observer
        );

        assertNotNull(observer.error);
        assertFalse(observer.completed);
    }

    @Test
    void getReturnsFoundValue() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        doReturn((List<?>) List.of(List.of("k1", "v1".getBytes()))).when(response).get();
        when(keyValueService.get("k1")).thenReturn(response);

        TestObserver<Keyvalue.GetResponse> observer = new TestObserver<>();
        grpcService.get(Keyvalue.GetRequest.newBuilder().setKey("k1").build(), observer);

        assertTrue(observer.completed);
        assertNotNull(observer.value);
        assertTrue(observer.value.getFound());
        assertEquals("v1", observer.value.getValue().toStringUtf8());
    }

    @Test
    void getReturnsNotFoundForEmptyResult() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        doReturn((List<?>) List.of()).when(response).get();
        when(keyValueService.get("missing")).thenReturn(response);

        TestObserver<Keyvalue.GetResponse> observer = new TestObserver<>();
        grpcService.get(Keyvalue.GetRequest.newBuilder().setKey("missing").build(), observer);

        assertTrue(observer.completed);
        assertNotNull(observer.value);
        assertFalse(observer.value.getFound());
        assertTrue(observer.value.getValue().isEmpty());
    }

    @Test
    void getPassesErrorToObserver() throws Exception {
        when(keyValueService.get("k-get-err")).thenThrow(new RuntimeException("boom"));

        TestObserver<Keyvalue.GetResponse> observer = new TestObserver<>();
        grpcService.get(Keyvalue.GetRequest.newBuilder().setKey("k-get-err").build(), observer);

        assertNotNull(observer.error);
        assertFalse(observer.completed);
    }

    @Test
    void getStringReturnsDecodedValue() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        doReturn((List<?>) List.of(List.of("k-str", "hello".getBytes()))).when(response).get();
        when(keyValueService.get("k-str")).thenReturn(response);

        TestObserver<Keyvalue.GetStringResponse> observer = new TestObserver<>();
        grpcService.getString(Keyvalue.GetRequest.newBuilder().setKey("k-str").build(), observer);

        assertTrue(observer.completed);
        assertNotNull(observer.value);
        assertTrue(observer.value.getFound());
        assertEquals("hello", observer.value.getValue());
    }

    @Test
    void deleteReturnsSuccess() throws Exception {
        TestObserver<Keyvalue.DeleteResponse> observer = new TestObserver<>();

        grpcService.delete(Keyvalue.DeleteRequest.newBuilder().setKey("k-del").build(), observer);

        assertTrue(observer.completed);
        assertNotNull(observer.value);
        assertTrue(observer.value.getSuccess());
    }

    @Test
    void countReturnsValueFromService() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        doReturn((List<?>) List.of(3)).when(response).get();
        when(keyValueService.count()).thenReturn(response);

        TestObserver<Keyvalue.CountResponse> observer = new TestObserver<>();
        grpcService.count(Keyvalue.CountRequest.newBuilder().build(), observer);

        assertTrue(observer.completed);
        assertNotNull(observer.value);
        assertEquals(3, observer.value.getCount());
    }

    @Test
    void rangeReturnsInvalidArgumentForBlankBounds() {
        TestObserver<Keyvalue.RangeResponse> observer = new TestObserver<>();

        grpcService.range(
                Keyvalue.RangeRequest.newBuilder().setKeySince("").setKeyTo("z").build(),
                observer
        );

        assertNotNull(observer.error);
        assertInstanceOf(StatusRuntimeException.class, observer.error);
    }

    @Test
    void rangeReturnsInvalidArgumentForReversedBounds() {
        TestObserver<Keyvalue.RangeResponse> observer = new TestObserver<>();

        grpcService.range(
                Keyvalue.RangeRequest.newBuilder().setKeySince("z").setKeyTo("a").build(),
                observer
        );

        assertNotNull(observer.error);
        assertInstanceOf(StatusRuntimeException.class, observer.error);
    }

    @Test
    void rangeStreamsTuples() throws Exception {
        TarantoolResponse<List<?>> response = tarantoolResponseMock();
        doReturn((List<?>) List.of(List.of(
                List.of("a", ByteString.copyFromUtf8("1").toByteArray()),
                List.of("b", ByteString.copyFromUtf8("2").toByteArray())
        ))).when(response).get();
        when(keyValueService.range("a", "z")).thenReturn(response);

        TestObserver<Keyvalue.RangeResponse> observer = new TestObserver<>();
        grpcService.range(
                Keyvalue.RangeRequest.newBuilder().setKeySince("a").setKeyTo("z").build(),
                observer
        );

        assertTrue(observer.completed);
        assertEquals(2, observer.streamValues.size());
        assertEquals("a", observer.streamValues.getFirst().getKey());
        assertEquals("1", observer.streamValues.getFirst().getValue().toStringUtf8());
    }

    @Test
    void rangePassesServiceErrorToObserver() throws Exception {
        when(keyValueService.range("a", "z")).thenThrow(new RuntimeException("boom"));

        TestObserver<Keyvalue.RangeResponse> observer = new TestObserver<>();
        grpcService.range(
                Keyvalue.RangeRequest.newBuilder().setKeySince("a").setKeyTo("z").build(),
                observer
        );

        assertNotNull(observer.error);
        assertFalse(observer.completed);
    }

    @SuppressWarnings("unchecked")
    private TarantoolResponse<List<?>> tarantoolResponseMock() {
        return (TarantoolResponse<List<?>>) mock(TarantoolResponse.class);
    }

    private static class TestObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;
        private final List<T> streamValues = new ArrayList<>();

        @Override
        public void onNext(T value) {
            this.value = value;
            this.streamValues.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}

