package aQute.jpm.main;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.Level;
import org.slf4j.impl.StaticLoggerBinder;

import aQute.bnd.exceptions.Exceptions;
import aQute.jpm.api.CommandData;
import aQute.jpm.api.JPM;
import aQute.jpm.api.JVM;
import aQute.jpm.lib.JustAnotherPackageManager;
import aQute.jpm.platform.PlatformImpl;
import aQute.lib.base64.Base64;
import aQute.lib.collections.ExtList;
import aQute.lib.getopt.Arguments;
import aQute.lib.getopt.CommandLine;
import aQute.lib.getopt.Description;
import aQute.lib.getopt.Options;
import aQute.lib.hex.Hex;
import aQute.lib.io.IO;
import aQute.lib.justif.Justif;
import aQute.lib.settings.Settings;
import aQute.lib.strings.Strings;
import aQute.libg.glob.Glob;
import aQute.libg.reporter.ReporterAdapter;
import aQute.struct.struct.Error;
import biz.aQute.result.Result;

/**
 * The command line interface to JPM
 */
@Description("Just Another Package Manager (for Java)\nMaintains a local repository of Java jars (apps or libs). Can automatically link these jars to an OS command or OS service. For more information see http://jpm.bndtools.org")
public class Main extends ReporterAdapter {
	private final static Logger	logger			= LoggerFactory.getLogger(Main.class);
	private static final String	JPM_CONFIG_BIN	= "jpm.config.bin";
	private static final String	JPM_CONFIG_HOME	= "jpm.config.home";
	static Pattern				ASSIGNMENT		= Pattern.compile("\\s*([-\\w\\d_.]+)\\s*(?:=\\s*([^\\s]+)\\s*)?");
	public final static Pattern	URL_PATTERN		= Pattern.compile("[a-zA-Z][0-9A-Za-z]{1,8}:.+");
	public final static Pattern	BSNID_PATTERN	= Pattern.compile("([-A-Z0-9_.]+?)(-\\d+\\.\\d+.\\d+)?",
		Pattern.CASE_INSENSITIVE);
	File						base			= new File(System.getProperty("user.dir"));
	Settings					settings;

	JPM							jpm;
	final PrintStream			err;
	final PrintStream			out;
	File						sm;
	private String				url;
	private JpmOptions			options;
	static String				encoding		= System.getProperty("file.encoding");
	int							width			= 120;																// characters
	int							tabs[]			= {
		40, 48, 56, 64, 72, 80, 88, 96, 104, 112
	};

	static {
		if (encoding == null)
			encoding = Charset.defaultCharset()
				.name();
	}

	/**
	 * Default constructor
	 *
	 * @throws UnsupportedEncodingException
	 */

	public Main() throws UnsupportedEncodingException {
		super(new PrintStream(System.err, true, encoding));
		err = new PrintStream(System.err, true, encoding);
		out = new PrintStream(System.out, true, encoding);
	}

	/**
	 * Main entry
	 *
	 * @throws Exception
	 */
	public static void main(String args[]) throws Exception {
		Main jpm = new Main();
		try {
			jpm.run(args);
		} finally {
			jpm.err.flush();
			jpm.out.flush();
		}
	}

	/**
	 * Show installed binaries
	 */

	public interface ModifyService extends ModifyCommand {
		@Description("Provide arguments to the service when started")
		String args();

		@Description("Set the log path. Log output will go to this file, otherwise in a reserved directory")
		String log();

		@Description("Set the log path")
		String work();

		@Description("Set the user id for the service when running")
		String user();

		@Description("If set, will be started at boot time after the given services have been started. Specify boot if there are no other dependencies.")
		List<String> after();

		@Description("Commands executed after the service exits, will run under root.")
		String epilog();

		@Description("Commands executed just before the service starts while still root.")
		String prolog();
	}

	public interface ModifyCommand {
		@Description("Provide or override the JVM arguments")
		String jvmargs();

		@Description("Provide a jvm range")
		String jvmlocation();

