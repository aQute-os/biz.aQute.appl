package biz.aQute.upnp;

import java.io.IOException;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import aQute.lib.strings.Strings;

public class SSDP extends Thread {

	final InetAddress group = Inet4Address.getByName("239.255.255.250");
	final MulticastSocket socket = new MulticastSocket(1900);
	final String unique;
	private String ip;

	public SSDP(String unique) throws Exception {
		this.unique =unique;
		this.ip = Util.toString(Inet4Address.getLocalHost().getAddress(), ".");
		socket.setReuseAddress(true);
		socket.joinGroup(group);
	}


	@Override
	public void run() {

		DatagramPacket dp = new DatagramPacket(new byte[2000], 2000);
		while (true)
			try {
				socket.receive(dp);

				String string = new String(dp.getData(), 0, dp.getLength());
				parse(dp, string);
			} catch (Exception e) {
				e.printStackTrace();
			}
	}

	private void parse(DatagramPacket dp, String string) throws IOException {
		Properties p = new Properties();
		p.load(new StringReader(string));
		Map<String, String> map = cleanup(p);
		if (map.containsKey("M-SEARCH")) {
			if ("ssdp:discover".equals(map.get("MAN"))) {
				if ("ssdp:all".equals(map.get("ST"))) {
					send(dp, string);
				}
			}
		}
	}

	private void send(DatagramPacket dp, String string) throws IOException {

		String[] st = new String[] { "upnp:rootdevice", "urn:schemas-upnp-org:device:basic:1",
				"uuid:2f402f80-da50-11e1-9b23-00178813b74c" };
		System.out.println("X: " + string);
		System.out.println(dp.getSocketAddress());

		String response = "HTTP/1.1 200 OK\r\n" //
				+ "EXT:\r\n" + "LOCATION: http://%s:80/description.xml\r\n"
				+ "SERVER: FreeRTOS/6.0.5, UPnP/1.0, IpBridge/0.1\r\n" + "ST: upnp:rootdevice\r\n"
				+ "USN: %s::upnp:rootdevice\r\n\r\n";

		response = String.format(response, ip, unique);

		dp.setData(response.getBytes());
		dp.setAddress(dp.getAddress());
		System.out.println("Sending response " + response + " to " + dp.getAddress());
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		socket.send(dp);
	}

	private Map<String, String> cleanup(Properties p) {
		Map<String, String> map = new HashMap<>();
		p.entrySet().stream().forEach(e -> {
			String k = ((String) e.getKey()).toUpperCase();
			String v = ((String) e.getValue()).trim();
			if (v.matches("\".*\""))
				v = v.substring(1, v.length() - 1);
			v = Strings.join(Strings.splitQuoted(v));
			map.put(k, v);
		});
		return map;
	}

	public void search() throws IOException {
		String search = "M-SEARCH * HTTP/1.1\r\n" + "HOST: 239.255.255.250:1900\r\n" + "ST: ssdp:all\r\n"
				+ "MAN: \"ssdp:discover\"\r\n" + "MX: 3\r\n\r\n";

		byte[] data = search.getBytes();
		DatagramPacket dp = new DatagramPacket(data, data.length);
		dp.setAddress(group);
		dp.setPort(1900);
		socket.send(dp);
	}

}
