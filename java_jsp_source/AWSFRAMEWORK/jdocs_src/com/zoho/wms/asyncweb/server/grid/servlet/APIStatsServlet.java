package com.zoho.wms.asyncweb.server.grid.servlet;

//java imports
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentHashMap;

//aws imports
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.HttpStreamServlet;

//servercommon imports
import com.adventnet.wms.servercommon.dc.DC;
import com.adventnet.wms.servercommon.stats.influx.StatsDB;
import com.adventnet.wms.servercommon.stats.influx.conf.StatsConf;
import com.adventnet.wms.servercommon.stats.influx.StatsDispatchManager;

public class APIStatsServlet extends HttpStreamServlet
{
        private static final Logger LOGGER = Logger.getLogger(APIStatsServlet.class.getName());

        private static final ConcurrentHashMap<String, ByteArrayOutputStream> BYTEARRAY_STREAM_MAP = new ConcurrentHashMap<>();

        static
        {
                Hashtable<String, String> statsDefs = new Hashtable<>();
                statsDefs.put("aws_api_stats", "[[qos,qos_statsdb_api,,aws_api,false],[cluster],[],[in,out,isavailable_issue,isfinished_issue]]");
                StatsConf.loadStatsKeyDef(statsDefs);
        }

        @Override
        public void onHeaderCompletion(HttpRequest req, HttpResponse res) throws Exception
        {
                if (!StatsConf.isInitialized())
                {
                        res.close();
                        return;
                }

                String conLength = req.getHeader("con-length");
                String contentLength = req.getHeader("content-length");
                if (conLength != null)
                {
                        res.setInputDataSize(Integer.parseInt(conLength));
                }
                else if (contentLength != null)
                {
                        res.setInputDataSize(Integer.parseInt(contentLength));
                }
                else
                {
                        res.setInputDataSize(-1);
                }
        }

        public void onData(HttpRequest request, HttpResponse response)
        {
                ByteArrayOutputStream baos;
                String reqId = response.getReqId();

                if (BYTEARRAY_STREAM_MAP.containsKey(reqId))
                {
                        baos = BYTEARRAY_STREAM_MAP.get(reqId);           //Existing stream
                }
                else
                {
                        baos = new ByteArrayOutputStream();
                        BYTEARRAY_STREAM_MAP.put(reqId, baos);            //New stream - adding it in map
                        StatsDB.addData("aws_api_stats", DC.getCluster(), 1, 0, 0, 0);
                }

                try
                {
                        String statType = request.getHeader("stattype");
                        String database = request.getHeader("database");
                        String retentionPolicy = request.getHeader("rp");

                        if (statType == null)
                        {
                                statType = "qos";               //No I18N
                        }

                        try
                        {
                                if (StatsConf.getWnet(statType) == null)        //if statType is not present in statConf, close the response
                                {
                                        response.close();
                                        return;
                                }
                        }
                        catch (Exception ignored)
                        {
                        }

                        try
                        {
                                while (request.getHttpStream().isAvailable())
                                {
                                        byte[] influxData = request.getHttpStream().read();
                                        baos.write(influxData, 0, influxData.length);
                                }
                        }
                        catch (Exception e)
                        {
                                StatsDB.addData("aws_api_stats", DC.getCluster(), 0, 0, 1, 0);
                                response.close();
                                return;
                        }

                        try
                        {
                                if (request.getHttpStream().isFinished())
                                {
                                        byte[] overallData = BYTEARRAY_STREAM_MAP.remove(reqId).toByteArray();
                                        StatsDispatchManager.dispatch(database, retentionPolicy, statType, overallData, null, null, false);

                                        StatsDB.addData("aws_api_stats", DC.getCluster(), 0, 1, 0, 0);

                                        response.commitChunkedTransfer();
                                        response.close();
                                }
                        }
                        catch (Exception e)
                        {
                                StatsDB.addData("aws_api_stats", DC.getCluster(), 0, 0, 0, 1);
                        }
                }
                catch (Exception e)
                {
                        LOGGER.log(Level.SEVERE, "PS --> Error in onData()", e);
                }
        }
}