package aQute.jpm.platform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.jpm.api.JVM;
import aQute.lib.io.IO;

class Linux extends Unix {

	private static final String	PATH_SEPARATOR			= Pattern.quote(File.pathSeparator);
	static final String			COMPLETION_DIRECTORY	= "/etc/bash_completion.d";
	private final static Logger	logger					= LoggerFactory.getLogger(Linux.class);

	Linux(File cache) {
		super(cache);
	}

	@Override
	public String getName() {
		return "Linux";
	}

	@Override
	public void uninstall() {

	}

	@Override
	public String toString() {
		return "Linux";
	}

	@Override
	public void getVMs(Collection<JVM> vms) throws Exception {
		String javaHome = System.getenv("JAVA_HOME");

		if (javaHome != null) {
			JVM jvm = getJVM0(new File(javaHome));

			if (jvm != null) {
				vms.add(jvm);
			}
		} else {
			Stream<String> paths = Stream.of(System.getenv("PATH")
				.split(PATH_SEPARATOR));

			Optional<Path> optJavaPath = paths.map(Paths::get)
				.map(path -> path.resolve("java"))
				.filter(Files::exists)
				.findFirst();

			if (optJavaPath.isPresent()) {
				Path javaPath = optJavaPath.get();

				while (Files.isSymbolicLink(javaPath)) {
					javaPath = Files.readSymbolicLink(javaPath);
				}

				File javaExe = javaPath.toFile();

				File javaParent = javaExe.getParentFile()
					.getParentFile();

				JVM jvm = getJVM(javaParent);

				if (jvm != null) {
					vms.add(jvm);
				}
			}
		}
	}

	@Override
	public JVM getJVM0(File vmdir) {
		if (!vmdir.isDirectory()) {
			return null;
		}

		File binDir = new File(vmdir, "bin");
		if (!binDir.isDirectory()) {
			logger.debug("Found a directory {}, but it does not have the expected bin directory", vmdir);
			return null;
		}

		File libDir = new File(vmdir, "lib");
		if (!libDir.isDirectory()) {
			logger.debug("Found a directory {}, but it does not have the expected lib directory", vmdir);
			return null;
		}

		JVM jvm = new JVM();
		jvm.name = vmdir.getName();
		jvm.javahome = vmdir.getAbsolutePath();
		jvm.version = getVersion(vmdir);
		jvm.platformVersion = jvm.version;

		return jvm;
	}

	private String getVersion(File vmdir) {
		File javaExe = new File(vmdir, "bin/java");

		String javaVersionOutput = null;

		try {
			if (javaExe.exists()) {

				ProcessBuilder builder = new ProcessBuilder(javaExe.getAbsolutePath(), "-version");

				try (BufferedReader reader = IO.reader(builder.start()
					.getErrorStream())) {
					javaVersionOutput = reader.lines()
						.collect(Collectors.joining(System.lineSeparator()));
				}

				try (Scanner scanner = new Scanner(javaVersionOutput)) {

					Pattern pattern = Pattern.compile("[1-9][0-9]*((.0)*.[1-9][0-9]*)*");

					while (scanner.hasNextLine()) {
						String line = scanner.nextLine();

						Matcher matcher = pattern.matcher(line);

						if (matcher.find()) {
							return matcher.group();
						}
					}
				}
			}
		} catch (IOException e) {}

		StringBuilder sb = new StringBuilder();
		sb.append("Unable to find java version for directory: ");
		sb.append(vmdir.getAbsolutePath());
		sb.append(System.lineSeparator());
		sb.append("\"java -version\" output: ");
		sb.append(System.lineSeparator());
		sb.append(javaVersionOutput);

		throw new NoSuchElementException(sb.toString());
	}

}
