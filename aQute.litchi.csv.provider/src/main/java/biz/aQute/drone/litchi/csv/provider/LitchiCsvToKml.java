package biz.aQute.drone.litchi.csv.provider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.opencsv.CSVReader;

import aQute.lib.json.JSONCodec;
import aQute.lib.tag.Tag;
import biz.aQute.drone.litchi.csv.provider.Elevation.Result;
import biz.aQute.drone.litchi.csv.provider.Record.GimbalMode;

public class LitchiCsvToKml {
	static JSONCodec codec = new JSONCodec();

	Tag kml = new Tag("kml");
	Tag document = new Tag(kml, "Document");
	Tag placemarks = new Tag(document, "Folder");
	Tag tour = new Tag(document, "gx:Tour");
	Tag playlist = new Tag(tour, "gx:Playlist");
	Coord prior;
	public boolean markers = true;
	public int speed = 100;
	public double maxDuration = Double.MAX_VALUE;

	private boolean first = true;
	final List<Record> records = new ArrayList<>();
	int counter = 1;

	static class FlyTo {

		public FlyTo(Coord location, double heading, double tilt, int index) {
			this.location = location;
			this.heading = heading;
			this.tilt = tilt;
			this.index = index;
		}

		final Coord location;
		final double heading;
		final double tilt;
		final int index;

		@Override
		public String toString() {
			return "FlyTo [location=" + location + ", heading=" + heading + ", tilt=" + tilt + ", index=" + index + "]";
		}
	}

	public void convert(Reader reader) throws Exception {
		kml.addAttribute("xmlns", "http://www.opengis.net/kml/2.2");
		kml.addAttribute("xmlns:gx", "http://www.google.com/kml/ext/2.2");

		try (CSVReader r = new CSVReader(reader);) {
			List<Record> records = StreamSupport.stream(r.spliterator(), false).skip(1).map(this::record)
					.collect(Collectors.toList());

			if (records.isEmpty())
				throw new IllegalArgumentException("Need at least 1 waypoint");

			elevations(records);

			List<FlyTo> flyTos = new ArrayList<FlyTo>();
			for (int i = 0; i < records.size(); i++) {
				waypoint(flyTos, get(records, i - 1), get(records, i), get(records, i + 1));
			}

			flyTos.forEach(this::marker);
			flyTos.forEach(this::flyTo);
		}

	}

	private Record get(List<Record> records, int i) {
		return i >= 0 && i < records.size() ? records.get(i) : null;
	}

	/**
	 * <Placemark> <name>Simple placemark</name> <description>Attached to the
	 * ground. Intelligently places itself at the height of the underlying
	 * terrain.</description> <Point>
	 * <coordinates>-122.0822035425683,37.42228990140251,0</coordinates> </Point>
	 * </Placemark>
	 * 
	 * @param r
	 */
	private void marker(FlyTo r) {
		if (!markers)
			return;

		Tag placeMark = new Tag(placemarks, "Placemark");
		new Tag(placeMark, "name", "" + r);
		new Tag(placeMark, "description", "" + r);
		Tag linestring = new Tag(placeMark, "LineString");
		new Tag(linestring, "extrude", "1");
		new Tag(linestring, "altitudeMode", "absolute");
		new Tag(linestring, "coordinates", "" + r.location.lon + "," + r.location.lat + "," + (r.location.height) + "\n"
				+ r.location.lon + "," + r.location.lat + "," + ("?"));
	}

	private void elevations(List<Record> records) throws IOException, Exception {
		final StringBuilder req = new StringBuilder(
				"https://maps.googleapis.com/maps/api/elevation/json?key=AIzaSyCDrSRlhI63FXcnm0tMV2B-oDtf3xp8uc8&locations=");
		String del = "";
		for (Record r : records) {
			req.append(del).append(r.latitude).append(",").append(r.longitude);
			del = "|";
		}

		URL uri = new URL(req.toString());
		Elevation elevation = codec.dec().from(uri.openStream()).get(Elevation.class);
		for (int i = 0; i < elevation.results.size(); i++) {
			Result el = elevation.results.get(i);
			Record r = records.get(i);
			r.elevation = el.elevation;
		}
	}

