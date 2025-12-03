//$Id$
package com.zoho.wms.asyncweb.server.runtime;

// Java import
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// Server common import
import com.adventnet.wms.servercommon.runtime.WmsRuntime;
import com.adventnet.wms.servercommon.taskengine.TaskManager;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public class TaskProfiler extends WmsRuntime
{
	public static AsyncLogger logger = new AsyncLogger(TaskProfiler.class.getName());
	private Hashtable<String, Hashtable> consDetails =  new Hashtable();

	public Hashtable getInfo(Hashtable params)
	{
		return consDetails;
	}

	public void periodicCollector(long timeElapsed)
	{
		try
		{
			Hashtable prevsummary = consDetails.remove(AWSConstants.SUMMARY);
			if(prevsummary==null)
			{
				prevsummary = new Hashtable();
			}


			ConcurrentHashMap taskstats = TaskManager.getStats();
			long periodtotal = 0l;
			Hashtable currentTasks =  new Hashtable();
			for(Enumeration e= taskstats.keys(); e.hasMoreElements();)
			{
				String taskName = (String) e.nextElement();
				long count = new Long(""+taskstats.get(taskName)).longValue();
				periodtotal += count;
				long trate = (count*1000l*60l) / timeElapsed;
				long prevmaxrate = 0l;

				Hashtable<String, String> taskdetails = consDetails.remove(taskName);
				if(taskdetails==null)
				{
					taskdetails = new Hashtable();
				}else
				{
					count = count + new Long(taskdetails.get(AWSConstants.TOTAL)).longValue();
					prevmaxrate = Long.parseLong(taskdetails.get(AWSConstants.MAXRATE));
				}
				taskdetails.put(AWSConstants.RATE, ""+trate); 
				taskdetails.put(AWSConstants.TOTAL, ""+count);
				if(trate >= prevmaxrate)
				{
					taskdetails.put(AWSConstants.MAXRATE, ""+trate);
					taskdetails.put(AWSConstants.MAXRATETIME, ""+System.currentTimeMillis());
				}

				currentTasks.put(taskName, taskdetails);	
			}
			for(Enumeration en = consDetails.keys(); en.hasMoreElements();)
			{
				String tskName = (String) en.nextElement();
				Hashtable prevTask = consDetails.get(tskName);
				prevTask.put(AWSConstants.RATE, AWSConstants.VALUE_0);
				consDetails.put(tskName, prevTask);
			}
			consDetails.putAll(currentTasks);
			long rate = (periodtotal*1000l*60l) / timeElapsed;
			long total = 0l;
			if(prevsummary.get(AWSConstants.TOTAL)!=null)
			{
				total = new Long(""+(prevsummary.get(AWSConstants.TOTAL))).longValue();
			}
			long totalcount = total + periodtotal;	

			long maxrate = 0l;
			long maxratetime = 0l;
			if(prevsummary.get(AWSConstants.MAXRATE)!=null)
			{
				maxrate = new Long(""+prevsummary.get(AWSConstants.MAXRATE)).longValue();
				maxratetime = new Long(""+prevsummary.get(AWSConstants.MAXRATETIME)).longValue();
			}

			Hashtable summary = new Hashtable();
			summary.put(AWSConstants.TOTAL, ""+totalcount);
			if(rate >= maxrate)
			{
				maxrate = rate;
				maxratetime = System.currentTimeMillis();
			}
			summary.put(AWSConstants.RATE, ""+rate);
			summary.put(AWSConstants.MAXRATE, ""+maxrate);
			summary.put(AWSConstants.MAXRATETIME, ""+maxratetime);
			consDetails.put(AWSConstants.SUMMARY, summary);

			TaskManager.resetStats();
		}catch(Exception exp)
		{
			logger.log(Level.INFO, " Exception ",TaskProfiler.class.getName(),AWSLogMethodConstants.PERIODIC_COLLECTOR, exp);
		}
	}
	
	protected void endOfDay(String day)
	{
		consDetails.clear();
	}

}
