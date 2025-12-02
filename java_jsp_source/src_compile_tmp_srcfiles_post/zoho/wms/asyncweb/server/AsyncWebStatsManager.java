//$Id$
package com.zoho.wms.asyncweb.server;

import java.util.Hashtable;
import com.adventnet.wms.servercommon.runtime.RuntimeAdmin;
import com.adventnet.wms.servercommon.components.executor.WMSTPExecutorFactory;
import com.adventnet.wms.servercommon.components.executor.WMSThreadPoolExecutorInventory;
import com.zoho.wms.asyncweb.server.stats.RuntimeStatsListener;
import com.zoho.wms.asyncweb.server.ConfManager;

public class AsyncWebStatsManager
{
	private static RuntimeStatsListener statslistener = ConfManager.getRuntimeStatsListener();

	/**
	 * To get bandwith stats of the server which include details of no of bytes read and write through internal and external http, https ports
	 * @return - <br>
	 * Hashtable with entries<br><br>
	 *<ul style="list-style-type:circle">
	 * 	<li>internal=internaldetails(Hashtable)</li>
	 *	<li>external=externaldetails(Hashtable)</li>
	 *</ul><br><br>
	 *
	 * Note :<br><br>
	 * <ul style="list-style-type:circle">
	 * 	<li>internaldetails
	 * 		<ul style="list-style-type:disc">
	 * 			<li>http = bandwidthstats(Hashtable)</li>
	 * 		</ul>
	 * 	</li>
	 * 	<li>externaldetails 
	 * 		<ul style="list-style-type:disc">
	 * 			<li>http = bandwidthstats(Hashtable)</li>
	 *        		<li>https= bandwidthstats(Hashtable)</li>	
 	 * 		</ul>
	 * 	</li>
	 * </ul>
	 * <br>
	 * <ul style="list-style-type:circle">
	 * 	<li>bandwidthstats - totalread,readrate/min,totalwrite,writerate/min and their respective string values
	 * 	   	<ul style="list-style-type:disc">
	 * 	   		<li>totalread - total no of bytes read</li>
	 * 	   		<li>readrate/min - no of bytes read/min</li>
	 * 	   		<li>totalwrite - total no of bytes written</li>
	 * 	   		<li>totalwrite/min - no of bytes written/min</li>
	 * 	   	</ul>
	 * 	 </li>
	 * 	 <li>All key and values in Hashtables(mentioned above) are of type String other than  mentioned</li>
	 * </ul>
	 */

	public static Hashtable getBandwidthStats()
	{
		try
		{
			Hashtable params = new Hashtable();
			return getBandwidthStats(params);
		}
		catch(Exception ex)
		{
			return (new Hashtable());
		}
	}

	/**
	 * To get bandwith stats of the server which include details of no of bytes read and write through internal and external http, https ports
	 * @param params - list of params passed via runtime
	 * @return - <br>
	 * Hashtable with entries<br><br>
	 *<ul style="list-style-type:circle">
	 * 	<li>internal=internaldetails(Hashtable)</li>
	 *	<li>external=externaldetails(Hashtable)</li>
	 *</ul><br><br>
	 *
	 * Note :<br><br>
	 * <ul style="list-style-type:circle">
	 * 	<li>internaldetails
	 * 		<ul style="list-style-type:disc">
	 * 			<li>http = bandwidthstats(Hashtable)</li>
	 * 		</ul>
	 * 	</li>
	 * 	<li>externaldetails 
	 * 		<ul style="list-style-type:disc">
	 * 			<li>http = bandwidthstats(Hashtable)</li>
	 *        		<li>https= bandwidthstats(Hashtable)</li>	
 	 * 		</ul>
	 * 	</li>
	 * </ul>
	 * <br>
	 * <ul style="list-style-type:circle">
	 * 	<li>bandwidthstats - totalread,readrate/min,totalwrite,writerate/min and their respective string values
	 * 	   	<ul style="list-style-type:disc">
	 * 	   		<li>totalread - total no of bytes read</li>
	 * 	   		<li>readrate/min - no of bytes read/min</li>
	 * 	   		<li>totalwrite - total no of bytes written</li>
	 * 	   		<li>totalwrite/min - no of bytes written/min</li>
	 * 	   	</ul>
	 * 	 </li>
	 * 	 <li>All key and values in Hashtables(mentioned above) are of type String other than  mentioned</li>
	 * </ul>
	 */

