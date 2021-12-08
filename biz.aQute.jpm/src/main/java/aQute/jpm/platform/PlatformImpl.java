package aQute.jpm.platform;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Formatter;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.version.MavenVersion;
import aQute.jpm.api.JPM;
import aQute.jpm.api.JVM;
import aQute.jpm.api.Platform;
import aQute.lib.getopt.CommandLine;
import aQute.lib.io.IO;
import aQute.libg.reporter.ReporterAdapter;
import aQute.libg.sed.Sed;
import aQute.service.reporter.Reporter;

public abstract class PlatformImpl implements Platform {
	final static Logger	logger	= LoggerFactory.getLogger(Windows.class);

	static PlatformImpl	platform;
	static Runtime		runtime	= Runtime.getRuntime();
	Reporter			reporter;
	JPM					jpm;

	/**
	 * Get the current platform manager.
	 *
	 * @param reporter
	 * @param type
	 */
	public static PlatformImpl getPlatform(Reporter reporter, Type type) {
		if (platform == null) {
			if (type == null)
				type = getPlatformType();

			switch (type) {
				case LINUX :
					platform = new Linux();
					break;
				case MACOS :
					platform = new MacOS();
					break;
				case WINDOWS :
					platform = new Windows();
					break;
				default :
					return null;
			}
		}
		platform.reporter = reporter;
		return platform;
	}

	public static Type getPlatformType() {
		String osName = System.getProperty("os.name")
			.toLowerCase();
		if (osName.startsWith("windows"))
			return Type.WINDOWS;
		else if (osName.startsWith("mac") || osName.startsWith("darwin")) {
			return Type.MACOS;
		} else if (osName.contains("linux"))
			return Type.LINUX;

		return Type.UNKNOWN;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		try (Formatter formatter = new Formatter(sb)) {
			formatter.format("Name                %s%n", getName());
			formatter.format("Local               %s%n", getLocal());
			formatter.format("Global              %s%n", getGlobal());
			return formatter.toString();
		}
	}

	@Override
	public int run(String args) throws Exception {
		return runtime.exec(args)
			.waitFor();
	}

	protected String[] add(String[] extra, String... more) {
		if (extra == null || extra.length == 0)
			return more;

		if (more == null || more.length == 0)
			return extra;

		String[] result = new String[extra.length + more.length];
		System.arraycopy(extra, 0, result, 0, extra.length);
		System.arraycopy(more, 0, result, extra.length, more.length);
		return result;
	}

	@Override
	public String installCompletion(Object target) throws Exception {
		return "No completion available for this platform";
	}

	public void parseCompletion(Object target, File f) throws Exception {
		IO.copy(getClass().getResource("unix/jpm-completion.bash"), f);

		Sed sed = new Sed(f);
		sed.setBackup(false);

		Reporter r = new ReporterAdapter();
		CommandLine c = new CommandLine(r);
		Map<String, Method> commands = c.getCommands(target);
		StringBuilder sb = new StringBuilder();
		for (String commandName : commands.keySet()) {
			sb.append(" " + commandName);
		}
		sb.append(" help");

		sed.replace("%listJpmCommands%", sb.toString()
			.substring(1));
		sed.doIt();
	}

	public void parseCompletion(Object target, PrintStream out) throws Exception {
		File tmp = File.createTempFile("jpm-completion", ".tmp");
		tmp.deleteOnExit();

		try {
			parseCompletion(target, tmp);
			IO.copy(tmp, out);
		} finally {
			IO.delete(tmp);
		}
	}

	public void setJpm(JPM jpm) {
		this.jpm = jpm;
	}

	public String verifyVM(File f) {
		if (!f.isDirectory())
			return "Not a directory";

		File bin = new File(f, "bin");
		if (!bin.isDirectory())
			return "No bin directory %s for a VM's executables";

		File lib = new File(f, "lib");
		if (!lib.isDirectory())
			return "No lib directory %s for a VM's libraries and certificates";

		return null;
	}

