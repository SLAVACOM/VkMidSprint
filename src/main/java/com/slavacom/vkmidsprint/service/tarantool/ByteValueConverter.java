package com.slavacom.vkmidsprint.service.tarantool;

import com.google.protobuf.ByteString;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Конвертер для преобразования значений между Java и Tarantool типами
 */
public class ByteValueConverter {

	/**
	 * Преобразует значение из Tarantool в ByteString
	 * Обрабатывает случаи, когда Tarantool возвращает bytes в разных форматах
	 */
	public static ByteString toByteString(Object value) {
		if (value == null) {
			return ByteString.EMPTY;
		}

		if (value instanceof byte[] bytes) {
			return ByteString.copyFrom(bytes);
		}

		if (value instanceof String str) {
			return ByteString.copyFromUtf8(str);
		}

		if (value instanceof Map<?, ?> map) {
			return mapToByteString(map);
		}

		// Fallback для неизвестных типов
		return ByteString.copyFromUtf8(value.toString());
	}

	/**
	 * Преобразует значение из Tarantool в UTF-8 строку
	 */
	public static String toUtf8String(Object value) {
		if (value == null) {
			return "";
		}

		if (value instanceof String str) {
			return str;
		}

		if (value instanceof byte[] bytes) {
			return new String(bytes, StandardCharsets.UTF_8);
		}

		if (value instanceof Map<?, ?> map) {
			return mapToByteString(map).toStringUtf8();
		}

		return value.toString();
	}

	/**
	 * Преобразует Map с числовыми ключами в ByteString
	 * Используется когда Tarantool возвращает bytes как {"0": 215, "1": 110, ...}
	 */
	private static ByteString mapToByteString(Map<?, ?> map) {
		int maxIndex = -1;
		for (Object key : map.keySet()) {
			Integer index = parseIndex(key);
			if (index != null && index > maxIndex) {
				maxIndex = index;
			}
		}

		if (maxIndex < 0) {
			return ByteString.EMPTY;
		}

		byte[] bytes = new byte[maxIndex + 1];
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			Integer index = parseIndex(entry.getKey());
			if (index == null || index < 0 || index >= bytes.length) {
				continue;
			}

			Object byteObj = entry.getValue();
			if (byteObj instanceof Number num) {
				bytes[index] = num.byteValue();
			}
		}

		return ByteString.copyFrom(bytes);
	}

	private static Integer parseIndex(Object key) {
		if (key instanceof Number num) {
			return num.intValue();
		}
		if (key instanceof String str) {
			try {
				return Integer.parseInt(str);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}
}

