package aQute.jpm.api;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.SortedSet;

import biz.aQute.result.Result;

public interface JPM extends Closeable {

	List<String> search(String spec) throws Exception;

	Result<File> getArtifact(String spec) throws Exception;

	/**
	 * Get the home directory
	 */
	File getHomeDir();

	/**
	 * Get the bin directory
	 */
	File getBinDir();

	/**
	 * Can write the home and bin dir
	 *
	 * @return true if the directories are writeable
	 */
	boolean hasAccess();

	File getCommandDir();

	default List<CommandData> getCommands() throws Exception {
		return getCommands(getCommandDir());
	}

	List<CommandData> getCommands(File commandDir) throws Exception;

	CommandData getCommand(String name) throws Exception;

	void deinit(Appendable out, boolean force) throws Exception;

	/**
	 * @param data
	 * @throws Exception
	 * @throws IOException
	 */
	String createCommand(CommandData data, boolean force) throws Exception;

	void deleteCommand(String name) throws Exception;

	void setJvmLocation(String jvmlocation) throws Exception;

	void setUnderTest();

	Result<CommandData> getCommandData(String artifact) throws Exception;

	SortedSet<JVM> getVMs() throws Exception;

	String getJvmLocation();

	void init() throws Exception;

	Platform getPlatform() throws Exception;

	JVM addVm(File f) throws Exception;

	/**
	 * Post install
	 */
	void doPostInstall() throws Exception;

	/**
	 * Get the available revisions of a program
	 */
	Result<List<String>> getRevisions(String program);

	JVM getVM(String jvmlocation) throws Exception;

}
