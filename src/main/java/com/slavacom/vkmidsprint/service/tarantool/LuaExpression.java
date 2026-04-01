package com.slavacom.vkmidsprint.service.tarantool;

/**
 * Константы для Lua выражений, выполняемых в Tarantool
 */
public class LuaExpression {

	private static final String SPACE_PATTERN = "box.space.%s";
	private static final String SPACE_NAME = "kv";

	/**
	 * Lua выражение для вставки/замены записи
	 * Параметры: key (string), value (varbinary)
	 */
	public static String replace() {
		return "local key, value = ...; return " + getSpaceName() + ":replace({key, value})";
	}

	/**
	 * Lua выражение для получения записи по ключу
	 * Параметр: key (string)
	 */
	public static String selectByKey() {
		return "local key = ...; return " + getSpaceName() + ":select{key}";
	}

	/**
	 * Lua выражение для получения количества кортежей в Space
	 */
	public static String count() {
		return String.format("return %s:count()", getSpaceName());
	}

	/**
	 * Lua выражение для получения диапазона [key_since, key_to]
	 * Параметры: key_since (string), key_to (string)
	 */
	public static String rangeByKey() {
		return "local key_since, key_to = ...; " +
				"local result = {}; " +
				"for _, tuple in " + getSpaceName() + ".index.range_idx:pairs(key_since, {iterator = 'GE'}) do " +
				"if tuple[1] > key_to then break end; " +
				"table.insert(result, tuple); " +
				"end; " +
				"return result;";
	}

	/**
	 * Lua выражение для удаления записи
	 * Параметр: key (string)
	 */
	public static String delete() {
		return "local key = ...; return " + getSpaceName() + ":delete{key}";
	}



	private static String getSpaceName() {
		return String.format(SPACE_PATTERN, SPACE_NAME).intern();
	}
}

