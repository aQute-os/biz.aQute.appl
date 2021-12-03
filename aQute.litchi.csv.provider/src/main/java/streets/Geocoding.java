package streets;

import java.util.List;

import org.osgi.dto.DTO;

public class Geocoding extends DTO {

	public static class Location extends DTO{
		public double lat;
		public double lng;
	}

	public static class Bounds extends DTO{
		public Location northeast;
		public Location southwest;
	}
	public static class Geometry extends DTO{
		public Location location;
		public String location_type;
		public Bounds viewport;

	}
	public static class Address extends DTO{
		public List<Object> address_components;
		public String formatted_address;
		public Geometry geometry;
		public String place_id;
		public List<String> types;
	}

	public List<Address> results;
	public String status;
}
