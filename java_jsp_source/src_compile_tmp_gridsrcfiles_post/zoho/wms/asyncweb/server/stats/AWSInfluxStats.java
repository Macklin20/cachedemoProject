//$Id$
package com.zoho.wms.asyncweb.server.stats;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

import com.adventnet.wms.servercommon.dc.DC;
import com.adventnet.wms.servercommon.stats.influx.StatsDB;
import com.adventnet.wms.servercommon.stats.influx.conf.StatsConf;

import com.zoho.wms.asyncweb.server.AbstractWebEngine;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.WebEngine;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.http2.Http2Constants;

public class AWSInfluxStats
{
	private static Logger logger = Logger.getLogger(AWSInfluxStats.class.getName());
	private static Logger msgtracelogger = Logger.getLogger("msgtracelogger");

	private static boolean initialized = false;

	static
	{
		initStatsDefinition();
	}

	public static void initStatsDefinition()
	{
		try
		{
			if(initialized)
			{
				logger.log(Level.INFO, "Stat Definition already initialized...");//No I18m
				return;
			}

			if(!ConfManager.isQOSEnabled() || DC.getServertype() == null)
			{
				logger.log(Level.INFO, "Non-WMS team, hence stat def not initialized.");//No I18n
				return;
			}

			Hashtable<String,String> statsDefs = new Hashtable<String,String>();

			statsDefs.put(AWSConstants.AWS_REQ_PROC,"[[\"qos\",\"qos_awsframework\",\"\",\"aws_req_proc\",false],[\"servertype\",\"cluster\"],[],[\"active_thread\",\"queue_size\",\"executor_count\"]]");
			statsDefs.put(AWSConstants.AWS_NETDATA_PROC,"[[\"qos\",\"qos_awsframework\",\"\",\"aws_netdata_proc\",false],[\"servertype\",\"cluster\"],[],[\"active_thread\",\"queue_size\",\"executor_count\"]]");
			statsDefs.put(AWSConstants.AWS_WEBENGINE,"[[\"qos\",\"qos_awsframework\",\"\",\"aws_webengine\",false],[\"servertype\",\"cluster\",\"engine_name\"],[],[\"active_thread\",\"queue_size\",\"executor_count\"]]");
			statsDefs.put(AWSConstants.AWS_REQUEST_STATS,"[[\"qos\",\"qos_awsframework\",\"\",\"aws_request_stats\",false],[\"servertype\",\"cluster\",\"req_uri\",\"response_code\",\"webenginename\",\"type\"],[],[\"timetaken\",\"count\"]]");
			statsDefs.put(AWSConstants.AWS_INFLATE,"[[\"qos\",\"qos_awsframework\",\"\",\"inflate\",false],[\"servertype\",\"cluster\"],[],[\"compSize\",\"inflatedSize\",\"timetaken\",\"count\"]]");
			statsDefs.put(AWSConstants.AWS_DEFLATE,"[[\"qos\",\"qos_awsframework\",\"\",\"deflate\",false],[\"servertype\",\"cluster\"],[],[\"orgSize\",\"deflatedSize\",\"timetaken\",\"count\"]]");
			statsDefs.put(AWSConstants.HEAVY_DATA,"[[\"qos\",\"qos_awsframework\",\"\",\"heavy_data\",false],[\"servertype\",\"cluster\",\"websocket\",\"type\"],[],[\"count\"]]");
			statsDefs.put(AWSConstants.HACK_ATTEMPT,"[[\"qos\",\"qos_awsframework\",\"\",\"hack_attempt\",false],[\"servertype\",\"cluster\",\"type\"],[],[\"count\"]]");
			statsDefs.put(AWSConstants.AWS_SERVERPORTMONITOR_STATS,"[[\"qos\",\"qos_awsframework\",\"\",\"aws_serverportmonitor\",false],[\"servertype\",\"cluster\",\"port\",\"retrycount\",\"failurecount\"],[],[\"count\"]]");
			statsDefs.put(AWSConstants.WRITE,"[[\"qos\",\"qos_awsframework\",\"\",\"aws_Writtendata\",false],[\"servertype\",\"cluster\",\"isSsl\",\"size\"],[],[\"count\"]]");
			statsDefs.put(AWSConstants.READ,"[[\"qos\",\"qos_awsframework\",\"\",\"aws_readdata\",false],[\"servertype\",\"cluster\",\"isSsl\",\"isStreamData\",\"size\"],[],[\"count\"]]");

			statsDefs.put(Http2Constants.STATS_H2CONN,"[[\"qos\",\"qos_awsframework\",\"\",\"h2_conn\",false],[\"servertype\",\"cluster\",\"operation\"],[],[\"count\"]]");
			statsDefs.put(Http2Constants.STATS_H2STREAM,"[[\"qos\",\"qos_awsframework\",\"\",\"h2_stream\",false],[\"servertype\",\"cluster\",\"operation\"],[],[\"count\"]]");
			statsDefs.put(Http2Constants.STATS_H2FRAME,"[[\"qos\",\"qos_awsframework\",\"\",\"h2_frame\",false],[\"servertype\",\"cluster\",\"operation\",\"frametype\"],[],[\"count\"]]");
			statsDefs.put(Http2Constants.STATS_H2EXP,"[[\"qos\",\"qos_awsframework\",\"\",\"h2_exp\",false],[\"servertype\",\"cluster\",\"operation\"],[],[\"count\"]]");
			statsDefs.put(Http2Constants.STATS_HTTP2_COUNTER,"[[\"qos\",\"qos_awsframework\",\"\",\"http2_counter\",false],[\"servertype\",\"cluster\",\"type\"],[],[\"count\"]]");

			statsDefs.put(Http2Constants.STATS_H2_COMPRESSION,"[[\"qos\",\"qos_awsframework\",\"\",\"h2_comp_stats\",false],[\"servertype\",\"cluster\",\"type\"],[],[\"data_size\",\"comp_size\"]]");
			statsDefs.put(Http2Constants.STATS_CLIENT_CONN,"[[\"qos\",\"qos_awsframework\",\"\",\"client_conn\",false],[\"servertype\",\"cluster\",\"scheme\"],[],[\"count\"]]");
			statsDefs.put(Http2Constants.STATS_HTTP_REQUEST,"[[\"qos\",\"qos_awsframework\",\"\",\"http_request\",false],[\"servertype\",\"cluster\",\"reqtype\",\"scheme\",\"version\"],[],[\"count\"]]");
			statsDefs.put(Http2Constants.STATS_HTTP_BANDWIDTH,"[[\"qos\",\"qos_awsframework\",\"\",\"http_bandwidth\",false],[\"servertype\",\"cluster\",\"scheme\",\"version\",\"operation\"],[],[\"count\"]]");
			statsDefs.put(Http2Constants.STATS_HTTP_CONNECTION_REUSE,"[[\"qos\",\"qos_awsframework\",\"\",\"http_connection_reuse\",false],[\"servertype\",\"cluster\",\"version\",\"scheme\"],[],[\"count\"]]");

			if(StatsConf.loadStatsKeyDef(statsDefs))
			{
				logger.log(Level.INFO, "AWS - StatsKeyDef Loaded successfully.");//No I18n
				StatsDB.initialize();
				logger.log(Level.INFO, "AWS - StatsDB Init successfully.");//No I18n
				initialized = true;
			}
			logger.log(Level.INFO, "AWS - Influx Stat def init successfully...");//No I18n
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception in AWS StatsKeyDef initialization : ", ex);//No I18n
		}
	}

