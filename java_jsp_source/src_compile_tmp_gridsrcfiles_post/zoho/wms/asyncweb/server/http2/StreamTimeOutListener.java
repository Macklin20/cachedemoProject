//$Id$
package com.zoho.wms.asyncweb.server.http2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.TimeOutListener;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;

public class StreamTimeOutListener extends TimeOutListener
{
	private static AsyncLogger logger = new AsyncLogger(StreamTimeOutListener.class.getName());
	public static final StreamTimeOutListener TRACKER = new StreamTimeOutListener();

	private StreamTimeOutListener()
	{
		super("StreamTimeOutListener", ConfManager.getStreamTrackerInterval());
	}

	public boolean isExpired(Object obj)
	{
		return (System.currentTimeMillis() >= (((Http2Stream)obj).getExpireTime()));
	}

	public boolean isInvalidEntry(Object obj, long time)
	{
		return (((Http2Stream)obj).isInvalidStreamTimeoutEntry(time));
	}

	public void handleExpired(ArrayList list)
	{
		List<Integer> streamIdList = new ArrayList<>();
		for(Iterator it = list.iterator(); it.hasNext();)
		{
			Http2Stream stream = (Http2Stream) it.next();
			streamIdList.add(stream.getStreamID());
			stream.close();
		}
//		logger.log(Level.INFO, "AWS - Http2 Stream timeout --> expiredlist size : "+list.size()+" StreamIDs:"+streamIdList);
	}
}
