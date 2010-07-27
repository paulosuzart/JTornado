package JTornado;

import static org.jtornadoweb.util.StringUtils.substring;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase {
	/**
	 * Create the test case
	 * 
	 * @param testName
	 *            name of the test case
	 */
	public AppTest(String testName) {
		super(testName);
	}

	/**
	 * @return the suite of tests being tested
	 */
	public static Test suite() {
		return new TestSuite(AppTest.class);
	}

	/**
	 * Rigourous Test :-)
	 */
	public void testStringUtils_substring() {
		String test = "paulo";
		assertEquals("substring[:2]", "pa", substring(test, ":2"));
		assertEquals("substring[:-2]", "pau", substring(test, ":-2"));
		assertEquals("substring[:3]", "pau", substring(test, ":3"));
		assertEquals("substring[2:]", "ulo", substring(test, "2:"));
		assertEquals("substring[0]", "p", substring(test, "0"));
		assertEquals("substring[2:3]", "u", substring(test, "2:3"));
	}
}
