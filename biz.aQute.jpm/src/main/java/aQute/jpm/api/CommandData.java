package aQute.jpm.api;

import java.util.List;

import aQute.struct.Define;
import aQute.struct.struct;

public class CommandData extends struct {

	public long			time			= System.currentTimeMillis();
	public String		coordinate;
	public String		name;
	@Define(optional = true)
	public String		title;
	@Define(optional = true)
	public String		description;
	@Define(optional = true)
	public String		jvmArgs;

	public List<String>	dependencies	= list();

	public boolean		installed;
	@Define(optional = true)
	public String		bin;
	public boolean		trace;

	public String		main;
	/**
	 * Use javaw instead of java
	 */
	public boolean		windows;

	/**
	 * Specifies a version range for the vm in the OSGi style. The range must
	 * match the classic 1.6 ... 1.17 kind of versions. (I.e. Java 17 == 1.17).
	 * If you only need a base version, set e.g. '1.12', this is a valid range
	 * from 1.12...infinite. If no range is set, the latest JVM is used.
	 */
	@Define(optional = true)
	public String		jvmVersionRange;

	/**
	 * Optional path to VM. This is normally calculated from the VM but this can
	 * be overridden.
	 */
	@Define(optional = true)
	public String		jvm;
	public String		sha;
}
