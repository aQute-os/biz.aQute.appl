package biz.aQute.upnp;

import java.net.URL;

import aQute.bnd.http.HttpClient;

public class Hub {
	URL		url = new URL("http://192.168.2.12/api");
	
	public Hub() throws Exception {
		HttpClient hc = new HttpClient();
		hc.build().post().upload("{\"devicetype\": \"Echo\"}");
	}
	
	public static void main(String args[]) throws Exception {
		HttpClient hc = new HttpClient();
		Object go = hc.build().post().upload("{\"devicetype\": \"Echo\"}").headers("Content-Type", "application/json").go( new URL("http://192.168.67.152/api"));
		System.out.println(go);
	}
	
}
