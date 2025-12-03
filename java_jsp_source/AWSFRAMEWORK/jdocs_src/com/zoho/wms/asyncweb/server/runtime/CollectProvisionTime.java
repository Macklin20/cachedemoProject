//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;

// Server common import
import com.adventnet.wms.servercommon.runtime.WmsRuntime;

// Wms import
import com.zoho.wms.asyncweb.server.ConfManager;

public class CollectProvisionTime extends WmsRuntime
{
	public Hashtable getInfo(Hashtable params)
	{
		Hashtable details = new Hashtable();
		details.put("provtime", ""+ConfManager.getServerStartTime());
		details.put("blog", ""+ConfManager.getBlog());
		return details;
	}

}
