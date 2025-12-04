//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.Hashtable;
import java.util.Date;
import java.util.Enumeration;
import java.util.logging.Level;

import java.text.SimpleDateFormat;
import java.text.DecimalFormat;
import com.zoho.wms.asyncweb.server.runtime.WmsRuntimeCounters;
import com.zoho.wms.asyncweb.server.runtime.DomainHit;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;

public class AnomalyTracker
{
	private static AsyncLogger logger = new AsyncLogger(AnomalyTracker.class.getName());

	private static Hashtable domainhitrate = new Hashtable();
	//private static Hashtable customData = new Hashtable();
	private static long usedmem;
	private static long externalhttpreadrate;
	private static long externalhttpsreadrate;
	private static long externalhttpwriterate;
	private static long externalhttpswriterate;
	private static DecimalFormat df = new DecimalFormat("#.##");

	public static void readBehaviour()
	{
		try
		{
			Enumeration domainhitenum = WmsRuntimeCounters.domainhitmap.keys();

			while(domainhitenum.hasMoreElements())
			{
				String domainname = (String)domainhitenum.nextElement();
				DomainHit hitobj = (DomainHit)WmsRuntimeCounters.domainhitmap.get(domainname);
				long externalhttphitrate = hitobj.getExternalHttpHitRate();
				long externalhttpshitrate = hitobj.getExternalHttpsHitRate();

				if(domainhitrate.get(domainname)==null)
				{
					Hashtable details = new Hashtable();
					details.put(AWSConstants.EXTERNALHTTPHITRATE,externalhttphitrate);
					details.put(AWSConstants.EXTERNALHTTPSHITSRATE,externalhttpshitrate);
					domainhitrate.put(domainname,details);
				}
				else
				{
					Hashtable details = (Hashtable)domainhitrate.get(domainname);
					
					if(externalhttphitrate > (Long)details.get(AWSConstants.EXTERNALHTTPHITRATE))
					{
						details.put(AWSConstants.EXTERNALHTTPHITRATE,externalhttphitrate);
					}
					if(externalhttpshitrate > (Long)details.get(AWSConstants.EXTERNALHTTPSHITSRATE))
					{
						details.put(AWSConstants.EXTERNALHTTPSHITSRATE,externalhttpshitrate);
					}	

				}

			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO,"[ANOMALY] Unable to calculate behavioural pattern for hits : ",AnomalyTracker.class.getName(),AWSLogMethodConstants.READ_BEHAVIOUR,ex);
		}

		try
		{
			Hashtable memInfo = AsyncWebStatsManager.getMemoryStats();
						
			if(!memInfo.isEmpty() && memInfo.get(AWSConstants.USED)!=null && (usedmem < Long.parseLong(""+memInfo.get(AWSConstants.USED))))
			{
				usedmem = Long.parseLong(""+memInfo.get(AWSConstants.USED));
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO,"[ANOMALY] Unable to calculate behavioural pattern for memory : ",AnomalyTracker.class.getName(),AWSLogMethodConstants.READ_BEHAVIOUR,ex);
		}


		try
		{
			Hashtable bandwidthStats = AsyncWebStatsManager.getBandwidthStats();
			Hashtable externaldetails = (Hashtable)bandwidthStats.get(AWSConstants.EXTERNAL);

			if(externaldetails!=null)
			{
				long currentexternalhttpreadrate = Long.parseLong(""+((Hashtable)externaldetails.get(AWSConstants.HTTP)).get(AWSConstants.READRATE_PER_MIN)); 			
				long currentexternalhttpsreadrate = Long.parseLong(""+((Hashtable)externaldetails.get(AWSConstants.HTTPS)).get(AWSConstants.READRATE_PER_MIN));
				long currentexternalhttpwriterate = Long.parseLong(""+((Hashtable)externaldetails.get(AWSConstants.HTTP)).get(AWSConstants.WRITERATE_PER_MIN));
				long currentexternalhttpswriterate = Long.parseLong(""+((Hashtable)externaldetails.get(AWSConstants.HTTPS)).get(AWSConstants.WRITERATE_PER_MIN));

				if(externalhttpreadrate < currentexternalhttpreadrate)
				{
					externalhttpreadrate = currentexternalhttpreadrate;
				}

				if(externalhttpsreadrate < currentexternalhttpsreadrate)
				{
					externalhttpsreadrate = currentexternalhttpsreadrate;
				}
				
				if(externalhttpwriterate < currentexternalhttpwriterate)
				{
					externalhttpwriterate = currentexternalhttpwriterate;
				}

				if(externalhttpswriterate < currentexternalhttpswriterate)
				{
					externalhttpswriterate = currentexternalhttpswriterate;
				}
			}

		}
		catch(Exception ex)
		{
			logger.log(Level.INFO,"[ANOMALY] Unable to calculate behavioural pattern for bandwidth : ",AnomalyTracker.class.getName(),AWSLogMethodConstants.READ_BEHAVIOUR,ex);
		}
	}
	
