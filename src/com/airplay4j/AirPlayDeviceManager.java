package com.airplay4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

/**
 * AirPlayDevice represents a network attached device capable of being an AirPlay
 * 
 * @author Jim
 */
public class AirPlayDeviceManager implements ServiceListener, Closeable
{
	JmDNS jmDNS;
	List<AirPlayDevice> devices = new ArrayList<AirPlayDevice>();
	
	/**
	 * Returns a collection of AirPlay devices, as currently observed by the manager.
	 * Network devices may take a few seconds before being found (after manager is initialized).
	 * Threads may wait on this manager, and will be notified when a new device is detected.
	 */
	public synchronized Collection<AirPlayDevice> getAllDevices()
	{
		List<AirPlayDevice> devices = new ArrayList<AirPlayDevice>();
		devices.addAll(this.devices);
		return devices;
	}
	
	/**
	 * A convenience accessor method will search the output of getAllDevices() and return the first device with the specified name.
	 */
	public synchronized AirPlayDevice getDeviceByName(String name)
	{
		for(AirPlayDevice device : getAllDevices())
			if(name.equals(device.getName()))
				return device;
		throw new NoSuchElementException("'"+name+"' could not be found on the network.  It may take a few seconds to detect all devices via mDNS");
	}

	public AirPlayDeviceManager(JmDNS jmDNS)
	{
		this.jmDNS = jmDNS;
		jmDNS.addServiceListener("_airplay._tcp.local.", this);
	}

	@Override
	public synchronized void serviceAdded(ServiceEvent serviceEvent)
	{
		ServiceInfo serviceInfo = serviceEvent.getInfo();
		if(serviceInfo == null || serviceInfo.getInetAddress() == null)
			serviceInfo = serviceEvent.getDNS().getServiceInfo(serviceEvent.getType(), serviceEvent.getName(), 2000);
		devices.add(new AirPlayDevice(serviceEvent.getName(), serviceInfo.getInetAddress(), serviceInfo.getPort()));
		
		notifyAll();
	}

	@Override
	public synchronized void serviceRemoved(ServiceEvent serviceEvent)
	{
		ServiceInfo serviceInfo = serviceEvent.getInfo();
		if(serviceInfo == null || serviceInfo.getInetAddress() == null)
			serviceInfo = serviceEvent.getDNS().getServiceInfo(serviceEvent.getType(), serviceEvent.getName(), 2000);

		// Iterate through the devices, removing any matching device
		Iterator<AirPlayDevice> deviceIterator = devices.iterator();
		while(deviceIterator.hasNext())
		{
			// Get the device, skip if the fields don't match the device being removed.
			AirPlayDevice device = deviceIterator.next();
			if(!device.getAddress().equals(serviceInfo.getInetAddress())) continue;
			if(device.getPort() != serviceInfo.getPort()) continue;
			if(device.getName().equals(serviceInfo.getName())) continue;
			
			// The device matched, remove it from the device list
			deviceIterator.remove();
		}
		
		notifyAll();
	}

	@Override
	public void serviceResolved(ServiceEvent serviceEvent) { /* ignore */ }

	@Override
	public String toString()
	{
		return getClass().getSimpleName() + "(" + devices + ")";
	}

	@Override
	public void close() throws IOException
	{
		jmDNS.close();
	}
}
