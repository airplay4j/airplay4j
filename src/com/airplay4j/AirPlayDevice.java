package com.airplay4j;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.jmdns.JmDNS;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;

/**
 * AirPlayDevice represents a network attached device capable of being an AirPlay.
 * In particular, this class was tested on an AppleTV3.  Other AirPlay devices are untested and may or may not work.
 * 
 * @author Jim
 */
public class AirPlayDevice
{
	/** This shared jmDNS service is used to listen for Apple TV devices on the network **/
	static JmDNS jmDNS;

	private String name;
	private InetAddress address;
	private int port;
	
	DefaultHttpClient httpclient = new DefaultHttpClient();

	public AirPlayDevice(String name, InetAddress address, int port)
	{
		this.name = name;
		this.address = address;
		this.port = port;
	}

	/**
	 * Play a multimedia file.  The file must be compatible with the AirPlayDevice (eg. MP3 files).  Blocks until media is complete.
	 * This method must bind to a local port in order for the AirPlay device to be able to make a request for the media file.
	 * If the AppleTV device consistently hangs or fails to load the media file, you may want to check your local firewall configuration.
	 **/
	public void play(File audio) throws Exception
	{
		// Create a temporary directory, copy the audio file into that directory.
		// This directory will serve as the webapp root (base directory being served up via http)
		File temporaryBase;
		try
		{
			temporaryBase = File.createTempFile("deleteme-", "");
			temporaryBase.delete();
			temporaryBase.mkdir();
			FileUtils.copyFileToDirectory(audio, temporaryBase);
		}
		catch(IOException e)
		{
			throw new RuntimeException(e);
		}

		// Initialize a simple webapp context
		final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
		context.setContextPath("/");
		context.setResourceBase(temporaryBase.getCanonicalPath());
		context.addServlet(DefaultServlet.class, "/");

		// Start an http server
		Server server = new Server();
		Connector connector = new SelectChannelConnector();
		server.addConnector(connector);
		server.setHandler(context);
		server.start();

		// Create a URI for the content we're serving to the AppleTV.  Direct the AppleTV to start playing the content at that URI.
		URI uri = new URI("http://192.168.1.111:" + connector.getLocalPort() + "/" + URLEncoder.encode(audio.getName(), "UTF-8").replaceAll("\\+", "%20"));
		startStream(uri.toASCIIString());

		// Allow the file to buffer & play until it finishes
		while(true)
		{
			Thread.sleep(2000);

			try
			{
				Map<String, String> state = getCurrentPlayState();
				System.out.println(state);
				if(Double.parseDouble(state.get("position")) != 0 && Double.parseDouble(state.get("position")) >= Double.parseDouble(state.get("duration")) - 1) break;
				// HELPFUL FOR DEBUGGING, BREAK EARLY: 	if(Double.parseDouble(state.get("position")) >= 5) break;
			}
			catch(Throwable e)
			{
				e.printStackTrace();
			}
		}

		// Stop the stream & media server
		stopStream();
		server.stop();
	}

	/** Send a message that orders the AppleTV to start streaming the specified URL **/
	protected void startStream(String streamURL) throws ClientProtocolException, IOException
	{
	    HttpPost request = new HttpPost("http:/"+getAddress()+":"+getPort()+"/play");
	    request.setEntity(new StringEntity("Content-Location: "+streamURL+"\nStart-Position: 0.000000\n\n"));
	    HttpResponse httpResponse = httpclient.execute(request);
		EntityUtils.consume(httpResponse.getEntity());
		if(httpResponse.getStatusLine().getStatusCode() != 200)
			throw new Error("Bad response code: " + httpResponse.getStatusLine().getStatusCode());
	}

	/** Stop the current media file. **/
	protected void stopStream() throws ClientProtocolException, IOException
	{
	    HttpPost request = new HttpPost("http:/"+getAddress()+":"+getPort()+"/stop");
	    request.setEntity(null);
	    HttpResponse response = httpclient.execute(request);
		EntityUtils.consume(response.getEntity());
		if(response.getStatusLine().getStatusCode() != 200)
			throw new Error("Bad response code: " + response.getStatusLine().getStatusCode());
	}

	/** Returns the current play state (scrubber position and duration) **/
	protected Map<String, String> getCurrentPlayState() throws IllegalStateException, IOException
	{
        HttpGet request = new HttpGet("http:/"+getAddress()+":"+getPort()+"/scrub");
        HttpResponse response = httpclient.execute(request);
        HttpEntity entity = response.getEntity();
		if(response.getStatusLine().getStatusCode() != 200)
		{
			 EntityUtils.consume(response.getEntity());
			throw new Error("Bad response code: " + response.getStatusLine().getStatusCode());
		}

		// Iterate through each line, key-value pairs are seperated by a colon.
		Map<String, String> responseMap = new HashMap<String, String>();
		for(String line : IOUtils.readLines(entity.getContent()))
			responseMap.put(line.split(": ")[0], line.split(": ")[1]);
		 EntityUtils.consume(response.getEntity());
		return responseMap;
	}

	/** Returns the name of the airplay device **/
	public String getName()
	{
		return name;
	}

	/** Returns the ip address of the airplay device **/
	public InetAddress getAddress()
	{
		return address;
	}

	/** Returns the port of the airplay device **/
	public int getPort()
	{
		return port;
	}

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "(" + name + ", " + address + ", " + port + ")";
	}
	
	@Override
	public int hashCode()
	{
		return address.hashCode()+port;
	}
}
