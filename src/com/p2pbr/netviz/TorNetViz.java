package com.p2pbr.netviz;

import processing.core.*;

import com.maxmind.geoip.*;
import java.util.*;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;

public class TorNetViz extends PApplet {
	private static final long serialVersionUID = 9075470452122575298L;
    
	// Map and drawing related items.
	PImage mapImage;
	private final String mapFilename = "1024px-Equirectangular-projection.jpg";
	
	// Constants for drawing.
	private final double WINDOW_SIZE = 15; // reference 'max'
	private final double MAX_BANDWIDTH = 100000000.0; 
	private final int mapX = 0;
	private final int mapY = 0;
	private final int WIDTH = 1024;
	private final int HEIGHT = 600;
	private final int FRAMERATE = 10;
	private final int DOT_RADIUS = 2;
	private final int CONSOLIDATED_DOT_RADIUS = 4;
	
	// configured in setup, psuedo-arguments
	private String DIRPATH;
	private String PACKET_MODE;
	private String STARTING_INPUT_STRING;
	private String ENDING_INPUT_STRING;
	private int ONE_DAY_IN_SECS;
	private int MAX_RESPONSE; // in ms
	private int WEB_MAX_RESPONSE; // in sec
	
	// Hookup to the MaxMind database.
	LookupService geoLookup;
	boolean dbConnected = false;
	
	// A clock for drawing items in a timely manner. *cue rimshot*
	private TimeStamp clock = null;
	
	// A LinkedList of Pins. These are all loaded in at the
	// beginning of the program, and are popped off and drawn if the
	// timestamp matches the simulated clock.
	private LinkedList<Pin> PinsToDraw;
	
	// An object that can be drawn on the map by Processing.
	private class Pin implements Comparable<Pin> {
		// For Processing.
		@SuppressWarnings("unused")
		PApplet parent;
		
		// Location and drawing related fields.
		@SuppressWarnings("unused")
		public PImage mapImage;
		public float x;
		public float y;
		
		// Consolidation boolean to determine drawing size.
		boolean consolidated;
		
		// Color. Used for drawing.		
		public int red;
		public int green;
		// int blue is excluded, as it's never modified.
		
		// Timestamp for drawing.
		public TimeStamp pinTime;
		
		// Last known address Pin, if unreached. Otherwise, null.
		private Pin LastKnown;
		
		// Primary constructor.
		public Pin(PApplet p, PImage mapImage, String[] pieces, boolean isWebData) {
			// Process the strings to get the IP address and the timestamp.
			// [0] ip, [1] timestamp, [2] response time, [3] last known ip, [4] application layer
			
			// Acquire the latitude and longitude.
			float[] latlon = getLatLonByIP(pieces[0]);
			
			// Initialize drawing stuff from latlon.
			this.parent = p;
			this.mapImage = mapImage;
			this.x = map(latlon[1], -180, 180, mapX, mapX+mapImage.width); // uses lon
			this.y = map(latlon[0], 90, -90, mapY, mapY+mapImage.height); // uses lat
			
			// Using this constructor means the pin is not consolidated.
			consolidated = false;
			
			// Get the response time.
			float response = parseFloat(pieces[2]);
			
			// Determine color based on response time.
			if (response == -1 || response > MAX_RESPONSE) { // unreached, red
				this.red = 0xff;
				this.green = 0x00;
			} else if (response == -2) { // last known for unreached, yellow
				this.red = 0xff;
				this.green = 0xff;
			} else if (isWebData) { // reached; web data
				this.red = (int) (0xff * (response / WEB_MAX_RESPONSE));
				this.green = (int) (0xff * ((WEB_MAX_RESPONSE - response) / WEB_MAX_RESPONSE));
			} else { // reached; intensity of green correlates to speed.
				this.red = (int) (0xff * (response / MAX_RESPONSE));
				this.green = (int) (0xff * ((MAX_RESPONSE - response) / MAX_RESPONSE));
			}
			
			// Set pinTime.
			pinTime = new TimeStamp(pieces[1]);
			
			// Set the LastKnown address Pin, if this is unreached.
			if (response == -1 && !isWebData) {
				// Creates a string array to be processed by the next Pin constructor.
				String[] LKPin = {pieces[3], pieces[1], "-2", "-1"};
				LastKnown = new Pin(p, mapImage, LKPin, false);
			} else {
				LastKnown = null;
			}
		}
		
