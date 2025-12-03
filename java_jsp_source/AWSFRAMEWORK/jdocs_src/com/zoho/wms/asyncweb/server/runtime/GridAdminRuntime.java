//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;

// Server common import
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.ConfManager;

public class GridAdminRuntime extends WmsRuntime
{
	public Hashtable getInfo(Hashtable params)
	{
		String opr = ""+params.get(AWSConstants.OPR);
		Hashtable details = new Hashtable();
		if(opr.equals("servercheck"))
		{
			details.put("status", ""+ConfManager.getServerCheckStatus());
		}else if(opr.equals("setservercheck"))
		{
			boolean status = new Boolean(""+params.get("status")).booleanValue();
			details.put("status", ""+ConfManager.setServerCheckStatus(status));
		}
		return details;
	}


}
