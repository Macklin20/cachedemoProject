//$Id$
package com.zoho.wms.asyncweb.server;

// Java import
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Comparator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

public abstract class TimeOutListener extends Thread
{

	private static AsyncLogger logger = new AsyncLogger(TimeOutListener.class.getName());

	private final Integer PUT = new Integer(1);
	private final Integer REMOVE = new Integer(2);

	private LinkedBlockingQueue<HashMap> queue = new LinkedBlockingQueue();
	private SortedMap lru = new TreeMap(new CustomWmsComparator()); 
	private static AtomicLong counter = new AtomicLong(0);

	private Tracker tracker;

	protected TimeOutListener(String thdName, long interval)
	{
		super(AWSConstants.AWS_THREAD_PREFIX+thdName+AWSConstants.THREAD_NAME_SEPARATOR+"queue"+AWSConstants.THREAD_NAME_SEPARATOR+counter.getAndIncrement());//No I18n
		this.start();
		tracker = new Tracker(thdName+"-tracker", interval);
		tracker.start();
	}

	public abstract boolean isExpired(Object obj);

	public abstract boolean isInvalidEntry(Object obj, long time);

	public abstract void handleExpired(ArrayList list);

	public void touch(long time,Object obj)
	{
		try
		{
			HashMap entry = new HashMap();
			entry.put(AWSConstants.OBJ,new TimeOutReferenceObject(time,obj));
			entry.put(AWSConstants.OPR, PUT);
			queue.put(entry);
		}
		catch(Exception e)
		{
			logger.log(Level.WARNING,"Error queueing in TimeOutListener - touch",TimeOutListener.class.getName(),AWSLogMethodConstants.TOUCH);//No I18N
		}
		
	}

	public void remove(long time,Object obj)
	{
		try
		{
			HashMap entry = new HashMap();
			entry.put(AWSConstants.OBJ,new TimeOutReferenceObject(time,obj));
			entry.put(AWSConstants.OPR, REMOVE);
			queue.put(entry);
		}
		catch(Exception e)
		{
			logger.log(Level.WARNING,"Error queueing in TimeOutListener - remove",TimeOutListener.class.getName(),AWSLogMethodConstants.REMOVE);//NO I18N
		}
	}

	public void update(long oldtime, long newtime, Object obj)
	{
		try
		{
			HashMap rementry = new HashMap();
			rementry.put(AWSConstants.OBJ,new TimeOutReferenceObject(oldtime,obj));
			rementry.put(AWSConstants.OPR, REMOVE);
			queue.put(rementry);

			HashMap addentry = new HashMap();
			addentry.put(AWSConstants.OBJ,new TimeOutReferenceObject(newtime,obj));
			addentry.put(AWSConstants.OPR, PUT);
			queue.put(addentry);
		}
		catch(Exception e)
		{
			logger.log(Level.WARNING,"Error queueing in TimeOutListener - remove",TimeOutListener.class.getName(),AWSLogMethodConstants.UPDATE);//No I18N
		}	
	}


	public void run()
	{
		while(true)
		{
			try
			{
				HashMap entry = queue.take();					
				synchronized(lru)
				{
					if(((Integer)entry.get(AWSConstants.OPR))==PUT)
					{
						//logger.log(Level.INFO,"--------------------------------------------PUT START ---------------------------------------------");
						lru.put(entry.get(AWSConstants.OBJ), "");
						//logger.log(Level.INFO,"--------------------------------------------PUT END :: "+lru.size()+"-------------------------------------------------");
						//logger.log(Level.INFO,"After PUT :: LRU Size "+lru.size()+"*******");
					}
					else
					{
						lru.remove(entry.get(AWSConstants.OBJ));
						//logger.log(Level.INFO,"After Remove :: LRU Size "+lru.size()+"*******");
					}
				}
			}
			catch(Exception e)
			{
				logger.log(Level.INFO, " Exception ",TimeOutListener.class.getName(),AWSLogMethodConstants.RUN, e);
			}
		}
	}
	
	class Tracker extends Thread
	{
		private long interval = 0;