	public static Hashtable getBandwidthStats(Hashtable params)
	{
		try
		{
			return RuntimeAdmin.execute(AWSConstants.AWS_BW,params);
		}
		catch(Exception ex)
		{
			return (new Hashtable());
		}
	}

	/**
         * To update bandwith stats of the server to RuntimeStatsListener 
         * @param data - bandwidth stats collected for every single minute
        */

	public static void onBandwidthStats(Hashtable data)
	{
		if(statslistener != null)
		{
			statslistener.updateBandwidthStats(data);
		}
	}

	/**
	 * To get details of domain hits, internal and default webengine hits through internal and external http,https ports
	 * @return - <br> 
	 * Hashtable with entries<br><br>
	 *<ul style="list-style-type:circle">
	 *	<li>internal = details(Hashtable)</li>
	 * 	<li>default = details(Hashtable)</li>
	 * 	<li>domainname = details(Hashtable)</li>
	 * </ul>
	 *
	 * <br><br>
	 *
	 * Note :<br><br>
	 * <ul style="list-style-type:circle">
	 * 	<li>details 
	 * 		<ul style="list-style-type:disc">
	 * 			<li>internal = internaldetails(Hashtable)</li>
	 * 		      	<li>external = externaldetails(Hashtable)</li>
	 * 	              	<li>average=totalaveragehitstoday</li>
	 * 	        </ul>
	 * 	 </li>
	 * 	 <li>internaldetails 
	 * 	 	<ul style="list-style-type:disc">
	 * 	 		<li>http = hitstats(Hashtable)</li>
	 * 	 	</ul>
	 * 	 </li>
	 *       <li>externaldetails 
	 *       	<ul style="list-style-type:disc">
	 *       		<li>http = hitstats(Hashtable)</li>
	 *        		<li>https= hitstats(Hashtable)</li>
	 *        	</ul>
	 *        </li>
	 * 	 <li>hitstats 
	 * 	 	<ul style="list-style-type:disc">
	 * 	 		<li>total,today,hitrate,maxhitrate,maxhitratetime,maxhitrate2day and their respective string values</li>
	 * 	   	        <li>total - total no of hits</li>
	 * 	   	       	<li>today - total no of hits today</li>
	 * 	   	       	<li>hitrate - no of hits/min</li>
	 * 	   	       	<li>maxhitrate - maximum hit/min recorded</li> 
	 * 	   	       	<li>maxhitratetime - time at which maximum hit/min is recorded</li>
	 * 	   	       	<li>maxhitrate2day - maximum rate recorded today</li>
	 * 	   	</ul>
	 * 	 </li>
	 * 	 <li>Domain name as configured in mappeddomain.properties,internal domains and custom domain.</li>
	 * 	 <li>All key and values in hashtables(mentioned above) are of type String other than as mentioned</li>
	 * </ul>
	 */

	public static Hashtable getHits()
	{
		try
		{
			Hashtable params = new Hashtable();
			return getHits(params);
		}
		catch(Exception ex)
		{
			return (new Hashtable());
		}
	}

