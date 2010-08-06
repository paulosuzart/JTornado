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
	 * @param target
	 * @param i
	 * @return
	 */
	public static String substring(final String target, String i) {
		String[] is = i.split(":");

		/* Supposed to be a number TODO validate i */
		if (is.length == 1 && !i.contains(":"))
			return String.valueOf(target.charAt(Integer.valueOf(is[0])));
		else if ("".equals(is[0])) {
			int index = Integer.valueOf(is[1]);
			if (index > 0)
				return target.substring(0, index);
			else if (index < 0)
				return target.substring(0, (target.length()) + index);
			else if (index == 0)
				return target.substring(0, 0);

		} else if (':' == (i.charAt(i.length() - 1))) {
			// Why :2 returns [,2] and 2: returns [2]. #java #fear
			int index = Integer.valueOf(is[0]);
			if (index > 0)
				return target.substring(index);
			else if (index < 0)
				return target.substring(target.length() + index, target.length());
			else if (index == 0)
				return target.substring(0);
		} else {
			int sIndex = Integer.valueOf(is[0]);
			int eIndex = Integer.valueOf(is[1]);
			if (sIndex < 0)
				sIndex = target.length() - 1 + sIndex;
			if (eIndex < 0)
				eIndex = target.length() + eIndex;
			return target.substring(sIndex, eIndex);
		}
		return null;
	}

}