	public static Hashtable detectAnomaly()
	{
		Hashtable anomalyData = new Hashtable();
		int threshold = ConfManager.getAnomalyDetectionThreshold();
		
		try
		{
			Hashtable domainStats = new Hashtable();
			Enumeration domainhitenum = WmsRuntimeCounters.domainhitmap.keys();

			while(domainhitenum.hasMoreElements())
			{
				String domainname = (String)domainhitenum.nextElement();
				DomainHit hitobj = (DomainHit)WmsRuntimeCounters.domainhitmap.get(domainname);
				long internalhttphitrate = hitobj.getInternalHttpHitRate();
				long externalhttphitrate = hitobj.getExternalHttpHitRate();
				long externalhttpshitrate = hitobj.getExternalHttpsHitRate();

				if(domainhitrate.get(domainname)==null)
				{
					Hashtable details = new Hashtable();
					details.put(AWSConstants.EXTERNALHTTPHITRATE,externalhttphitrate);
					details.put(AWSConstants.EXTERNALHTTPSHITSRATE,externalhttpshitrate);
					domainhitrate.put(domainname,details);
				}
				else
				{
					Hashtable details = (Hashtable)((Hashtable)domainhitrate.get(domainname)).clone();
					Hashtable anomalyDetails = new Hashtable();

					if(externalhttphitrate > ((Long)details.get(AWSConstants.EXTERNALHTTPHITRATE))*threshold)
					{
						if(externalhttphitrate > ConfManager.getAnomalyHitRateThreshold())
						{
							Hashtable info = new Hashtable();
							info.put(AWSConstants.CURRENT, externalhttphitrate);
							info.put(AWSConstants.NORMAL, (Long)details.get(AWSConstants.EXTERNALHTTPHITRATE));
							anomalyDetails.put(AWSConstants.EXTERNALHTTPHITRATE,info);
						}
					}

					if(externalhttpshitrate > ((Long)details.get(AWSConstants.EXTERNALHTTPSHITSRATE))*threshold)
					{
						if(externalhttpshitrate > ConfManager.getAnomalyHitRateThreshold())
						{
							Hashtable info = new Hashtable();
							info.put(AWSConstants.CURRENT, externalhttpshitrate);
							info.put(AWSConstants.NORMAL, (Long)details.get(AWSConstants.EXTERNALHTTPSHITSRATE));
							anomalyDetails.put(AWSConstants.EXTERNALHTTPSHITSRATE,info);
						}
					}

					if(anomalyDetails.size() > 0)
					{
						domainStats.put(domainname, anomalyDetails);
					}
				}
			}
		
			if(domainStats.size() > 0)
			{
				anomalyData.put(AWSConstants.HITS, domainStats);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO,"[ANOMALY] Unable to detect anomaly for hits : " ,AnomalyTracker.class.getName(),AWSLogMethodConstants.DETECT_ANOMALY,ex);
		}

		try
		{
			Hashtable memStats = new Hashtable();
			Hashtable memInfo = AsyncWebStatsManager.getMemoryStats();
			
			if(!memInfo.isEmpty() && memInfo.get(AWSConstants.USED)!=null)	
			{
				long currentusedmem = Long.parseLong(""+memInfo.get(AWSConstants.USED));

				/*if(usedmem*threshold < Long.parseLong(""+memInfo.get(AWSConstants.USED)))
				{
					usedmem = Long.parseLong(""+memInfo.get(AWSConstants.USED));
				}*/

				if(currentusedmem >  usedmem*threshold)
				{

					if(currentusedmem > ConfManager.getAnomalyMemThreshold())
					{
						Hashtable details = new Hashtable();
						details.put(AWSConstants.CURRENT,currentusedmem);
						details.put(AWSConstants.NORMAL,usedmem);
						memStats.put(AWSConstants.USED_MEM, details);
					}
				}
			}
			
			if(memStats.size() > 0)
			{
				anomalyData.put(AWSConstants.MEMORY, memStats);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO,"[ANOMALY] Unable to detect anomaly for memory : " ,AnomalyTracker.class.getName(),AWSLogMethodConstants.DETECT_ANOMALY,ex);
		}
		
		try
		{
			Hashtable bwStats = new Hashtable();
			Hashtable bandwidthStats = AsyncWebStatsManager.getBandwidthStats();
			Hashtable externaldetails = (Hashtable)bandwidthStats.get(AWSConstants.EXTERNAL);

			if(externaldetails!=null)
			{
				long currentexternalhttpreadrate = Long.parseLong(""+((Hashtable)externaldetails.get(AWSConstants.HTTP)).get(AWSConstants.READRATE_PER_MIN)); 			
				long currentexternalhttpsreadrate = Long.parseLong(""+((Hashtable)externaldetails.get(AWSConstants.HTTPS)).get(AWSConstants.READRATE_PER_MIN));
				long currentexternalhttpwriterate = Long.parseLong(""+((Hashtable)externaldetails.get(AWSConstants.HTTP)).get(AWSConstants.WRITERATE_PER_MIN));
				long currentexternalhttpswriterate = Long.parseLong(""+((Hashtable)externaldetails.get(AWSConstants.HTTPS)).get(AWSConstants.WRITERATE_PER_MIN));
				
				if(externalhttpreadrate*threshold < currentexternalhttpreadrate)
				{
					if(currentexternalhttpreadrate > ConfManager.getAnomalyBWRateThreshold())
					{
						Hashtable details = new Hashtable();
						details.put(AWSConstants.CURRENT, currentexternalhttpreadrate);
						details.put(AWSConstants.NORMAL, externalhttpreadrate);
						bwStats.put(AWSConstants.INTERNALHTTPREADRATE,details);
					}
				}

				if(externalhttpsreadrate*threshold < currentexternalhttpsreadrate)
				{
					if(currentexternalhttpsreadrate > ConfManager.getAnomalyBWRateThreshold())
					{
						Hashtable details = new Hashtable();
						details.put(AWSConstants.CURRENT, currentexternalhttpsreadrate);
						details.put(AWSConstants.NORMAL, externalhttpsreadrate);
						bwStats.put(AWSConstants.EXTERNALHTTPREADRATE,details);
					}
				}

				if(externalhttpwriterate*threshold < currentexternalhttpwriterate)
				{
					if(currentexternalhttpwriterate > ConfManager.getAnomalyBWRateThreshold())
					{
						Hashtable details = new Hashtable();
						details.put(AWSConstants.CURRENT, currentexternalhttpwriterate);
						details.put(AWSConstants.NORMAL, externalhttpwriterate);
						bwStats.put(AWSConstants.INTERNALHTTPWRITERATE,details);
					}
				}

				if(externalhttpswriterate*threshold < currentexternalhttpswriterate)
				{
					if(currentexternalhttpswriterate > ConfManager.getAnomalyBWRateThreshold())
					{
						Hashtable details = new Hashtable();
						details.put(AWSConstants.CURRENT, currentexternalhttpswriterate);
						details.put(AWSConstants.NORMAL, externalhttpswriterate);
						bwStats.put(AWSConstants.EXTERNALHTTPWRITERATE,details);
					}
				}
			}
	
			if(bwStats.size()>0)
			{
				anomalyData.put(AWSConstants.BANDWIDTH,bwStats);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO,"[ANOMALY] Unable to detect anomaly for bandwidth : " ,AnomalyTracker.class.getName(),AWSLogMethodConstants.DETECT_ANOMALY,ex);
		}

		return anomalyData;
	}

