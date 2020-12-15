package biz.aQute.drone.litchi.csv.provider;

import org.osgi.dto.DTO;

public class Record extends DTO {
	public enum GimbalMode {
		DISABLED, FOCUS_POI, INTERPOLATE;
	}

	public int			index;
	public double		latitude;
	public double		longitude;
	public double		altitude;
	public double		heading;
	public double		curvesize;
	public double		rotationdir;
	public GimbalMode	gimbalmode;
	public double		gimbalpitchangle;
	public double		elevation;

}
