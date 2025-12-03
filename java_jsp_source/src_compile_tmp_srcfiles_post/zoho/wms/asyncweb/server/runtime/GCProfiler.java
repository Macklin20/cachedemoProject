//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.List;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.logging.Level;
import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;

// Server common import
import com.adventnet.wms.servercommon.runtime.WmsRuntime;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.AsyncWebStatsManager;

public class GCProfiler extends WmsRuntime
{
	private Hashtable<String, String> consDetails = new Hashtable();	
	private static AsyncLogger gclogger = new AsyncLogger("gclogger");//No I18N

	public Hashtable getInfo(Hashtable params)
	{
		return consDetails;
	}

	protected void periodicCollector(long timeElapsed)
	{
		try
		{
			List<GarbageCollectorMXBean> gcbeans = ManagementFactory.getGarbageCollectorMXBeans();
			Iterator it=gcbeans.iterator();
			while(it.hasNext())
			{
				try
				{
					GarbageCollectorMXBean gcbean = (GarbageCollectorMXBean)it.next();
					long ccount = gcbean.getCollectionCount();
					long ctime = gcbean.getCollectionTime();
					long inttime = ctime;
					long intcnt = ccount;
					String gcname = gcbean.getName();
					String prevstats = consDetails.get(gcname);
					if(prevstats != null)
					{
						intcnt = ccount - (Long.parseLong(prevstats.split("-")[0]));
						inttime =  ctime - (Long.parseLong(prevstats.split("-")[1]));
					}
					if(intcnt>0)
					{
						gclogger.log(Level.INFO,gcname+" - Count ["+intcnt+"] Time ["+inttime+"]",GCProfiler.class.getName(),AWSLogMethodConstants.PERIODIC_COLLECTOR);//No I18N
					}
					consDetails.put(gcname, ccount+"-"+ctime);

				}
				catch(Exception e)
				{
				}
			}
			AsyncWebStatsManager.onGCStats(consDetails);
		}
		catch(Exception e)
		{
		}

	}

}
