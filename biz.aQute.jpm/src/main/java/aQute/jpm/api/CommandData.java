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
	@Define(optional = true)
	public String		jvmVersionRange;
	@Define(optional = true)
	public String		jvmLocation;
	public String		sha;
}
