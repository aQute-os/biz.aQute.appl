package biz.aQute.drone.litchi.csv.provider;

public class Coord {
	final double	lat;
	final double	lon;
	final double	height;

	public Coord(double lat, double lng, double height) {
		this.lat = lat;
		this.lon = lng;
		this.height = height;
	}

	public double distance(Coord to) {
		return distance(lat, lon, to.lat, to.lon) + to.height - this.height;
	}

	/**
	 * The simplest is to interpolate linearly in angle. If you have points
	 * (a,b)(a,b) and (c,d)(c,d) and want nn intervals (so n−1n−1) intervening
	 * points), your points are (a+in(c−a),b+in(d−b))(a+in(c−a),b+in(d−b)) for
	 * i=1,2,3…n−1i=1,2,3…n−1 This will not follow a great circle, nor be
	 * exactly evenly spaced, but will be smooth and involves not a single trig
	 * call. The errors in the interpolation decrease as the step length gets
	 * shorter. 100100 km is only 164164 radius, so that is pretty small.
	 * 
	 * Alternately, you can do the midpoint calculation a few times, then do
	 * linear interpolation. That gets you shorter steps at the price of more
	 * computation
	 * 
	 * @param ratio
	 * @param to
	 * @return
	 */
	public Coord interpolate(double ratio, Coord to) {
		double lat = this.lat + ratio * ( to.lat-this.lat);
		double lon = this.lon + ratio * ( to.lon-this.lon);
		double height = this.height + ratio * ( to.height-this.height);
		return new Coord(lat,lon, height);
	}

	public static double distance(double lat1, double lon1, double lat2,
			double lon2) {
		double theta = lon1 - lon2;
		double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2))
				+ Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2))
						* Math.cos(deg2rad(theta));
		dist = Math.acos(dist);
		dist = rad2deg(dist);
		dist = dist * 60 * 1853.159616;
		return dist;
	}

	/* ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::: */
	/* :: This function converts decimal degrees to radians : */
	/* ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::: */
	public static double deg2rad(double deg) {
		return (deg * Math.PI / 180.0);
	}

	/* ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::: */
	/* :: This function converts radians to decimal degrees : */
	/* ::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::::: */
	private static double rad2deg(double rad) {
		return (rad * 180 / Math.PI);
	}

	public Coord interpolateDistance(double length, Coord to) {
		double d = distance( to);
		if ( Math.abs(d) <  1)
			return this;

		double ratio = length / d; 
		return interpolate(ratio, to);
	}

	@Override
	public String toString() {
		return lat + "," + lon + "," + Math.round(height);
	}

}