		@Description("Provide the name of the main class used to launch this command or service in fully qualified form, e.g. aQute.main.Main")
		String main();

		@Description("Provide the name of the command or service")
		String name();

		@Description("Provide the title of the command or service")
		String title();

		@Description("Collect permission requests and print them at the end of a run. This can provide detailed information about what resources the command is using.")
		boolean trace();

		@Description("Java is default started in console mode, you can specify to start it in windows mode (or javaw)")
		boolean windows();

	}

	/**
	 * Services
	 */
	@Arguments(arg = {
		"[name]"
	})
	@Description("Manage the JPM services. Without arguments and options, this will show all the current services. Careful, if --remove is used all services are removed without any parameters.")
	public interface ServiceOptions extends Options, ModifyService {

		@Description("forece delete if necessary")
		boolean force();

		@Description("Create a new service on an existing artifact")
		String create();

		@Description("Remove the given service")
		boolean remove();

		@Description("Consider staged versions. Normally only masters are considered.")
		boolean staged();

		@Description("Update the service with the latest master/staged version (see --staged)")
		boolean update();

		@Description("Specify the coordinate of the service, identifies the main binary")
		String coordinates();
	}

	/**
	 * Commands
	 */
	@Arguments(arg = "[command]")
	@Description("Manage the commands that have been installed so far")
	public interface CommandOptions extends Options, ModifyCommand {
		String create();

		@Description("Remove the given service")
		boolean remove();

	}

	@Description("Remove jpm and all created data from the system (including commands and services). "
		+ "Without the --force flag only list the elements that would be deleted.")
	public interface deinitOptions extends Options {

		@Description("Actually remove jpm from the system")
		boolean force();
	}

	/**
	 * Main options
	 */

	@Arguments(arg = "cmd ...")
	@Description("Options valid for all commands. Must be given before sub command")
	interface JpmOptions extends Options {

		@Description("Print exception stack traces when they occur.")
		boolean exceptions();

		@Description("Trace on.")
		boolean trace();

		@Description("Be pedantic about all details.")
		boolean pedantic();

		@Description("Specify a new base directory (default working directory).")
		String base();

		@Description("Do not return error status for error that match this given regular expression.")
		String[] failok();

		@Description("Remote library url, default \"http://repo1.maven.org/maven2/\". Setting: 'jpm settings library.url=...'")
		String library();

		@Description("Specify the home directory of jpm. (can also be permanently set with 'jpm settings jpm.home=...'")
		String home();

		@Description("Provide or override the JVM location when installing jpm (for Windows only)")
		String jvmlocation();

		@Description("Wait for a key press, might be useful when you want to see the result before it is overwritten by a next command")
		boolean key();

		@Description("Show the release notes")
		boolean release();

		@Description("Change settings file (one-shot)")
		String settings();

		@Description("Specify executables directory (one-shot)")
		String bindir();

		@Description("Specify the platform (this is mainly for testing purposes). Is either WINDOWS, MACOS, or LINUX")
		PlatformImpl.Type os();

		boolean xtesting();

		int width();
	}

