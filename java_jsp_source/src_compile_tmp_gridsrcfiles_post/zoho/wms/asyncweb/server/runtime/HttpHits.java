//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.logging.Level;

// Server common import 
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.adventnet.wms.servercommon.util.WMSUtil;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.AsyncWebStatsManager;

public class HttpHits extends WmsRuntime
{
	public static AsyncLogger logger = new AsyncLogger(HttpHits.class.getName());

	public HttpHits()
	{
	}

	public void periodicCollector(long timeElapsed)
	{
		Enumeration domainhitenum = WmsRuntimeCounters.domainhitmap.keys();
		Hashtable alldomains = new Hashtable();

		while(domainhitenum.hasMoreElements())
		{
			try
			{
				String domainname = (String)domainhitenum.nextElement();
				DomainHit hitobj = (DomainHit)WmsRuntimeCounters.domainhitmap.get(domainname);
				Hashtable details = new Hashtable();

				long hits = hitobj.getExternalHttpHits();
				long httphitrate = hitobj.getExternalHttpHitRate();
				long httpprevsample = hitobj.getExternalHttpPrevSample();
				long maxhttphitrate2day = hitobj.getExternalMaxHttpHitRateToday();
				long maxhttphitrate = hitobj.getExternalMaxHttpHitRate();
				long maxhttphitratetime = hitobj.getMaxExternalHttpHitRateTime();
				long httphitstillyday = hitobj.getExternalHttpHitsTillYDay();
				long totalhits2day = (hits-httphitstillyday);
				long lastMinuteHits = hits-httpprevsample;
				
				details.put(AWSConstants.HITS_PER_MIN, lastMinuteHits);
				details.put(AWSConstants.TOTAL, hits);
                                details.put(AWSConstants.TODAY,totalhits2day);
                                details.put(AWSConstants.HITRATE, httphitrate);
                                details.put(AWSConstants.MAXHITRATE, maxhttphitrate);
                                details.put(AWSConstants.MAXHITRATETIME, maxhttphitratetime);
                                details.put(AWSConstants.MAXHITRATE2DAY, maxhttphitrate2day);

				httphitrate = (hits-httpprevsample) / ((timeElapsed/(1000l*60l)) < 1 ? 1 : (timeElapsed/(1000l*60l)));
				if(httphitrate > 0)
				{
					hitobj.setExternalHttpHitRate(httphitrate);
				}
				httpprevsample = hits;
				if(httpprevsample > 0)
				{
					hitobj.setExternalHttpHitPrevSample(httpprevsample);
				}

				if(httphitrate > maxhttphitrate2day)
				{
					hitobj.setMaxExternalHttpHitRateToday(httphitrate);
				}
				if(httphitrate > maxhttphitrate)
				{
					hitobj.setMaxExternalHttpHitRate(httphitrate);
					hitobj.setMaxExternalHttpHitRateTime(System.currentTimeMillis());
				}

				Hashtable httpsdetails = new Hashtable();
				
				long httpshits = hitobj.getExternalHttpsHits();
				long httpshitrate = hitobj.getExternalHttpsHitRate();
                                long httpsprevsample = hitobj.getExternalHttpsPrevSample();
                                long maxhttpshitrate2day = hitobj.getExternalMaxHttpsHitRateToday();
                                long maxhttpshitrate = hitobj.getExternalMaxHttpsHitRate(); 
                                long maxhttpshitratetime = hitobj.getMaxExternalHttpsHitRateTime();
                                long httpshitstillyday = hitobj.getExternalHttpsHitsTillYDay();
                                long totalhttpshits2day = (hits-httpshitstillyday);
				long lastMinuteHttpsHits = httpshits-httpsprevsample;

				httpsdetails.put(AWSConstants.HITS_PER_MIN, lastMinuteHttpsHits);
				httpsdetails.put(AWSConstants.TOTAL, httpshits);
                                httpsdetails.put(AWSConstants.TODAY, totalhttpshits2day);    
                                httpsdetails.put(AWSConstants.HITRATE, httpshitrate);    
                                httpsdetails.put(AWSConstants.MAXHITRATE, maxhttpshitrate);    
                                httpsdetails.put(AWSConstants.MAXHITRATETIME, maxhttpshitratetime);    
                                httpsdetails.put(AWSConstants.MAXHITRATE2DAY, maxhttpshitrate2day);

				httpshitrate = (httpshits-httpsprevsample) / ((timeElapsed/(1000l*60l)) < 1 ? 1 : (timeElapsed/(1000l*60l)));
				if(httpshitrate > 0)
				{
					hitobj.setExternalHttpsHitRate(httpshitrate);
				}
				httpsprevsample = httpshits;
				if(httpsprevsample > 0)
				{
					hitobj.setExternalHttpsHitPrevSample(httpsprevsample);
				}

				if(httpshitrate > maxhttpshitrate2day)
				{
					hitobj.setMaxExternalHttpHitRateToday(httpshitrate);
				}

				if(httpshitrate > maxhttpshitrate)
				{
					hitobj.setMaxExternalHttpsHitRate(httpshitrate);
					hitobj.setMaxExternalHttpsHitRateTime(System.currentTimeMillis());
				}

				Hashtable external = new Hashtable();
                                external.put(AWSConstants.HTTP, details);
                                external.put(AWSConstants.HTTPS, httpsdetails);

				Hashtable info = new Hashtable();
                                info.put(AWSConstants.EXTERNAL, external);

				alldomains.put(domainname,info);

			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",HttpHits.class.getName(),AWSLogMethodConstants.PERIODIC_COLLECTOR, ex);
			}
		}
		try
		{
			AsyncWebStatsManager.onHitsStats(alldomains);
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
		Hashtable alldomains = new Hashtable();

		Enumeration domainhitenum = WmsRuntimeCounters.domainhitmap.keys();

		while(domainhitenum.hasMoreElements())
		{
			try
			{

				String domainname = (String)domainhitenum.nextElement();
				DomainHit dh = (DomainHit)WmsRuntimeCounters.domainhitmap.get(domainname);

				Hashtable details = new Hashtable();
				long totalhits = dh.getExternalHttpHits();
				long httphitstillyday = dh.getExternalHttpHitsTillYDay();
				long totalhits2day = (totalhits-httphitstillyday);
				if(params.get(AWSConstants.H)==null)
				{
					details.put(AWSConstants.TOTAL, ""+totalhits);
					details.put(AWSConstants.TODAY, ""+totalhits2day);
					details.put(AWSConstants.HITRATE, ""+dh.getExternalHttpHitRate());
					details.put(AWSConstants.MAXHITRATE, ""+dh.getExternalMaxHttpHitRate());
					details.put(AWSConstants.MAXHITRATETIME, ""+dh.getMaxExternalHttpHitRateTime());
					details.put(AWSConstants.MAXHITRATE2DAY, ""+dh.getExternalMaxHttpHitRateToday());
				}
				else
				{
					details.put(AWSConstants.TOTAL, WMSUtil.HumanReadable.getCountSize(totalhits));
					details.put(AWSConstants.TODAY, WMSUtil.HumanReadable.getCountSize(totalhits2day));
					details.put(AWSConstants.HITRATE, WMSUtil.HumanReadable.getCountSize(dh.getExternalHttpHitRate()));
					details.put(AWSConstants.MAXHITRATE, WMSUtil.HumanReadable.getCountSize(dh.getExternalMaxHttpHitRate()));
					details.put(AWSConstants.MAXHITRATETIME, WMSUtil.HumanReadable.getTime(dh.getMaxExternalHttpHitRateTime()));
					details.put(AWSConstants.MAXHITRATE2DAY, WMSUtil.HumanReadable.getCountSize(dh.getExternalMaxHttpHitRateToday()));
				}


				Hashtable httpsdetails = new Hashtable();
				long totalhttpshits = dh.getExternalHttpsHits();
				long httpshitstillyday = dh.getExternalHttpsHitsTillYDay();
				long totalhttpshits2day = (totalhttpshits-httpshitstillyday);
				if(params.get(AWSConstants.H)==null)
				{
					httpsdetails.put(AWSConstants.TOTAL, ""+totalhttpshits);
					httpsdetails.put(AWSConstants.TODAY, ""+totalhttpshits2day);
					httpsdetails.put(AWSConstants.HITRATE, ""+dh.getExternalHttpsHitRate());
					httpsdetails.put(AWSConstants.MAXHITRATE, ""+dh.getExternalMaxHttpsHitRate());
					httpsdetails.put(AWSConstants.MAXHITRATETIME, ""+dh.getMaxExternalHttpsHitRateTime());
					httpsdetails.put(AWSConstants.MAXHITRATE2DAY, ""+dh.getExternalMaxHttpsHitRateToday());
				}
				else
				{
					httpsdetails.put(AWSConstants.TOTAL, WMSUtil.HumanReadable.getCountSize(totalhttpshits));
					httpsdetails.put(AWSConstants.TODAY, WMSUtil.HumanReadable.getCountSize(totalhttpshits2day));
					httpsdetails.put(AWSConstants.HITRATE, WMSUtil.HumanReadable.getCountSize(dh.getExternalHttpsHitRate()));
					httpsdetails.put(AWSConstants.MAXHITRATE, WMSUtil.HumanReadable.getCountSize(dh.getExternalMaxHttpsHitRate()));
					httpsdetails.put(AWSConstants.MAXHITRATETIME, WMSUtil.HumanReadable.getTime(dh.getMaxExternalHttpsHitRateTime()));
					httpsdetails.put(AWSConstants.MAXHITRATE2DAY, WMSUtil.HumanReadable.getCountSize(dh.getExternalMaxHttpsHitRateToday()));
				}

				Hashtable external = new Hashtable();
				external.put(AWSConstants.HTTP, details);
				external.put(AWSConstants.HTTPS, httpsdetails);

				Hashtable info = new Hashtable();
				info.put(AWSConstants.EXTERNAL, external);
				long average = ((totalhits2day + totalhttpshits2day) / (((getTimeElapsedSinceToday() / (1000l*60l))<1) ? 1  : (getTimeElapsedSinceToday() / (1000l*60l))));
				if(params.get(AWSConstants.H)==null)
				{
					info.put(AWSConstants.AVERAGE, ""+average);
				}
				else
				{
					info.put(AWSConstants.AVERAGE, WMSUtil.HumanReadable.getCountSize(average));
				}

				alldomains.put(domainname,info);
			}
			catch(Exception ex)
			{	
				logger.log(Level.INFO, " Exception ",HttpHits.class.getName(),AWSLogMethodConstants.GET_INFO, ex);
			}
		}
		return alldomains;
	}

	public Hashtable resetAndGetInfo()
	{
		Hashtable alldomains = new Hashtable();

		Enumeration domainhitenum = WmsRuntimeCounters.domainhitmap.keys();

		while(domainhitenum.hasMoreElements())
		{
			try
			{
				String domainname = (String)domainhitenum.nextElement();
				DomainHit dh = (DomainHit)WmsRuntimeCounters.domainhitmap.get(domainname);

				Hashtable details = new Hashtable();
				long totalhits = dh.getExternalHttpHits(true);
				details.put(AWSConstants.TOTAL, ""+totalhits);
				Hashtable httpsdetails = new Hashtable();
				long totalhttpshits = dh.getExternalHttpsHits(true);
				httpsdetails.put(AWSConstants.TOTAL, ""+totalhttpshits);

				Hashtable external = new Hashtable();
				external.put(AWSConstants.HTTP, details);
				external.put(AWSConstants.HTTPS, httpsdetails);

				Hashtable info = new Hashtable();
				info.put(AWSConstants.EXTERNAL, external);

				dh.resetHits(true);
				alldomains.put(domainname,info);
			}
			catch(Exception ex)
			{	
				logger.log(Level.INFO, " Exception ",HttpHits.class.getName(),AWSLogMethodConstants.RESET_AND_GET_INFO, ex);
			}
		}
		return alldomains;
	}

	protected void endOfDay(String day)
	{
		Enumeration domainhitenum = WmsRuntimeCounters.domainhitmap.keys();

		while(domainhitenum.hasMoreElements())
		{
			String domainname = (String)domainhitenum.nextElement();
			DomainHit dh = (DomainHit)WmsRuntimeCounters.domainhitmap.get(domainname);
			dh.setMaxExternalHttpHitRateToday(0l);
			dh.setExternalHttpHitsTillYDay(dh.getExternalHttpHits());

			dh.setMaxExternalHttpsHitRateToday(0l);
			dh.setExternalHttpsHitsTillYDay(dh.getExternalHttpsHits());
		}
	}	

}