	/**
	 * To get details of domain hits, internal and default webengine hits through internal and external http,https ports
	 * @param params - list of params to be passed via runtime
	 * @return - <br> 
	 * Hashtable with entries<br><br>
	 *<ul style="list-style-type:circle">
	 *	<li>internal = details(Hashtable)</li>
	 * 	<li>default = details(Hashtable)</li>
	 * 	<li>domainname = details(Hashtable)</li>
	 * </ul>
	 *
	 * <br><br>
	 *
	 * Note :<br><br>
	 * <ul style="list-style-type:circle">
	 * 	<li>details 
	 * 		<ul style="list-style-type:disc">
	 * 			<li>internal = internaldetails(Hashtable)</li>
	 * 		      	<li>external = externaldetails(Hashtable)</li>
	 * 	              	<li>average=totalaveragehitstoday</li>
	 * 	        </ul>
	 * 	 </li>
	 * 	 <li>internaldetails 
	 * 	 	<ul style="list-style-type:disc">
	 * 	 		<li>http = hitstats(Hashtable)</li>
	 * 	 	</ul>
	 * 	 </li>
	 *       <li>externaldetails 
	 *       	<ul style="list-style-type:disc">
	 *       		<li>http = hitstats(Hashtable)</li>
	 *        		<li>https= hitstats(Hashtable)</li>
	 *        	</ul>
	 *        </li>
	 * 	 <li>hitstats 
	 * 	 	<ul style="list-style-type:disc">
	 * 	 		<li>total,today,hitrate,maxhitrate,maxhitratetime,maxhitrate2day and their respective string values</li>
	 * 	   	        <li>total - total no of hits</li>
	 * 	   	       	<li>today - total no of hits today</li>
	 * 	   	       	<li>hitrate - no of hits/min</li>
	 * 	   	       	<li>maxhitrate - maximum hit/min recorded</li> 
	 * 	   	       	<li>maxhitratetime - time at which maximum hit/min is recorded</li>
	 * 	   	       	<li>maxhitrate2day - maximum rate recorded today</li>
	 * 	   	</ul>
	 * 	 </li>
	 * 	 <li>Domain name as configured in mappeddomain.properties,internal domains and custom domain.</li>
	 * 	 <li>All key and values in hashtables(mentioned above) are of type String other than as mentioned</li>
	 * </ul>
	 */

	public static Hashtable getHits(Hashtable params)
	{
		try
		{
			return RuntimeAdmin.execute(AWSConstants.AWS_HITS,params);
		}
		catch(Exception ex)
		{
			return (new Hashtable());
		}
	}

	/**
         * To update HttpHits stats of the server to RuntimeStatsListener 
         * @param data - HttpHits stats collected for every single minute
        */

	public static void onHitsStats(Hashtable data)
	{
		if(statslistener != null)
		{
			statslistener.updateHitsStats(data);
		}
	}
	
	/**
	 * To get details of used, unused and heap memory in JVM
	 * @return - <br> 
	 * Hashtable with keys total,used,max,maxtime and their respective values.<br><br>
	 *<ul style="list-style-type:circle"> 
	 * 	     <li>total - total memory in bytes</li>
	 * 	     <li>used - used memory in bytes</li>
	 * 	     <li>max - max memory usage recorded in bytes</li>
	 * 	     <li>maxtime - time at which max memory usage recorded in milliseconds</li>
	 *</ul>
	 */

	public static Hashtable getMemoryStats()
	{
		try
		{
			Hashtable params = new Hashtable();
			return getMemoryStats(params);
		}
		catch(Exception ex)
		{
			return (new Hashtable());
		}
	}
	
	/**
	 * To get details of used, unused and heap memory in JVM
	 * @param params - list of params to be passed via runtime
	 * @return - <br> 
	 * Hashtable with keys total,used,max,maxtime and their respective values.<br><br>
	 *<ul style="list-style-type:circle"> 
	 * 	     <li>total - total memory in bytes</li>
	 * 	     <li>used - used memory in bytes</li>
	 * 	     <li>max - max memory usage recorded in bytes</li>
	 * 	     <li>maxtime - time at which max memory usage recorded in milliseconds</li>
	 *</ul>
	 */

	public static Hashtable getMemoryStats(Hashtable params)
	{
		try
		{
			return RuntimeAdmin.execute(AWSConstants.AWS_MEMINFO,params);
		}
		catch(Exception ex)
		{
			return (new Hashtable());
		}
	}

	/**
         * To update memory stats of the server to RuntimeStatsListener 
         * @param data - memory stats collected for every single minute 
        */

	public static void onMemoryStats(Hashtable data)
	{
		if(statslistener != null)
		{
			statslistener.updateMemoryStats(data);
		}
	}

	/**
	 * To get garbage collection stats such as gcname, collection count and time<br>
	 * @return - Hashtable gcname = gcdetails(collectioncount-collectiontime)<br>
	 */

	public static Hashtable getGCStats()
	{
		try
		{
			Hashtable params = new Hashtable();
			return getGCStats(params);
		}
		catch(Exception ex)
		{
			return (new Hashtable());
		}
	}
        
        /**
	 * To get garbage collection stats such as gcname, collection count and time<br>
	 * @return - Hashtable gcname = gcdetails(collectioncount-collectiontime)<br>
	 */

	public static Hashtable getGCStats(Hashtable params)
	{
		try
		{
			return RuntimeAdmin.execute(AWSConstants.AWS_GCPROFILER,params);
		}
		catch(Exception ex)
		{
			return (new Hashtable());
		}
	}