		// Consolidation constructor.
		public Pin(PApplet p, PImage mapImage, float x, float y, float successRate, TimeStamp time) {
			
			// Initialize drawing stuff.
			this.parent = p;
			this.mapImage = mapImage;
			this.x = x;
			this.y = y;
			
			// Using this constructor means the pin is consolidated.
			consolidated = true;
			
			// Use the success rate to determine color.
			this.red = (int) (0xff * (1 - successRate));
			this.green = (int) (0xff * successRate);
			
			// Set pinTime.
			pinTime = time;
			
			// Set LastKnown to null.
			LastKnown = null;
		}
		
		// Well duh.
		public void drawSelf() {
			
			// REMOVE: print debugging.
			System.err.println(consolidated + "\t" + red + "\t" + green);
			
			// Draw the last known reached location, if unreached.
			if (LastKnown != null) {
				LastKnown.drawSelf();
			}
			
			// Actually draw the sucker.
			fill(red, green, 0x00);
			stroke(red, green, 0x00);
			
			// Draws consolidated pins:
			if (consolidated) {
				ellipse(this.x, this.y, CONSOLIDATED_DOT_RADIUS, CONSOLIDATED_DOT_RADIUS);
			
			// Draws single pins:
			} else {
				ellipse(this.x, this.y, DOT_RADIUS, DOT_RADIUS);
			}
		}

		// The default sorting method, sorting by Lat/Long.
		public int compareTo(Pin other) {
			if (x == other.x) {
				if (y == other.y) {
					return this.pinTime.compareTo(other.pinTime);
				} else {
					float dy = y - other.y;
					if (dy < 0) {
						return -1;
					} else {
						return 1;
					}
				}
			} else {
				float dx = x - other.x;
				if (dx < 0) {
					return -1;
				} else {
					return 1;
				}
			}
		}
	}
	
	// An object containing a year, month, date, hour, minute.
	// No support for seconds.
	// Used to mark PinCollections with their time, and to keep track of a
	// simulated clock.
	private class TimeStamp implements Comparable<TimeStamp> {
		// Year zero is the year 2000. Heresy indeed.
		// Discarded seconds.
		byte year; byte month; byte date; byte hour; byte minute;
		
		// Minutes and hours to increment the TimeStamp by on an advancement call.
		int minIncrement; int hrIncrement;

		// Constructs a timestamp from a particular format.
		// YEAR_MONTH_DATE-HOUR:MINUTE:SECOND
		// Does not allow advancement.
		public TimeStamp(String s) {
			this(s, 0);
		}
		
		// Constructs a timestamp from a particular format, and also
		// initializes the advancement mechanism.
		public TimeStamp(String s, int minInc) {
			String[] chopped = s.split("[_\\-:.]");
			for (int i = 0; i < 5; i++) {
				byte temp = Byte.parseByte(chopped[i]);
				switch (i) {
					case 0: year = temp; break;
					case 1: month = temp; break;
					case 2: date = temp; break;
					case 3: hour = temp; break;
					case 4: minute = temp; break;
				}
			}
			
			// Set up the increments.
			minIncrement = minInc;
			hrIncrement = 0;
			while (minIncrement >= 60) {
				minIncrement -= 60;
				hrIncrement++;
			}
		}
		
		// Advances the TimeStamp if being used as a clock.
		public void AdvanceClock() {
			minute += minIncrement;
			hour += hrIncrement;
			if (minute >= 60) {
				minute -= 60;
				hour++;
				if (hour >= 24) {
					hour -= 24;
					date++;
					if (ShouldAdvanceMonth()) {
						date = 1;
						month++;
						if (month > 12) {
							month = 1;
							year++;
						}
					}
				}
			}
		}
		
		private boolean ShouldAdvanceMonth() {
			return (date > 31 && ( (month < 8 && month % 2 == 1) || (month >= 8 && month % 2 == 0) ))
						|| (date > 30 && (month == 4 || month == 6 || month == 9 || month == 11))
						|| ((( date > 29 && year % 4 == 0 ) || ( date > 28 && year % 4 != 0 )) && month == 2);
		}
		
