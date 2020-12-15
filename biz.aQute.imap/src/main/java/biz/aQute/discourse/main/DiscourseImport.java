package biz.aQute.discourse.main;

import java.net.URI;
import java.util.Formatter;
import java.util.List;

import aQute.lib.collections.ExtList;
import aQute.lib.getopt.CommandLine;
import aQute.lib.getopt.Options;
import aQute.lib.settings.Settings;
import aQute.libg.reporter.ReporterAdapter;
import biz.aQute.discourse.DiscourseAPI;

public class DiscourseImport extends ReporterAdapter {
	
	final Settings		settings = new Settings("~/.discourse");
	
	public static void main(String[] args) throws Exception {
		DiscourseImport di = new DiscourseImport();
		CommandLine cl = new CommandLine(di);
		if (args.length == 0) {
			Formatter f = new Formatter();
			cl.help(f, di);
			System.out.println(f.toString());
			return;
		}
		
		List<String> arguments = new ExtList<>(args);
		String cmd = arguments.remove(0);
		String execute = cl.execute(di, cmd, arguments);
		if (execute != null) {
			System.out.println(execute);
		}
	}
	
	interface Discourse extends Options {
		URI uri();
		String user(String deflt);
		String key();
	}
	
	public void _categories(Discourse discourse) {
		DiscourseAPI da = getDiscourseAPI(discourse);
	}

	private DiscourseAPI getDiscourseAPI(Discourse discourse) {
		if ( discourse.uri() == null)
		// TODO Auto-generated method stub
		return null;
	}
	

}
