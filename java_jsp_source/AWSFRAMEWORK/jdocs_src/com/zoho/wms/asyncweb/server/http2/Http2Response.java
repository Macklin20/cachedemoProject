package com.zoho.wms.asyncweb.server.http2;

//Java import
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

//aws import
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.AsyncWebClient;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.exception.UnsupportedWSVersionException;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.http.Cookie;
import com.zoho.wms.asyncweb.server.http.Multipart;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;
import com.zoho.wms.asyncweb.server.stats.MIAdapter;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.util.Util;

//wms import
import com.adventnet.wms.common.HttpDataWraper;
import com.adventnet.wms.servercommon.ServerUtil;

// dependencies import
import com.zoho.instrument.InstrumentManager;
import com.zoho.instrument.Request;
import com.zoho.instrument.common.NetworkCall;
import com.zoho.logs.logclient.v2.LogAPI;
import com.zoho.logs.logclient.v2.json.ZLMap;

/**
 *
 * @author durai - 11882
 *
 */

public class Http2Response extends HttpResponse
{
	private static AsyncLogger logger = new AsyncLogger(Http2Response.class.getName());

	private String http2ConnID = null;
	private int streamId = -1;
	private HttpRequest request = null;
	private LinkedHashMap reqtimeline = new LinkedHashMap();
	private String reqId = null;
	private boolean responseHeaderPushed = false;
	private boolean streammode = false;
	private int responseCode = -1;
	private String responseMessage = null;
	protected long firstReadTime = -1;
	protected long lastWriteTime = -1;
	private long servletTimeTaken = -1;
	private ZLMap endaccesslogszlmap = null;
	private boolean closed = false;
	private ConcurrentLinkedQueue<byte[]> queue = new ConcurrentLinkedQueue<>();

	public Http2Response(String http2ConnID, int streamId, String ipaddr, HttpRequest req)
	{
		super(null, null, ipaddr, 0);
		this.http2ConnID = http2ConnID;
		this.streamId = streamId;
		this.request = req;
	}

	private Http2Stream getStream()
	{
		return getHttp2Conn().getStream(streamId);
		//        if(getHttp2Conn().getStream(streamId) == null)
		//        {
		//            throw new AWSException("Stream Expired : connID:"+http2ConnID+" streamID:"+streamId);
		//        }
		//        return getHttp2Conn().getStream(streamId);
	}

	private Http2Connection getHttp2Conn()
	{
		return ConnectionManager.getConnection(http2ConnID);
		//        if(ConnectionManager.getConnection(http2ConnID) == null)
		//        {
		//            throw new AWSException("Connection Expired : connID:"+http2ConnID+" streamID:"+streamId);
		//        }
		//        return ConnectionManager.getConnection(http2ConnID);
	}

	private void pushResponseHeaders() throws Exception
	{
		if(isResponseHeaderPushed())
		{
			return;
		}

		if(getHttp2Conn().isStreamClosed(streamId))
		{
			throw new IOException("Closed Request");
		}

		if(ConfManager.isHttp2LogsEnabled()) // for debugging purpose
		{
			addHeaderIfAbsent("Http2_ConnID_streamID", http2ConnID+" - "+streamId);
			addHeaderIfAbsent("req_id", getReqId());
			logger.log(Level.INFO, "[Http2 Response-pushResponseHeaders-h2ID:"+http2ConnID+"-streamID:"+streamId+"] - No of Headers:"+headerMap.size()+" - headerMap:\n"+headerMap);
		}
		ByteBuffer headerFramePayLoad = getStream().getHeaderFrame(headerMap, Http2Constants.END_HEADERS_FLAG);
		if(!getStream().isWriteInitiated())
		{
			getStream().setWriteInitiated(true);
			setOutputDataSize(headerFramePayLoad.position());
		}
		getHttp2Conn().writeFrame(streamId, Http2Constants.HEADER_FRAME, headerFramePayLoad);
		setResponseHeaderPushed(true);

		getHttp2Conn().writeResponseDataFrames(streamId); // In dataflow case, onOutputBufferRefill is not called when res.write is not called in service method. Hence, giving a dummy method call to trigger onOutputBufferRefill.

	}

	public void pushResponseData(byte[] data) throws IOException, AWSException
	{
		pushResponseData(data,false);
	}

