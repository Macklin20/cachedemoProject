//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;

// Server common import 
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.zoho.wms.asyncweb.server.AWSConstants;
// Wms import
import com.zoho.wms.asyncweb.server.MessageIdTracker;

public class MessageIdsRuntime extends WmsRuntime
{
	public Hashtable getInfo(Hashtable params)
	{
		Hashtable returndetails = new Hashtable();
		if(AWSConstants.GET.equals(params.get(AWSConstants.OPR)))
		{
			returndetails = MessageIdTracker.getStats();
		}else if(AWSConstants.CLEAR.equals(params.get(AWSConstants.OPR)))
		{
			MessageIdTracker.clearStats();
		}
		return returndetails;
	}

	protected void periodicCollector(long timeElapsed)
	{
		MessageIdTracker.printStats();
		MessageIdTracker.clearStats();
	}

}
