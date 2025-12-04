//$Id$
package com.zoho.wms.asyncweb.server.stats;

import com.zoho.instrument.MetricHolder.*;
import com.zoho.instrument.transport.HandShake; 
import com.zoho.instrument.*;
import java.net.InetAddress;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;

import java.util.logging.Level;

public class MIAdapter extends Thread
{
	private static AsyncLogger logger = new AsyncLogger(MIAdapter.class.getName());    	
	private static boolean status =false;

	public MIAdapter()
	{
		super(AWSConstants.AWS_THREAD_PREFIX+"MIAdapter-Thread");//No I18n
	}

	public void run()
    	{
		if(ConfManager.isMIEnabled())
		{
			try
			{
				Properties miprops = ConfManager.getMIConfProperties();
				String appPort = (String) miprops.get(AWSConstants.PORT);
				String mi_domain = (String) miprops.get(AWSConstants.DOMAIN_NAME);  
				InstrumentInitiator.initialize(mi_domain, appPort, ConfManager.getMIXML(), true);

				String comps = miprops.getProperty("instrumentedcomponents");
				if (comps != null)
				{
					String components[] = comps.split(",");
					for (String component : components)
					{
						int reqtype = Integer.parseInt(miprops.getProperty(component + "_reqtype"));
						long duration = Long.parseLong(miprops.getProperty(component + "_duration"));
						long noofremotecalls = Long.parseLong(miprops.getProperty(component + "_noofremotecalls"));
						long memoryallocated = Long.parseLong(miprops.getProperty(component + "_memoryallocated"));
						long bytesin = Long.parseLong(miprops.getProperty(component + "_bytesin"));
						long bytesout = Long.parseLong(miprops.getProperty(component + "_bytesout"));
						setInstrumentationConfiguration(reqtype, duration, noofremotecalls, memoryallocated, bytesin, bytesout);
					}
					logger.log(Level.INFO,"Threshold set for instrumented components successfully..",MIAdapter.class.getName(),AWSLogMethodConstants.RUN);
				}
				else
				{	
					logger.log(Level.INFO,"Instrumented components not found..",MIAdapter.class.getName(),AWSLogMethodConstants.RUN);	
				}
				status = true;	
				logger.log(Level.INFO,"Connection to MI Server Successful",MIAdapter.class.getName(),AWSLogMethodConstants.RUN);
			}	
			catch (Exception e)
			{
				logger.log(Level.INFO,"Connection to MI failed",MIAdapter.class.getName(),AWSLogMethodConstants.RUN,e);
			}
		}
    	}

	public static boolean getStatus()
	{
		return status;
	}

    	public static void setInstrumentationConfiguration(int requesttype, long duration, long noofremotecalls, long memoryallocated, long bytesin, long bytesout) throws Exception
    	{
        	Map<Integer, Long> requestMap = RuntimeConfiguration.getConfigMapForRequest(duration, noofremotecalls, memoryallocated, bytesin, bytesout);
       		RuntimeConfiguration.addConfiguration(requesttype, requestMap);
	}

}