	/**
         * To update GC stats of the server to RuntimeStatsListener 
         * @param data - GC stats collected for every single minute 
        */

	public static void onGCStats(Hashtable data)
	{
		if(statslistener != null)
		{
			statslistener.updateGCStats(data);
		}
	}
	
	/**
         * To update CPU stats of the server to RuntimeStatsListener 
         * @param data - CPU info collected for every single minute 
        */

	public static void onCPUStats(Hashtable data)
	{
		if(statslistener != null)
		{
			statslistener.updateCPUStats(data);
		}
	}

	/**
         * To update Disk usage stats of the server to RuntimeStatsListener 
         * @param data - disk usage stats collected for every single minute 
        */

	public static void onDiskUsageStats(Hashtable data)
	{
		if(statslistener != null)
		{
			statslistener.updateDiskUsageStats(data);
		}
	}

	/**
	 * To get the detailed stats of the suspected ips
	 * @return - Hashtable ip = suspectcodes(Whose value may be 0 - SuspectCode.HEAVY_READ, 1 - SuspectCode.HEAVY_WRITE, 2 - SuspectCode.MALFORMED_HEADER, 4 - SuspectCode.UNKNOWN) <br> 
	 */

	public static Hashtable getSuspectedIPStats()
	{
		try
		{
			Hashtable params = new Hashtable();
			params.put(AWSConstants.OPR,AWSConstants.GET);
			return RuntimeAdmin.execute(AWSConstants.AWS_SUSPECTIPS,params);
		}
		catch(Exception ex)
		{
			return (new Hashtable());
		}
	}

	/**
	 * Returns strings identifying this pool, as well as its state, including indications of run state and estimated worker and task counts.
	 * @return hashtable of threadname and string identifying this pool, as well as its state
	 */

	public static Hashtable getThreadPoolStats()
	{
		return getThreadPoolStats(null);
	}

	/**
	 * Returns strings identifying this pool, as well as its state, including indications of run state and estimated worker and task counts.
	 * @param threadPoolName PoolName given during initialization
	 * @return hashtable of threadname and string identifying this pool, as well as its state
	 */

	public static Hashtable getThreadPoolStats(String threadPoolName)
	{
		try
		{
			if(threadPoolName != null && threadPoolName.length() > 0)
                        {
				Hashtable ht = new Hashtable();
				ht.put(threadPoolName, WMSTPExecutorFactory.getStats(threadPoolName));
				return ht;
			}
			else
			{
				return WMSTPExecutorFactory.getStats();
			}
		}
		catch(Exception ex)
		{
			return new Hashtable();
		}
	}

	/**
         * Returns the current number of threads in the pool.
         * @return the hashtable of threadname and number of threads
         */

	public static Hashtable getCurrentPoolSize()
	{
		return getCurrentPoolSize(null);
	}

	/**
         * Returns the current number of threads in the pool.
         * @param threadPoolName PoolName given during initialization
         * @return the hashtable of threadname and number of threads
         */

	public static Hashtable getCurrentPoolSize(String threadPoolName)
	{
		try
		{
			if(threadPoolName != null && threadPoolName.length() > 0)
                        {
				Hashtable ht = new Hashtable();
				ht.put(threadPoolName, WMSThreadPoolExecutorInventory.getCurrentPoolSize(threadPoolName));
				return ht;
			}
			else
			{
				return WMSTPExecutorFactory.getCurrentPoolSize();
			}
		}
		catch(Exception ex)
		{
			return new Hashtable();
		}
	}

	 /**
	 * Returns the approximate number of threads that are actively executing tasks.
	 * @return the hashtable of threadname and number of threads
	 */

	public static Hashtable getActiveCount()
	{
		return getActiveCount(null);
	}

	 /**
	 * Returns the approximate number of threads that are actively executing tasks.
	 * @param threadPoolName PoolName given during initialization
	 * @return the hashtable of threadname and number of threads
	 */

	public static Hashtable getActiveCount(String threadPoolName)
	{
		try
		{
			if(threadPoolName != null && threadPoolName.length() > 0)
                        {
				Hashtable ht = new Hashtable();
				ht.put(threadPoolName, WMSTPExecutorFactory.getActivePoolSize(threadPoolName));
				return ht;
			}
			else
			{
				return WMSTPExecutorFactory.getActivePoolSize();
			}
		}
		catch(Exception ex)
		{
			return new Hashtable();
		}
	}

