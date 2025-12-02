//$Id$
package com.zoho.wms.asyncweb.server.runtime;

import java.util.Hashtable;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.adventnet.wms.servercommon.runtime.WmsRuntime;

public class ConfRuntime extends WmsRuntime
{
	public static AsyncLogger logger = new AsyncLogger(ConfRuntime.class.getName());

	public Hashtable getInfo(Hashtable params)
	{
		String opr=(String)params.get(AWSConstants.OPR);
		boolean adaptermode = false;
		if(params.get("adapter") != null)
		{
			adaptermode = Boolean.parseBoolean((String)params.get("adapter"));
		}
		params.clear();
		if(opr.equals("reloadConf"))
		{
			try {
				boolean status=ConfManager.initialize(adaptermode);
				params.put(opr,status);
			}
			catch(Exception e) {
				logger.log(Level.INFO, " Exception ",ConfRuntime.class.getName(),AWSLogMethodConstants.GET_INFO, e);	
			}

		}
		else if(opr.equals("getconfdetails"))
		{
			try
			{
				params.put("conf/awsadapterconf.properties",ConfManager.getDetails());
				params.put("conf/mappeddomain.properties",ConfManager.getMappedDomainDetails());
			}
			catch(Exception e)
			{
				logger.log(Level.INFO, " Exception ",ConfRuntime.class.getName(),AWSLogMethodConstants.GET_INFO, e);
			}

		}
		return params;
	}
}

