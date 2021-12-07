package aQute.jpm.lib;

import static aQute.lib.io.IO.copy;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;
import org.osgi.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import aQute.bnd.exceptions.Exceptions;
import aQute.bnd.header.Attrs;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.header.Parameters;
import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.Constants;
import aQute.jpm.api.CommandData;
import aQute.jpm.api.JPM;
import aQute.jpm.api.JVM;
import aQute.jpm.platform.PlatformImpl;
import aQute.lib.base64.Base64;
import aQute.lib.collections.ExtList;
import aQute.lib.converter.Converter;
import aQute.lib.io.IO;
import aQute.lib.json.JSONCodec;
import aQute.lib.justif.Justif;
import aQute.lib.settings.Settings;
import aQute.lib.strings.Strings;
import aQute.libg.cryptography.SHA1;
import aQute.maven.api.Archive;
import aQute.maven.api.IPom.Dependency;
import aQute.maven.api.MavenScope;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.POM;
import aQute.service.reporter.Reporter;
import aQute.struct.struct;
import biz.aQute.result.Result;

/**
 * JPM is the Java package manager. It manages a local repository in the user
 * global directory and/or a global directory. This class is the main entry
 * point for the command line. This program maintains a repository, a list of
 * installed commands, and a list of installed service. It provides the commands
 * to changes these resources.
 */

public class JustAnotherPackageManager implements JPM {
	private final static Logger	logger			= LoggerFactory.getLogger(JustAnotherPackageManager.class);
	private static final String	JPM_VMS_EXTRA	= "jpm.vms.extra";
	public static final String	COMMANDS		= "commands";
	public static final String	LOCK			= "lock";
	static JSONCodec			codec			= new JSONCodec();
	static Executor				executor;

	/**
	 * Verify that the jar file is correct. This also verifies ok when there are
	 * no checksums or.
	 */
	static Pattern				MANIFEST_ENTRY	= Pattern.compile("(META-INF/[^/]+)|(.*/)");

	public static void setExecutor(Executor executor) {
		JustAnotherPackageManager.executor = executor;
	}

	/**
	 * Copy from the copy method in StructUtil. Did not want to drag that code
	 * in. maybe this actually should go to struct.
	 *
	 * @param from
	 * @param to
	 * @param excludes
	 * @throws Exception
	 */
	static public <T extends struct> T xcopy(struct from, T to, String... excludes) throws Exception {
		Arrays.sort(excludes);
		for (Field f : from.fields()) {
			if (Arrays.binarySearch(excludes, f.getName()) >= 0)
				continue;

			Object o = f.get(from);
			if (o == null)
				continue;

			Field tof = to.getField(f.getName());
			if (tof != null)
				try {
					tof.set(to, Converter.cnv(tof.getGenericType(), o));
				} catch (Exception e) {
					System.out.println("Failed to convert " + f.getName() + " from " + from.getClass() + " to "
						+ to.getClass() + " value " + o + " exception " + e);
				}
		}

		return to;
	}

	static Executor getExecutor() {
		if (executor == null)
			executor = Executors.newFixedThreadPool(4);
		return executor;
	}

	final File			homeDir;
	final File			binDir;
	final File			commandDir;
	final PlatformImpl	platform;
	final File			cache;
	final File			settingsFile;
	final Reporter		reporter;
	final MavenAccess	maven;
	final HttpClient	client;

	boolean				localInstall	= false;

	boolean				underTest		= System.getProperty("jpm.intest") != null;

	Settings			settings;

	String				jvmLocation		= null;

	/**
	 * Constructor
	 *
	 * @param url
	 * @throws Exception
	 */
	public JustAnotherPackageManager(Reporter reporter, PlatformImpl platform, File homeDir, File binDir, String url)
		throws Exception {
		assert binDir != null;
		assert homeDir != null;

		if (platform == null)
			this.platform = PlatformImpl.getPlatform(reporter, null);
		else
			this.platform = platform;

		this.cache = new File(homeDir, "cache");
		this.cache.mkdirs();
		this.settingsFile = new File(homeDir, "settings.json");
		settings = new Settings(settingsFile.getAbsolutePath());

		this.reporter = reporter;
		this.homeDir = homeDir;
		IO.mkdirs(homeDir);

		commandDir = new File(homeDir, COMMANDS);
		IO.mkdirs(commandDir);

		this.binDir = binDir;
		IO.mkdirs(binDir);
		this.client = new HttpClient();
		this.maven = new MavenAccess(reporter, url, null, client);
	}