	public static void addAWSRequestStats(String requestUri, Integer responseCode, String webengineName, String type, Long timetaken, Integer count)
	{
		if(responseCode > 0)
		{
			addAWSRequestStats(requestUri, ""+responseCode, webengineName, type, timetaken, count);
		}
		else
		{
			addAWSRequestStats(requestUri, AWSConstants.NA, webengineName, type, timetaken, count);
		}
	}

	public static void addAWSRequestStats(String requestUri, String responseCode, String webengineName, String type, Long timetaken, Integer count)
	{
		try
		{
			if(initialized && ConfManager.isQOSEnabled() && !ConfManager.isInfluxStatsExcludedURL(requestUri))
			{
				if (requestUri == null)
				{
					requestUri = AWSConstants.NA;
				}

				if (webengineName == null)
				{
					webengineName = AWSConstants.NA;
				}
				else
				{
					try
					{
						if(webengineName.endsWith(AWSConstants.HYPHEN_WSMSG))
						{
							webengineName = webengineName.replace(AWSConstants.HYPHEN_WSMSG, AWSConstants.EMPTY_STRING);
						}
						AbstractWebEngine engine = WebEngine.getEngineByAppName(webengineName);
						if(!requestUri.equals(AWSConstants.NA) && !engine.isURLPresent(requestUri))
						{
							requestUri = AWSConstants.UN_KNOWN;
						}
					}
					catch(Exception ex)
					{
						requestUri = AWSConstants.UN_KNOWN;
					}
				}
				StatsDB.addData(AWSConstants.AWS_REQUEST_STATS, DC.getServertype(), DC.getCluster(), requestUri, responseCode, webengineName, type, timetaken, count);//No I18n
			}
		}
		catch(Exception ex)
		{
		}
	}

