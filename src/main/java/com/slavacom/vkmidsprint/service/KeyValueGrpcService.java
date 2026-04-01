package com.slavacom.vkmidsprint.service;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.tarantool.mapping.TarantoolResponse;
import kv.KeyValueServiceGrpc;
import kv.Keyvalue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import com.slavacom.vkmidsprint.service.tarantool.ByteValueConverter;
import com.slavacom.vkmidsprint.service.tarantool.TarantoolResponseParser;

import java.util.List;

/**
 * gRPC сервис для работы с хранилищем ключ-значение
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class KeyValueGrpcService extends KeyValueServiceGrpc.KeyValueServiceImplBase {

	private final KeyValueService keyValueService;

	@Override
	public void put(Keyvalue.PutRequest request, StreamObserver<Keyvalue.PutResponse> responseObserver) {
		String key = request.getKey();
		log.info("Received PUT request: key='{}', value size='{}'", key, request.getValue().size());

		try {
			byte[] valueBytes = request.getValue().toByteArray();
			keyValueService.put(key, valueBytes);

			Keyvalue.PutResponse response = Keyvalue.PutResponse.newBuilder()
					.setSuccess(true)
					.build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error writing key='{}'", key, e);
			responseObserver.onError(e);
		}
	}

	@Override
	public void get(Keyvalue.GetRequest request, StreamObserver<Keyvalue.GetResponse> responseObserver) {
		String key = request.getKey();
		log.info("Received GET request: key='{}'", key);
		
		try {
			ValueResult result = fetchValue(key);
			
			Keyvalue.GetResponse response = Keyvalue.GetResponse.newBuilder()
					.setValue(ByteValueConverter.toByteString(result.value()))
					.setFound(result.found())
					.build();
			
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error reading key='{}'", key, e);
			responseObserver.onError(e);
		}
	}

	@Override
	public void getString(Keyvalue.GetRequest request, StreamObserver<Keyvalue.GetStringResponse> responseObserver) {
		String key = request.getKey();
		log.info("Received GET_STRING request: key='{}'", key);

		try {
			ValueResult result = fetchValue(key);

			Keyvalue.GetStringResponse response = Keyvalue.GetStringResponse.newBuilder()
					.setValue(ByteValueConverter.toUtf8String(result.value()))
					.setFound(result.found())
					.build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error reading string key='{}'", key, e);
			responseObserver.onError(e);
		}
	}

	@Override
	public void delete(Keyvalue.DeleteRequest request, StreamObserver<Keyvalue.DeleteResponse> responseObserver) {
		String key = request.getKey();
		log.info("Received DELETE request: key='{}'", key);
		
		try {
			keyValueService.delete(key);
			
			Keyvalue.DeleteResponse response = Keyvalue.DeleteResponse.newBuilder()
					.setSuccess(true)
					.build();
			
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error deleting key='{}'", key, e);
			responseObserver.onError(e);
		}
	}

	@Override
	public void count(Keyvalue.CountRequest request, StreamObserver<Keyvalue.CountResponse> responseObserver) {
		log.info("Received COUNT request");
		
		try {
			TarantoolResponse<List<?>> tarantoolResponse = keyValueService.count();
			int count = TarantoolResponseParser.extractCount(tarantoolResponse);
			
			Keyvalue.CountResponse response = Keyvalue.CountResponse.newBuilder()
					.setCount(count)
					.build();
			
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error counting entries", e);
			responseObserver.onError(e);
		}
	}

	@Override
	public void range(Keyvalue.RangeRequest request, StreamObserver<Keyvalue.RangeResponse> responseObserver) {
		String keySince = request.getKeySince();
		String keyTo = request.getKeyTo();
		log.info("Received RANGE request: keySince='{}', keyTo='{}'", keySince, keyTo);

		if (keySince.isBlank() || keyTo.isBlank()) {
			responseObserver.onError(Status.INVALID_ARGUMENT
					.withDescription("key_since and key_to must be non-empty")
					.asRuntimeException());
			return;
		}

		if (keySince.compareTo(keyTo) > 0) {
			responseObserver.onError(Status.INVALID_ARGUMENT
					.withDescription("key_since must be <= key_to")
					.asRuntimeException());
			return;
		}

		try {
			TarantoolResponse<List<?>> tarantoolResponse = keyValueService.range(keySince, keyTo);
			List<?> tuples = TarantoolResponseParser.extractTuples(tarantoolResponse);
			List<?> normalizedTuples = TarantoolResponseParser.normalizeTuples(tuples);

			for (Object tupleObj : normalizedTuples) {
				List<?> tuple = TarantoolResponseParser.asTuple(tupleObj);
				if (tuple.size() < 2) {
					continue;
				}

				Object keyObj = TarantoolResponseParser.getValue(tuple, 0);
				Object valueObj = TarantoolResponseParser.getValue(tuple, 1);
				if (keyObj == null) {
					continue;
				}

				Keyvalue.RangeResponse response = Keyvalue.RangeResponse.newBuilder()
						.setKey(keyObj.toString())
						.setValue(ByteValueConverter.toByteString(valueObj))
						.build();
				responseObserver.onNext(response);
			}

			responseObserver.onCompleted();
		} catch (Exception e) {
			log.error("Error reading range from '{}' to '{}'", keySince, keyTo, e);
			responseObserver.onError(e);
		}
	}

	private ValueResult fetchValue(String key) throws Exception {
		TarantoolResponse<List<?>> tarantoolResponse = keyValueService.get(key);
		List<?> tuples = TarantoolResponseParser.extractTuples(tarantoolResponse);
		List<?> firstTuple = TarantoolResponseParser.getFirstTuple(tuples);

		if (firstTuple.isEmpty()) {
			return new ValueResult(null, false);
		}

		Object value = TarantoolResponseParser.getValue(firstTuple, 1);
		return new ValueResult(value, true);
	}

	private record ValueResult(Object value, boolean found) {
	}
}