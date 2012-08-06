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
/*	 Compiler was unhappy with this code:
 * java.lang.RuntimeException: java.lang.ClassNotFoundException: com.p2pbr.netviz.Net${target}

	    if (!args[0].contains("Net")) {
			PApplet.main(new String[] { "--present", "com.p2pbr.netviz.Net" + args[0] });
	    } else 
*/
		if (args[0].compareToIgnoreCase("TorNetViz") == 0) {
			PApplet.main(new String[] { "--present", "com.p2pbr.netviz.TorNetViz" });
		}
/*	Compiler was also unhappy with this code:
 * java.lang.RuntimeException: java.lang.ClassNotFoundException: com.p2pbr.netviz.${target}
		
		else {
			PApplet.main(new String[] { "--present", "com.p2pbr.netviz." + args[0] });
	    }  
*/
	}
}