		// If the integers are equal, proceed to the next test.
		// If they are not, return the result of the test.
		public int compareTo(TimeStamp other) {
			if (year == other.year) {
				if (month == other.month) {
					if (date == other.date) {
						if (hour == other.hour) {
							return subCompare(minute, other.minute);
						}
						return subCompare(hour, other.hour);
					}
					return subCompare(date, other.date);
				}
				return subCompare(month, other.month);
			}
			return subCompare(year, other.year);
		}
					
		private int subCompare(byte ours, byte others) {
			if (ours < others) {
				return -1;
			} else if (ours > others) {
				return 1;
			} else { // (ours == others)
				return 0;
			}
		}
	}
	
	// Needs to initialize the clock to the time specified on command line.
	// Also load all the Pins into PinCollections.
	
	public void setup() {
		// connect to the database of geolocation data
		try {
			geoLookup = new LookupService("GeoLiteCity.dat");
			dbConnected = true;
		} catch(Exception e) {
			dbConnected = false;
		}
		
		// load the map image
		mapImage = loadImage(mapFilename);		
		size(WIDTH, HEIGHT);
		background(0x00, 0x55, 0xcc);
		
		// set the frame rate for Processing
		frameRate(FRAMERATE);
		
		// From TorArgs.ini in the local directory, set the global variables.
		try {
			ProcessArguments();
		} catch (FileNotFoundException ignored) {
			ignored.printStackTrace();
		}
		
		// Setup the clock at a rounded increment.
			// [data time] 		(1440 mins / day) divided by
			// [animation time] (FRAMERATE frames/second * INPUT seconds / day)
		int minIncr = 1440 / (FRAMERATE * ONE_DAY_IN_SECS);
		if (minIncr < 1) {
			minIncr = 1;
		}
		clock = new TimeStamp(STARTING_INPUT_STRING, minIncr);
		
		// Fetch and process files into Pins.
		try {
			Queue<Pin> temp = CreatePins();
			ConsolidatePins(temp);
		} catch (FileNotFoundException ignored) {
			ignored.printStackTrace();
		}
		
		// draw map
		image(mapImage, mapX, mapY);
	}
	
	// Pulls text from TorArgs.ini in the working directory, uses each
	// line as a global variable. Must be properly formatted.
	private void ProcessArguments() throws FileNotFoundException {

		// Setup the scanner on the file.
		Scanner theArgs = new Scanner(new File("TorArgs.ini"));
	
		// Set each line to global variables.
		DIRPATH = theArgs.nextLine(); // first line
		PACKET_MODE = theArgs.nextLine(); // second line
		STARTING_INPUT_STRING = theArgs.nextLine(); // third line
		ENDING_INPUT_STRING = theArgs.nextLine(); // fourth line
		ONE_DAY_IN_SECS = theArgs.nextInt(); // fifth line
		MAX_RESPONSE = theArgs.nextInt(); // sixth line
		WEB_MAX_RESPONSE = theArgs.nextInt(); // seventh line
	}
	
	// Make Pin objects from each line of the input files.
	// All .viz files are in a single location, labelled by timestamp.
	private Queue<Pin> CreatePins() throws FileNotFoundException {
		
		// Readies a PriorityQueue as temp storage.
		Queue<Pin> retVal = new PriorityQueue<Pin>();
		
		// Determine if all packets are wanted.
		boolean allPackets = PACKET_MODE.equalsIgnoreCase("ALL");
		
		// Open the directory.
		File measures = new File(DIRPATH);
		
		// This filter returns the directories that are equal to or
		// after the command line arg starting timestamp.
		FileFilter filter = new FileFilter() {
			public boolean accept(File file) {
				return file.getName().compareTo(STARTING_INPUT_STRING) >= 0 &&
					   file.getName().compareTo(ENDING_INPUT_STRING) <= 0;
			}
		};

		// For each file in the array:
		File[] vizFiles = measures.listFiles(filter);		
		for (int i = 0; i < vizFiles.length; i++) {

			// Create a new scanner.
			Scanner scotty;
			try {
			    scotty = new Scanner(vizFiles[i]);
			} catch(FileNotFoundException e) {
			    e.printStackTrace();
			    continue;
			}
			
			// If the file is empty:
			if (!scotty.hasNextLine()) {
				
				// Continue to the next one.
				continue;
			}
			
			// Read in a line of data.
			// [0] ip, [1] timestamp, [2] response time, [3] last known ip, [4] application layer
			String[] currPin = scotty.nextLine().split("\t");
			
			// Get the last index of the array.
			int lastIndex = currPin.length - 1;
			
			// If it's Web data, set the boolean, reconfigure it.
			boolean isWebData = currPin[lastIndex].equalsIgnoreCase("WEB");
			
			// Add the pin to the list.
			retVal.add(new Pin(this, mapImage, currPin, isWebData));
				
			// Running until there are no more lines in the file:
			while(scotty.hasNextLine()) {
				
				// Get the next line.
				currPin = scotty.nextLine().split("\t");
				
				// Bad data, throw it out.
				if (currPin.length < 5 && !isWebData) {
					currPin = scotty.nextLine().split("\t");
				
				// If the application layer matches or ALL pins are wanted,
				// create a Pin from currPin, put it in the list.
				
				// Iterate through the next several pins of the same timestamp,
				// as they will also have the same application layer type.
				} else if (currPin[lastIndex].equalsIgnoreCase(PACKET_MODE) || allPackets) {
					
					// Add the Pin to the queue.
					retVal.add(new Pin(this, mapImage, currPin, isWebData));
				}
			}
		}
		return retVal;
	}
	
