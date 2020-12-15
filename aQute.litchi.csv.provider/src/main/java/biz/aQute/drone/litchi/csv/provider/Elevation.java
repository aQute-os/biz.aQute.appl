package biz.aQute.drone.litchi.csv.provider;

import java.util.List;

import org.osgi.dto.DTO;

public class Elevation extends DTO {
	public enum Status {
		OK, // indicating the API request was successful.
		INVALID_REQUEST, // indicating the API request was malformed.
		OVER_QUERY_LIMIT, // indicating the requestor has exceeded quota.
		REQUEST_DENIED, // indicating the API did not complete the request.
		UNKNOWN_ERROR, // indicating an unknown error.
	}

	public static class Location extends DTO {
		public double	lat;
		public double	lng;
	}

	public static class Result extends DTO  {
		public double	elevation;
		public Location	location;
		double			resolution;
	}

	public List<Result>	results;
	public Status		status;

}
