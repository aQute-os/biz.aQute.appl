package biz.aQute.upnp;

import java.io.IOException;

import biz.aQute.upnp.Service.Device;

public class Main {
	Service			service = new Service();
	SSDP			ssdp = new SSDP("001788FFFE13B74C");
	
	Main() throws Exception {
		
	}

	
	private void open() throws IOException {
		service.devices.put("1", new Device("TV Left"));
		service.devices.put("2", new Device("TV Right"));
		//ssdp.start();
		ssdp.search();
		//service.start();
	}

	
	public static void main(String args[]) throws Exception {
		Main main = new Main();
		main.open();
		Thread.sleep(1000000);

	}



}
