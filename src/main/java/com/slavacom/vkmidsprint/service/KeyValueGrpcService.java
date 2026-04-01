package com.slavacom.vkmidsprint.service;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import io.tarantool.client.TarantoolClient;
import io.tarantool.client.crud.TarantoolCrudClient;
import io.tarantool.mapping.TarantoolResponse;
import kv.KeyValueServiceGrpc;
import kv.Keyvalue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class KeyValueGrpcService extends KeyValueServiceGrpc.KeyValueServiceImplBase {

	private final TarantoolCrudClient crudClient;
	private final TarantoolClient boxClient;

	private static final String SPACE_NAME = "kv";

	@Override
	public void put(Keyvalue.PutRequest request, StreamObserver<Keyvalue.PutResponse> responseObserver) {
		log.info("Received PUT request: key='{}', value size='{}'", request.getKey(), request.getValue().size());
		try {
			byte[] valueBytes = request.getValue().toByteArray();
			String expression = "local key, value = ...; return box.space." + SPACE_NAME + ":replace({key, value})";
			List<?> input = Arrays.asList(request.getKey(), valueBytes);
			boxClient.eval(expression, input).get();

			Keyvalue.PutResponse grpcResponse = Keyvalue.PutResponse.newBuilder()
					.setSuccess(true)
					.build();

			responseObserver.onNext(grpcResponse);
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error writing key='{}'", request.getKey(), e);
			responseObserver.onError(e);
		}
	}

	@Override
	public void get(Keyvalue.GetRequest request, StreamObserver<Keyvalue.GetResponse> responseObserver) {
		log.info("Received GET request: key='{}'", request.getKey());
		try {
			String expression = "local key = ...; return box.space." + SPACE_NAME + ":select{key}";
			List<?> input = Collections.singletonList(request.getKey());
			TarantoolResponse<List<?>> response = boxClient.eval(expression, input).get(); // Получаем список кортежей
			List<?> tuplesRaw = response.get();
			List<?> tuplesList;
			if (tuplesRaw.isEmpty()) {
				tuplesList = Collections.emptyList();
			} else {
				Object firstTuple = tuplesRaw.getFirst();
				if (firstTuple instanceof List<?> list) {
					tuplesList = list;
				} else {
					tuplesList = Collections.emptyList();
				}
			}
				// Извлекаем значение (находится во втором элементе tuple)
			ByteString valueBytes = ByteString.EMPTY;
			boolean found = false;

			if (!tuplesList.isEmpty()) {
				Object tuple = tuplesList.getFirst();
				if (tuple instanceof List<?> tupleList && tupleList.size() > 1) {
					Object value = tupleList.get(1);
					found = true;

					if (value instanceof byte[] bytes) {
						// Это обычный byte array
						valueBytes = ByteString.copyFrom(bytes);
					} else if (value instanceof String str) {
						// Это строка
						valueBytes = ByteString.copyFromUtf8(str);
					} else if (value instanceof java.util.Map<?, ?> map) {
						// Tarantool возвращает bytes как объект с числовыми ключами
						// Конвертируем в byte array
						byte[] bytes = new byte[map.size()];
						for (int i = 0; i < map.size(); i++) {
							Object byteObj = map.get(i);
							if (byteObj instanceof Number num) {
								bytes[i] = num.byteValue();
							}
						}
						valueBytes = ByteString.copyFrom(bytes);
					} else if (value != null) {
						// Fallback для других типов
						valueBytes = ByteString.copyFromUtf8(value.toString());
					}
				}
			}

			Keyvalue.GetResponse grpcResponse = Keyvalue.GetResponse.newBuilder()
					.setValue(valueBytes)
					.setFound(found)
					.build();
			responseObserver.onNext(grpcResponse);
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error reading key='{}'", request.getKey(), e);
			responseObserver.onError(e);
		}
	}

	@Override
	public void delete(Keyvalue.DeleteRequest
							   request, StreamObserver<Keyvalue.DeleteResponse> responseObserver) {
		log.info("Received DELETE request: key='{}'", request.getKey());
		try {
			String expression = "local key = ...; return box.space." + SPACE_NAME + ":delete{key}";
			boxClient.eval(expression, List.of(request.getKey())).get();

			Keyvalue.DeleteResponse grpcResponse = Keyvalue.DeleteResponse.newBuilder()
					.setSuccess(true)
					.build();

			responseObserver.onNext(grpcResponse);
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error deleting key='{}'", request.getKey(), e);
			responseObserver.onError(e);
		}
	}

	@Override
	public void count(Keyvalue.CountRequest request, StreamObserver<Keyvalue.CountResponse> responseObserver) {
		log.info("Received COUNT request");

		try {
			String expression = String.format("return #box.space.%s:select{}", SPACE_NAME);

			TarantoolResponse<List<?>> response = boxClient.eval(expression).get();

			List<?> resultList = response.get();
			int count = (resultList.isEmpty() || resultList.getFirst() == null) ? 0 : (Integer) resultList.getFirst();

			Keyvalue.CountResponse grpcResponse = Keyvalue.CountResponse.newBuilder()
					.setCount(count)
					.build();

			responseObserver.onNext(grpcResponse);
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error counting entries in space '{}'", SPACE_NAME, e);
			responseObserver.onError(e);
		}
	}
}