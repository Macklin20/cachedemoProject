//$Id$
package com.zoho.wms.asyncweb.server.runtime;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Properties;

import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.AWSConstants;

import com.zoho.instrument.Stats;

public class WmsRuntimeCounters
{
	public static Hashtable<String,DomainHit> domainhitmap = new Hashtable();
	private static Hashtable<Integer,Long> portaccessmap = new Hashtable();
	private static boolean domainhitmaploaded = false;
	public static Hashtable<String,AtomicLong> pushReceivedMap = new Hashtable();

	private static void loadDomainHitMap()
	{
		try
		{
			Properties prop = Util.getProperties(ConfManager.getDomainMap());
			Enumeration propenum = prop.propertyNames();
			while(propenum.hasMoreElements())
			{
				String domainname = (String)propenum.nextElement();
				if(!domainhitmap.contains(domainname))
				{
					domainhitmap.put(domainname,new DomainHit());
				}
			}
		}
		catch(Exception ex)
		{
		}	
		domainhitmap.put(AWSConstants.DEFAULT,new DomainHit());
		domainhitmap.put(AWSConstants.OTHERS,new DomainHit());
		domainhitmaploaded = true;
	}
	
	public static boolean getDomainHitMapLoaded()
	{
		return domainhitmaploaded;
	}
		
	public static void setDomainHitMapLoaded(boolean mode)
	{
		domainhitmaploaded = false;
	}

	public static DomainHit getDomainHit(String domain)
	{
		if(!domainhitmaploaded)
		{
			loadDomainHitMap();
		}

		if(domainhitmap.get(domain) == null)
		{
			if(!domain.matches("((\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5})|(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}))"))
			{
				return domainhitmap.get(AWSConstants.OTHERS);
			}
		}

		return  domainhitmap.get(domain);	
	}

	public static void resetHits()
	{
		Enumeration domainhitenum = domainhitmap.keys();
		while(domainhitenum.hasMoreElements())
		{
			DomainHit dh = (DomainHit)domainhitenum.nextElement();
			dh.resetHits();
		}
	}

	public static void updatePortAccess(Integer port, Long milliseconds)
	{
		portaccessmap.put(port, milliseconds);
	}

	public static Long getPortAccess(Integer port)
	{
		return portaccessmap.get(port);
	}

}
