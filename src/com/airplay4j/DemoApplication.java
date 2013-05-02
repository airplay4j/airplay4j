package com.airplay4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.jmdns.JmDNS;


/**
 * DemoApplication is a simple program plays media files to an AirPlay device (such as an AppleTV3).
 * This application/library has been tested on Linux with an AppleTV3.  There is no known reason for this application
 * to be platform dependent, and should (in theory) work on Windows/Mac/Android with any AirPlay capable device.
 * 
 * Usage: java com.ap4j.DemoApplication [DEVICE_NAME] [MP3_FILES...]
 * Example: java com.ap4j.DemoApplication "Living Room" ~/Music/StairwayToHeaven.mp3
**/
public class DemoApplication
{
	/** Amount of time to wait for specified device to appear on the network **/
	protected static final long MAXIMUM_WAIT_TIME = 3*1000;
	
	public static void main(String[] args) throws Exception
	{
		// Simple sanity check on arguments, print usage information on error.
		if(args.length < 2) { printUsage(); return; }
		
		// Find the device, waiting for the device to appear on the network if necessary.
		AirPlayDevice device = getDevice(args[0], MAXIMUM_WAIT_TIME);
		
		// Iterate through the remaining arguments, attempting to play each one
		for(int i = 1; i < args.length; i++)
			playRecursive(device, new File(args[i]));
	}
	
	/**
	 * The user ran the application with invalid parameters.  Print usage information.
	 */
	public static void printUsage() throws InterruptedException, IOException
	{
		System.err.println("USAGE ERROR: Incorrect command line arguments");

		System.err.println("Usage: DemoApplication [DEVICE_NAME] [MP3_FILES]");

		System.err.println("Searching for valid device names (for your reference)... please wait...");

		JmDNS jmdns = null;
		AirPlayDeviceManager manager = null;
		try
		{
			jmdns = JmDNS.create();
			manager = new AirPlayDeviceManager(jmdns);
			
			Set<AirPlayDevice> devicesPrinted = new HashSet<AirPlayDevice>();
			
			long endTime = System.currentTimeMillis()+MAXIMUM_WAIT_TIME;
			while(System.currentTimeMillis() < endTime)
				synchronized(manager)
				{
					for(AirPlayDevice device : manager.getAllDevices())
						if(devicesPrinted.add(device))
							System.err.println("   * "+device.getName());
					
					manager.wait(Math.max(0, endTime-System.currentTimeMillis()));
				}
		}
		finally
		{
			if(jmdns != null) jmdns.close();
			if(manager != null) manager.close();
		}
	}
	
	/**
	 * Lookup an AirPlay device by name, waiting a maximum of waitTime (milliseconds) for the device to appear on the network.
	 */
	public static AirPlayDevice getDevice(String name, long waitTime) throws InterruptedException, IOException
	{
		JmDNS jmdns = null;
		AirPlayDeviceManager manager = null;
		try
		{
			jmdns = JmDNS.create();
			manager = new AirPlayDeviceManager(jmdns);
			
			AirPlayDevice device = null;
			
			long startTime = System.currentTimeMillis();
			while(device == null && System.currentTimeMillis() < startTime+waitTime)
				synchronized(manager)
				{
					try { return manager.getDeviceByName(name); }
					catch(NoSuchElementException e) { manager.wait(Math.max(0, startTime+waitTime-System.currentTimeMillis())); }
				}
			throw new NoSuchElementException("Device ("+name+") could not be located on network (devices: "+manager.getAllDevices()+").");
		}
		finally
		{
			if(jmdns != null) jmdns.close();
			if(manager != null) manager.close();
		}
	}
	
	/** Plays a media file (or recursively plays a directory full of media) on the specified device. **/
	public static void playRecursive(AirPlayDevice device, File file) throws Exception
	{
		if(file == null || !file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
		if(file.isDirectory())
			for(File child : file.listFiles())
				playRecursive(device, child);
		else device.play(file);
	}
}
