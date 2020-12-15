package biz.aQute.drone.main;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.util.Formatter;

import aQute.lib.collections.ExtList;
import aQute.lib.env.Env;
import aQute.lib.getopt.CommandLine;
import aQute.lib.getopt.Options;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;
import aQute.lib.tag.Tag;
import aQute.libg.reporter.ReporterAdapter;
import biz.aQute.drone.litchi.csv.provider.LitchiCsvToKml;

/**
 * 
 */
public class Drone extends ReporterAdapter {

	final Env env = new Env();

	public static void main(String args[]) throws Exception {
		Drone l = new Drone();
		CommandLine c = new CommandLine(l);

		if (args.length == 0) {
			help(l, c);
			return;
		}

		ExtList<String> arguments = new ExtList<>(args);
		String cmd = arguments.remove(0);

		String result = c.execute(l, cmd, arguments);
		if (!l.isOk()) {
			l.report(System.out);
		}
		if (result != null) {
			System.out.println(result);
			help(l, c);
		}
	}

	private static void help(Drone l, CommandLine c) throws Exception {
		try (Formatter f = new Formatter()) {
			c.help(f, l);
			System.out.println(f.toString());
			return;
		}
	}

	interface KmlOptions extends Options {
		boolean nomarkers();
		int speed(int m_s);
		String out(String deflt);
		double max(double deflt);
		double adjustHeight(double deflt);
	}

	public void _kml(KmlOptions options) throws Exception {
		System.out.println("options " + options);
		for (String arg : options._arguments()) {
			File in = env.getFile(arg);
			if (!in.isFile()) {
				error("No such file %s", in);
				return;
			}
			trace("reading %s", in);
			Reader r = IO.reader(in);
			LitchiCsvToKml lk = new LitchiCsvToKml();
			lk.markers = !options.nomarkers();
			lk.speed = options.speed(lk.speed);
			lk.maxDuration = options.max(lk.maxDuration);
			
			lk.convert(r);
			Tag kml = lk.getKml();

			String[] parts = Strings.extension(arg);
			String path = options.out(parts[0]+".kml");
			File out = env.getFile(path);
			trace("writing %s", out);
			try (Writer w = IO.writer(out)) {
				w.write(kml.toString());
			}
		}
	}
}
