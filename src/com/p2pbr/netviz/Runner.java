package com.p2pbr.netviz;

import processing.core.*;

public class Runner {
	public static void main(String args[]) {
		if (args.length < 1) {
			System.out.println("Need to specify the class to run.");
			System.out.println("If you are running from ant, use -Dtarget=<name>");
			return;
		}
	    System.loadLibrary("jpcap");
	    if (!args[0].contains("Net")) {
		PApplet.main(new String[] { "--present", "com.p2pbr.netviz.Net" + args[0] });
	    } else {
		PApplet.main(new String[] { "--present", "com.p2pbr.netviz." + args[0] });
	    }
	}
}
