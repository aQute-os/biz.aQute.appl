package biz.aQute.upnp;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

public class AlexaHueHub {
	final SSDP 		ssdp;
	final String 	mac;

	public AlexaHueHub() throws Exception {
        this.mac = getMAC();
        this.ssdp = new SSDP(this.mac);
	}

	private static String getMAC() throws UnknownHostException, SocketException {
		InetAddress ip = InetAddress.getLocalHost();
        NetworkInterface network = NetworkInterface.getByInetAddress(ip);
        byte[] macb = network.getHardwareAddress();
        String mac = "";
        for ( int i=0; i<macb.length; i++) {
        	mac += String.format("%02X", 0xFF & macb[i]);
        }

		return mac;
	}
}
