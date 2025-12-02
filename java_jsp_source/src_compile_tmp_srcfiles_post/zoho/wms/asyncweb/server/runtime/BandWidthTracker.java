//$Id$
package com.zoho.wms.asyncweb.server.runtime;

import java.util.concurrent.atomic.AtomicLong;


public class BandWidthTracker
{
	private static AtomicLong exthttpread = new AtomicLong(0l); 	
	private static AtomicLong exthttpsread = new AtomicLong(0l); 	

	private static AtomicLong exthttpwrite = new AtomicLong(0l); 	
	private static AtomicLong exthttpswrite = new AtomicLong(0l); 

	private static AtomicLong exthttpreadnew = new AtomicLong(0l); 	
	private static AtomicLong exthttpsreadnew = new AtomicLong(0l); 	

	private static AtomicLong exthttpwritenew = new AtomicLong(0l); 	
	private static AtomicLong exthttpswritenew = new AtomicLong(0l); 

	public static void updateHttpRead(long bytes)
	{
		exthttpread.addAndGet(bytes);
		exthttpreadnew.addAndGet(bytes);
	}

	public static void updateHttpWrite(long bytes)
	{
		exthttpwrite.addAndGet(bytes);
		exthttpwritenew.addAndGet(bytes);
	}

	public static void updateHttpsRead(long bytes)
	{
		exthttpsread.addAndGet(bytes);
		exthttpsreadnew.addAndGet(bytes);
	}

	public static void updateHttpsWrite(long bytes)
	{
		exthttpswrite.addAndGet(bytes);
		exthttpswritenew.addAndGet(bytes);
	}

	public static long getExternalHttpRead()
	{
		return getExternalHttpRead(false);
	}

	public static long getExternalHttpRead(boolean isnew)
	{
		if(isnew)
		{
			return exthttpreadnew.longValue();
		}
		return exthttpread.longValue();
	}

	public static long getExternalHttpWrite()
	{
		return getExternalHttpWrite(false);
	}

	public static long getExternalHttpWrite(boolean isnew)
	{
		if(isnew)
		{
			return exthttpwritenew.longValue(); 
		}
		return exthttpwrite.longValue();
	}

	public static long getExternalHttpsRead()
	{
		return getExternalHttpsRead(false);
	}

	public static long getExternalHttpsRead(boolean isnew)
	{
		if(isnew)
		{
			return exthttpsreadnew.longValue();
		}
		return exthttpsread.longValue();
	}

	public static long getExternalHttpsWrite()
	{
		return getExternalHttpsWrite(false);
	}

	public static long getExternalHttpsWrite(boolean isnew)
	{
		if(isnew)
		{
			return exthttpswritenew.longValue();
		}
		return exthttpswrite.longValue();
	}

	public static void resetAll()
	{
		resetAll(false);
	}

	public static void resetAll(boolean resetnew)
	{
		if(resetnew)
		{
			exthttpreadnew = new AtomicLong(0l); 	
			exthttpsreadnew = new AtomicLong(0l); 
			exthttpwritenew = new AtomicLong(0l); 	
			exthttpswritenew = new AtomicLong(0l); 
		}
		else
		{
			exthttpread = new AtomicLong(0l); 	
			exthttpsread = new AtomicLong(0l); 
			exthttpwrite = new AtomicLong(0l); 	
			exthttpswrite = new AtomicLong(0l); 
		}
	}
}
