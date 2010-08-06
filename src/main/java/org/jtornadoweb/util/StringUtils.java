package org.jtornadoweb.util;

import static java.lang.Integer.valueOf;
import static java.lang.String.valueOf;

/**
 * Methods for string with python semantics.
 * 
 * @author paulosuzart
 * 
 */
public class StringUtils {

	/**
	 * Same as s[:3], s[3:], s[-1], etc. <b>WARN</b> This method does not
	 * validate if the indexes at <pre>i</pre> parameter are really numbers.
	 * 
	 * @param target
	 * @param i
	 * @return
	 */
	public static String substring(final String target, final String i) {
		final String[] is = i.split(":", 2);
		final int len = target.length();
		if (!i.contains(":"))
			return valueOf(target.charAt(norm(len, valueOf(is[0]))));
		else if (i.startsWith(":"))
			return target.substring(0, norm(len, valueOf(is[1])));
		else if (i.endsWith(":"))
			return target.substring(norm(len, valueOf(is[0])));
		else
			return target.substring(norm(len, valueOf(is[0])),
					norm(len, valueOf(is[1])));
	}

	/**
	 * Return a new index 'normalized', that is, for negative index, points to
	 * the corresponding index at the end.
	 * 
	 * @param len
	 * @param i
	 * @return
	 */
	private static int norm(final int len, final int i) {
		return i < 0 ? len + i : i;
	}

}