	void waypoint(List<FlyTo> flyTos, Record prevRecord, Record currentRecord, Record nextRecord) {
		Coord prev, current, next;

		current = new Coord(currentRecord.latitude, currentRecord.longitude,
				currentRecord.altitude + currentRecord.elevation);

		if (prevRecord == null) {
			flyTos.add(new FlyTo(current, currentRecord.heading, currentRecord.gimbalpitchangle, currentRecord.index));
			return;
		}

		prev = new Coord(prevRecord.latitude, prevRecord.longitude, prevRecord.altitude + prevRecord.elevation);

		if (nextRecord == null) {
			flyTos.add(new FlyTo(current, currentRecord.heading, currentRecord.gimbalpitchangle, currentRecord.index));
			return;
		}
		next = new Coord(nextRecord.latitude, nextRecord.longitude, nextRecord.altitude + nextRecord.elevation);

		Coord before = current.interpolateDistance(currentRecord.curvesize, prev);
		Coord after = current.interpolateDistance(currentRecord.curvesize, next);

		Coord midpoint = before.interpolate(0.5, after);
		Coord top = midpoint.interpolate(0.5, current);

		moveTo(flyTos, prev, before, currentRecord.heading, currentRecord.gimbalpitchangle, currentRecord.index);
		moveTo(flyTos, before, top, currentRecord.heading, currentRecord.gimbalpitchangle, currentRecord.index);
		moveTo(flyTos, top, after, currentRecord.heading, currentRecord.gimbalpitchangle, currentRecord.index);
	}

	void moveTo(List<FlyTo> flyTos, Coord from, Coord to, double heading, double gimblePitchAngle, int index) {

//		while ( from.distance(to) > 15) {
//			from = from.interpolateDistance(10, to);
//			flyTos.add( new FlyTo(from,heading,gimblePitchAngle,index));
//		}
		flyTos.add(new FlyTo(to, heading, gimblePitchAngle, index));
	}

	void flyTo(FlyTo flyto) {
		flyTo(flyto.location, flyto.heading, flyto.tilt);
	}

	void flyTo(Coord to, double heading, double gimblePitchAngle) {
		System.out.println(to);

		Tag flyto = new Tag(playlist, "gx:FlyTo");
		if (first) {
			new Tag(flyto, "gx:duration").addContent("0");
			first = false;
		} else {
			double dist = prior.distance(to);
			double duration = dist / speed;
			if ( duration > maxDuration) {
				duration = maxDuration;
			}
			new Tag(flyto, "gx:duration").addContent("" + duration);
		}
		prior = to;

		new Tag(flyto, "gx:flyToMode").addContent("smooth");

		Tag camera = new Tag(flyto, "Camera");
		camera.addContent(new Tag("longitude", "" + to.lon));
		camera.addContent(new Tag("latitude", "" + to.lat));
		camera.addContent(new Tag("altitude", "" + (to.height)));
		camera.addContent(new Tag("heading", "" + heading));
		camera.addContent(new Tag("gx:horizFov", "94"));
		// pitch = 0 == forward kml=90
		// pitch = -90 == down kml = 0
		double tilt = 90 + gimblePitchAngle;
		camera.addContent(new Tag("tilt", "" + tilt));
		camera.addContent(new Tag("altitudeMode", "absolute"));
	}

	Record record(String[] line) {
		Record record = new Record();
		// latitude,longitude,altitude(m),heading(deg),curvesize(m),rotationdir,gimbalmode,gimbalpitchangle,actiontype1,actionparam1,actiontype2,actionparam2,actiontype3,actionparam3,actiontype4,actionparam4,actiontype5,actionparam5,actiontype6,actionparam6,actiontype7,actionparam7,actiontype8,actionparam8,actiontype9,actionparam9,actiontype10,actionparam10,actiontype11,actionparam11,actiontype12,actionparam12,actiontype13,actionparam13,actiontype14,actionparam14,actiontype15,actionparam15

		record.latitude = Double.parseDouble(line[0]);
		record.longitude = Double.parseDouble(line[1]);
		record.altitude = Double.parseDouble(line[2]);
		record.heading = Double.parseDouble(line[3]);
		record.curvesize = Double.parseDouble(line[4]);
		record.rotationdir = Double.parseDouble(line[5]);
		record.gimbalmode = getMode(line[6]);
		record.gimbalpitchangle = Double.parseDouble(line[7]);
		record.index = counter++;
		return record;
	}

	private GimbalMode getMode(String mode) {
		switch (mode) {
		case "0":
			return GimbalMode.DISABLED;
		case "1":
			return GimbalMode.FOCUS_POI;
		case "2":
			return GimbalMode.INTERPOLATE;
		}
		return GimbalMode.DISABLED;
	}

	public void write(String file) throws FileNotFoundException, IOException {
		File f = new File(file);
		try (PrintWriter pw = new PrintWriter(f);) {
			kml.print(2, pw);
		}
	}

	public Tag getKml() {
		return kml;
	}
}