	/**
	 * Initialize the repository and other global vars.
	 *
	 * @param opts the options
	 * @throws IOException
	 */
	@Description("Just Another Package Manager for Java (\"jpm help jpm\" to see a list of global options)")
	public void _jpm(JpmOptions opts) throws IOException {

		try {
			setExceptions(opts.exceptions());
			if (opts.trace()) {
				setTrace(opts.trace());
				StaticLoggerBinder.getSingleton()
					.add("*", Level.DEBUG);
			}
			setPedantic(opts.pedantic());
			PlatformImpl platform = PlatformImpl.getPlatform(this, opts.os());
			logger.debug("Platform {}", platform);

			if (opts.base() != null)
				base = IO.getFile(base, opts.base());

			if (opts.settings() != null) {
				settings = new Settings(opts.settings());
				logger.debug("Using settings file: {}", opts.settings());
			} else {
				settings = new Settings(platform.getConfigFile());
			}

			String home = settings.getOrDefault(JPM_CONFIG_HOME, IO.getFile("~/.jpm")
				.getAbsolutePath());
			File homeDir = new File(home);
			File binDir;

			if (opts.home() != null) {
				homeDir = IO.getFile(base, opts.home());
			}
			if (opts.bindir() != null) {
				binDir = new File(opts.bindir());
				binDir = binDir.getAbsoluteFile();
			} else {
				String bin = settings.getOrDefault(JPM_CONFIG_BIN, new File(home, "bin").getAbsolutePath());
				binDir = new File(bin);
			}
			if (!binDir.isAbsolute())
				binDir = new File(base, opts.bindir());

			logger.debug("home={}, bin={}", homeDir, binDir);

			url = opts.library();

			if (url == null)
				url = settings.getOrDefault("library.url",
					"https://repo1.maven.org/maven2," + "https://oss.sonatype.org/content/repositories/snapshots/,"
						+ "https://bndtools.jfrog.io/bndtools/update-snapshot");

			jpm = new JustAnotherPackageManager(this, platform, homeDir, binDir, url);

			if (opts.jvmlocation() != null) {
				jpm.setJvmLocation(opts.jvmlocation());
			}

			platform.setJpm(jpm);

			checkPath();

			try {
				this.options = opts;
				if (opts.xtesting())
					jpm.setUnderTest();

				CommandLine handler = opts._command();
				List<String> arguments = opts._arguments();

				if (arguments.isEmpty()) {
					Justif j = new Justif();
					Formatter f = j.formatter();
					handler.help(f, this);
					err.println(j.wrap());
				} else {
					String cmd = arguments.remove(0);
					String help = handler.execute(this, cmd, arguments);
					if (help != null) {
						err.println(help);
					}
				}

				if (options.width() > 0)
					this.width = options.width();
			} finally {
				jpm.close();
			}
		}

		catch (Exception t) {
			Throwable tt = Exceptions.unrollCause(t);
			exception(tt, "%s", tt);
		} finally {
			// Check if we need to wait for it to finish
			if (opts.key()) {
				System.out.println("Hit a key to continue ...");
				System.in.read();
			}
		}

		if (!check(opts.failok())) {
			System.exit(getErrors().size());
		}
	}

	private void checkPath() {
		String PATH = System.getenv()
			.get("PATH");

		if (PATH != null) {
			boolean inPath = Strings.split(File.pathSeparator, PATH)
				.stream()
				.map(File::new)
				.filter(f -> f.equals(jpm.getBinDir()))
				.findAny()
				.isPresent();
			if (!inPath) {
				warning("bin directory is not in path: %s", jpm.getBinDir());
			}
		}
	}

	/**
	 * Install a jar options
	 */
	@Arguments(arg = {
		"command"
	})
	@Description("Install a jar into the repository. If the jar defines a number of headers it can also be installed as a command and/or a service. "
		+ "If not, additional information such as the name of the command and/or the main class must be specified with the appropriate flags.")
	public interface installOptions extends ModifyCommand, Options {
		// @Description("Ignore command and service information")
		// boolean ignore(); // pl: not used

		@Description("Force overwrite of existing command")
		boolean force();

		/**
		 * Install a file and extra commands
		 */
		@Description("Install jar without resolving dependencies")
		boolean local();
	}

	/**
	 * A better way to install
	 */

	@Description("Install an artifact from a url, file, or Maven central")
	public void _install(installOptions opts) throws Exception {

		String coordinate = opts._arguments()
			.get(0);

		Result<CommandData> result = jpm.getCommandData(coordinate);
		if (result.isErr()) {
			error("failed to install %s: %s", coordinate, result.getMessage());
			return;
		}

		CommandData cmd = result.unwrap();

		updateCommandData(cmd, opts);

		List<aQute.struct.struct.Error> errors = cmd.validate();
		if (!errors.isEmpty()) {
			error("Command not valid");
			for (Error error : errors) {
				error("[%s] %s %s %s %s", error.code, error.description, error.path, error.failure, error.value);
			}
		} else {
			String r = jpm.createCommand(cmd, opts.force());
			if (r != null) {
				error("[%s] %s", coordinate, r);
			}
		}
	}

