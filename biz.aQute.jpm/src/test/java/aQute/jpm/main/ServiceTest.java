package aQute.jpm.main;

import junit.framework.TestCase;

public class ServiceTest extends TestCase {

	// @Override
	// protected void setUp() throws Exception {
	// removeTestService();
	// }
	//
	// @Override
	// protected void tearDown() throws Exception {
	// removeTestService();
	// }
	//
	// public void testServiceCreate() throws Exception {
	// MainNoFail jpm = new MainNoFail();
	//
	// jpm.run(new String[] {
	// "-etu", "service", "-f", "--create",
	// "src/test/resources/biz.aQute.jpm.daemon.jar", "dm"
	// });
	//
	// @SuppressWarnings("unused")
	// Service service = jpm.jpm.getService("dm");
	//
	// // assertNotNull(service);
	// }
	//
	// private void removeTestService() throws Exception {
	// MainNoFail jpm = new MainNoFail();
	//
	// jpm.run(new String[] {
	// "-etu", "service", "--remove", "dm"
	// });
	//
	// Service service = jpm.jpm.getService("dm");
	//
	// assertNull(service);
	// }
	//
	// private static class MainNoFail extends Main {
	//
	// public MainNoFail() throws UnsupportedEncodingException {
	// super();
	// }
	//
	// @Override
	// public boolean check(String... pattern) {
	// return true;
	// }
	//
	// @Override
	// public void run(String[] args) throws Exception {
	// try {
	// super.run(args);
	// } finally {
	// err.flush();
	// out.flush();
	// }
	// }
	// }
}