	public void pushResponseData(byte[] data, boolean setsize) throws IOException, AWSException
	{
		try
		{
			if(ConfManager.isHttp2LogsEnabled())
			{
				logger.log(Level.INFO, "[Http2 Response-pushResponseData-h2ID:"+http2ConnID+"-streamID:"+streamId+"] - "+"Data Length:"+data.length+" maxFrameSize:"+getHttp2Conn().getResponseFrameMaxSize());
			}

			if( ! isResponseHeaderPushed())
			{
				commitResponseHeader();
			}
			if(setsize)
			{
				setOutputDataSize(getStream().getDataFrame(data).position());
			}
			if(getWriteLimit() > 0 && getOutputDataSize() > getWriteLimit())
			{
				throw new IOException("HEAVY WRITE - outputdatasize:"+ getOutputDataSize() +" write_limit:"+ getWriteLimit());
			}

			if(data.length <= getHttp2Conn().getResponseFrameMaxSize())
			{
				getHttp2Conn().writeResponseDataFrame(streamId, getStream().getDataFrame(data));
			}
			else
			{
				queue.addAll(Http2Util.getChuckedByteArrays(data, getHttp2Conn().getResponseFrameMaxSize()));
				while(!queue.isEmpty())
				{
					getHttp2Conn().writeResponseDataFrame(streamId, getStream().getDataFrame(queue.poll()));
				}
			}
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Response-pushResponseData-h2ID:"+http2ConnID+"-streamID:"+streamId+"] - Unable to push Response Data", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2RES_PUSHRESPONSEDATA);

			if(getHttp2Conn() == null)
			{
				throw new IOException("Closed Connection : "+ex.getMessage());
			}
			else if(getHttp2Conn().isStreamClosed(streamId))
			{
				throw new IOException("Closed Request : "+ex.getMessage());
			}
			else
			{
				throw new AWSException("Unable to push Response "+ex.getMessage());
			}
		}
	}

	public void sendFile(ByteBuffer data) throws IOException, AWSException
	{
		try
		{
			write(data);
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Response-sendFile-h2ID:"+http2ConnID+"-streamID:"+streamId+"] - Unable to sendFile", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2RES_SENDFILE);
			if(getHttp2Conn().isStreamClosed(streamId))
			{
				throw new IOException("Closed Request : "+ex.getMessage());
			}
			else
			{
				throw new AWSException("Unable to push Response "+ex.getMessage());
			}
		}
	}

	public void addHeader(String key, String value)
	{
		if(key != null && value != null)
		{
			headerMap.put(key.toLowerCase(), value);
		}
	}

	private void addHeaderIfAbsent(String key, String value)
	{
		if( ! isHeaderPresent(headerMap, key))
		{
			addHeader(key, value);
		}
	}

	private void appendCustomHeaders(Hashtable headerdata)
	{
		for(Object obj : headerdata.keySet())
		{
			String key = (String) obj;
			String value = (String) headerdata.get(key);
			if(key != null && value != null)
			{
				addHeader(key, value);
			}
		}
	}

	public void commitResponseHeader() throws IOException, AWSException
	{
		try
		{
			//            logger.log(Level.INFO, "[Http2 Response-commitResponseHeader-h2ID:"+http2ConnID+"-streamID:"+streamId+"]");
			if(isResponseHeaderPushed())
			{
				return;
			}

			if(getResponseCode() == -1)
			{
				addStatusLine(HttpResponseCode.OK, HttpResponseCode.OK_MSG);
			}

			if(lastModified == -1)
			{
				lastModified = System.currentTimeMillis();
			}

			addHeaderIfAbsent(Http2Constants.STATUS_PSEUDO_HEADER, String.valueOf(getResponseCode()));

			DateFormat df = new SimpleDateFormat(AWSConstants.SIMPLE_DATE_FORMAT_1);
			addHeaderIfAbsent(AWSConstants.HDR_LAST_MODIFIED, df.format(new Date(lastModified)));
			addHeaderIfAbsent(AWSConstants.HDR_DATE, df.format(new Date()));

			addHeaderIfAbsent(AWSConstants.HDR_CONTENT_TYPE, contentType);

			if(contentLength != -1)
			{
				addHeaderIfAbsent(AWSConstants.HDR_CONTENT_LENGTH, ""+contentLength);
			}
			else
			{
				getStream().setChunked(true);
			}

			//addHeaderIfAbsent(AWSConstants.HDR_CONNECTION, AWSConstants.HDR_KEEP_ALIVE); // Not allowed in 'HTTP/2' rfc 9113 - 8.2.2. Connection-Specific Header Fields
			if(ConfManager.isServerHeaderEnabled())
			{
				addHeaderIfAbsent(AWSConstants.HDR_SERVER, AWSConstants.HDR_AWSERVER);
			}

			if(xframerestrict)
			{
				addHeaderIfAbsent(AWSConstants.HDR_X_FRAME_OPTIONS, AWSConstants.SAMEORIGIN);
			}

			if(cookielist != null)
			{
				Set<String> cookienames = cookielist.keySet();
				Iterator itr = cookienames.iterator();
				while(itr.hasNext())
				{
					String name = (String)itr.next();
					Cookie ck = (Cookie)cookielist.get(name);
					if(ck != null)
					{
						addHeaderIfAbsent(AWSConstants.HDR_SET_COOKIE,ck.toString());
					}
				}
			}

			if(gzip != null)
			{
				addHeaderIfAbsent(AWSConstants.HDR_CONTENT_ENCODING, AWSConstants.GZIP);
				if(!chunkmode)
				{
					addHeaderIfAbsent(AWSConstants.HDR_CONTENT_LENGTH, ""+gzip.length);//No I18N
				}
			}

			if(ConfManager.isSTSEnabled() && getHttp2Conn().isSSL())
			{
				addHeaderIfAbsent(AWSConstants.HDR_STRICT_TRANSPORT_SECURITY,AWSConstants.MAX_AGE_EQUAL_TO+ConfManager.getSTSMaxLifeTime());
			}

			pushResponseHeaders();

			if(gzip !=null)
			{
				write(gzip);
			}
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Response-commitResponseHeader-h2ID:"+http2ConnID+"-streamID:"+streamId+"]", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2RES_COMMITRESPONSEHEADER);
			if(getHttp2Conn().isStreamClosed(streamId))
			{
				throw new IOException("Closed Request : "+ex.getMessage());
			}
			else
			{
				throw new IOException("Unable to Write Response : "+ex.getMessage());
			}
		}
	}

	public void commitPushResponseHeader() throws IOException, AWSException
	{
		commitResponseHeader();
	}

	public void commitPushResponseHeader(Hashtable headerdata) throws IOException, AWSException
	{
		appendCustomHeaders(headerdata);
		commitResponseHeader();
	}

	public void commitChunkedTransfer(Hashtable headerdata) throws IOException, AWSException
	{
		appendCustomHeaders(headerdata);
		commitResponseHeader();
	}

	public void commitChunkedTransfer(Hashtable headerdata,boolean appendheader) throws IOException, AWSException
	{
		appendCustomHeaders(headerdata);
		commitResponseHeader();
	}

	public void commitChunkedTransfer() throws IOException, AWSException
	{
		commitResponseHeader();
	}

	public void commitChunkedTransfer(boolean appendheader) throws IOException, AWSException
	{
		commitResponseHeader();
	}

	public void commitChunkedTransfer(int statuscode, String statusmsg) throws IOException, AWSException
	{
		addStatusLine(statuscode, statusmsg);
		commitResponseHeader();
	}

	public void commitChunkedTransfer(int statuscode, String statusmsg, boolean appendheader) throws IOException, AWSException
	{
		addStatusLine(statuscode, statusmsg);
		commitResponseHeader();
	}

	public void commitChunkedTransfer(int statuscode, String statusmsg, Hashtable headerdata) throws IOException, AWSException
	{
		addStatusLine(statuscode, statusmsg);
		appendCustomHeaders(headerdata);
		commitResponseHeader();
	}

	public void commitChunkedTransfer(int statuscode, String statusmsg, Hashtable headerdata, boolean appendheader) throws IOException, AWSException
	{
		addStatusLine(statuscode, statusmsg);
		appendCustomHeaders(headerdata);
		commitResponseHeader();
	}

	public void commitLiveChunkedTransfer() throws IOException, AWSException
	{
		commitResponseHeader();
	}

	public void sendError(int statuscode, String statusmsg) throws IOException, AWSException
	{
		errresponse = true;
		addStatusLine(statuscode, statusmsg);
		commitResponseHeader();
	}

	public void sendRedirect(int statuscode , String statusmsg , String url) throws IOException, AWSException
	{
		addStatusLine(statuscode, statusmsg);
		addHeaderIfAbsent(AWSConstants.HDR_LOCATION, url);
		commitResponseHeader();
	}

	public void sendWSVersionError(int statuscode, String statusmsg) throws IOException, AWSException
	{
		addStatusLine(statuscode, statusmsg);
		addHeaderIfAbsent(AWSConstants.HDR_DATE,  ""+new Date());
		addHeaderIfAbsent(AWSConstants.HDR_SEC_WEBSOCKET_VERSION,ConfManager.getSupportedWSVersions());
		commitResponseHeader();
	}


	public void write(Multipart multipart) throws AWSException, IOException
	{
		write(multipart, null);
	}

	public void write(Multipart multipart, Hashtable headerdata) throws IOException, AWSException
	{
		multipart.processMultipart(false);
		if (headerdata == null)
		{
			headerdata = new Hashtable();
		}
		headerdata.put(AWSConstants.HDR_CONTENT_TYPE, multipart.getContentType());
		headerdata.put(AWSConstants.HDR_CONTENT_LENGTH, ""+multipart.getContentLength());
		commitChunkedTransfer(headerdata);
		writeBinary(multipart.getData());
	}

	public void write(ByteBuffer data) throws IOException, AWSException
	{
		write(data.array(),true);
	}

	public void write(ByteBuffer data, boolean setsize) throws IOException, AWSException
	{
		write(data.array(),setsize);
	}

	public synchronized void write(ByteBuffer data, boolean setsize,int opcode) throws IOException, AWSException
	{
		write(data.array(),true);
	}

	public void write(String data) throws IOException, AWSException
	{
		write(data.getBytes(),true);
	}

	public void write(String data, boolean setsize) throws IOException, AWSException
	{
		write(data.getBytes(),setsize);
	}

	public void write(byte[] data) throws IOException, AWSException
	{
		write(data,true);
	}

	public void write(byte[] data, boolean setsize) throws IOException, AWSException
	{
		pushResponseData(data,setsize);
	}

	public void write(byte[] data,int offset, int length) throws IOException, AWSException
	{
		write(Arrays.copyOfRange(data,offset,offset+length));
	}

	public void write(byte[] data,int offset, int length, boolean setsize) throws IOException, AWSException
	{
		write(Arrays.copyOfRange(data,offset,offset+length),setsize);
	}

	public void writeBinary(byte[] data) throws IOException, AWSException
	{
		write(data);
	}

	public void writeBinary(byte[] data,int offset, int length) throws IOException, AWSException
	{
		write(Arrays.copyOfRange(data,offset,offset+length));
	}

	public void seqWrite(byte[] data,long index) throws IOException, AWSException
	{
		seqWrite(data, index, Util.WS_OPCODE_BINARY);
	}

	public void seqWrite(String  data,long index) throws IOException, AWSException
	{
		seqWrite(data.getBytes(StandardCharsets.UTF_8), index, Util.WS_OPCODE_TEXT);
	}

	private synchronized void seqWrite(byte[] data,long index, int opcode) throws IOException, AWSException
	{
		pushResponseData(data,true);
	}

	public void writePostContent(byte[] data) throws IOException, AWSException
	{
		pushResponseData(data,true);
	}

	public void writeMobileContent(byte[] data) throws IOException, AWSException
	{
		writeMobileContent(data,-1);
	}

	public void writeMobileContent(byte[] data, long index) throws IOException, AWSException
	{
		try
		{
			//            logger.log(Level.INFO, "[Http2 Response-writeMobileContent-h2ID:"+http2ConnID+"-streamID:"+streamId+"]");

			if(getResponseCode() == -1)
			{
				addStatusLine(HttpResponseCode.OK, HttpResponseCode.OK_MSG);
			}

			addHeaderIfAbsent(Http2Constants.STATUS_PSEUDO_HEADER, ""+getResponseCode());
			addHeaderIfAbsent(AWSConstants.HDR_CONTENT_TYPE, AWSConstants.APPLICATION_VND_MS_SYNC_WBXML);
			addHeaderIfAbsent(AWSConstants.HDR_CONTENT_LENGTH, data.length+"");//No I18N
			addHeaderIfAbsent(AWSConstants.HDR_SERVER, AWSConstants.HDR_MICROSOFT_IIS_6_0);
			addHeaderIfAbsent(AWSConstants.HDR_MS_SERVER_ACTIVESYNC, AWSConstants.VERSION_8_1);

			pushResponseHeaders();
			pushResponseData(data,true);
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Response-writeMobileContent-h2ID:"+http2ConnID+"-streamID:"+streamId+"]", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2RES_WRITEMOBILECONTENT);
			throw new IOException("Unable to write response : "+ex.getMessage());
		}
	}

	public void sendOptionsResponse(String host, String reqHeaders) throws CharacterCodingException, IOException, AWSException
	{
		sendOptionsResponse(HttpResponseCode.OK, HttpResponseCode.OK_MSG, host, reqHeaders);
	}

	public void sendOptionsResponse(int statuscode, String statusmsg, String host, String reqHeaders) throws CharacterCodingException, IOException, AWSException
	{
		try
		{
			if(isResponseHeaderPushed())
			{
				return;
			}

			addStatusLine(statuscode, statusmsg);
			addHeaderIfAbsent(Http2Constants.STATUS_PSEUDO_HEADER, ""+getResponseCode());

			if(!ConfManager.isValidDomain(host))
			{
				host = ConfManager.getValidDomains();
			}
			if(host != null)
			{
				addHeaderIfAbsent(AWSConstants.HDR_ACCESS_CONTROL_ALLOW_ORIGIN, host);
			}
			if(reqHeaders != null)
			{
				addHeaderIfAbsent(AWSConstants.HDR_ACCESS_CONTROL_ALLOW_HEADERS, reqHeaders);
			}

			// For preflight request, there won't be any response content written in body, hence not included the content length header
			StringBuffer buffer = new StringBuffer();
			buffer.append(AWSConstants.POST_COMMA_GET);//No I18n
			if(ConfManager.getSupportedGetMethods()!=null)
			{
				buffer.append(",").append(ConfManager.getSupportedGetMethods());
			}
			if(ConfManager.getSupportedPostMethods() != null)
			{
				buffer.append(",").append(ConfManager.getSupportedPostMethods());
			}
			addHeaderIfAbsent(AWSConstants.HDR_ACCESS_CONTROL_ALLOW_METHODS, buffer.toString());

			addHeaderIfAbsent(AWSConstants.HDR_ACCESS_CONTROL_MAX_AGE, ""+(30*60));
			addHeaderIfAbsent(AWSConstants.HDR_ACCESS_CONTROL_ALLOW_CREDENTIALS, AWSConstants.TRUE);

			if(ConfManager.isSTSEnabled() && getHttp2Conn().isSSL())
			{
				addHeaderIfAbsent(AWSConstants.HDR_STRICT_TRANSPORT_SECURITY,AWSConstants.MAX_AGE_EQUAL_TO+ConfManager.getSTSMaxLifeTime());
			}

			pushResponseHeaders();
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Response-sendOptionsResponse-h2ID:"+http2ConnID+"-streamID:"+streamId+"]", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2RES_SENDOPTIONSRESPONSE);
			throw new AWSException("Unable to send options Response : "+ex.getMessage());
		}
	}

	public void sendOptionsResponse(String host, HashMap reqHeaderMap) throws CharacterCodingException, IOException, AWSException
	{
		sendOptionsResponse(HttpResponseCode.OK, HttpResponseCode.OK_MSG,host,reqHeaderMap);
	}

	public void sendOptionsResponse(int statuscode, String statusmsg, String host, HashMap reqHeaderMap) throws CharacterCodingException, IOException, AWSException
	{
		try
		{
			if(isResponseHeaderPushed())
			{
				return;
			}

			addStatusLine(statuscode, statusmsg);
			addHeaderIfAbsent(Http2Constants.STATUS_PSEUDO_HEADER, ""+getResponseCode());

			if(!ConfManager.isValidDomain(host))
			{
				host = ConfManager.getValidDomains();
			}
			if(host != null)
			{
				addHeaderIfAbsent(AWSConstants.HDR_ACCESS_CONTROL_ALLOW_ORIGIN, host);
			}

			if(reqHeaderMap != null)
			{
				Iterator itr = reqHeaderMap.keySet().iterator();
				while(itr.hasNext())
				{
					String headerkey = (String)itr.next();
					String headervalue = (String)reqHeaderMap.get(headerkey);
					if(headervalue != null)
					{
						addHeaderIfAbsent(headerkey, headervalue);
					}
				}
			}

			pushResponseHeaders();
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Response-sendOptionsResponse_reqMap-h2ID:"+http2ConnID+"-streamID:"+streamId+"]", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2RES_SENDOPTIONSRESPONSE);
			throw new AWSException("Unable to send options Response : "+ex.getMessage());
		}
	}





	public void addStatusLine(int responseCode, String responseMessage)
	{
		this.responseCode = responseCode;
		this.responseMessage = responseMessage;
	}

	public int getResponseCode()
	{
		return responseCode;
	}

	public String getResponseMessage()
	{
		return responseMessage;
	}

	public void setDataFlowNotification(boolean status)
	{
		getStream().setDataFlowNotification(status);
	}

	public void setReuseConnection()
	{
	}

	public void setReuseConnection(boolean value)
	{
	}

	public void readNextRequest()
	{
	}

	public boolean isSecuredAccess()
	{
		return getClient().isSecuredAccess();
	}

	public ArrayList getFailedIndexList()
	{
		return null;
	}

	public String getScheme()
	{
		return request.getScheme();
	}

	public void setRequestState(int state)
	{
		if(state == StateConstants.REQUEST_ACKNOWLEDGED)
		{
			getStream().removeFromTimeoutTracker();
		}
	}

	public void setInputDataSize(long size) throws IOException
	{
		getHttp2Conn().setInputDataSize(streamId, size);
	}

	public void setReqPayloadSize(long size)
	{
	}

	public void setOutputDataSize(long size)
	{
		getStream().setOutputDataSize(size);
		if(contentLength == -1)
		{
			contentLength = size;
		}
	}

	public long getOutputDataSize()
	{
		return getStream().getOutputDataSize();
	}

	public void setWriteLimit(int length)
	{
		getStream().setWriteLimit(length);
	}

	public long getWriteLimit()
	{
		return getStream().getWriteLimit();
	}

	public void setReadLimit(int limit)
	{
		getStream().setReadLimit(limit);
	}

	public int getReadLimit()
	{
		return getStream().getReadLimit();
	}

	private AsyncWebClient getClient()
	{
		return getHttp2Conn().getClient();
	}

	private String getRequestURL()
	{
		return request.getRequestURL();
	}

	public boolean isStreamModeEnabled()
	{
		return streammode;
	}

	public void setStreamMode(boolean value)
	{
		streammode = value;
	}

	public String getStatsRequestURL()
	{
		return request.getStatsRequestUrl();
	}

	public void setWebSocket(WebSocket wsservlet, boolean compressionenabled, HashMap compressiondetails)
	{
	}

	public void holdRead() throws IOException
	{
	}

	public boolean isOnHold()
	{
		return false;
	}

	public void enable() throws IOException
	{
	}

	public void upgradeToWebsocket(String wsKey, String wsVersion, String wsSupportedExtensions,String wsSupportedProtocols) throws CharacterCodingException, IOException, NoSuchAlgorithmException, UnsupportedWSVersionException, AWSException
	{
	}

	public void ping()
	{
	}

	public String getReqId()
	{
		return reqId;
	}

	public void setReqId(String reqId)
	{
		this.reqId = reqId;
	}

	public boolean isKeepAliveValid()
	{
		return true;
	}

	public boolean isKeepAliveEnabled()
	{
		return true;
	}

	public void enableKeepAlive()
	{
	}

	public void setKeepAliveTimeout(long timeout)
	{
	}

	public int getClientId()
	{
		return getClient().hashCode();
	}

	public boolean isReinitSet()
	{
		return false;
	}

	public boolean isActive()
	{
		Http2Stream stream = getStream();
		if(stream != null)
		{
			return !stream.isClosed();
		}
		return false;
	}

	public void setWSServletThreadSafe(boolean status)
	{
	}

	public void setWriteCapacity(int limit)
	{
	}

	public int getWriteCapacity()
	{
		return 0;
	}

	public void setMaxDataPerWrite(int maxdataperwrite)
	{
	}

	public int getMaxDataPerWrite()
	{
		return 0;
	}

	public void setMaxDataPerWSRead(int wsreadlimit)
	{
	}

	public int getMaxDataPerWSRead()
	{
		return 0;
	}

	public long getInittime()
	{
		return getStream().getInittime();
	}

	public int getZeroReadCount()
	{
		return getClient().getZeroReadCount();
	}

	public void setMaxZeroReadCount(int count)
	{
		if(count > getMaxZeroReadCount())
		{
			getClient().setMaxZeroReadCount(count);
		}
	}

	public int getMaxZeroReadCount()
	{
		return getClient().getMaxZeroReadCount();
	}

	public String getWebEngineName()
	{
		return request.getEngineName();
	}

	public boolean isResponseHeaderPushed()
	{
		return responseHeaderPushed;
	}

	public void setResponseHeaderPushed(boolean value)
	{
		responseHeaderPushed = value;
	}

	public long getConnectionInitTime()
	{
		return getClient().getInittime();
	}

	public long getReqQueueInsertTime()
	{
		return getStream().getReqQueueInsertTime();
	}

	public long getTimeTakenForHandshake()
	{
		return getClient().getTimeTakenForHandshake();
	}

	public boolean isNewConnection()
	{
		return false;
	}

	public void notifyEndRequest()
	{
		// service team should call notifyEndRequest only for Data Flow Case, In H1, readNextRequest/client.close() is called based on keepalive case. In h2, both are handled in response.close itself.
	}

	public long getRequestProcessorTime()
	{
		return getStream().getRequestProcessorTime();
	}

	public void invokeNotification(int notification)
	{
		getHttp2Conn().invokeNotification(streamId, notification);
	}

	public void addServletTimeTaken(long timetaken)
	{
		if(servletTimeTaken == -1)
		{
			servletTimeTaken = timetaken;
		}
		else
		{
			servletTimeTaken += timetaken;
		}
	}

	public void setFirstReadTime(long time)
	{
		this.firstReadTime = time;
	}

	public void updateLastWriteTime()
	{
		lastWriteTime = System.currentTimeMillis();
	}


	public void setUserId(Long userid)
	{
		updateTimeLine("userid",userid);//No I18N
	}

	public Object updateTimeLine(String key, long stime, long etime)
	{
		ConcurrentHashMap hm = new ConcurrentHashMap();
		hm.put(AWSConstants.START,stime);
		hm.put(AWSConstants.END,etime);
		return updateTimeLine(key,hm);
	}

	public void replaceAndEndTimeLine(String oldkey, String newKey)
	{
		ConcurrentHashMap hm = removeTimeLineInfo(oldkey);
		if(hm!=null && hm.get(AWSConstants.END)==null)
		{
			hm.put(AWSConstants.END,System.currentTimeMillis());
			updateTimeLine(newKey,hm);
		}
	}

	public void startTimeLine(String key)
	{
		startTimeLine(key,System.currentTimeMillis());
	}

	public void startTimeLine(String key, long stime)
	{
		ConcurrentHashMap hm = getTimeLineInfo(key);
		if(hm == null)
		{
			hm = new ConcurrentHashMap();
			hm.put(AWSConstants.START,stime);
			updateTimeLine(key, hm);
		}
	}

	public void endTimeLine(String key)
	{
		if(key != null)
		{
			endTimeLine(key,System.currentTimeMillis());
		}
	}

	public void endTimeLine(String key,long stime)
	{
		ConcurrentHashMap hm = getTimeLineInfo(key);
		if(hm != null)
		{
			hm.put(AWSConstants.END,System.currentTimeMillis());
		}
	}

	public Object updateTimeLine(String key, Object value)
	{
		if( reqtimeline != null)
		{
			return reqtimeline.put(key,value);
		}
		else
		{
			return null;
		}
	}

	public LinkedHashMap getTimeLine()
	{
		return reqtimeline;
	}

	public ConcurrentHashMap getTimeLineInfo(String key)
	{
		if(reqtimeline != null)
		{
			return getTimeLineInfoValue(key, reqtimeline.get(key));
		}
		return null;
	}

	public ConcurrentHashMap removeTimeLineInfo(String key)
	{
		if(reqtimeline != null)
		{
			return getTimeLineInfoValue(key, reqtimeline.remove(key));
		}
		return null;
	}

	private ConcurrentHashMap getTimeLineInfoValue(String key, Object value)
	{
		try
		{
			if(value != null)
			{
				if(value instanceof ConcurrentHashMap)
				{
					return (ConcurrentHashMap) value;
				}
				ConcurrentHashMap chm = new ConcurrentHashMap<>();
				chm.put(key, value);
				return chm;
			}
		}
		catch(Exception e)
		{
		}
		return null;
	}

	public void setTimeLine(LinkedHashMap reqtimeline)
	{
		this.reqtimeline = reqtimeline;
	}



	public void startInstrumentation(HttpRequest httpreq)
	{
		try
		{
			if(!MIAdapter.getStatus() || isStreamModeEnabled())
			{
				return;
			}
			String requrl = getRequestURL();
			if(requrl == null)
			{
				requrl = "unknown";//No I18n
			}
			if(ConfManager.isMIExcludedURL(requrl) || (websocket && !ConfManager.isMIIncludedWSURL(requrl)))
			{
				return;
			}
			LinkedHashMap timelineinfo = getTimeLine();
			if(timelineinfo == null || timelineinfo.size() == 0)
			{
				return;
			}

			ConcurrentHashMap<String,Long> eventinfo;

			if(timelineinfo.get(AWSConstants.TL_CREATION_TIME)!=null)
			{
				eventinfo=(ConcurrentHashMap)timelineinfo.get(AWSConstants.TL_CREATION_TIME);
			}
			else if(timelineinfo.get(AWSConstants.TL_RESET_TIME)!=null)
			{
				eventinfo=(ConcurrentHashMap)timelineinfo.get(AWSConstants.TL_RESET_TIME);
			}
			else
			{
				return;
			}

			InstrumentManager.startInstrumentation();
			Request request = InstrumentManager.getCurrentRequest();
			if(request == null)
			{
				return;
			}
			try
			{
				isMIStarted = true;

				long time =(Long) eventinfo.get(AWSConstants.START);
				request.setStartTime(time);
				request.setActualStartTime(time);
				request.setRequestType(1);
				String skeletonurl = getStatsRequestURL();
				if(skeletonurl != null)
				{
					request.setURL(skeletonurl);
				}
				else
				{
					request.setURL(getRequestURL());
				}
			}
			catch(Exception ex)
			{
				logger.addExceptionLog(Level.INFO, "Exception in start Instrumentation : requrl : "+requrl+", request : "+httpreq, ex);//No I18n
				if(request != null)
				{
					request.setResponseStatus(HttpResponseCode.INTERNAL_SERVER_ERROR);
					request.complete(ex);
				}
				isMIRequestCompleted = true;
				InstrumentManager.finishInstrumentation();
			}
		}
		catch(Exception exp)
		{
			logger.log(Level.INFO, "Exception in Instrumentation start : request : "+httpreq, exp);//No I18n
		}
	}

	public void finishInstrumentation(HttpRequest httpreq)
	{
		if(!MIAdapter.getStatus() || isMIRequestCompleted || !isMIStarted)
		{
			return;
		}

		Request request = InstrumentManager.getCurrentRequest();
		try
		{
			if(request == null)
			{
				return;
			}
			if( httpreq.getZUID() != -1)
			{
				request.setUserId(httpreq.getZUID());
			}

			String skeletonurl = getStatsRequestURL();
			if(skeletonurl != null)
			{
				request.setURL(skeletonurl);
			}
			else
			{
				request.setURL(getRequestURL());
			}

			if(getTimeLine() == null)
			{
				return;
			}
			LinkedHashMap timelineinfo = new LinkedHashMap(getTimeLine());

			// Removed as these stats are incomplete, will be handled in next phase. CC :: MI team
			timelineinfo.remove(AWSConstants.TL_READ_TIME);
			timelineinfo.remove(AWSConstants.TL_WRITE_TIME);
			timelineinfo.remove(AWSConstants.BYTESIN);
			timelineinfo.remove(AWSConstants.BYTESOUT);

			int failedcalls=0;
			ConcurrentHashMap<String,Long> eventinfo;
			String[] destIps = new String[]{ServerUtil.getServerIP()};
			Set<String> keys = timelineinfo.keySet();
			for(String key:keys)
			{
				eventinfo = (ConcurrentHashMap)timelineinfo.get(key);
				if(eventinfo.get(AWSConstants.START)==null || eventinfo.get(AWSConstants.END)==null)
				{
					failedcalls++;
					continue;
				}
				long starttime = eventinfo.get(AWSConstants.START);
				long endtime = eventinfo.get(AWSConstants.END);
				NetworkCall call = NetworkCall.newInstance(destIps);
				call.setStartTime(starttime-request.getStartTime());
				call.setActualStartTime(starttime);
				call.setISCompleted(0);
				call.setKey(key);
				if(key.endsWith("failure"))
				{
					call.setISFailed(1);
					failedcalls++;
				}
				call.setDuration((endtime-starttime));
				request.addCall(call);
			}


			if((failedcalls > 0) || (timelineinfo.size() < 2) || (request.getURL().equals("unknown")))
			{
				request.setResponseStatus(HttpResponseCode.INTERNAL_SERVER_ERROR);
				request.complete(new Exception("Incomplete/failed/unknown request"));
				isMIRequestCompleted = true;
			}
			else
			{
				request.setResponseStatus(HttpResponseCode.OK);
				request.complete();
				isMIRequestCompleted = true;
			}

		}
		catch(Exception exp)
		{
			logger.addExceptionLog(Level.INFO, "Exception in Instrumentation : httpReq : "+httpreq, exp);//No I18n
			if(request != null)
			{
				request.setResponseStatus(HttpResponseCode.INTERNAL_SERVER_ERROR);
				request.complete(exp);
				isMIRequestCompleted = true;
			}
		}
		finally
		{
			try
			{
				if(!isMIRequestCompleted && request != null)
				{
					request.setResponseStatus(HttpResponseCode.INTERNAL_SERVER_ERROR);
					request.complete();
					isMIRequestCompleted = true;
				}
			}
			catch(Exception miexp)
			{
				logger.addExceptionLog(Level.INFO, "Exception in Instrumentation : req.complete : httpReq : "+httpreq, miexp);//No I18n
			}
			finally
			{
				try
				{
					InstrumentManager.finishInstrumentation();
				}
				catch(Exception ex)
				{
					logger.addExceptionLog(Level.INFO, "Exception in Instrumentation : finish : httpReq : "+httpreq, ex);//No I18n
				}
				request = null;
			}
		}
	}

	private String getRequestType()
	{
		return request.getRequestType();
	}

	private String getRawIPAddress()
	{
		return getClient().getRawIPAddress();
	}

	private int getSocketPort()
	{
		return getClient().getSocketPort();
	}

	private int getLocalPort()
	{
		return getClient().getLocalPort();
	}

	private HashMap getRequestHeaders()
	{
		return request.getHeaders();
	}

	private HashMap getRequestParams()
	{
		return request.getParams();
	}

	private void setEndAccessLogs(ZLMap zlmap)
	{
		endaccesslogszlmap = zlmap;
	}

	private void prepareEndAWSAccessLog()
	{
		try
		{
			if(ConfManager.isEndAWSAccessLogEnabled())
			{
				ZLMap zlmap = new ZLMap();
				zlmap.put(AWSConstants.REQID, getReqId());
				zlmap.put(AWSConstants.REQUEST_URI, getRequestURL());
				zlmap.put(AWSConstants.PARAM, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessParams(getRequestParams()))));
				zlmap.put(AWSConstants.REQUEST_HEADER, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessHeaders(getRequestHeaders()))));
				zlmap.put(AWSConstants.RESPONSE_HEADER, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessResponseHeaders(headerMap))));
				zlmap.put(AWSConstants.RESPONSE_CODE, getResponseCode());
				zlmap.put(AWSConstants.RESPONSE_MESSAGE, getResponseMessage());
				zlmap.put(AWSConstants.METHOD, getRequestType());
				zlmap.put(AWSConstants.REMOTE_IP, getRawIPAddress());
				zlmap.put(AWSConstants.SERVER_PORT, getSocketPort());
				zlmap.put(AWSConstants.LOCAL_PORT, getLocalPort());
				zlmap.put(AWSConstants.THREAD_ID, Thread.currentThread().getId());
				zlmap.put(AWSConstants.THREAD_NAME, Thread.currentThread().getName());
				zlmap.put(AWSConstants.VERSION, Http2Constants.HTTP2_VERSION);
				zlmap.put(AWSConstants.USER_AGENT_1, HttpDataWraper.getString(request.getHeader(AWSConstants.USER_AGENT)));

				zlmap.put(Http2Constants.HTTP2_CONN_ID, http2ConnID);
				zlmap.put(Http2Constants.HTTP2_STREAM_ID, streamId);

				setEndAccessLogs(zlmap);
			}
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION_IN_FORMATTING_ACCESSLOGGER, e);
		}
	}

	public void printEndAWSAccessLog()
	{
		long requestTimeTaken = (firstReadTime == -1) ? -1 : ((lastWriteTime == -1) ? (System.currentTimeMillis() - firstReadTime) : (lastWriteTime - firstReadTime));

		try
		{
			if(ConfManager.isEndAWSAccessLogEnabled() && endaccesslogszlmap != null)
			{
				endaccesslogszlmap.put(AWSConstants.REQUEST_TIME_TAKEN, requestTimeTaken); //request timetaken (last write time - first read time)
				endaccesslogszlmap.put(AWSConstants.TIME_TAKEN, servletTimeTaken); //application processing time
				LogAPI.log(AWSConstants.ENDAWSACCESS, endaccesslogszlmap);
			}
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION_IN_FORMATTING_ACCESSLOGGER, e);
		}
	}





	public void close() throws IOException, AWSException
	{
		if(closed)
		{
			return;
		}

		closed = true;

		if(ConfManager.isHttp2LogsEnabled())
		{
			logger.log(Level.INFO, "[Http2 Response-close-h2ID:"+http2ConnID+"-streamID:"+streamId+"]");
		}

		try
		{
			Http2Connection http2Conn = getHttp2Conn();
			if(http2Conn == null)
			{
				throw new AWSException("Http Connection Unavailable");
			}
			if(http2Conn.isStreamClosed(streamId))
			{
				return;
			}

			if( ! isResponseHeaderPushed())
			{
				commitResponseHeader();
			}

			prepareEndAWSAccessLog();

			getStream().setCloseAfterWrite(true);
			getHttp2Conn().writeResponseDataFrames(streamId);

			headerMap = null;
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Response-close-h2ID:"+http2ConnID+"-streamID:"+streamId+"]", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2RES_CLOSE);
		}
	}

	public void forceclose()
	{
		if(closed)
		{
			return;
		}

		closed = true;

		if(ConfManager.isHttp2LogsEnabled())
		{
			logger.log(Level.SEVERE, "[Http2 Response-forceClose-h2ID:"+http2ConnID+"-streamID:"+streamId+"]");
		}

		if(getHttp2Conn() != null)
		{
			getHttp2Conn().closeConnection();
		}
	}

	public long getAvgSocketReadTime()
	{
		return getHttp2Conn().getAvgSocketReadTime();
	}

	public long getAvgSocketWriteTime()
	{
		return getHttp2Conn().getAvgSocketWriteTime();
	}

	public boolean isDataFrameQueueEmpty()
	{       
		return queue.isEmpty();
	}

	public void disableHeartBeatMonitor()
	{
		getClient().disableAndRemoveFromRequestHeartBeatTracker();
	}

	public void enableHeartBeatMonitor()
	{
		getClient().enableHearBeatTracker();
	}

	public String getIpWithPort()
	{
		return getClient().getIPAddress();
	}

	public boolean isSameClient(HttpResponse responseObject)
	{
		return getClientId() == responseObject.getClientId();
	}

	/**
	 * To set security filter status of the request
	 */
	public void setSecurityFilterStatus(boolean status)
	{
		try
		{
			getStream().setSecurityFilterStatus(status);
		}
		catch(Exception e)
		{
		}
	}

	public boolean isSecurityFilterDisabled()
	{
		try
		{
			return getStream().isSecurityFilterDisabled();
		}
		catch(Exception e)
		{
		}
		return false;
	}
}
