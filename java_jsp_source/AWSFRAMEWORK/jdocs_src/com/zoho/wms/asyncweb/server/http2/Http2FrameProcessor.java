package com.zoho.wms.asyncweb.server.http2;

import java.io.Serializable;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;

import com.adventnet.wms.servercommon.components.executor.WMSTPExecutorFactory;
import com.adventnet.wms.servercommon.components.executor.WmsTask;

public class Http2FrameProcessor
{
    private static AsyncLogger logger = new AsyncLogger(Http2FrameProcessor.class.getName());

    static
    {
        initialize();
    }

    private static void initialize()
    {
        try
        {
            //Default values: core:10, max:50, threadcreationlimit:100
            WMSTPExecutorFactory.createNewExecutor(AWSConstants.HTTP2_FRAMEPROCESSOR, ConfManager.getHttp2FrameProcessorCorePoolSize(), ConfManager.getHttp2FrameProcessorMaxPoolSize(), 1000, new Http2FrameDispatcher(), ConfManager.getHttp2FrameProcessorThreadCreationLimit());
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Exception in TPExecutorFactory Initialisation :: ", ex);//No I18n
        }
    }

    static class Event implements Serializable
    {
        private String http2ConnID;

        public Event(String http2ConnID)
        {
            this.http2ConnID = http2ConnID;
        }

        public String getHttp2ConnID()
        {
            return http2ConnID;
        }
    }

    static class Http2FrameDispatcher implements WmsTask
    {
        @Override
        public void handle(Object obj)
        {
            try
            {
                Event event = (Event) obj;
                handleProcess(event.getHttp2ConnID());
            }
            catch(Exception e)
            {
                logger.log(Level.SEVERE, "Exception in Http2FrameDispatcherEvent :: ", e);
            }
        }
    }

    private static void handleProcess(String http2ConnID)
    {
        try
        {
            ConnectionManager.getConnection(http2ConnID).notifyProcessFrame();
        }
        catch (Exception ex)
        {
            logger.log(Level.SEVERE, "Exception in Http2FrameProcessor - handleProcess :: http2ConnID:"+http2ConnID, ex);//No I18n
        }
    }

    public static void process(String http2ConnID)
    {
        try
        {
            WMSTPExecutorFactory.execute(AWSConstants.HTTP2_FRAMEPROCESSOR, new Event(http2ConnID));
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Exception in Http2FrameProcessor :: http2ConnID:"+http2ConnID, ex);//No I18n
        }
    }
}