	private boolean updateCommandData(CommandData data, ModifyCommand opts) throws Exception {
		boolean update = false;
		if (opts.main() != null) {
			data.main = opts.main();
			update = true;
		}
		if (opts.jvmargs() != null) {
			data.jvmArgs = opts.jvmargs();
			update = true;
		}
		if (opts.jvmlocation() != null) {
			JVM vm = jpm.getVM(opts.jvmlocation());
			if (vm != null) {
				data.jvmVersionRange = opts.jvmlocation();
			} else
				error("could not find a vm for %s", opts.jvmlocation());
			update = true;
		}
		if (opts.name() != null) {
			data.name = opts.name();
			update = true;
		}
		if (opts.title() != null) {
			data.title = opts.title();
			update = true;
		}
		if (opts.trace() != data.trace) {
			data.trace = opts.trace();
			update = true;
		}
		if (opts.windows() != data.windows) {
			data.windows = opts.windows();
			update = true;
		}

		return update;
	}

	@Description("Manage the jpm commands")
	public void _command(CommandOptions opts) throws Exception {

		if (opts.remove()) {
			List<String> args = opts._arguments();
			for (CommandData cmd : jpm.getCommands()) {
				if (args.contains(cmd.name)) {
					jpm.deleteCommand(cmd.name);
				}
			}
			return;
		}

		if (opts._arguments()
			.isEmpty()) {
			print(jpm.getCommands());
			return;
		}

		String cmd = opts._arguments()
			.get(0);

		CommandData data = jpm.getCommand(cmd);
		if (data == null) {
			error("Not found: %s", cmd);
		} else {
			CommandData newer = new CommandData();
			JustAnotherPackageManager.xcopy(data, newer);

			if (updateCommandData(newer, opts)) {
				jpm.deleteCommand(data.name);
				String result = jpm.createCommand(newer, true);
				if (result != null)
					error("Failed to update command %s: %s", cmd, result);
			}
			print(newer);
		}
	}

	private void print(CommandData command) throws Exception {
		Justif j = new Justif(width, tabs);
		Formatter f = j.formatter();
		f.format("%n[%s]%n", command.name);
		f.format("%s\n\n", Strings.display(command.description, command.title));
		f.format("SHA-1\t1%s%n", command.sha);
		f.format("Coordinate\t1%s%n", command.coordinate);
		f.format("JVMArgs\t1%s%n", "JVM Args", command.jvmArgs);
		f.format("Main class\t1%s%n", command.main);
		f.format("Install time\t1%s%n", new Date(command.time));
		f.format("Path\t1%s%n", command.bin);
		f.format("Installed\t1%s%n", command.installed);
		f.format("JRE\t1%s%n", Strings.display(command.jvmVersionRange, "<default>"));
		f.format("Trace\t1%s%n", command.trace ? "On" : "Off");
		list(f, "Dependencies", command.dependencies);

		out.append(j.wrap());
	}

	private void list(Formatter f, String title, List<?> elements) {
		if (elements == null || elements.isEmpty())
			return;

		f.format("[%s]\t1", title);
		String del = "";
		for (Object element : elements) {
			f.format("%s%s", del, element);
			del = "\f";
		}
		f.format("%n");
	}

	private void print(List<CommandData> commands) {
		Justif j = new Justif(width, tabs);
		Formatter f = j.formatter();
		for (CommandData command : commands) {
			f.format("%s\t1%s%n", command.name, Strings.display(command.description, command.title));
		}
		out.append(j.wrap());
	}

	@Description("Remove jpm from the system by deleting all artifacts and metadata")
	public void _deinit(deinitOptions opts) throws Exception {
		jpm.deinit(out, opts.force());
	}

