//$Id$

package com.zoho.wms.asyncweb.server.runtime;

import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.zoho.wms.asyncweb.server.DOSManager;
import com.zoho.wms.asyncweb.server.DOSMonitor;

import java.util.Hashtable;

/**
 *
 * @author Jeri John
 */
public class DOSMonitorRuntime extends WmsRuntime
{

	@Override
		public Hashtable getInfo(Hashtable params)
		{

			Hashtable response = new Hashtable();
			String block = ""+params.get("block");//boolean to decide blocking of ip if dos is identified
			String monitor = ""+params.get("monitor");//boolean to decide enable/disable dos monitoring
			String engine = ""+params.get("engine");//Engine name

			DOSMonitor dosMonitor = (DOSMonitor)DOSManager.getDOSByEngineName(engine);
			if(dosMonitor!=null)
			{
				if(block != null)
				{
					boolean blockip = Boolean.parseBoolean(block);
					if(blockip)
					{

						dosMonitor.enableBlocking();
					}
					else
					{
						dosMonitor.disableBlocking();
					}
				}
				if(monitor != null)
				{
					boolean dosmonitor = Boolean.parseBoolean(monitor);
					if(dosmonitor)
					{
						dosMonitor.enableMonitoring();
					}
					else
					{
						dosMonitor.disableMonitoring();
					}
				}
			}
			else
			{
				response.put("Error","Unable to find DOSMonitor class for this engine");	
			}
			return response;
		}

}
