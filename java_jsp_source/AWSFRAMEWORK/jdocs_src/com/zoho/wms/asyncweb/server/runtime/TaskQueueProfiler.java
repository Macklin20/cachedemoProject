//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;

// Server common import 
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.adventnet.wms.servercommon.taskengine.TaskManager;

public class TaskQueueProfiler extends WmsRuntime
{
	Hashtable consDetalis = new Hashtable();
	
	public Hashtable getInfo(Hashtable params)
	{
		periodicCollector(getTimeElapsedSinceLastPoll());
		return consDetalis;
	}

	protected void endOfDay(String day)
	{
		TaskManager.clearQueStats();
	}	

	protected void periodicCollector(long timeElapsed)
	{
		Hashtable details = new Hashtable();
		consDetalis.put("timeelapsed", ""+getTimeElapsedSinceToday());
		consDetalis.put("stats", TaskManager.getQueStats());
	}

}
