package org.jtornadoweb.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups a series of static methods related to Http operations.
 * 
 * @author paulosuzart@gmail.com
 * 
 */
public class HttpUtils {

	/**
	 * Return all parameters. "Same" as python cgi.pase_qs. If no parameters are
	 * found, returns an empty Map of parameters (not null).
	 * 
	 * @param url
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static Map<String, List<String>> parseQueryString(String url)
			throws UnsupportedEncodingException {
		Map<String, List<String>> params = new HashMap<String, List<String>>();

		// Removes any ? or / of the url and splits it on & character.
		String[] query = url.replaceAll("[\\?/]", "").split("[&");
		// If there is nothing after / or ?, just returns an empty map of
		// parameters.
		if ("".equals(query[0]))
			return params;

		for (String param : query) {
			String pair[] = param.split("=");
			String key = URLDecoder.decode(pair[0], "UTF-8");
			String value = URLDecoder.decode(pair[1], "UTF-8");
			List<String> values = params.get(key);
			if (values == null) {
				values = new ArrayList<String>();
				params.put(key, values);
			}
			values.add(value);
		}

		return params;
	}
}
