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
	
	// configured in setup
	private String DIRPATH;
	private String PACKET_MODE;
	private String STARTING_INPUT_STRING;
	private String ENDING_INPUT_STRING;
	private int INPUT_ONE_DAY_IN_SECS;
	
	// Hookup to the MaxMind database.
	LookupService geoLookup;
	boolean dbConnected = false;
	
	// A clock for drawing items in a timely manner. *cue rimshot*
	private TimeStamp clock = null;
	
	// A priority queue of Pins. These are all loaded in at the
	// beginning of the program, and are popped off and drawn if the
	// LinkedList's timestamp matches the simulated clock.
	private Queue<PinCollection> PinsToDraw;

	// An object that keep together bunches of Pins and labels them with
	// a common timestamp. More memory efficient, nice encapsulation.
	// Lists should be LinkedLists, because deleting is important.
	private class PinCollection implements Comparable<PinCollection> {
		// Tracks the PinCollection's internal time.
		private TimeStamp pinTime;
		
		// Stores all the Pins in a queue.
		private Queue<Pin> pins;
		
		// Constructor makes a TimeStamp from a string, 
		public PinCollection(String s, Queue<Pin> newPins) {
			pinTime = new TimeStamp(s);
			pins = newPins;
		}
		
		// Iterates through all the Pins and draws them all.
		public void drawThesePins() {
			
			// Drawing everything in the Queue.
			while (!pins.isEmpty()) {
				pins.remove().drawSelf();
			}
		}
		
		// Return the pinTime.
		public TimeStamp getPinTime() {
			return pinTime;
		}
		
		// Compare method to implement Comparable. Calls TimeStamp comparable.
		public int compareTo(PinCollection other) {
			return pinTime.compareTo(other.pinTime);
		}
	}
	
	// An object that can be drawn on the map by Processing.
	private class Pin {
		// For Processing.
		@SuppressWarnings("unused")
		PApplet parent;
		
		// Location and drawing related fields.
		@SuppressWarnings("unused")
		public PImage mapImage;
		public float x;
		public float y; 
		
		// Response time. Unreached = -1, "last known" Pin = -2.
		// Used for drawing.
		private float response;
		
		// Last known address Pin, if unreached. Otherwise, null.
		private Pin LastKnown;
		
		// Constructor.
		public Pin(PApplet p, PImage mapImage, String[] pieces) {
			// Process the strings to get the IP address and the timestamp.
			// [0] ip, [1] timestamp, [2] response time, [3] last known ip, [4] application layer
			
			// Acquire the latitude and longitude.
			float[] latlon = getLatLonByIP(pieces[0]);
			
			// Initialize drawing stuff from latlon.
			this.parent = p;
			this.mapImage = mapImage;
			this.x = map(latlon[1], -180, 180, mapX, mapX+mapImage.width); // uses lon
			this.y = map(latlon[0], 90, -90, mapY, mapY+mapImage.height); // uses lat
			
			// Set the response time.
			response = parseFloat(pieces[2]);
			
			// Set the LastKnown address Pin, if this is unreached.
			if (response == -1) {
				// Creates a string array to be processed by the next Pin constructor.
				String[] LKPin = {pieces[3], pieces[1], "-2", "-1"};
				LastKnown = new Pin(p, mapImage, LKPin);
			} else {
				LastKnown = null;
			}
		}
		
		// Well duh.
		public void drawSelf() {
			
			// Draw the last known reached location, if unreached.
			if (LastKnown != null) {
				LastKnown.drawSelf();
			}
			
			// Determine color based on response time.
			int red = 0x00;
			int green = 0x00;
			int blue = 0x00;
			if (response == -1) { // unreached
				red = 0xff;
			} else if (response == -2) { // last known for unreached
				red = 0xff;
				green = 0xff;
			} else { // reached (can modify for speed later)
				green = 0xff;
			}
			
			// Actually draw the sucker.
			fill(red, green, blue);
			stroke(red, green, blue);
			ellipse(this.x, this.y, DOT_RADIUS, DOT_RADIUS);
		}
	}
	
	// An object containing a year, month, date, hour, minute, and second.
	// Used to mark PinCollections with their time, and to keep track of a
	// simulated clock.
	private class TimeStamp implements Comparable<TimeStamp> {
		// Year zero is the year 2000. Heresy indeed.
		// Discarded seconds.
		int year; int month; int date; int hour; int minute;
		
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
			String[] chopped = s.split("[_\\-:]");
			for (int i = 0; i < 4; i++) {
				int temp = parseInt(chopped[i]);
				switch (i) {
					case 0: year = temp; break;
					case 1: month = temp; break;
					case 2: date = temp; break;
					case 3: hour = temp; break;
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
					
		private int subCompare(int ours, int others) {
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
		int minIncr = 1440 / (FRAMERATE * INPUT_ONE_DAY_IN_SECS);
		clock = new TimeStamp(STARTING_INPUT_STRING, minIncr);
		
		// Fetch and process files into Pins.
		try {
			CreatePins();
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
		INPUT_ONE_DAY_IN_SECS = theArgs.nextInt(); // fifth line
	}
	
	// Make Pin objects from each line of the input files.
	// All .viz files are in a single location, labelled by timestamp.
	private void CreatePins() throws FileNotFoundException {
		
		// Create the pin containers.
		PinsToDraw = new PriorityQueue<PinCollection>();
		
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
		
		// Determine if all packets are wanted.
		boolean allPackets = PACKET_MODE.equalsIgnoreCase("ALL");

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
			
			// Create a new list of Pins.
			Queue<Pin> pinJar = new LinkedList<Pin>();
				
			// Read in a line of data.
			// [0] ip, [1] timestamp, [2] response time, [3] last known ip, [4] application layer
			String[] pieces = scotty.nextLine().split("\t");
			
			// If it's Web data, reconfigure it.
			if (pieces.length < 5) {
				String[] webArray = {pieces[0], pieces[1], "0", "0", pieces[3]};
				pieces = webArray;
			}
			
			// If the application layer matches or ALL pins are wanted,
			// create a Pin from the next line, put it in the list.
			// Make pins from the rest of the file.
			if (pieces[4].equalsIgnoreCase(PACKET_MODE) || allPackets) {
				pinJar.add(new Pin(this, mapImage, pieces));
				
				// Running until there are no more lines in the file:
				while(scotty.hasNextLine()) {
					
					// Read in a line of data.
					// [0] ip, [1] timestamp, [2] response time, [3] last known ip, [4] application layer
					pieces = scotty.nextLine().split("\t");
					
					// If it's Web data, reconfigure it.
					if (pieces.length < 5) {
						String[] webArray = {pieces[0], pieces[1], "0", "0", pieces[3]};
						pieces = webArray;
					}
					
					// Add it into the container.
					pinJar.add(new Pin(this, mapImage, pieces));
				}
				
			// Otherwise, continue to the next file.	
			} else {
				continue;
			}
			
			// Package the Pins in a PinCollection, put it in the PriorityQueue.
			// Ensures usage of currPin's timestamp, which is the same
			// as the rest of the list.
			PinsToDraw.add(new PinCollection(vizFiles[i].getName(), pinJar));
		}
	}
	
	// Called by Processing, FRAMERATE number of times a second.
	// Check the pins in the PriorityQueue, put the appropriate ones
	// into the LinkedList. Draw all the pins in the LinkedList.
	public void draw() {
		// Advance the clock by a precalculated amount of time.
		clock.AdvanceClock();
		
		// Now we have to update the Pins.	
		// Keep drawing everything in the Priority Queue.
		while (!PinsToDraw.isEmpty() && PinsToDraw.peek().getPinTime().compareTo(clock) <= 0) {
			PinsToDraw.remove().drawThesePins();
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

	// Removed main method, because it's handled by Runner, I think.
	// Easy to reinstate from NetViz if needed.
}

