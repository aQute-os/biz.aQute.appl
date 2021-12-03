package biz.aQute.upnp;

import java.util.Formatter;

public class Util {
	static String toString(byte[] bytes, String del) {

		String d = "";
		try (Formatter sb = new Formatter()) {
			for (int i = 0; i < bytes.length; i++) {
				sb.format("%s%d", d, 0xFF & bytes[i]);
				d = del;
			}
			return sb.toString();
		}
	}

}
