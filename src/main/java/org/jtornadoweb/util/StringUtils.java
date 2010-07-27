package org.jtornadoweb.util;

/**
 * Methods with python semantics.
 * 
 * @author paulo
 * 
 */
public class StringUtils {

	public static final String COLON = ":";

	/**
	 * Same as s[:3], s[3:], s[-1], etc.
	 * 
	 * @param s
	 * @param i
	 * @return
	 */
	public static String substring(final String s, String i) {
		String[] is = i.split(":");

		/* Supposed to be a number TODO validate i */
		if (is.length == 1 && !i.contains(":"))
			return String.valueOf(s.charAt(Integer.valueOf(is[0])));
		else if ("".equals(is[0])) {
			int index = Integer.valueOf(is[1]);
			if (index > 0)
				return s.substring(0, index);
			else if (index < 0)
				return s.substring(0, (s.length()) + index);
			else if (index == 0)
				return s.substring(0, 0);

		} else if (':' == (i.charAt(i.length() - 1))) {
			// Why :2 returns [,2] and 2: returns [2]. #java #fear
			int index = Integer.valueOf(is[0]);
			if (index > 0)
				return s.substring(index);
			else if (index < 0)
				return s.substring(s.length() + index, s.length());
			else if (index == 0)
				return s.substring(0);
		} else {
			return s.substring(Integer.valueOf(is[0]), Integer.valueOf(is[1]));
		}
		return null;
	}

}
