package streets;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import aQute.lib.io.IO;
import aQute.lib.json.JSONCodec;
import aQute.lib.settings.Settings;
import streets.Geocoding.Address;

public class Streets {
	static double bottom = 48.38;
	static double top =    48.41;
	static double left =   -4.48;
	static double right =  -4.45;


	final static JSONCodec codec = new JSONCodec();

	public static void main(String args[]) throws MalformedURLException, Exception {
		Settings s = new Settings();
		String[] list = IO.collect(Streets.class.getResourceAsStream("streets.txt")).split("\n");
		for (String street : list) {
			//street = "Rue de Guilvinec";
			String spec = "2," + street+",Brest";
			String address = URLEncoder.encode(spec, "UTF-8");
			InputStream go = (InputStream) new URL("https://maps.googleapis.com/maps/api/geocode/json?address="
					+ address + "&key=AIzaSyCDrSRlhI63FXcnm0tMV2B-oDtf3xp8uc8").getContent();
			String result = IO.collect(go);
			Geocoding geocoding = codec.dec().from(result).get(Geocoding.class);
			if ("OK".equals(geocoding.status)) {
				for (Address adr : geocoding.results) {
					// lat == y
					// lng == x

					double x = adr.geometry.location.lng;
					double y = adr.geometry.location.lat;
					if ( x > left  && x < right) {
						if ( y > bottom && y < top) {
							System.out.println(adr.formatted_address);
						}
					}

				}
			}

		}

	}
}
