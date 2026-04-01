package com.slavacom.vkmidsprint.service;

import com.slavacom.vkmidsprint.service.tarantool.LuaExpression;
import io.tarantool.client.TarantoolClient;
import io.tarantool.mapping.TarantoolResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Сервис для работы с хранилищем ключ-значение через Tarantool
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyValueService {

	private final TarantoolClient boxClient;

	/**
	 * Вставляет или обновляет значение по ключу
	 */
	public void put(String key, byte[] value) throws Exception {
		try {
			String expression = LuaExpression.replace();
			List<?> input = List.of(key, value);
			boxClient.eval(expression, input).get();
			log.debug("Successfully put key='{}', value size='{}'", key, value.length);
		} catch (Exception e) {
			log.error("Error putting key='{}'", key, e);
			throw e;
		}
	}

	/**
	 * Получает значение по ключу
	 */
	public TarantoolResponse<List<?>> get(String key) throws Exception {
		try {
			String expression = LuaExpression.selectByKey();
			List<?> input = List.of(key);
			CompletableFuture<TarantoolResponse<List<?>>> future = boxClient.eval(expression, input);
			TarantoolResponse<List<?>> response = future.get();
			log.debug("Successfully got key='{}'", key);
			return response;
		} catch (Exception e) {
			log.error("Error getting key='{}'", key, e);
			throw e;
		}
	}

	/**
	 * Удаляет значение по ключу
	 */
	public void delete(String key) throws Exception {
		try {
			String expression = LuaExpression.delete();
			List<?> input = List.of(key);
			boxClient.eval(expression, input).get();
			log.debug("Successfully deleted key='{}'", key);
		} catch (Exception e) {
			log.error("Error deleting key='{}'", key, e);
			throw e;
		}
	}

	/**
	 * Подсчитывает количество записей в хранилище
	 */
	public TarantoolResponse<List<?>> count() throws Exception {
		try {

			String expression = LuaExpression.count();
			CompletableFuture<TarantoolResponse<List<?>>> future = boxClient.eval(expression);
			TarantoolResponse<List<?>> response = future.get();
			log.debug("Successfully counted entries");
			return response;
		} catch (Exception e) {
			log.error("Error counting entries", e);
			throw e;
		}
	}

	/**
	 * Возвращает все записи в диапазоне ключей [keySince, keyTo]
	 */
	public TarantoolResponse<List<?>> range(String keySince, String keyTo) throws Exception {
		try {
			String expression = LuaExpression.rangeByKey();
			List<?> input = List.of(keySince, keyTo);
			CompletableFuture<TarantoolResponse<List<?>>> future = boxClient.eval(expression, input);
			TarantoolResponse<List<?>> response = future.get();
			log.debug("Successfully got range from '{}' to '{}'", keySince, keyTo);
			return response;
		} catch (Exception e) {
			log.error("Error getting range from '{}' to '{}'", keySince, keyTo, e);
			throw e;
		}
	}
}