	public static String getSizeInMB(long size)
	{
		return df.format((size * 100 / (1024L * 1024L)) / 100F);
	}

	public static String getSizeInKB(long size)
	{
		return df.format((size * 100 / (1024L)) / 100F);
	}

	public static Hashtable getHealthData()
	{
		Hashtable data = new Hashtable();
		data.put(AWSConstants.DATE,getFormattedDate());
		data.put(AWSConstants.DOMAIN_HIT_RATE,domainhitrate);
		data.put(AWSConstants.USED_MEMORY,getSizeInMB(usedmem)+AWSConstants.SPACE_MB_PER_MIN);
		data.put(AWSConstants.EXTERNALHTTPREADRATE,getSizeInKB(externalhttpreadrate)+AWSConstants.SPACE_KB_PER_MIN);
		data.put(AWSConstants.EXTERNALHTTPSREADRATE,getSizeInKB(externalhttpsreadrate)+AWSConstants.SPACE_KB_PER_MIN);
		data.put(AWSConstants.EXTERNALHTTPWRITERATE,getSizeInKB(externalhttpwriterate)+AWSConstants.SPACE_KB_PER_MIN);
		data.put(AWSConstants.EXTERNALHTTPSWRITERATE,getSizeInKB(externalhttpswriterate)+AWSConstants.SPACE_KB_PER_MIN);
		return data;
	}
	
	private static String getFormattedDate()
	{
		String datefield = "";
		try
		{
			SimpleDateFormat sdf = new SimpleDateFormat(AWSConstants.SIMPLE_DATE_FORMAT_2);//No I18N
			datefield = sdf.format(new Date());
		}
		catch(Exception ex)
		{
		}
		return datefield;
	}

	public static void resetCounters()
	{
		domainhitrate = new Hashtable();
		usedmem = 0l;
		externalhttpreadrate = 0l;
		externalhttpwriterate = 0l;
		externalhttpsreadrate = 0l;
		externalhttpswriterate = 0l;
	}

}
