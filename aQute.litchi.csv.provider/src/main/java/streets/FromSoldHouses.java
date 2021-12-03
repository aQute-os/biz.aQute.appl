package streets;

import java.util.List;

import org.osgi.dto.DTO;

import aQute.lib.converter.TypeReference;
import aQute.lib.json.JSONCodec;

public class FromSoldHouses {

	public static class Sold extends DTO {
		public double lat;
		public String titre;
		public double lng;
	}

	public static void main(String args[]) throws Exception {
		JSONCodec codec = new JSONCodec();
		List<Sold> list = codec.dec().from(FromSoldHouses.class.getResourceAsStream("soldhouses.json"))
				.get(new TypeReference<List<Sold>>() {
				});
		System.out.println("lat,lng,title");
		int n =0;
		for (Sold s : list) {
			System.out.println(s.lat+","+s.lng+","+n+" " + s.lat+" "+s.lng);
			n++;
		}
	}
}