	// Takes the list of Pins and consolidates those with the same LatLong,
	// then reorders them by timestamp in a list for drawing.
	public void ConsolidatePins(Queue<Pin> temp) {
		
		// Create the pin container.
		PinsToDraw = new LinkedList<Pin>();
		
		// Get the very first pin.
		Pin leader = temp.remove();
		
		// While the list is not empty:
		while (!temp.isEmpty()) {
			
			// Data about reached/unreached.
			int reached = 0; 
			int total = 1;
			
			// Check if leader was reached.
			if (leader.red != 0xFF && leader.green != 0x00) {
				reached++;
			}
			
			// Repeatedly:
			while (!temp.isEmpty()) {
				
				// Get the next pin.
				Pin current = temp.remove();
				
				// If their LatLongs match:
				if (current.x == leader.x && current.y == leader.y) {
					
					// Check if current was reached:
					if (current.red != 0xFF && current.green != 0x00) {
						reached++;
					}
					
					// Increment total.
					total++;
					
				// Otherwise:
				} else {
					
					// If no LatLongs matched leader:
					if (total == 1) {
						
						// Add leader to the PinsToDraw list.
						PinsToDraw.add(leader);
					
					// Otherwise, construct a Consolidated Pin, push it onto the list.
					} else {
						PinsToDraw.add(new Pin(this, mapImage, leader.x, leader.y, (float)reached / total, leader.pinTime));
					}
					
					// REMOVE: error printing
					System.err.println(reached + " / " + total);
					
					// Save current as the next leader, then break the loop.
					leader = current;
					break;
				}
			}
		}
		
		// Sort PinsToDraw by TimeStamp.
		Collections.sort(PinsToDraw, new Comparator<Pin>() {
			public int compare(Pin one, Pin two) {
				return one.pinTime.compareTo(two.pinTime);
			}
		});
	}
			
	
	// Called by Processing, FRAMERATE number of times a second.
	// Check the pins in the PriorityQueue, put the appropriate ones
	// into the LinkedList. Draw all the pins in the LinkedList.
	public void draw() {
		// Advance the clock by a precalculated amount of time.
		clock.AdvanceClock();
			
		// Keep drawing everything in the Priority Queue.
		while (!PinsToDraw.isEmpty() && PinsToDraw.peek().pinTime.compareTo(clock) <= 0) {
			PinsToDraw.remove().drawSelf();
		}
		
		// When the list of things to draw is empty:
		if (PinsToDraw.isEmpty()) {
			
			// Save an image if the start and end strings are equal.
			// Intention: When PNGs are produced, only a single viz file is
			// chosen for drawing.
			if (STARTING_INPUT_STRING.equalsIgnoreCase(ENDING_INPUT_STRING)) {
				save(PACKET_MODE + ".png");
			}
		
			// Exit the program.
			exit();
		}
	}
	
	// Self-explanatory.
	float[] getLatLonByIP(String ip) {
		float lat = 1000;
		float lon = 1000;
		if (!dbConnected) {
			return null;
		}

		Location loc = geoLookup.getLocation(ip);
		if (loc != null) {
			lat = loc.latitude;
			lon = loc.longitude;
		}
		
		float[] latlon = {lat, lon};
		return latlon;
	}
}