	/**
	 * <pre>
	 * IMPLEMENTOR="Azul Systems, Inc."
	IMPLEMENTOR_VERSION="Zulu17.30+15-CA"
	JAVA_VERSION="17.0.1"
	JAVA_VERSION_DATE="2021-10-19"
	LIBC="default"
	MODULES="java.base java.compiler java.datatransfer java.xml java.prefs java.desktop java.instrument java.logging java.management java.security.sasl java.naming java.rmi java.management.rmi java.net.http java.scripting java.security.jgss java.transaction.xa java.sql java.sql.rowset java.xml.crypto java.se java.smartcardio jdk.accessibility jdk.internal.jvmstat jdk.attach jdk.charsets jdk.compiler jdk.crypto.ec jdk.crypto.cryptoki jdk.crypto.mscapi jdk.dynalink jdk.internal.ed jdk.editpad jdk.hotspot.agent jdk.httpserver jdk.incubator.foreign jdk.incubator.vector jdk.internal.le jdk.internal.opt jdk.internal.vm.ci jdk.internal.vm.compiler jdk.internal.vm.compiler.management jdk.jartool jdk.javadoc jdk.jcmd jdk.management jdk.management.agent jdk.jconsole jdk.jdeps jdk.jdwp.agent jdk.jdi jdk.jfr jdk.jlink jdk.jpackage jdk.jshell jdk.jsobject jdk.jstatd jdk.localedata jdk.management.jfr jdk.naming.dns jdk.naming.rmi jdk.net jdk.nio.mapmode jdk.random jdk.sctp jdk.security.auth jdk.security.jgss jdk.unsupported jdk.unsupported.desktop jdk.xml.dom jdk.zipfs"
	OS_ARCH="x86_64"
	OS_NAME="Windows"
	SOURCE=".:git:f6b830aa983b"
	 * </pre>
	 */

	@Override
	public JVM getJVM(File vmdir) throws Exception {
		if (!vmdir.isDirectory()) {
			return null;
		}

		File binDir = new File(vmdir, "bin");
		if (!binDir.isDirectory()) {
			logger.debug("Found a directory {}, but it does not have the expected bin directory", vmdir);
			return null;
		}

		File releaseFile = new File(vmdir, "release");
		if (!releaseFile.isFile() || !releaseFile.exists()) {
			logger.debug("Found a directory {}, but it doesn't contain an expected release file", vmdir);
			return null;
		}

		try (InputStream is = IO.stream(releaseFile)) {
			Properties releaseProps = new Properties();
			releaseProps.load(is);

			JVM jvm = new JVM();
			jvm.name = vmdir.getName();
			jvm.vendor = cleanup(releaseProps.getProperty("IMPLEMENTOR"));
			jvm.javahome = vmdir.getCanonicalPath();
			jvm.version = cleanup(releaseProps.getProperty("JAVA_VERSION"));
			jvm.platformVersion = MavenVersion.cleanupVersion(jvm.version);
			jvm.modules = cleanup(releaseProps.getProperty("MODULES"));
			jvm.os_arch = cleanup(releaseProps.getProperty("OS_ARCH"));
			jvm.os_name = cleanup(releaseProps.getProperty("OS_NAME"));

			return jvm;
		}
	}

	protected String getJava(String jvmVersionRange, boolean windows) throws Exception {
		String java;
		JVM jvm = jpm.getVM(jvmVersionRange);
		if (jvm == null) {
			java = IO.getJavaExecutablePath(windows ? "javaw" : "java");
			if (java == null)
				java = windows ? "javaw" : "java";
		} else {
			java = (windows ? jvm.javaw() : jvm.java()).getAbsolutePath();
		}
		return java;
	}

	private String cleanup(String v) {
		if (v == null)
			return null;

		v = v.trim();
		if (v.startsWith("\""))
			v = v.substring(1);
		if (v.endsWith("\""))
			v = v.substring(0, v.length() - 1);

		return v;
	}
}