	/**
	 * Main entry for the command line
	 *
	 * @param args
	 * @throws Exception
	 */
	public void run(String[] args) throws Exception {
		StaticLoggerBinder.getSingleton().reporter = this;
		CommandLine cl = new CommandLine(this);
		ExtList<String> list = new ExtList<>(args);
		String help = cl.execute(this, "jpm", list);
		check();
		if (help != null)
			err.println(help);
	}

	/**
	 * Setup jpm to run on this system.
	 */
	@Description("Install jpm on the current system")
	interface InitOptions extends Options {

		@Description("Provide or override the JVM location (for Windows only)")
		String jvmlocation();

	}

	@Description("Install jpm on the current system")
	public void _init(InitOptions opts) throws Exception {

		jpm.init();

		if (opts.jvmlocation() != null) {
			jpm.setJvmLocation(opts.jvmlocation());
		}

		try {
			String s = System.getProperty("jpm.jar", System.getProperty("java.class.path"));
			if (s == null) {
				error("Cannot initialize because not clear what the command jar is from java.class.path: %s", s);
				return;
			}
			String parts[] = s.split(File.pathSeparator);
			s = parts[0];
			try {
				File f = new File(s).getAbsoluteFile();
				if (f.exists()) {
					CommandLine cl = new CommandLine(this);

					String help = null;

					if (jpm.getJvmLocation() != null) {
						help = cl.execute(this, "install",
							Arrays.asList("-fl", "-J", jpm.getJvmLocation(), f.getAbsolutePath()));
					} else {
						help = cl.execute(this, "install", Arrays.asList("-fl", f.getAbsolutePath()));
					}

					if (help != null) {
						error(help);
						return;
					}

					settings.put(JPM_CONFIG_BIN, jpm.getBinDir()
						.getAbsolutePath());
					settings.put(JPM_CONFIG_HOME, jpm.getHomeDir()
						.getAbsolutePath());
					settings.save();

					out.println("Home dir      " + jpm.getHomeDir());
					out.println("Bin  dir      " + jpm.getBinDir());
				} else
					error("Cannot find the jpm jar from %s", f);
			} catch (InvocationTargetException e) {
				exception(e.getTargetException(), "Could not install jpm, %s", e.getTargetException());
				if (isExceptions())
					e.printStackTrace();
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * Show platform
	 */
	@Arguments(arg = {
		"[cmd]", "..."
	})
	@Description("Show the name of the platform, or more specific information")
	public interface PlatformOptions extends Options {
		@Description("Show detailed information")
		boolean verbose();
	}

	/**
	 * Show the platform info.
	 *
	 * @param opts
	 * @throws IOException
	 * @throws Exception
	 */
	@Description("Show platform information")
	public void _platform(PlatformOptions opts) throws IOException, Exception {
		CommandLine cli = opts._command();
		List<String> cmds = opts._arguments();
		if (cmds.isEmpty()) {
			if (opts.verbose()) {
				Justif j = new Justif(80, 30, 40, 50, 60);
				jpm.getPlatform()
					.report(j.formatter());
				out.append(j.wrap());
			} else
				out.println(jpm.getPlatform()
					.getName());
		} else {
			String execute = cli.execute(jpm.getPlatform(), cmds.remove(0), cmds);
			if (execute != null) {
				out.append(execute);
			}
		}
	}

	/**
	 * Show all the installed VMs
	 */
	@Description("Manage installed VMs ")
	interface VMOptions extends Options {
		String add();
	}

	public void _vm(VMOptions opts) throws Exception {
		if (opts.add() != null) {
			File f = IO.getFile(base, opts.add())
				.getCanonicalFile();

			if (!f.isDirectory()) {
				error("No such directory %s to add a JVM", f);
			} else {
				jpm.addVm(f);
			}
		}

		SortedSet<JVM> vms = jpm.getVMs();

		for (JVM jvm : vms) {
			out.printf("%-30s %-5s %-20s %-10s %s\n", jvm.name, jvm.version, jvm.vendor, jvm.os_arch, jvm.javahome);
		}

	}

	@Arguments(arg = {
		"service"
	})
	@Description("Start a service")
	interface startOptions extends Options {
		boolean clean();
	}

	@Arguments(arg = {})
	@Description("Show the current version. The qualifier represents the build date.")
	interface VersionOptions extends Options {

	}

	/**
	 * Show the current version
	 *
	 * @throws IOException
	 */
	@Description("Show the current version of jpm")
	public void _version(VersionOptions options) throws IOException {
		Enumeration<URL> urls = getClass().getClassLoader()
			.getResources("META-INF/MANIFEST.MF");
		while (urls.hasMoreElements()) {
			URL url = urls.nextElement();
			logger.debug("found manifest {}", url);
			Manifest m = new Manifest(url.openStream());
			String name = m.getMainAttributes()
				.getValue("Bundle-SymbolicName");
			if (name != null && name.trim()
				.equals("biz.aQute.jpm")) {
				out.println(m.getMainAttributes()
					.getValue("Bundle-Version"));
				return;
			}
		}
		error("No version found in jar");
	}

	/**
	 * Handle the global settings
	 */
	@Description("Manage user settings of jpm (in ~/.jpm). Without argument, print the current settings. "
		+ "Can alse be used to create change a settings with \"jpm settings <key>=<value>\"")
	interface settingOptions extends Options {
		boolean clear();

		boolean publicKey();

		boolean secretKey();

		boolean id();

		boolean mac();

		boolean hex();
	}

	@Description("Manage user settings of jpm (in ~/.jpm)")
	public void _settings(settingOptions opts) throws Exception {
		try {
			logger.debug("settings {}", opts.clear());
			List<String> rest = opts._arguments();

			if (opts.clear()) {
				settings.clear();
				logger.debug("clear {}", settings.entrySet());
			}

			if (opts.publicKey()) {
				out.println(tos(opts.hex(), settings.getPublicKey()));
				return;
			}
			if (opts.secretKey()) {
				out.println(tos(opts.hex(), settings.getPrivateKey()));
				return;
			}
			if (opts.id()) {
				out.printf("%s\n", tos(opts.hex(), settings.getPublicKey()));
			}

			if (opts.mac()) {
				for (String s : rest) {
					byte[] data = s.getBytes(UTF_8);
					byte[] signature = settings.sign(data);
					out.printf("%s\n", tos(opts.hex(), signature));
				}
				return;
			}

			if (rest.isEmpty()) {
				list(null, settings);
			} else {
				boolean set = false;
				for (String s : rest) {
					Matcher m = ASSIGNMENT.matcher(s);
					logger.debug("try {}", s);
					if (m.matches()) {
						logger.debug("matches {} {} {}", s, m.group(1), m.group(2));
						String key = m.group(1);
						Glob instr = key == null ? Glob.ALL : new Glob(key);
						List<String> select = settings.keySet()
							.stream()
							.filter(k -> instr.matches(k))
							.collect(Collectors.toList());

						String value = m.group(2);
						if (value == null) {
							logger.debug("list wildcard {} {} {}", instr, select, settings.keySet());
							list(select, settings);
						} else {
							logger.debug("assignment 	");
							settings.put(key, value);
							set = true;
						}
					} else {
						err.printf("Cannot assign %s\n", s);

					}
				}
				if (set) {
					logger.debug("saving");
					settings.save();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String tos(boolean hex, byte[] data) {
		return hex ? Hex.toHexString(data) : Base64.encodeBase64(data);
	}

	private void list(Collection<String> keys, Map<String, String> map) {
		for (Entry<String, String> e : map.entrySet()) {
			if (keys == null || keys.contains(e.getKey()))
				out.printf("%-40s = %s\n", e.getKey(), e.getValue());
		}
	}

	/**
	 * Alternative for command -r {@code <commandName>}
	 */
	@Arguments(arg = {
		"command|service", "..."
	})
	@Description("Remove the specified command(s) or service(s) from the system")
	interface UninstallOptions extends Options {}

	@Description("Remove a command or a service from the system")
	public void _remove(UninstallOptions opts) throws Exception {
		if (!jpm.hasAccess()) {
			error("No write acces, might require administrator or root privileges (sudo in *nix)");
			return;
		}

		ArrayList<String> toDelete = new ArrayList<>();

		ArrayList<String> names = new ArrayList<>();
		List<CommandData> commands = jpm.getCommands();
		for (CommandData command : commands) {
			names.add(command.name);
		}

		for (String pattern : opts._arguments()) {
			Glob glob = new Glob(pattern);
			for (String name : names) {
				if (glob.matcher(name)
					.matches()) {
					toDelete.add(name);
				}
			}
		}

		int ccount = 0, scount = 0;

		for (String name : toDelete) {
			if (jpm.getCommand(name) != null) { // Try command first
				logger.debug("Corresponding command found, removing");
				jpm.deleteCommand(name);
				ccount++;
			} else { // No match amongst commands & services
				error("No matching command or service found for: %s", name);
			}
		}
		out.format("%d command(s) removed and %d service(s) removed%n", ccount, scount);
	}

	/**
	 * Constructor for testing purposes
	 */
	public Main(JustAnotherPackageManager jpm) throws UnsupportedEncodingException {
		this();
		this.jpm = jpm;
	}

	/**
	 * Show a list of candidates from a coordinate
	 */
	@Arguments(arg = "coordinate")
	@Description("Print out the candidates from a coordinate specification. A coordinate is:\n\n"
		+ "    coordinate \t0:\t1[groupId ':'] artifactId \n\t1[ '@' [ version ] ( '*' | '=' | '~' | '!')]\n"
		+ "    '*'        \t0:\t1Version, if specified, is treated as required prefix of the actual version. Sees MASTER | STAGING | LOCKED\n"
		+ "    '='        \t0:\t1Version, if specified, must match exactly. Sees MASTER\n"
		+ "    '~'        \t0:\t1Version, if specified, is treated as required prefix of the actual version. Sees all phases\n"
		+ "    '!'        \t0:\t1Version, if specified, is treated as required prefix of the actual version. Sees normally invisible phases")
	interface CandidateOptions extends Options {

	}

	@Description("List the candidates for a coordinate")
	public void _candidates(CandidateOptions options) throws Exception {
		List<String> arguments = options._arguments();
		String gav = arguments.remove(0);
		Result<List<String>> revisions = jpm.getRevisions(gav);
		if (revisions.isErr()) {
			error(revisions.getMessage());
		} else {
			List<String> list = revisions.unwrap();
			out.println(Strings.join("\n", list));
		}
	}

	@Arguments(arg = {})
	interface UseOptions extends Options {
		@Description("Forget the current settings.")
		boolean forget();

		@Description("Save settings.")
		boolean save();
	}

	@Description("Manage the current setting of the home directory and the bin directory. These settings can be set with the initial options -g/--global, -u/--user, and -h/--home")
	public void _use(UseOptions o) {
		out.println("Home dir      " + jpm.getHomeDir());
		out.println("Bin  dir      " + jpm.getBinDir());
		if (o.forget()) {
			settings.remove(JPM_CONFIG_BIN);
			settings.remove(JPM_CONFIG_HOME);
			settings.save();
		} else if (o.save()) {
			if (!jpm.getHomeDir()
				.isDirectory()) {
				error(
					"The current home directory is not a JPM directory, init to initialize it, this will save the permanent settings to use that directory by default");
				return;
			}
			settings.put(JPM_CONFIG_BIN, jpm.getBinDir()
				.getAbsolutePath());
			settings.put(JPM_CONFIG_HOME, jpm.getHomeDir()
				.getAbsolutePath());
			settings.save();
		}
	}

}