	public static void updateThreadStat(String statName, ArrayList<Integer> activeCountList, ArrayList<Long> queuedTaskList)
	{
		updateThreadStat(statName, activeCountList, queuedTaskList, null);
	}

	public static void updateThreadStat(String statName, ArrayList<Integer> activeCountList, ArrayList<Long> queuedTaskList, String engineName)
	{
		try
		{
			if(initialized && ConfManager.isQOSEnabled())
			{
				Iterator iterator = activeCountList.iterator();
				for(int i = 0; iterator.hasNext(); i++)
				{
					if(engineName != null)
					{
						StatsDB.addData(statName, DC.getServertype(), DC.getCluster(), engineName, activeCountList.get(i), queuedTaskList.get(i), 1);
					}
					else
					{
						StatsDB.addData(statName, DC.getServertype(), DC.getCluster(), activeCountList.get(i), queuedTaskList.get(i), 1);
					}
				}
			}
		}
		catch(Exception ex)
		{
		}
	}
	
	public static void updateWSCompressionStats(String statsName, int datalength, int outputlength, long stimetaken)
	{
		if(initialized && ConfManager.isCompressionStatsEnabled())
		{
			try
			{
				StatsDB.addData(statsName, DC.getServertype(), DC.getCluster(), datalength, outputlength, stimetaken, 1);//No I18n
			}
			catch(Exception e)
			{
			}
		}
	}

	public static void addHeavyDataStats(boolean websocket, String type)
	{
		if(initialized && ConfManager.isHeavyDataStatsEnabled())
		{
			try
			{
				StatsDB.addData(AWSConstants.HEAVY_DATA, DC.getServertype(), DC.getCluster(), websocket, type, 1);//No I18n
			}
			catch(Exception e)
			{
			}
		}
	}

	public static void addWrittenlength(String isSsl, String size)
        {
                if(initialized)
                {
                        try
                        {
                                StatsDB.addData(AWSConstants.WRITE, DC.getServertype(), DC.getCluster(),isSsl, size, 1);//No I18n
                        }
                        catch(Exception e)
                        {
                        }
                }
        }

	public static void addreadlength(String isSsl, boolean isStreamData , String size)
	{
		if(initialized)
		{
			try
			{
				StatsDB.addData(AWSConstants.READ, DC.getServertype(), DC.getCluster(), isSsl,isStreamData, size, 1);//No I18n
			}
			catch(Exception e)
			{
			}
		}
	}

	public static void addHackAttemptStats(String type)
	{
		if(initialized && ConfManager.isHackAttemptStatsEnabled())
		{
			try
			{
				StatsDB.addData(AWSConstants.HACK_ATTEMPT, DC.getServertype(), DC.getCluster(), type, 1);//No I18n
			}
			catch(Exception e)
			{
			}
		}
	}

	public static void addHttp2Stats(String stat_type, String... args)
	{
		if(initialized && ConfManager.isQOSEnabled() && ConfManager.isHttp2Enabled())
		{
			try
			{
				switch (stat_type)
				{
					case Http2Constants.STATS_H2CONN: StatsDB.addData(Http2Constants.STATS_H2CONN, DC.getServertype(), DC.getCluster(), args[0], 1); break;
					case Http2Constants.STATS_H2STREAM: StatsDB.addData(Http2Constants.STATS_H2STREAM, DC.getServertype(), DC.getCluster(), args[0], 1); break;
					case Http2Constants.STATS_H2FRAME: StatsDB.addData(Http2Constants.STATS_H2FRAME, DC.getServertype(), DC.getCluster(), args[0], args[1], 1); break;
					case Http2Constants.STATS_H2EXP: StatsDB.addData(Http2Constants.STATS_H2EXP, DC.getServertype(), DC.getCluster(), args[0], 1); break;
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE, "Http2 Stats Exception", ex);
			}
		}
	}

