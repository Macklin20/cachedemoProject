package com.zoho.wms.asyncweb.server.http2;

import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Iterator;

import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;

public class Http2StatsUpdater extends Thread
{
    public static Logger logger = Logger.getLogger(Http2StatsUpdater.class.getName());
    private static boolean active = false;

	public static void initialize()
	{
		if(isActive())
		{
			logger.log(Level.INFO, "Http2 Stats Updater thread is already running.");
			return;
		}
		active =true;
		new Http2StatsUpdater().start();
	}

	public static boolean isActive()
	{
		return active;
	}

	public static void stopThread()
	{
		active = false;
	}

	public void run()
    {
		logger.info("TimerUtil thread started!");//No I18n

		long nextMinuteInMillis = 0l;
		long timetosleep = 0l;

		while(active)
	    {
		    try
		    {
				nextMinuteInMillis = getNextMinuteInMillis();
				timetosleep = nextMinuteInMillis - System.currentTimeMillis() + 1;
				Thread.sleep(timetosleep);

				// Tasks to do every 1 min
				updateHttp2Stats();
				resetSocketTimeTakenStats();
		    }
		    catch(Exception ex)
		    {
				logger.log(Level.SEVERE, "Http2 Stats Updater thread has been stopped!", ex);//No I18n
			}
	    }
	    active = false;
	    logger.log(Level.INFO, "Http2 Stats Updater thread has been stopped!");//No I18n
    }

	private static long getNextMinuteInMillis()
	{
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MINUTE, cal.get(Calendar.MINUTE)+1);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis();
	}

    private void updateHttp2Stats()
    {
		if( ! ConfManager.isHttp2CounterStatsEnabled())
		{
			return;
		}

        try
        {
            long h2_active_conn_count = ConnectionManager.getActiveHttp2ConnectionCount();
            long h2_overall_active_stream_count = 0;
            long h2_overall_unprocessed_frame_count = 0;
            long h2_waitingstream_list_size = 0;

            ConcurrentHashMap<String, Http2Connection> connectionMap = ConnectionManager.getConnectionMap();
            for(Http2Connection con : connectionMap.values())
            {
                h2_overall_active_stream_count += con.getActiveStreamCount();
                h2_overall_unprocessed_frame_count += con.getUnProcessedFramesQueueSize();
                h2_waitingstream_list_size += con.getWaitingStreamListSize();
            }

            AWSInfluxStats.addHttp2CounterStats("h2_active_conn_count", h2_active_conn_count);
            AWSInfluxStats.addHttp2CounterStats("h2_overall_active_stream_count", h2_overall_active_stream_count);
            AWSInfluxStats.addHttp2CounterStats("h2_overall_unprocessed_frame_count", h2_overall_unprocessed_frame_count);
            AWSInfluxStats.addHttp2CounterStats("h2_waitingstream_list_size", h2_waitingstream_list_size);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "[Exception - Http2 StatsUpdater-updateHttp2Stats]", ex);
            AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STATSUPDATER_UPDATEHTTP2STATS);
        }
    }

    private void resetSocketTimeTakenStats()
    {
		if( ! ConfManager.isHttp2SocketTimeTakenStatsEnabled())
		{
			return;
		}

		try
		{
			Iterator<Http2Connection> itr = ConnectionManager.getConnectionMap().values().iterator();
			while(itr.hasNext())
			{
				itr.next().resetSocketTimeStats();
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 StatsUpdater-resetSocketTimeTakenStats]", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STATSUPDATER_RESETSOCKETTIMETAKENSTATS);
		}
    }




}
