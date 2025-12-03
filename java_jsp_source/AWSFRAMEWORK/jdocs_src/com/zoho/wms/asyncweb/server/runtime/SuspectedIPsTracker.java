//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// java import
import java.util.Hashtable;
import java.util.Map;

// Server common import
import com.adventnet.wms.servercommon.FixedHashMap;
import com.adventnet.wms.servercommon.SuspectCodes;


public class SuspectedIPsTracker
{
	private static FixedHashMap<String, Integer> ips = new FixedHashMap(50);

	public static void heavyRead(String ip)
	{
		ips.put(ip, SuspectCodes.HEAVY_READ);
	}

	public static void heavyWrite(String ip)
	{
		ips.put(ip, SuspectCodes.HEAVY_WRITE);
	}

	public static void malformedHeader(String ip)
	{
		ips.put(ip, SuspectCodes.MALFORMED_HEADER);
	}

	public static void unknown(String ip)
	{
		ips.put(ip, SuspectCodes.UNKNOWN);
	}

	public static void clear()
	{
		ips.clear();
	}

	public static Hashtable get()
	{
		return new Hashtable((Map) ips);
	}
	
}
