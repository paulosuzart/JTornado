package JTornado;

import static org.jtornadoweb.util.StringUtils.substring;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import static org.jtornadoweb.util.HttpUtils.parseQueryString;

public class UtilsTest extends TestCase {
	public UtilsTest(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(UtilsTest.class);
	}

	/**
	 * Tests substring in python semantics.
	 */
	public void testStringUtils_substring() {
		String test = "paulo";
		assertEquals("substring[:2]", "pa", substring(test, ":2"));
		assertEquals("substring[:-2]", "pau", substring(test, ":-2"));
		assertEquals("substring[:3]", "pau", substring(test, ":3"));
		assertEquals("substring[2:]", "ulo", substring(test, "2:"));
		assertEquals("substring[0]", "p", substring(test, "0"));
		assertEquals("substring[2:3]", "u", substring(test, "2:3"));
		assertEquals("substring[1:-2]", "au", substring(test, "1:-2"));
		assertEquals("substring[-1]", "o", substring(test, "-1"));
	}

	/**
	 * Tests parseQueryString.
	 */
	public void testHttpUtils_parseQueryString() {
		try {
			Map<String, List<String>> params1 = parseQueryString("name=jobson&age=75&car=01&car=02");
			assertNotNull("Param not null", params1);
			assertEquals("Param name=jobson", "jobson", params1.get("name").get(0));
			assertEquals("Param age=75", "75", params1.get("age").get(0));
			assertNotNull("Param car not null", params1.get("car"));
			assertEquals("Param car contains 2 entries", 2, params1.get("car")
					.size());
		} catch (UnsupportedEncodingException e) {
			assertTrue(false); // What am I doing? 0.o
		}
	}
}