	public static void addHttp2CounterStats(String type, long count) //revisit 4
	{
		if(initialized && ConfManager.isQOSEnabled())
		{
			try
			{
				StatsDB.addData(Http2Constants.STATS_HTTP2_COUNTER, DC.getServertype(), DC.getCluster(), type, count);
			}
			catch (Exception ex)
			{
				logger.log(Level.SEVERE, "Http2 Counter Stats Exception", ex);
			}
		}
	}

	public static void addHttp2HeaderCompressionStats(String type, long data_size, long compressed_size)
	{
		if(initialized && ConfManager.isQOSEnabled() && ConfManager.isHttp2Enabled())
		{
			try
			{
				StatsDB.addData(Http2Constants.STATS_H2_COMPRESSION, DC.getServertype(), DC.getCluster(), type, data_size, compressed_size);
			}
			catch (Exception ex)
			{
				logger.log(Level.SEVERE, "Http2 Header Compression Stats Exception", ex);
			}
		}
	}

	public static void addClientConnectionStats(String scheme)
	{
		if(initialized && ConfManager.isQOSEnabled())
		{
			try
			{
				StatsDB.addData(Http2Constants.STATS_CLIENT_CONN, DC.getServertype(), DC.getCluster(), scheme, 1);
			}
			catch (Exception ex)
			{
				logger.log(Level.SEVERE, "Client Connection Stats Exception", ex);
			}
		}
	}

	public static void addHttpRequestStats(String reqtype, String scheme, String version)
	{
		if(initialized && ConfManager.isQOSEnabled())
		{
			try
			{
				StatsDB.addData(Http2Constants.STATS_HTTP_REQUEST, DC.getServertype(), DC.getCluster(), reqtype, scheme, version, 1);
			}
			catch (Exception ex)
			{
				logger.log(Level.SEVERE, "Http Request Stats Exception", ex);
			}
		}
	}

	public static void addHttpBandWidthStats(String scheme, boolean isHttp2, String operation, long count)
	{
		if(initialized && ConfManager.isQOSEnabled())
		{
			try
			{
				String version = isHttp2 ? Http2Constants.HTTP2_VERSION : AWSConstants.HTTP_1_1;
				StatsDB.addData(Http2Constants.STATS_HTTP_BANDWIDTH, DC.getServertype(), DC.getCluster(), scheme, version, operation, count);
			}
			catch (Exception ex)
			{
				logger.log(Level.SEVERE, "Http BandWidth Stats Exception", ex);
			}
		}
	}

	public static void addHttpConnectionReuseStat(String version, String scheme)
	{
		if(initialized && ConfManager.isQOSEnabled())
		{
			try
			{
				StatsDB.addData(Http2Constants.STATS_HTTP_CONNECTION_REUSE, DC.getServertype(), DC.getCluster(), version, scheme, 1);
			}
			catch (Exception ex)
			{
				logger.log(Level.SEVERE, "Http BandWidth Stats Exception", ex);
			}
		}
	}

	public static void addServerPortMonitorStats(String port, int retrycount,int failurecount)
	{
		if(initialized && ConfManager.isQOSEnabled())
		{
			try
			{
				StatsDB.addData(AWSConstants.AWS_SERVERPORTMONITOR_STATS,DC.getServertype(),DC.getCluster(),port,retrycount,failurecount,1);
			}
			catch(Exception e)
			{
			}
		}
	}

	public static void updateWmsThreadStats(String statsName, int activeCount, int queueSize)
	{
		if(initialized && ConfManager.isQOSEnabled())
		{
			try
			{
				StatsDB.addData(statsName, DC.getServertype(), DC.getCluster(), activeCount, queueSize, 1);
			}
			catch(Exception e)
			{
			}
		}
	}

	public static void updateWmsThreadStats(String statsName, int activeCount, int queueSize, String engineName)
	{
		if(initialized && ConfManager.isQOSEnabled())
		{
			try
			{
				StatsDB.addData(statsName, DC.getServertype(), DC.getCluster(), engineName, activeCount, queueSize, 1);
			}
			catch(Exception e)
			{
			}
		}
	}
}
