package aQute.jpm.api;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Formatter;
import java.util.Map;

public interface Platform {
	enum Type {
		UNKNOWN,
		WINDOWS,
		LINUX,
		MACOS
	}

	/**
	 * Return the place where we place the jpm home directory for global access.
	 * E.g. /var/jpm
	 */
	File getGlobal();

	/**
	 * Return the place where we place the jpm home directory for user/local
	 * access. E.g. ~/.jpm
	 */
	File getLocal();

	void shell(String initial) throws Exception;

	String getName();

	void uninstall() throws Exception;

	int run(String args) throws Exception;

	String createCommand(CommandData data, Map<String, String> map, boolean force, String... deps) throws Exception;

	void deleteCommand(CommandData cmd) throws Exception;

	String user() throws Exception;

	/**
	 * Return the directory on this platform were normally executables are
	 * installed.
	 */
	File getGlobalBinDir();

	default String defaultCacertsPassword() {
		return "changeme";
	}

	void report(Formatter out) throws Exception;

	String installCompletion(Object target) throws Exception;

	String getConfigFile();

	void getVMs(Collection<JVM> vms) throws Exception;

	JVM getJVM(File f) throws Exception;

	default boolean hasPost() {
		return false;
	}

	default void doPostInstall() {}

	/**
	 * Is called to initialize the platform if necessary.
	 *
	 * @throws IOException
	 * @throws Exception
	 */

	default void init() throws Exception {}

}
