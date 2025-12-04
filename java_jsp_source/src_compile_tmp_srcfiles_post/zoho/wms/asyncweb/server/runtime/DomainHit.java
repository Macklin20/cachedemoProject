//$Id$
package com.zoho.wms.asyncweb.server.runtime;

import java.util.concurrent.atomic.AtomicLong;

public 	class DomainHit
{
//	private AtomicLong inthitcounter = new AtomicLong();
	private AtomicLong httphitcounter = new AtomicLong();
	private AtomicLong httpshitcounter = new AtomicLong();

//	private AtomicLong inthitcounternew = new AtomicLong();
	private AtomicLong httphitcounternew = new AtomicLong();
	private AtomicLong httpshitcounternew = new AtomicLong();

	private long httpprevsample = 0l;
	private long httphitstillyday = 0l;

	private long httphitrate = 0l;
	private long maxhttphitrate = 0l;
	private long maxhttphitratetime = 0l;
	private long maxhttphitrate2day = 0l;

	private long httpsprevsample = 0l;
	private long httpshitstillyday = 0l;

	private long httpshitrate = 0l;
	private long maxhttpshitrate = 0l;
	private long maxhttpshitratetime = 0l;
	private long maxhttpshitrate2day = 0l;

/*	private long intprevsample = 0l;
	private long inthitstillyday = 0l;
*/
	private long inthitrate = 0l;
/*	private long intmaxhitrate = 0l;
	private long intmaxhitratetime = 0l;
	private long intmaxhitrate2day = 0l;

	public void initHitParams()
	{
		intmaxhitratetime = System.currentTimeMillis();
		maxhttphitratetime = System.currentTimeMillis();
		maxhttpshitratetime = System.currentTimeMillis();
	}

	public void updateInternalHttpHit()
	{
		inthitcounter.incrementAndGet();
		inthitcounternew.incrementAndGet();
	}

	public long getInternalHttpHits()
	{
		return getInternalHttpHits(false);	
	}

	public long getInternalHttpHits(boolean isnew)
	{
		if(isnew)
		{
			return inthitcounternew.longValue(); 
		}
		return inthitcounter.longValue();	
	}
*/
	public long getInternalHttpHitRate()
	{
		return inthitrate;
	}

/*	public long getInternalHttpPrevSample()
	{
		return intprevsample;
	}

	public long getInternalMaxHttpHitRateToday()
	{
		return intmaxhitrate2day;
	}

	public long getInternalMaxHttpHitRate()
	{
		return intmaxhitrate;
	}

	public void setMaxInternalHttpHitRateToday(long rate)
	{
		intmaxhitrate2day = rate;
	}

	public void setMaxInternalHttpHitRate(long rate)
	{
		intmaxhitrate = rate;
	}

	public void setMaxIntHttpHitRateTime(long time)
	{
		intmaxhitratetime = time;
	}

	public long getMaxIntHttpHitRateTime()
	{
		return intmaxhitratetime;
	}

	public void setInternalHitRate(long hits)
	{
		this.inthitrate = hits;
	}

	public void setInternalHitPrevSample(long sample)
	{
		this.intprevsample = sample;
	}

	public void setInternalHttpHitsTillYDay(long hits)
	{
		inthitstillyday = hits;	
	}

	public long getInternalHttpHitsTillYDay()
	{
		return inthitstillyday;	
	}
*/
	public void updateExternalHttpHit()
	{

		httphitcounter.incrementAndGet();
		httphitcounternew.incrementAndGet();
	}


	public void updateExternalHttpsHit()
	{
		httpshitcounter.incrementAndGet();
		httpshitcounternew.incrementAndGet();
	}


	public long getExternalHttpHits()
	{
		return getExternalHttpHits(false);
	}

	public long getExternalHttpHits(boolean isnew)
	{
		if(isnew)
		{
			return httphitcounternew.longValue();	
		}
		return httphitcounter.longValue();	
	}

	public long getExternalHttpHitRate()
	{
		return httphitrate;
	}

	public long getExternalHttpPrevSample()
	{
		return httpprevsample;
	}

	public long getExternalMaxHttpHitRateToday()
	{
		return maxhttphitrate2day;
	}

	public long getExternalMaxHttpHitRate()
	{
		return maxhttphitrate;
	}

	public void setExternalHttpHitRate(long rate)
	{
		this.httphitrate = rate;
	}

	public void setExternalHttpHitPrevSample(long sample)
	{
		this.httpprevsample = sample;
	}

	public void setMaxExternalHttpHitRateToday(long rate)
	{
		maxhttphitrate2day = rate;
	}

	public void setMaxExternalHttpHitRate(long rate)
	{
		maxhttphitrate = rate;
	}

	public void setMaxExternalHttpHitRateTime(long time)
	{
		maxhttphitratetime = time;
	}

	public long getMaxExternalHttpHitRateTime()
	{
		return maxhttphitratetime;
	}

	public void setExternalHttpHitsTillYDay(long hits)
	{
		httphitstillyday = hits;
	}

	public long getExternalHttpHitsTillYDay()
	{
		return httphitstillyday;
	}

	public long getExternalHttpsHits()
	{
		return getExternalHttpsHits(false);
	}

	public long getExternalHttpsHits(boolean isnew)
	{
		if(isnew)
		{
			return httpshitcounternew.longValue();
		}
		return httpshitcounter.longValue();
	}

	public long getExternalHttpsHitRate()
	{
		return httpshitrate;
	}

	public long getExternalHttpsPrevSample()
	{
		return httpsprevsample;
	}

	public long getExternalMaxHttpsHitRateToday()
	{
		return maxhttpshitrate2day;
	}

	public long getExternalMaxHttpsHitRate()
	{
		return maxhttpshitrate;
	}

	public void setExternalHttpsHitRate(long rate)
	{
		this.httpshitrate = rate;
	}

	public void setExternalHttpsHitPrevSample(long sample)
	{
		this.httpsprevsample = sample;
	}

	public void setMaxExternalHttpsHitRateToday(long rate)
	{
		maxhttpshitrate2day = rate;
	}

	public void setMaxExternalHttpsHitRate(long rate)
	{
		maxhttpshitrate = rate;
	}

	public void setMaxExternalHttpsHitRateTime(long time)
	{
		maxhttpshitratetime = time;
	}

	public long getMaxExternalHttpsHitRateTime()
	{
		return maxhttpshitratetime;
	}

	public void setExternalHttpsHitsTillYDay(long hits)
	{
		httpshitstillyday = hits;
	}

	public long getExternalHttpsHitsTillYDay()
	{
		return httpshitstillyday;
	}

	public void resetHits()
	{
		resetHits(false);
	}

	public void resetHits(boolean isnew)
	{
		if(isnew)
		{
			httphitcounternew.set(0);
			httpshitcounternew.set(0);
//			inthitcounternew.set(0); 
		}
		else
		{
			httphitcounter.set(0);
			httpshitcounter.set(0);
//			inthitcounter.set(0);
		}
	}
}
