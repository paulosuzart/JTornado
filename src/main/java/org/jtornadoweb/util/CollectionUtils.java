package org.jtornadoweb.util;

import java.util.Map;

public class CollectionUtils {

	public static <K, V> V setDefault(Map<K, V> map, K key, V value) {

		V _value = map.get(key);
		if (_value != null)
			return _value;
		else {
			map.put(key, value);
			return value;
		}

	}

}
