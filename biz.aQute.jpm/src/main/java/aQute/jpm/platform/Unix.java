package aQute.jpm.platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.jpm.api.CommandData;
import aQute.lib.getopt.Arguments;
import aQute.lib.getopt.Description;
import aQute.lib.getopt.Options;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;
import aQute.libg.glob.Glob;

public abstract class Unix extends PlatformImpl {
	private final static Logger	logger		= LoggerFactory.getLogger(Unix.class);

	public static String		JPM_GLOBAL	= "/var/jpm";

	@Override
	public File getGlobal() {
		return new File("/var/jpm");
	}

	@Override
	public File getGlobalBinDir() {
		return new File("/usr/local/bin");
	}

	@Override
	public File getLocal() {
		File home = new File(System.getenv("HOME"));
		return new File(home, "jpm");
	}

	@Override
	public String createCommand(CommandData data, Map<String, String> map, boolean force, String... extra)
		throws Exception {
		data.bin = getExecutable(data);

		File f = new File(data.bin);
		if (f.isDirectory()) {
			f = new File(data.bin, data.name);
			data.bin = f.getAbsolutePath();
		}

		if (f.exists())
			if (!force)
				return "Command already exists " + data.bin;
			else
				delete(f);

		String java = data.jvmLocation;
		if (java == null) {
			java = IO.getJavaExecutablePath(data.windows ? "javaw" : "java");
		}

		try (Formatter frm = new Formatter()) {
			frm.format("#!/bin/sh\n");
			frm.format("exec");

			frm.format(" %s", data.jvmLocation);

			frm.format(" -Dpid=$$");

			if (data.jvmArgs != null) {
				frm.format(" %s", data.jvmArgs);
			}

			frm.format(" -cp");

			assert !data.dependencies.isEmpty();

			String del = " ";
			for (String dep : data.dependencies) {
				frm.format("%s%s", del, dep);
				del = ":";
			}

			frm.format(" %s \"$@\"\n", data.main);

			String script = frm.toString();
			IO.store(script, f);

			makeExecutable(f);
			logger.debug(script);
		}

		return null;
	}

	private void makeExecutable(File f) throws IOException {
		if (f.isFile()) {
			Set<PosixFilePermission> attrs = new HashSet<>();
			attrs.add(PosixFilePermission.OWNER_EXECUTE);
			attrs.add(PosixFilePermission.OWNER_READ);
			attrs.add(PosixFilePermission.OTHERS_READ);
			attrs.add(PosixFilePermission.OTHERS_EXECUTE);
			attrs.add(PosixFilePermission.GROUP_READ);
			attrs.add(PosixFilePermission.GROUP_EXECUTE);
			Files.setPosixFilePermissions(f.toPath(), attrs);
		}
	}

	private void delete(File f) throws IOException {
		if (f.isFile()) {
			Set<PosixFilePermission> attrs = new HashSet<>();
			attrs.add(PosixFilePermission.OWNER_WRITE);
			Files.setPosixFilePermissions(f.toPath(), attrs);
			IO.delete(f);
		}
	}

	@Override
	public void deleteCommand(CommandData data) throws Exception {
		File executable = new File(getExecutable(data));
		IO.deleteWithException(executable);
	}

	protected String getExecutable(CommandData data) {
		return new File(jpm.getBinDir(), data.name).getAbsolutePath();
	}

	@Override
	public String user() throws Exception {
		ProcessBuilder pb = new ProcessBuilder();
		Map<String, String> environment = pb.environment();
		String user = environment.get("USER");
		return user;
		// Command id = new Command("id -nu");
		// StringBuilder sb = new StringBuilder();
		//
		// int n = id.execute(sb,sb);
		// if ( n != 0)
		// throw new IllegalArgumentException("Getting user id fails: " + n +
		// " : " + sb);
		//
		// return sb.toString().trim();
	}

	@Override
	public void report(Formatter out) throws IOException, Exception {
		out.format("Name     \t%s\n", getName());
		out.format("Global   \t%s\n", getGlobal());
		out.format("Local    \t%s\n", getLocal());
	}

	/**
	 * Add the bindir to PATH env. variable in .profile
	 */

	@Arguments(arg = {})
	@Description("Add the bin directory for this jpm to your PATH in the user's environment variables in the ~/.profile file.")
	interface PathOptions extends Options {
		@Description("Remove the bindir from the user's environment variables.")
		boolean remove();

		@Description("Delete a path from the PATH environment variable")
		List<String> delete();

		@Description("Add the current binary dir to the PATH environment variable")
		boolean add();

		@Description("Add additional paths to the PATH environment variable")
		List<String> extra();

		@Description("Override the default .profile and use this file. This file is relative to the home directory if not absolute.")
		String profile();

		@Description("If the profile file does not exist, create")
		boolean force();

	}

	static Pattern PATH_P = Pattern.compile("(export|set)\\s+PATH\\s*=(.*):\\$PATH\\s*$");

	@Description("Add the bin directory for this jpm to your PATH in the user's environment variables")
	public void _path(PathOptions options) throws IOException {
		File file = options.profile() == null ? IO.getFile("~/.profile") : IO.getFile(IO.home, options.profile());

		if (!file.isFile()) {
			if (!options.force()) {
				reporter.error("No such file %s", file);
				return;
			}
			IO.store("# created by jpm\n", file);
		}

		String parts[] = System.getenv("PATH")
			.split(":");
		List<String> paths = new ArrayList<>(Arrays.asList(parts));

		for (int i = 0; i < parts.length; i++) {
			System.out.printf("%2d:%s %s %s%n", i, parts[i].toLowerCase()
				.contains("jpm") ? "*" : " ", new File(parts[i]).isDirectory() ? " " : "!", parts[i]);
		}

		String bd = jpm.getBinDir()
			.getAbsolutePath();
		String s = IO.collect(file);

		//
		// Remove the current binary path. If it is add, we add it later
		//
		if (options.remove() || options.add()) {
			if (!bd.equals(getGlobalBinDir().getAbsolutePath())) {
				s = s.replaceAll("(PATH\\s*=)" + bd + ":(.*\\$PATH)", "$1$2");
				logger.debug("removed {}", bd);
			}
		}

		if (options.delete() != null) {
			for (String delete : options.delete()) {
				s = s.replaceAll("(PATH\\s*=)" + Glob.toPattern(delete) + ":(.*\\$PATH)", "$1$2");
			}
		}

		//
		// Remove any orphans
		//
		s = s.replaceAll("\\s*(set|export)\\s+PATH\\s*=\\$PATH\\s*", "");

		List<String> additions = new ArrayList<>();
		if (options.add()) {
			if (!bd.equals(getGlobalBinDir().getAbsolutePath())) {
				additions.add(bd);
			}
		}
		if (options.extra() != null)
			for (String add : options.extra()) {
				File f = IO.getFile(add);
				if (!f.isDirectory()) {
					reporter.error("%s is not a directory, not added", f.getAbsolutePath());
				} else {
					if (!paths.contains(f.getAbsolutePath()))
						additions.add(f.getAbsolutePath());
				}
			}

		if (!additions.isEmpty()) {
			s += "\nexport PATH=" + Strings.join(":", additions) + ":$PATH\n";
			logger.debug("s {}", s);
		}
		IO.store(s, file);
	}

	@Override
	public String getConfigFile() {
		return "~/.jpm/settings.json";
	}

}
