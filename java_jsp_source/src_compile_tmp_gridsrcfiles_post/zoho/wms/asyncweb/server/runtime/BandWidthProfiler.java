//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;
import java.util.logging.Level;

// Server common import 
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.adventnet.wms.servercommon.util.WMSUtil;

// Wms import
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.AsyncWebStatsManager;

public class BandWidthProfiler extends WmsRuntime
{
	private static AsyncLogger logger = new AsyncLogger(BandWidthProfiler.class.getName());
	
	protected void periodicCollector(long timeElapsed)
	{
		try
		{
			Hashtable exthttpdetails = new Hashtable();
               		long exthttpread = BandWidthTracker.getExternalHttpRead();
                	exthttpdetails.put(AWSConstants.TOTALREAD, ""+exthttpread);
                	long exthttpwrite = BandWidthTracker.getExternalHttpWrite();
                	exthttpdetails.put(AWSConstants.TOTALWRITE, ""+exthttpwrite);

                	Hashtable exthttpsdetails = new Hashtable();
                	long exthttpsread = BandWidthTracker.getExternalHttpsRead();
                	exthttpsdetails.put(AWSConstants.TOTALREAD, ""+exthttpsread);
                	long exthttpswrite = BandWidthTracker.getExternalHttpsWrite();
                	exthttpsdetails.put(AWSConstants.TOTALWRITE, ""+exthttpswrite);

                	Hashtable external = new Hashtable();
                	external.put(AWSConstants.HTTP, exthttpdetails);
                	external.put(AWSConstants.HTTPS, exthttpsdetails);

        		Hashtable info = new Hashtable();
                	info.put(AWSConstants.EXTERNAL, external);

                	BandWidthTracker.resetAll();
        		AsyncWebStatsManager.onBandwidthStats(info);
		}
		catch(Exception e)
		{
		}

	}

	public Hashtable getInfo(Hashtable params)
	{

		if(params != null && params.get(AWSConstants.RESET_AND_GETSTATS) != null)
		{
			boolean resetandgetstats = Boolean.parseBoolean(""+params.get(AWSConstants.RESET_AND_GETSTATS)); 
			if(resetandgetstats)
			{
				return resetAndGetInfo();
			}
		}

		Hashtable exthttpdetails = new Hashtable();
		long time = (getTimeElapsedSinceToday() / (1000l * 60l));
		long exthttpread = BandWidthTracker.getExternalHttpRead();
		if(params.get(AWSConstants.H)==null)
		{
			exthttpdetails.put(AWSConstants.TOTALREAD, ""+exthttpread);
			exthttpdetails.put(AWSConstants.READRATE_PER_MIN, ""+(exthttpread/time));
		}
		else
		{
			exthttpdetails.put(AWSConstants.TOTALREAD, WMSUtil.HumanReadable.getCountSize(exthttpread));
			exthttpdetails.put(AWSConstants.READRATE_PER_MIN, WMSUtil.HumanReadable.getCountSize(exthttpread/time));
		}
		long exthttpwrite = BandWidthTracker.getExternalHttpWrite();
		if(params.get(AWSConstants.H)==null)
		{
			exthttpdetails.put(AWSConstants.TOTALWRITE, ""+exthttpwrite);
			exthttpdetails.put(AWSConstants.WRITERATE_PER_MIN, ""+(exthttpwrite/time));
		}
		else
		{
			exthttpdetails.put(AWSConstants.TOTALWRITE, WMSUtil.HumanReadable.getCountSize(exthttpwrite));
			exthttpdetails.put(AWSConstants.WRITERATE_PER_MIN, WMSUtil.HumanReadable.getCountSize(exthttpwrite/time));
		}

		Hashtable exthttpsdetails = new Hashtable();
		long exthttpsread = BandWidthTracker.getExternalHttpsRead();
		if(params.get(AWSConstants.H)==null)
		{
			exthttpsdetails.put(AWSConstants.TOTALREAD, ""+exthttpsread);
			exthttpsdetails.put(AWSConstants.READRATE_PER_MIN, ""+(exthttpsread/time));
		}
		else
		{
			exthttpsdetails.put(AWSConstants.TOTALREAD, WMSUtil.HumanReadable.getCountSize(exthttpsread));
			exthttpsdetails.put(AWSConstants.READRATE_PER_MIN, WMSUtil.HumanReadable.getCountSize(exthttpsread/time));
		}
		long exthttpswrite = BandWidthTracker.getExternalHttpsWrite();
		if(params.get(AWSConstants.H)==null)
		{
			exthttpsdetails.put(AWSConstants.TOTALWRITE, ""+exthttpswrite);
			exthttpsdetails.put(AWSConstants.WRITERATE_PER_MIN, ""+(exthttpswrite/time));
		}
		else
		{
			exthttpsdetails.put(AWSConstants.TOTALWRITE, WMSUtil.HumanReadable.getCountSize(exthttpswrite));
			exthttpsdetails.put(AWSConstants.WRITERATE_PER_MIN, WMSUtil.HumanReadable.getCountSize(exthttpswrite/time));
		}

		Hashtable external = new Hashtable();
		external.put(AWSConstants.HTTP, exthttpdetails);
		external.put(AWSConstants.HTTPS, exthttpsdetails);
		Hashtable info = new Hashtable();
		info.put(AWSConstants.EXTERNAL, external);
		logger.addDebugLog(Level.FINE, AWSLogConstants.GETINFO_CALLED_FOR_BANDWIDTHPROFILER, BandWidthProfiler.class.getName(),AWSLogMethodConstants.GET_INFO, new Object[]{info});
		return info;
	}

	public Hashtable resetAndGetInfo()
	{
		Hashtable exthttpdetails = new Hashtable();
		long exthttpread = BandWidthTracker.getExternalHttpRead(true);
		exthttpdetails.put(AWSConstants.TOTALREAD, ""+exthttpread);
		long exthttpwrite = BandWidthTracker.getExternalHttpWrite(true);
		exthttpdetails.put(AWSConstants.TOTALWRITE, ""+exthttpwrite);

		Hashtable exthttpsdetails = new Hashtable();
		long exthttpsread = BandWidthTracker.getExternalHttpsRead(true);
		exthttpsdetails.put(AWSConstants.TOTALREAD, ""+exthttpsread);
		long exthttpswrite = BandWidthTracker.getExternalHttpsWrite(true);
		exthttpsdetails.put(AWSConstants.TOTALWRITE, ""+exthttpswrite);

		Hashtable external = new Hashtable();
		external.put(AWSConstants.HTTP, exthttpdetails);
		external.put(AWSConstants.HTTPS, exthttpsdetails);

		Hashtable info = new Hashtable();
                info.put(AWSConstants.EXTERNAL, external);

		BandWidthTracker.resetAll(true);
		return info;
	}

	protected void endOfDay(String day)
	{
		BandWidthTracker.resetAll();
	}	

}