	@Override
	public JVM addVm(File platformRoot) throws Exception {
		if (!platformRoot.isDirectory()) {
			reporter.error("No such directory %s for a VM", platformRoot);
			return null;
		}

		JVM jvm = getPlatform().getJVM(platformRoot);
		if (jvm == null) {
			return null;
		}

		String list = settings.get(JPM_VMS_EXTRA);
		if (list == null)
			list = platformRoot.getCanonicalPath();
		else {
			ExtList<String> elist = new ExtList<>(list.split("\\s*,\\s*"));
			elist.remove(platformRoot.getCanonicalPath());
			elist.add(0, platformRoot.getCanonicalPath());
			list = Strings.join(",", elist);

		}
		settings.put(JPM_VMS_EXTRA, list);
		settings.save();
		return jvm;
	}

	@Override
	public Result<File> getArtifact(String spec) throws Exception {
		Result<File> result = fromUrl(spec);
		if (result.isOk())
			return result;

		List<String> reasons = new ArrayList<>();
		reasons.add(result.getMessage());
		result = fromFile(spec);
		if (result.isOk())
			return result;
		reasons.add(result.getMessage());

		result = fromGAV(spec);
		if (result.isOk())
			return result;
		reasons.add(result.getMessage());

		return Result.error("could not find %s: possible reasons %s", spec, reasons);

	}

	@Override
	public void close() {
		if (executor != null && executor instanceof ExecutorService)
			((ExecutorService) executor).shutdown();
	}

