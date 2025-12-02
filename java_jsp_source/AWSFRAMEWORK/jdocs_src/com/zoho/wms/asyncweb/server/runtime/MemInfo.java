//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;

// Server common import
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.adventnet.wms.servercommon.util.WMSUtil;

import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.AsyncWebStatsManager;

public class MemInfo extends WmsRuntime
{
	private long totalmem = 0l;
	private long usedmem = 0l;
	private long maxmem = 0l;
	private long maxmemtime = 0l;

	public MemInfo()
	{
		totalmem = Runtime.getRuntime().totalMemory();
		long temp = (totalmem - Runtime.getRuntime().freeMemory());
		usedmem = (temp > 0) ? temp : usedmem;
		maxmem = usedmem;
		maxmemtime = System.currentTimeMillis();
	}

	public Hashtable getInfo(Hashtable params)
	{

		long temp = (totalmem - Runtime.getRuntime().freeMemory());
		usedmem = (temp > 0) ? temp : usedmem;
		if(usedmem > maxmem)
		{
			maxmem = usedmem;
			maxmemtime = System.currentTimeMillis();
		}


		Hashtable details = new Hashtable();
		if(params.get(AWSConstants.H)==null)
		{
			details.put(AWSConstants.TOTAL, ""+totalmem);
			details.put(AWSConstants.USED, ""+usedmem);
			details.put(AWSConstants.MAX, ""+maxmem);
			details.put(AWSConstants.MAXTIME, ""+maxmemtime);
		}
		else
		{
			details.put(AWSConstants.TOTAL, WMSUtil.HumanReadable.getMemSize(totalmem));
			details.put(AWSConstants.USED, WMSUtil.HumanReadable.getMemSize(usedmem));
			details.put(AWSConstants.MAX, WMSUtil.HumanReadable.getMemSize(maxmem));
			details.put(AWSConstants.MAXTIME, WMSUtil.HumanReadable.getTime(maxmemtime));
		}

		return details;
	}

	protected void periodicCollector(long timeElapsed)
	{
		try
		{
			long temp = (totalmem - Runtime.getRuntime().freeMemory());
			usedmem = (temp > 0) ? temp : usedmem;
			if(usedmem > maxmem)
			{
				maxmem = usedmem;
				maxmemtime = System.currentTimeMillis();
			}
			Hashtable meminfo = new Hashtable();
			meminfo.put(AWSConstants.TOTAL_MEMORY, totalmem);
			meminfo.put(AWSConstants.USED_MEMORY, usedmem);
			AsyncWebStatsManager.onMemoryStats(meminfo);
		}
		catch(Exception e)
		{
		}
		
	}

}
