package com.slavacom.vkmidsprint.service.tarantool;

import io.tarantool.mapping.TarantoolResponse;

import java.util.Collections;
import java.util.List;

/**
 * Парсер для извлечения данных из ответов Tarantool
 */
public class TarantoolResponseParser {

	private TarantoolResponseParser() {
		// Утилитный класс
	}

	/**
	 * Извлекает список кортежей из ответа Tarantool
	 */
	public static List<?> extractTuples(TarantoolResponse<List<?>> response) {
		if (response == null) {
			return Collections.emptyList();
		}

		List<?> result = response.get();
		return result != null ? result : Collections.emptyList();
	}

	/**
	 * Извлекает первый кортеж из списка
	 */
	public static List<?> getFirstTuple(List<?> tuples) {
		if (tuples.isEmpty()) {
			return Collections.emptyList();
		}

		Object firstTuple = tuples.getFirst();
		if (firstTuple instanceof List<?> list) {
			if (list.size() == 1 && list.getFirst() instanceof List<?> nestedTuple) {
				return nestedTuple;
			}
			return list;
		}

		// На случай, если tuple пришел напрямую как [key, value]
		if (tuples.size() > 1) {
			return tuples;
		}

		return Collections.emptyList();
	}

	/**
	 * Нормализует список tuple'ов, учитывая возможную дополнительную вложенность от eval
	 */
	public static List<?> normalizeTuples(List<?> tuples) {
		if (tuples.isEmpty()) {
			return Collections.emptyList();
		}

		Object first = tuples.getFirst();
		if (tuples.size() == 1 && first instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof List<?>) {
			return list;
		}

		return tuples;
	}

	/**
	 * Приводит объект к tuple [key, value] с учетом вложенности
	 */
	public static List<?> asTuple(Object tupleObj) {
		if (!(tupleObj instanceof List<?> list)) {
			return Collections.emptyList();
		}

		if (list.size() == 1 && list.getFirst() instanceof List<?> nestedTuple) {
			return nestedTuple;
		}

		return list;
	}

	/**
	 * Извлекает значение из tuple по индексу
	 * Tuple структура: [key, value]
	 */
	public static Object getValue(List<?> tuple, int index) {
		if (tuple.size() > index) {
			return tuple.get(index);
		}
		return null;
	}

	/**
	 * Проверяет, является ли элемент кортежем (списком)
	 */
	public static boolean isTuple(Object obj) {
		return obj instanceof List<?>;
	}

	/**
	 * Извлекает целое число из ответа (для count операции)
	 */
	public static int extractCount(TarantoolResponse<List<?>> response) {
		List<?> resultList = response.get();

		if (resultList == null || resultList.isEmpty() || resultList.getFirst() == null) {
			return 0;
		}

		Object count = resultList.getFirst();
		if (count instanceof Integer) {
			return (Integer) count;
		}

		if (count instanceof Number num) {
			return num.intValue();
		}

		return 0;
	}
}