	@Override
	public String createCommand(CommandData data, boolean force) {
		try {
			Map<String, String> map = null;
			if (data.trace) {
				map = new HashMap<>();
				map.put("java.security.manager", "aQute.jpm.service.TraceSecurityManager");
				logger.debug("tracing");
			}
			JVM jvm = getVM(data.jvmVersionRange);
			if (jvm != null) {
				data.jvmLocation = jvm.path + (data.windows ? "/bin/javaw" : "/bin/java");
			}

			String s = platform.createCommand(data, map, force);
			if (s == null)
				storeData(new File(commandDir, data.name), data);
			return s;
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	@Override
	public void deinit(Appendable out, boolean force) {
		Settings settings = new Settings(platform.getConfigFile());
		try {
			if (!force) {
				Justif justify = new Justif(80, 40);
				StringBuilder sb = new StringBuilder();
				try (Formatter f = new Formatter(sb)) {

					String list = listFiles(homeDir);
					if (list != null) {
						f.format("home:%n");
						f.format(list);
					}
					list = listFiles(binDir);
					if (list != null) {
						f.format("binaries:%n");
						f.format(list);
					}
					list = listFiles(commandDir);
					if (list != null) {
						f.format("commands:%n");
						f.format(list);
					}
					f.format("%n%n");

					f.format("All files listed above will be deleted if deinit is run with the force flag set"
						+ " (\"jpm deinit -f\" or \"jpm deinit --force\"%n%n");
					f.flush();

					justify.wrap(sb);
					out.append(sb.toString());
				}
			} else { // i.e. if(force)
				IO.delete(binDir);
				IO.delete(cache);
				IO.delete(commandDir);
				IO.delete(settingsFile);
			}
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}

	}

	@Override
	public void deleteCommand(String name) {
		try {
			CommandData cmd = getCommand(name);
			if (cmd == null)
				throw new IllegalArgumentException("No such command " + name);

			platform.deleteCommand(cmd);
			File tobedel = new File(commandDir, name);
			IO.delete(tobedel);
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	/**
	 * Post install
	 */
	@Override
	public void doPostInstall() {
		getPlatform().doPostInstall();
	}

	@Override
	public File getBinDir() {
		return binDir;
	}

	@Override
	public CommandData getCommand(String name) {
		File f = new File(commandDir, name);
		if (!f.isFile())
			return null;

		return getData(CommandData.class, f);
	}

	@Override
	public Result<CommandData> getCommandData(String coordinate) throws Exception {

		Result<File> artifact = getArtifact(coordinate);

		if (artifact.isErr())
			return Result.error(artifact.getMessage());

		File source = artifact.unwrap();

		CommandData data = new CommandData();

		File cached = cache(source);
		data.sha = cached.getName();

		try (JarFile jar = new JarFile(source)) {
			Manifest m = jar.getManifest();
			Attributes main = m.getMainAttributes();
			data.coordinate = coordinate;
			data.main = main.getValue("Main-Class");
			data.description = main.getValue(Constants.BUNDLE_DESCRIPTION);
			data.title = main.getValue("JPM-Name");

			data.dependencies.add(cached.getAbsolutePath());

			doPom(coordinate).forEach(f -> data.dependencies.add(f.getAbsolutePath()));

			Parameters command = OSGiHeader.parseHeader(main.getValue("JPM-Command"));

			if (!command.isEmpty()) {
				if (command.size() > 1)
					reporter.error("Only one command can be specified");

				Map.Entry<String, Attrs> e = command.entrySet()
					.iterator()
					.next();

				data.name = e.getKey();
				Attrs attrs = e.getValue();

				if (attrs.containsKey("jvmargs"))
					data.jvmArgs = attrs.get("jvmargs");

				if (attrs.containsKey("title"))
					data.title = attrs.get("title");

				if (attrs.containsKey("jvmversion")) {
					VersionRange range = new VersionRange(attrs.get("jvmversion"));
					data.jvmVersionRange = range.toString();
				}
				if (data.title == null)
					data.title = data.name;

			}
			return Result.ok(data);
		}
	}

	@Override
	public File getCommandDir() {
		return commandDir;
	}

	@Override
	public List<CommandData> getCommands(File commandDir) {
		List<CommandData> result = new ArrayList<>();

		if (!commandDir.exists()) {
			return result;
		}

		for (File f : commandDir.listFiles()) {
			CommandData data = getData(CommandData.class, f);
			if (data != null)
				result.add(data);
		}
		return result;
	}

	@Override
	public File getHomeDir() {
		return homeDir;
	}

	@Override
	public String getJvmLocation() {
		return jvmLocation;
	}

	@Override
	public PlatformImpl getPlatform() {
		return platform;
	}

	@Override
	public Result<List<String>> getRevisions(String spec) {
		Program p = Program.valueOf(spec);
		if (p == null) {
			return Result.error("not a valid program spec");
		}
		Result<List<Revision>> result = getRevisions(p);
		if (result.isErr())
			return Result.error(result.getMessage());

		List<String> list = result.unwrap()
			.stream()
			.sorted(Comparator.reverseOrder())
			.map(Object::toString)
			.collect(Collectors.toList());
		return Result.ok(list);
	}

	@Override
	public SortedSet<JVM> getVMs() throws Exception {
		TreeSet<JVM> set = new TreeSet<>(JVM.comparator);
		String list = settings.get(JPM_VMS_EXTRA);
		if (list != null) {
			ExtList<String> elist = new ExtList<>(list.split("\\s*,\\s*"));
			for (String dir : elist) {
				File f = new File(dir);
				JVM jvm = getPlatform().getJVM(f);
				if (jvm == null) {
					jvm = new JVM();
					jvm.path = f.getCanonicalPath();
					jvm.name = "Not a valid VM";
					jvm.platformVersion = jvm.vendor = jvm.version = "";
				}
				set.add(jvm);
			}
		}
		getPlatform().getVMs(set);
		return set;
	}

	@Override
	public boolean hasAccess() {
		return binDir.canWrite() && homeDir.canWrite();
	}

	@Override
	public void init() {}

	@Override
	public List<String> search(String spec) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setJvmLocation(String jvmLocation) {
		this.jvmLocation = jvmLocation;
	}

	public void setLocalInstall(boolean b) {
		localInstall = b;
	}

	@Override
	public void setUnderTest() {
		underTest = true;
	}

	public String verify(JarFile jar, String... algorithms) throws IOException {
		if (algorithms == null || algorithms.length == 0)
			algorithms = new String[] {
				"MD5", "SHA"
			};
		else if (algorithms.length == 1 && algorithms[0].equals("-"))
			return null;

		try {
			Manifest m = jar.getManifest();
			if (m.getEntries()
				.isEmpty())
				return "No name sections";

			for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements();) {
				JarEntry je = e.nextElement();
				if (MANIFEST_ENTRY.matcher(je.getName())
					.matches())
					continue;

				Attributes nameSection = m.getAttributes(je.getName());
				if (nameSection == null)
					return "No name section for " + je.getName();

				for (String algorithm : algorithms) {
					try {
						MessageDigest md = MessageDigest.getInstance(algorithm);
						String expected = nameSection.getValue(algorithm + "-Digest");
						if (expected != null) {
							byte digest[] = Base64.decodeBase64(expected);
							copy(jar.getInputStream(je), md);
							if (!Arrays.equals(digest, md.digest()))
								return "Invalid digest for " + je.getName() + ", " + expected + " != "
									+ Base64.encodeBase64(md.digest());
						} else
							reporter.error("could not find digest for %s-Digest", algorithm);
					} catch (NoSuchAlgorithmException nsae) {
						return "Missing digest algorithm " + algorithm;
					}
				}
			}
		} catch (Exception e) {
			return "Failed to verify due to exception: " + e;
		}
		return null;
	}

	private File cache(File source) throws Exception {
		String hex = SHA1.digest(source)
			.asHex()
			.toLowerCase();
		File f = new File(cache, hex);
		if (!f.isFile()) {
			File tmp = IO.createTempFile(cache, hex, ".tmp");
			IO.copy(source, tmp);
			Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		return f;
	}

	private List<File> doPom(String coordinate) {
		try {
			Result<Archive> result = getArchive(coordinate);
			if (result.isOk()) {
				POM pom = maven.storage.getPom(result.unwrap().revision);
				Collection<Dependency> dependencies = pom.getDependencies(MavenScope.runtime, true)
					.values();
				List<File> deps = new ArrayList<>();
				for (Dependency d : dependencies) {
					Archive archive = d.getArchive();
					File file = maven.storage.get(archive)
						.getValue();

					deps.add(cache(file));
				}

				return deps;
			}
		} catch (Exception e) {
			reporter.exception(e, "failed to get dependencies %s", e.getMessage());
		}
		return Collections.emptyList();
	}

	private Result<File> fromFile(String path) throws Exception {
		File file = IO.getFile(path);
		if (!file.isFile())
			file = new File(path);
		if (!file.isFile())
			return Result.error("not a file");
		else
			return Result.ok(file);
	}

	private Result<File> fromGAV(String spec) throws Exception {
		Result<Archive> archive = getArchive(spec);
		if (archive.isErr())
			return Result.error(archive.getMessage());

		Promise<File> promise = maven.storage.get(archive.unwrap());
		File file = promise.getValue();
		return Result.ok(file);
	}

	private Result<File> fromUrl(String spec) throws Exception {
		try {
			URL url = new URL(spec);
			File file = client.build()
				.useCache()
				.go(url);
			return Result.ok(file);
		} catch (MalformedURLException e) {
			return Result.error("not a url: %s", e);
		}
	}

	private Result<Archive> getArchive(String spec) {
		if (Archive.isValid(spec)) {
			Archive archive = new Archive(spec);
			return Result.ok(archive);
		} else {
			Program p = Program.valueOf(spec);
			if (p == null) {
				return Result.error("not a valid program spec");
			}

			Result<List<Revision>> revisions = getRevisions(p);
			if (revisions.isErr())
				return Result.error(revisions.getMessage());

			List<String> reasons = new ArrayList<>();

			for (Revision r : revisions.unwrap())
				try {
					if (r.isSnapshot())
						continue;

					Archive archive = r.archive(null, null);
					return Result.ok(archive);
				} catch (Exception e) {
					reasons.add(e.getMessage());
				}
			return Result.error("not found: %s", reasons);
		}
	}

	/**
	 * @param clazz
	 * @param dataFile
	 * @throws Exception
	 */

	private <T> T getData(Class<T> clazz, File dataFile) {
		try {
			return codec.dec()
				.from(dataFile)
				.get(clazz);
		} catch (Exception e) {
			// e.printStackTrace();
			// System.out.println("Cannot read data file "+dataFile+": " +
			// IO.collect(dataFile));
			return null;
		}
	}

	private Result<List<Revision>> getRevisions(Program program) {
		try {
			List<Revision> revisions = maven.storage.getRevisions(program);
			if (revisions == null || revisions.isEmpty()) {
				return Result.error("no revisions found");
			}

			Collections.sort(revisions, Comparator.reverseOrder());
			return Result.ok(revisions);
		} catch (Exception e) {
			return Result.error("failed to load revision metadata");
		}
	}

	@Override
	public JVM getVM(String jvmVersionRange) throws Exception {
		SortedSet<JVM> vMs = getVMs();
		if (vMs == null || vMs.isEmpty())
			return null;

		if (jvmVersionRange == null)
			return vMs.first();

		VersionRange range = new VersionRange(jvmVersionRange);
		for (JVM jvm : vMs) {
			Version version = new Version(jvm.platformVersion);
			if (range.includes(version)) {
				return jvm;
			}
		}
		return null;
	}

	// Adapter to list without planning to delete
	private String listFiles(final File cache) {
		return listFiles(cache, null);
	}

	private String listFiles(final File cache, List<File> toDelete) {
		boolean stopServices = false;
		if (toDelete == null) {
			toDelete = new ArrayList<>();
		} else {
			stopServices = true;
		}
		int count = 0;
		try (Formatter f = new Formatter()) {

			f.format(" - Cache:%n    * %s%n", cache);
			f.format(" - Commands:%n");
			for (CommandData cdata : getCommands(new File(cache, COMMANDS))) {
				f.format("    * %s \t0 handle for \"%s\"%n", cdata.bin, cdata.name);
				toDelete.add(new File(cdata.bin));
				count++;
			}
			f.format("%n");

			String result = (count > 0) ? f.toString() : null;
			return result;
		}
	}

	private void storeData(File dataFile, Object o) throws Exception {
		codec.enc()
			.to(dataFile)
			.put(o);
	}
}
