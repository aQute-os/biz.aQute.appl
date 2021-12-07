package aQute.jpm.platform;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Formatter;
import java.util.Map;

import aQute.jpm.api.JPM;
import aQute.jpm.api.Platform;
import aQute.lib.getopt.CommandLine;
import aQute.lib.io.IO;
import aQute.libg.reporter.ReporterAdapter;
import aQute.libg.sed.Sed;
import aQute.service.reporter.Reporter;

public abstract class PlatformImpl implements Platform {

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
}