	 /**
	 * Returns the approximate total number of tasks that have ever been scheduled for execution.
	 * @return the hashtable of threadname and number of tasks
	 */

	public static Hashtable getTotalTaskCount()
	{
		return getTotalTaskCount(null);
	}

	 /**
	 * Returns the approximate total number of tasks that have ever been scheduled for execution.
	 * @param threadPoolName PoolName given during initialization
	 * @return the hashtable of threadname and number of tasks
	 */

	public static Hashtable getTotalTaskCount(String threadPoolName)
	{
		try
		{
			if(threadPoolName != null && threadPoolName.length() > 0)
                        {
				Hashtable ht = new Hashtable();
				ht.put(threadPoolName, WMSTPExecutorFactory.getTotalTaskCount(threadPoolName));
				return ht;
			}
			else
			{
				return WMSTPExecutorFactory.getTotalTaskCount();
			}
		}
		catch(Exception ex)
		{
			return new Hashtable();
		}
	}

	 /**
	 * Returns the approximate total number of tasks that have completed execution.
	 * @return the hashtable of threadname and number of tasks
	 */

	public static Hashtable getCompletedTaskCount()
	{
		return getCompletedTaskCount(null);
	}

	 /**
	 * Returns the approximate total number of tasks that have completed execution.
	 * @param threadPoolName PoolName given during initialization
	 * @return the hashtable of threadname and number of tasks
	 */

	public static Hashtable getCompletedTaskCount(String threadPoolName)
	{
		try
		{
			if(threadPoolName != null && threadPoolName.length() > 0)
                        {
				Hashtable ht = new Hashtable();
				ht.put(threadPoolName, WMSTPExecutorFactory.getCompletedTaskCount(threadPoolName));
				return ht;
			}
			else
			{
				return WMSTPExecutorFactory.getCompletedTaskCount();
			}
		}
		catch(Exception ex)
		{
			return new Hashtable();
		}
	}

	 /**
	 * Returns the largest number of threads that have ever simultaneously been in the pool.
	 * @return the hashtable of threadname and number of threads
	 */

	public static Hashtable getLargestPool()
	{
		return getLargestPool(null);
	}

	 /**
	 * Returns the largest number of threads that have ever simultaneously been in the pool.
	 * @param threadPoolName PoolName given during initialization
	 * @return the hashtable of threadname and number of threads
	 */

	public static Hashtable getLargestPool(String threadPoolName)
	{
		try
		{
			if(threadPoolName != null && threadPoolName.length() > 0)
                        {
				Hashtable ht = new Hashtable();
				ht.put(threadPoolName, WMSThreadPoolExecutorInventory.getLargestPoolSize(threadPoolName));
				return ht;
			}
			else
			{
				return WMSTPExecutorFactory.getLargestPoolSize();
			}
		}
		catch(Exception ex)
		{
			return new Hashtable();
		}
	}

	/**
	 * Returns the number of additional elements that this queue can ideally (in the absence of memory or resource constraints) accept without blocking, or Integer.MAX_VALUE if there is no intrinsic limit.
	 * @return the hashtable of threadname and remaining capacity
	 */

	public static Hashtable getRemainingQueueCapacity()
	{
		return getRemainingQueueCapacity(null);
	}

	 /**
	 * Returns the number of additional elements that this queue can ideally (in the absence of memory or resource constraints) accept without blocking, or Integer.MAX_VALUE if there is no intrinsic limit.
	 * @param threadPoolName PoolName given during initialization
	 * @return the hashtable of threadname and remaining capacity
	 */

	public static Hashtable getRemainingQueueCapacity(String threadPoolName)
	{
		try
		{
			if(threadPoolName != null && threadPoolName.length() > 0)
			{
				Hashtable ht = new Hashtable();
				ht.put(threadPoolName, WMSTPExecutorFactory.getQueueRemainingCapacity(threadPoolName));
				return ht;
			}
			else
			{
				return WMSTPExecutorFactory.getQueueRemainingCapacity();
			}
		}
		catch(Exception ex)
		{
			return new Hashtable();
		}
	}
}
