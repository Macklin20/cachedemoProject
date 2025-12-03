//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;

// Server common import
import com.adventnet.wms.servercommon.runtime.WmsRuntime;

public class GC extends WmsRuntime
{
	public Hashtable getInfo(Hashtable params)
	{
		return new Hashtable();
	}

	protected void periodicCollector(long timeElapsed)
	{
		System.gc();
	}

}
