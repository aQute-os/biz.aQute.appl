package aQute.jpm.platform;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import aQute.jpm.lib.CommandData;
import aQute.jpm.lib.JVM;
import aQute.jpm.lib.ServiceData;
import aQute.lib.io.IO;

class MacOS extends Unix {
	private final static Logger		logger	= LoggerFactory.getLogger(MacOS.class);
	static DocumentBuilderFactory	dbf		= DocumentBuilderFactory.newInstance();
	static XPathFactory				xpf		= XPathFactory.newInstance();

	@Override
	public File getGlobal() {
		return new File("/Library/Java/PackageManager").getAbsoluteFile();
	}

	@Override
	public File getGlobalBinDir() {
		return new File("/usr/local/bin").getAbsoluteFile();
	}

	@Override
	public File getLocal() {
		return IO.getFile("~/Library/PackageManager")
			.getAbsoluteFile();
	}

	@Override
	public void shell(String initial) throws Exception {
		run("open -n /Applications/Utilities/Terminal.app");
	}

	@Override
	public String getName() {
		return "MacOS";
	}

	@Override
	public String createCommand(CommandData data, Map<String, String> map, boolean force, String... extra)
		throws Exception {
		if (data.bin == null)
			data.bin = getExecutable(data);

		File f = new File(data.bin);
		if (f.isDirectory()) {
			f = new File(data.bin, data.name);
			data.bin = f.getAbsolutePath();
		}

		if (!force && f.exists())
			return "Command already exists " + data.bin;

		process("macos/command.sh", data, data.bin, map, extra);
		return null;
	}

	@Override
	public String createService(ServiceData data, Map<String, String> map, boolean force, String... extra)
		throws Exception {
		// File initd = getInitd(data);
		File launch = getLaunch(data);
		if (!force && launch.exists())
			return "Cannot create service " + data.name + " because it exists";

		process("macos/launch.sh", data, launch.getAbsolutePath(), map, add(extra, data.serviceLib));
		return null;
	}

	@Override
	public String deleteService(ServiceData data) {
		// File initd = getInitd(data);
		File launch = getLaunch(data);

		if (launch.exists() && !launch.delete())
			return "Cannot delete service " + data.name + " because it exists and cannot be deleted: " + launch;

		return null;
	}

	@Override
	public void installDaemon(boolean user) throws IOException {
		String dest = "~/Library/LaunchAgents/org.bndtools.jpm.run.plist";
		if (!user) {
			dest = "/Library/LaunchAgents/org.bndtools.jpm.run.plist";
		}
		IO.copy(getClass().getResource("macos/daemon.plist"), IO.getFile(dest));
	}

	@Override
	public void uninstallDaemon(boolean user) throws IOException {
		if (user)
			IO.delete(new File("~/Library/LaunchAgents/org.bndtools.jpm.run.plist"));
		else
			IO.delete(new File("/Library/LaunchAgents/org.bndtools.jpm.run.plist"));
	}

	@Override
	public void uninstall() throws IOException {}

	@Override
	public String defaultCacertsPassword() {
		return "changeit";
	}

	@Override
	public String toString() {
		return "MacOS/Darwin";
	}

	/**
	 * Return the VMs on the platform.
	 *
	 * @throws Exception
	 */
	@Override
	public void getVMs(Collection<JVM> vms) throws Exception {
		String paths[] = {
			"/System/Library/Java/JavaVirtualMachines", "/Library/Java/JavaVirtualMachines", System.getenv("JAVA_HOME")
		};
		for (String path : paths) {
			if (path != null) {
				File[] vmFiles = new File(path).listFiles();
				if (vmFiles != null) {
					for (File vmdir : vmFiles) {
						JVM jvm = getJVM(vmdir);
						if (jvm != null)
							vms.add(jvm);
					}
				}
			}
		}
	}

	@Override
	public JVM getJVM(File vmdir) throws Exception {
		if (!vmdir.isDirectory()) {
			return null;
		}

		File contents = new File(vmdir, "Contents");
		if (!contents.isDirectory()) {
			JVM jvm = _getJVMFromRTJar(vmdir);

			if (jvm != null) {
				return jvm;
			}

			logger.debug("Found a directory {}, but it does not have the expected Contents directory", vmdir);
			return null;
		}

		File plist = new File(contents, "Info.plist");
		if (!plist.isFile()) {
			logger.debug("The VM in {} has no Info.plist with the necessary details", vmdir);
			return null;
		}

		File home = new File(contents, "Home");
		String error = verifyVM(home);
		if (error != null) {
			reporter.error("Invalid vm directory for MacOS %s: %s", vmdir, error);
			return null;
		}

		DocumentBuilder db = dbf.newDocumentBuilder();
		try {
			Document doc = db.parse(plist);
			XPath xp = xpf.newXPath();
			Node versionNode = (Node) xp.evaluate("//dict/key[text()='JVMVersion']", doc, XPathConstants.NODE);
			Node platformVersionNode = (Node) xp.evaluate("//dict/key[text()='JVMPlatformVersion']", doc,
				XPathConstants.NODE);
			Node vendorNode = (Node) xp.evaluate("//dict/key[text()='JVMVendor']", doc, XPathConstants.NODE);
			@SuppressWarnings("unused")
			Node capabilitiesNode = (Node) xp.evaluate("//dict/key[text()='JVMCapabilities']", doc,
				XPathConstants.NODE);

			JVM jvm = new JVM();
			jvm.name = vmdir.getName();
			jvm.path = home.getCanonicalPath();
			jvm.platformRoot = vmdir.getCanonicalPath();
			jvm.version = getSiblingValue(versionNode);
			jvm.platformVersion = getSiblingValue(platformVersionNode);
			jvm.vendor = getSiblingValue(vendorNode);

			return jvm;
		} catch (Exception e) {
			logger.debug("Could not parse the Info.plist in {}", vmdir, e);
			throw e;
		}
	}

	private JVM _getJVMFromRTJar(File vmdir) throws Exception {
		File rtJar = new File(vmdir, "lib/rt.jar");

		if (rtJar.exists()) {
			try (JarFile jarFile = new JarFile(rtJar)) {
				File vm = vmdir.getCanonicalFile();

				JVM jvm = new JVM();
				jvm.name = vm.getName();
				jvm.path = vm.getCanonicalPath();
				jvm.platformRoot = vm.getCanonicalPath();

				Manifest manifest = jarFile.getManifest();
				Attributes attrs = manifest.getMainAttributes();
				jvm.version = attrs.getValue("Specification-Version");
				jvm.platformVersion = attrs.getValue("Implementation-Version");
				jvm.vendor = attrs.getValue("Specification-Vendor");

				return jvm;
			} catch (Exception e) {
				logger.debug("Could not get versions from rt.jar {}", vmdir, e);
				throw e;
			}
		}

		return null;
	}

	private String getSiblingValue(Node node) {
		if (node == null)
			return null;
		node = node.getNextSibling();
		while (node.getNodeType() == Node.TEXT_NODE)
			node = node.getNextSibling();

		return node.getTextContent();
	}

}
