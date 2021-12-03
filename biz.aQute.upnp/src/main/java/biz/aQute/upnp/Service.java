package biz.aQute.upnp;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.osgi.dto.DTO;

import aQute.lib.json.JSONCodec;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Service extends NanoHTTPD {
	static JSONCodec codec = new JSONCodec();
	static Random random = new Random();
	final InetAddress adress = Inet4Address.getLocalHost();
	final byte[] bytes = adress.getAddress();
	final String ip = String.format("%d.%d.%d.%d", 0xFF & bytes[0], 0xFF & bytes[1], 0xFF & bytes[2], 0xFF & bytes[3]);
	final String usn = "uuid:" //
			+ UUID.randomUUID();
	final Map<String,Device>		devices = new HashMap<>();
	long lasttime = 0;
	int api;

	public static class State extends DTO {
		public boolean on;
		public int bri;
		public int[] xyz = new int[] {0,0};
		public boolean reachable = true;
				
	}
	
	public static class Streaming extends DTO {
		public boolean renderer = false;
		public boolean proxy = false;
	}

	public static class Capabilities extends DTO {
		public boolean certified = false;
		public Streaming streaming = new Streaming();
	}
	
	
	public static class Device extends DTO {
		public String type= "Extended Color Light";
		public String name;
		public String uniqueid=random.nextLong()+"";
		public String modelid = "LCT007";
		public State state = new State(); 
		public Capabilities capabilities = new Capabilities(); 
		public Device(String name) {
			this.name = name;
		}
	}
	
	
	public Service() throws Exception {
		super(80);
	}

	@Override
	public synchronized Response serve(IHTTPSession session) {
		try {
			URI uri = new URI(session.getUri());
			System.out.println(session.getMethod() + " " + uri);
			String path = uri.getPath();
			if (path.equals("/description.xml")) {
				String msg = "<?xml version=\"1.0\"?>\n" //
						+ "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\n"
						//
						+ "  <specVersion>\n" //
						+ "    <major>1</major>\n" //
						+ "    <minor>0</minor>\n"
						//
						+ "  </specVersion>\n" //
						+ "  <URLBase>http://%s:80/</URLBase>\n" //
						+ "  <device>\n"
						//
						+ "    <deviceType>urn:schemas-upnp-org:device:Basic:1</deviceType>\n"
						//
						+ "    <friendlyName>Philips hue (192.168.0.21)</friendlyName>\n"
						//
						+ "    <manufacturer>Royal Philips Electronics</manufacturer>\n"
						//
						+ "    <manufacturerURL>http://www.philips.com</manufacturerURL>\n"
						//
						+ "    <modelDescription>Philips hue Personal Wireless Lighting</modelDescription>\n"
						//
						+ "    <modelName>Philips hue bridge 2012</modelName>\n"
						//
						+ "    <modelNumber>1000000000000</modelNumber>\n"
						//
						+ "    <modelURL>http://www.meethue.com</modelURL>\n"
						//
						+ "    <serialNumber>93eadbeef13</serialNumber>\n" //
						+ "    <UDN>%s</UDN>\n"
						//
						+ "    <serviceList>\n" //
						+ "      <service>\n" //
						+ "        <serviceType>(null)</serviceType>\n"
						//
						+ "        <serviceId>(null)</serviceId>\n" //
						+ "        <controlURL>(null)</controlURL>\n"
						//
						+ "        <eventSubURL>(null)</eventSubURL>\n" //
						+ "        <SCPDURL>(null)</SCPDURL>\n"
						//
						+ "      </service>\n" //
						+ "    </serviceList>\n"
						//
						+ "    <presentationURL>index.html</presentationURL>\n" //
						+ "    <iconList>\n" //
						+ "      <icon>\n"
						//
						+ "        <mimetype>image/png</mimetype>\n" //
						+ "        <height>48</height>\n"
						//
						+ "        <width>48</width>\n" //
						+ "        <depth>24</depth>\n"
						//
						+ "        <url>hue_logo_0.png</url>\n" //
						+ "      </icon>\n" //
						+ "      <icon>\n"
						//
						+ "        <mimetype>image/png</mimetype>\n" //
						+ "        <height>120</height>\n"
						//
						+ "        <width>120</width>\n" //
						+ "        <depth>24</depth>\n"
						//
						+ "        <url>hue_logo_3.png</url>\n" //
						+ "      </icon>\n" //
						+ "    </iconList>\n"
						//
						+ "  </device>\n" //
						+ "</root>";

				msg = String.format(msg, ip, usn);
				System.out.println("response " //
						+ msg);
				return newFixedLengthResponse(Status.OK, "text/xml", msg);
			} else if (path.equals("/api")) {
				if (session.getMethod().equals(Method.POST) && inGrace()) {
//					if ( api > 0) {
//						System.out.println("conflict");
//						return newFixedLengthResponse(Status.CONFLICT, "text/plain", "");
//					}
//					api++;
					
					System.out.println("getting content");
					InputStream in = session.getInputStream();
					Thread.sleep(10);
					byte[] data = new byte[ in.available()];
					int read = in.read(data);
					String body = new String( data, 0, read);
					System.out.println("Received " //
							+ uri //
							+ " " //
							+ session.getMethod() //
							+ " " //
							+ body);

					String msg = "{\"success\":{\"username\":\"anonymous\"}}]";
					System.out.println("sending " + msg);
					return newFixedLengthResponse(Status.OK, "application/json", msg);
				} else {
					System.out.println("oops");
				}
			}
			if ("/api/anonymous/lights".equals(path)) {
				Map<String,Map<String,String>> result = new HashMap<>();
				for ( Map.Entry<String, Device> e : devices.entrySet()) {
					Device d = e.getValue();
					Map<String,String> m = new HashMap<>();
					m.put("name", d.name);
					m.put("uniqueid", d.uniqueid);
					result.put(e.getKey(), m);
				}
				String msg = codec.enc().put(result).toString();				
				System.out.println("msg=" + msg);
				return newFixedLengthResponse(Status.OK, "application/json", msg);
			} if (path.startsWith("/api/anonymous/lights/")) {
				Pattern p = Pattern.compile("/api/anonymous/lights/(\\d+)");
				Matcher m = p.matcher(path);
				if ( m.lookingAt()) {
					String n = m.group(1);
					StringBuilder sb = new StringBuilder();
					codec.enc().to(sb).put(devices.get(n)).close();
					String msg = sb.toString();
					System.out.println(msg);
					return newFixedLengthResponse(Status.OK, "application/json", msg);
				} else {
					System.out.println("unknown uri");
				}
				
			} else {
				System.out.println("Received " //
						+ uri);

			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return newFixedLengthResponse(Status.NOT_FOUND, "Not found " //
				+ session.getUri(), "");
	}

	public boolean inGrace() {
		if ((System.currentTimeMillis() - lasttime) < 1000) {
			return true;
		}
		lasttime = System.currentTimeMillis();
		api = 0;
		return false;
	}
}