		public Tracker(String thdName, long interval)
		{
			super(AWSConstants.AWS_THREAD_PREFIX+thdName);
			this.interval = interval;
		}


		public void run()
		{
			while(true)
			{
				try
				{
					this.sleep(interval);
					ArrayList expList = new ArrayList();
					synchronized(lru)
					{						
						//logger.log(Level.INFO,"--------------------------------------------HEADMAP START-------------------------------------------------");
						TimeOutReferenceObject headRef = (TimeOutReferenceObject)getReferenceObject();
						SortedMap expiredMap = lru.headMap(headRef);
						//logger.log(Level.INFO,"ExpiredMap Size :: "+expiredMap.size()+" LRU Size :: "+lru.size());
						//logger.log(Level.INFO,"--------------------------------------------HEADMAP END-------------------------------------------------");
						
						for(Iterator it = expiredMap.keySet().iterator(); it.hasNext();)
						{
							Object obj = it.next();
							TimeOutReferenceObject queObj= (TimeOutReferenceObject)obj;
							try
							{
								Object expObj = queObj.getObject();
								if(isExpired(expObj))
								{								
									expList.add(expObj);
									it.remove();
								}
								else if(isInvalidEntry(expObj,queObj.getExpireTime()))
								{
									it.remove();	
								}
								/*else
								{
									break;
								}*/
							}
							catch(Exception e1)
							{
								logger.log(Level.INFO, " Exception ",Tracker.class.getName(),AWSLogMethodConstants.RUN, e1);
							}
						}						
					}					
					if(expList.size()>0)
					{
						//logger.log(Level.INFO,"Exp List :: "+expList.size());
						handleExpired(expList);
					}
				}
				catch(Exception e)
				{
					logger.log(Level.INFO, " Exception ",Tracker.class.getName(),AWSLogMethodConstants.RUN, e);
				}
			}
		}
	}

	private Object getReferenceObject()
	{
		return new TimeOutReferenceObject(System.currentTimeMillis(),Long.MAX_VALUE);
	}

	class CustomWmsComparator implements Comparator
	{
		public int compare(Object o1,Object o2)
		{
			
			TimeOutReferenceObject val1 = (TimeOutReferenceObject)o1;
			TimeOutReferenceObject val2 = (TimeOutReferenceObject)o2;
			//logger.log(Level.INFO,"val1 "+val1+" val2 "+val2);

			long time1 = val1.getExpireTime();
			long time2 = val2.getExpireTime();

			if(time1 > time2 )
			{
				//logger.log(Level.INFO,"Compare Return 1");
				return 1;
			}
			if(time1 < time2 )
			{
				//logger.log(Level.INFO,"Compare Return -1");
				return -1;
			}
			if(time1 == time2)
			{
				Object obj1 = val1.getObject();
				Object obj2 = val2.getObject();
				if(obj1 instanceof Long && obj2 instanceof Long)
				{
					long id1 = ((Long)obj1).longValue();
					long id2 = ((Long)obj2).longValue();

					if(id1 == id2)
					{
						return 0;
					}
					if(id1 > id2)
					{
						return 1;
					}
					if(id2 > id1)
					{
						return -1;
					}
				}
				else
				{
					if(obj1.hashCode() == obj2.hashCode())
					{
						return 0;	
					}
					if(obj1.hashCode() > obj2.hashCode())
					{
						return 1;
					}
					if(obj2.hashCode() > obj1.hashCode())
					{
						return -1;
					}
				}
			}
			//logger.log(Level.INFO,"Compare Return -1");
			return -1;
		}

		public boolean equals(Object obj)
		{
			return false;
		}
	}

	class TimeOutReferenceObject
	{
		private Object obj;
		private long time;

		public TimeOutReferenceObject(long time, Object obj)
		{
			this.time = time;
			this.obj = obj;
		}

		public long getExpireTime()
		{
			return time;
		}

		public Object getObject()
		{
			return obj;
		}
		
		public String toString()
		{
			return "Exp Time :: "+time+" , Object :: "+obj.toString();
		}
	}
}
