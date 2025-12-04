//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;

// Server common import
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.runtime.SuspectedIPsTracker;

public class SuspectedIPsRuntime extends WmsRuntime
{
	public Hashtable getInfo(Hashtable params)
	{
		String opr = ""+params.get(AWSConstants.OPR);
		Hashtable details = new Hashtable();
		if(opr.equals(AWSConstants.GET))
		{
			details = SuspectedIPsTracker.get();
		}else if(opr.equals(AWSConstants.CLEAR))
		{
			SuspectedIPsTracker.clear();
		}
		return details;
	}

}
