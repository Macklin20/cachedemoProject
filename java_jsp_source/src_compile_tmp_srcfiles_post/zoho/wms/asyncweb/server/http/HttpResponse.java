//$Id$
package com.zoho.wms.asyncweb.server.http;

// Java import
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.Date;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import java.util.Base64;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
// Wms import
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.AsyncWebClient;
import com.zoho.wms.asyncweb.server.exception.UnsupportedWSVersionException;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.stats.MIAdapter;

import com.zoho.instrument.Request;
import com.zoho.instrument.InstrumentManager;
import com.zoho.instrument.common.NetworkCall;
import com.adventnet.wms.servercommon.ServerUtil;

import com.zoho.logs.common.util.LogTypes;
import com.adventnet.wms.common.HttpDataWraper;
import com.zoho.logs.logclient.v2.LogAPI;
import com.zoho.logs.logclient.v2.json.ZLMap;

public class HttpResponse
{
	public static AsyncLogger logger = new AsyncLogger(HttpResponse.class.getName());

	protected SelectionKey key;
	protected long contentLength = -1;
	protected long lastModified = -1;
	protected String charset = ConfManager.getResponseCharset();
	protected String contentType = ConfManager.getDefaultHttpContentType();
	protected String ipaddr;
	protected boolean websocket=false;
	protected boolean errresponse=false;
	protected byte[] gzip = null;
	protected CharsetEncoder encoder = null;

	protected Hashtable<String,String> headerMap = new Hashtable<String,String>();
	protected HashMap<String, Cookie> cookielist = new HashMap<String, Cookie>();
	protected boolean xframerestrict = false;
	protected boolean chunkmode=false;

	private AsyncWebClient client;
	protected boolean firstFrameSent = false;
	protected boolean isCFInProgress = false;
	protected long servletdispatchertime;
	protected boolean isMIRequestCompleted = false;
	protected boolean isMIStarted = false;
	protected long clientId;

	/**
	 * To initialize a http response
	 * @param client - client to which response is to be sent
	 * @param key - selection key associated with the request
	 * @param ipaddr - ipaddr of the client
	 */

	public HttpResponse(AsyncWebClient client, SelectionKey key, String ipaddr, long clientId)
	{
		this.client = client;
		this.key = key;
		this.ipaddr = ipaddr;
		this.clientId = clientId;
	}

	/**
	 * To get the selection key associated with client request
	 * @return - selection key
	 */
	
	/*public SelectionKey getKey()
	{
		return key;
	}*/

	/**
	 * To write a file in response
	 * @param data - file contents as bytebuffer
	 */
	
	public void sendFile(ByteBuffer data) throws IOException, AWSException
	{
		commitResponseHeader();
		setOutputDataSize(data.remaining());

		if(client.isWriteDataInQueueEnabled())
		{
			if(data.hasRemaining())
			{
				client.writeData(data);
			}
		}
		else
		{
			while(data.hasRemaining())
			{
				client.writeData(data);
			}
		}
	}

	/**
	 * To set the request state
	 * @param state - Integer representing the state of the request
	 *
	 * Note: List of Possible values
	 * 	 1. StateConstants.REQUEST_IN_PROCESS = 1
	 * 	 2. StateConstants.REQUEST_ACKNOWLEDGED = 2
	 * 	 3. StateConstants.ON_HEADER = 10
	 * 	 4. StateConstants.ON_DATA = 11
	 * 	 5. StateConstants.ON_COMPLETION = 12
	 * 	 6. StateConstants.ON_OUTPUTBUFFERREFILL = 13
	 * 	 7. StateConstants.ON_WRITECOMPLETE = 14
	 * 	 8. StateConstants.ON_WRITEFAILURE = 15
	 */

	public void setRequestState(int state)
	{
		client.setRequestState(state);
	}

	/**
	 * To set the input data size of the client - HttpStreamServlet
	 * @param size - maximum no of bytes to be read from a client
	 * 
	 * Note: -1 represents no limit
	 */
	
	public void setInputDataSize(long size) throws IOException
	{
		client.setInputDataSize(size);
	}

	/**
	 * To set the input data size of the client - HttpServlet
	 * @param size - maximum no of bytes to be read from a client
	 *
	 * Note: -1 represents no limit
	 */

	public void setReqPayloadSize(long size) 
	{
		client.setReqPayloadSize(size);
	}

	/**
	 * To set the output data size of the client
	 * @param size - maximum no of bytes to be written to a  client
	 */
	
	public void setOutputDataSize(long size)
	{
		client.setOutputDataSize(size);
	}

	/**
	 * To enable dataflow notification
	 * @param status - true or false
	 */

	public void setDataFlowNotification(boolean status)
	{
		client.setDataFlowNotification(status);
	}

	/**
	 * To enable pingpong in WCP
	 */

	public void enablePingResponse()
        {
                client.setPingResponse(true);
        }
	
	/**
         * To set the write limit for the connection
         * @param length - write limit in bytes
         * 
	 * Note: -1 represents no limit
	 */

        public void setWriteLimit(int length)
        {
                client.setWriteLimit(length);
        }

	/**
	 * To get write limit for the connection
	 * @return write limit for the connection
	 */

	public long getWriteLimit()
	{
		return client.getWriteLimit();
	}

	/**
	 * To enable reuse of the connection
	 */
	
	public void setReuseConnection()
	{
		client.setReuseConnection();
	}

	/**
	 * To enable/disable reuse of the connection
	 */

	public void setReuseConnection(boolean value)
	{
		client.setReuseConnection(value);
	}

	/**
	 * To read next request from the connection
	 */
	
	/*public void readNextRequest()
	{
		client.readNextRequest();
	}*/
	
	public void setUserId(Long userid)                                                                                                                                        
	{
		updateTimeLine("userid",userid);//No I18N
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

	/**
	 * To write data to the client
	 * @param data - bytebuffer of the data
	 */
	
	public void write(ByteBuffer data) throws IOException, AWSException
	{
		write(data,true);
	}

	/**
	 * To write data to the client
	 * @param data - bytebuffer of the data
	 * @param setsize - to set maximum write limit as data limit if true 
	 */
	
	public void write(ByteBuffer data, boolean setsize) throws IOException, AWSException
	{
		write(data,setsize,Util.WS_OPCODE_TEXT);
	}

	/**
	 * To write data to the client
	 * @param data - bytebuffer of the data
	 * @param setsize - to set maximum write limit as data limit if true
	 * @param opcode - to set Websocket opcode i.e. type of data written. One of the following values
	 * 		   1.Util.WS_OPCODE_TEXT - 1
	 * 		   2.Util.WS_OPCODE_BINARY -2
	 */
	
	public synchronized void write(ByteBuffer data, boolean setsize,int opcode) throws IOException, AWSException
	{
		if(!client.isResponseHeaderPushed() && !websocket)
		{
			commitChunkedTransfer();
		}
		if(websocket)
		{
			data = getWebSocketWriteFrame(data,opcode, true);
		}
		if(chunkmode)
		{
			data=getChunkedData(data);
		}
		if(setsize)
		{
			client.setOutputDataSize(data.limit());
		}
		client.writeData(data,-1);
	}

	private ByteBuffer getChunkedData(ByteBuffer data) throws IOException
	{
		try
		{
			byte[] dat = data.array();
			byte[] hex = Integer.toHexString(dat.length).getBytes(AWSConstants.UTF_8);
			byte[] newline = AWSConstants.NEWLINE.getBytes(AWSConstants.UTF_8);//No I18N
			int destbuffsize = hex.length+newline.length+dat.length+newline.length;

			ByteArrayOutputStream chunkBuffer = new ByteArrayOutputStream(destbuffsize);
			chunkBuffer.write(hex);
			chunkBuffer.write(newline);
			chunkBuffer.write(dat);
			chunkBuffer.write(newline);
			
			return ByteBuffer.wrap(chunkBuffer.toByteArray());
			
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception ",HttpResponse.class.getName(),AWSLogMethodConstants.GET_CHUNKED_DATA, ex);
			throw new IOException("Error forming chunked data for "+client.getRequestURL()+" : "+ex.getMessage());//No I18N
		}
	}

	public void disableHeartBeatMonitor()
	{
		client.disableAndRemoveFromRequestHeartBeatTracker();		
	}

	public void enableHeartBeatMonitor()
	{
		client.enableHearBeatTracker();
	}

	/** 
	 * To write data to the client
	 * @param data - data to write
	 */
	
	public void write(String data) throws IOException, AWSException
	{
		write(ByteBuffer.wrap(data.getBytes(AWSConstants.UTF_8)),true);
	}

	/**
	 * To write data to the client
	 * @param data - data to write
	 * @param setsize - to set maximum write limit as data limit if true
	 */
	
	public void write(String data, boolean setsize) throws IOException, AWSException
	{
		write(ByteBuffer.wrap(data.getBytes(AWSConstants.UTF_8)),setsize);
	}

	/**
	 * To write data to the client
	 * @param data - byte array of data
	 */
	
	public void write(byte[] data) throws IOException, AWSException
	{
		write(ByteBuffer.wrap(data),true);
	}

	/**
	 * To write data to the client
	 * @param data - byte array of data
	 * @param setsize - to set maximum write limit as data limit if true
	 */
	
	public void write(byte[] data, boolean setsize) throws IOException, AWSException
	{
		write(ByteBuffer.wrap(data),setsize);
	}

	/**
	 * To write data to the client
	 * @param data - byte array of data
	 * @param offset - start index of data
	 * @param length - length of the data from start index
	 */
	
	public void write(byte[] data,int offset, int length) throws IOException, AWSException
	{
		write(ByteBuffer.wrap(Arrays.copyOfRange(data,offset,offset+length)),true);
	}

	/**
	 * To write data to the client
	 * @param data - byte array of data
	 * @param offset - start index of data
	 * @param length - length of the data from start index
	 * @param setsize - to set maximum write limit as data limit if true
	 */
	
	public void write(byte[] data,int offset, int length, boolean setsize) throws IOException, AWSException
	{
		write(ByteBuffer.wrap(Arrays.copyOfRange(data, offset, offset+length)),setsize);
	}

	/**
	 * To write binary data to websocket
	 * @param data - byte array of data
	 */

	public void writeBinary(byte[] data) throws IOException, AWSException
	{
		write(ByteBuffer.wrap(data),true,Util.WS_OPCODE_BINARY);
	}

	/**
	 * To write binary data to the client
	 * @param data - byte array of data
	 * @param offset - start index of data
	 * @param length - length of data from start index
	 */
	
	public void writeBinary(byte[] data,int offset, int length) throws IOException, AWSException
	{
		write(ByteBuffer.wrap(Arrays.copyOfRange(data, offset, offset+length)),true,Util.WS_OPCODE_BINARY);
	}

	/**
	 * To write data in sequence to the client
	 * @param data - byte array of data
	 * @param index - index of the write in sequence
	 */
	
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
		if(websocket)
		{
			data = getWebSocketWriteFrame(data, opcode, true);
		}
		if(!client.isResponseHeaderPushed() && !websocket)
		{
			commitChunkedTransfer();
		}
		ByteBuffer bbuffer = ByteBuffer.wrap(data);
		if(chunkmode)
		{
			bbuffer=getChunkedData(bbuffer);
		}
		client.setOutputDataSize(bbuffer.limit());
		client.writeData(bbuffer,index);
	}
	
	/**
	 * To write data for a post request to the client
	 * @param data - byte array of data
	 */
	
	public void writePostContent(byte[] data) throws IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		ByteBuffer bb = ByteBuffer.wrap(data);
		int contentLength = bb.limit();
		DateFormat df = new SimpleDateFormat(AWSConstants.SIMPLE_DATE_FORMAT);
		appendHeaderValue(AWSConstants.HDR_LAST_MODIFIED, df.format(new Date(lastModified)));
		appendHeaderValue(AWSConstants.HDR_CONTENT_TYPE, contentType);
		appendHeaderValue(AWSConstants.HDR_CONTENT_LENGTH,contentLength+ "");//No I18N
		appendHeaderValue(AWSConstants.HDR_DATE, df.format(new Date()));
		appendHeaderValue(AWSConstants.HDR_CONNECTION, AWSConstants.HDR_KEEP_ALIVE);
		if(ConfManager.isServerHeaderEnabled())
		{
			appendHeaderValue(AWSConstants.HDR_SERVER, AWSConstants.HDR_AWSERVER);
		}
		if(xframerestrict)
		{
			appendHeaderValue(AWSConstants.HDR_X_FRAME_OPTIONS, AWSConstants.SAMEORIGIN);
		}
		client.writeData(getHeaderData());
		client.setResponseHeaderPushed();
		setOutputDataSize(contentLength);
		client.writeData(bb);
	}

	/**
	 * To write data to a mobile client
	 * @param data - byte array of data
	 */

	public void writeMobileContent(byte[] data) throws IOException, AWSException
	{
		writeMobileContent(data,-1);
	}


	private ByteBuffer getHeaderData() throws IOException, AWSException
	{
		if(ConfManager.isSTSEnabled() && client.isSSL())
		{
			appendHeaderValue(AWSConstants.HDR_STRICT_TRANSPORT_SECURITY,AWSConstants.MAX_AGE_EQUAL_TO+ConfManager.getSTSMaxLifeTime());
		}
		int responseCode = (client.getResponseCode() == -1) ? HttpResponseCode.OK : client.getResponseCode();
		String responseMessage = (client.getResponseMessage() == null) ? HttpResponseCode.OK_MSG : client.getResponseMessage();
		String statusLine = AWSConstants.HTTP_1_1 + AWSConstants.SPACE + responseCode + AWSConstants.SPACE + responseMessage + AWSConstants.NEWLINE;
		CharBuffer headerBuffer = CharBuffer.allocate(1024);
		headerBuffer = checkAndResizeHeaderBuffer(headerBuffer, statusLine.length());
		headerBuffer.put(statusLine);
		client.setStatusLine(responseCode, responseMessage);
		for(Enumeration enu = headerMap.keys(); enu.hasMoreElements();)
		{
			String headerkey = (String)enu.nextElement();
			String headervalue = (String)headerMap.get(headerkey);
			if(gzip != null && (headerkey.toLowerCase().contains(AWSConstants.CONTENT_LENGTH) || headerkey.toLowerCase().contains(AWSConstants.CONTENT_ENCODING)))
			{
				continue;
			}
			if(headervalue != null)
			{
				headerBuffer = addHeaderToHeaderBuffer(headerkey, headervalue, headerBuffer);
			}
		}
		if(gzip != null)
		{
			appendHeaderValue(AWSConstants.HDR_CONTENT_ENCODING, AWSConstants.GZIP);
			headerBuffer = addHeaderToHeaderBuffer(AWSConstants.HDR_CONTENT_ENCODING, AWSConstants.GZIP, headerBuffer);
			if(!chunkmode)
			{
				appendHeaderValue(AWSConstants.HDR_CONTENT_LENGTH, ""+gzip.length);//No I18N
				headerBuffer = addHeaderToHeaderBuffer(AWSConstants.HDR_CONTENT_LENGTH, ""+gzip.length, headerBuffer);
			}
		}
		if(cookielist != null && !cookielist.isEmpty())
		{
			ArrayList<HashMap<String,String>> accessCookie = new ArrayList<>();
			Set<String> cookienames = cookielist.keySet();
			Iterator itr = cookienames.iterator();
			while(itr.hasNext())
			{
				String name = (String)itr.next();
				Cookie ck = (Cookie)cookielist.get(name);
				if(ck != null)
				{
					headerBuffer = addHeaderToHeaderBuffer(AWSConstants.HDR_SET_COOKIE, ck.toString(), headerBuffer);
					accessCookie.add(ck.getAccessCookieValue());
				}
			}
			client.setAccessCookie(accessCookie);
		}
		headerBuffer = checkAndResizeHeaderBuffer(headerBuffer, AWSConstants.NEWLINE.length());
		headerBuffer.put(AWSConstants.NEWLINE);
		headerBuffer.flip();
		return getEncodedHeaderBuffer(headerBuffer);
	}

	private CharBuffer addHeaderToHeaderBuffer(String key, String value, CharBuffer headerBuffer) throws AWSException
	{
		int length = key.length()+value.length()+2+AWSConstants.NEWLINE.length();
		headerBuffer = checkAndResizeHeaderBuffer(headerBuffer, length);
		headerBuffer.put(key);
		headerBuffer.put(": ");
		headerBuffer.put(value);
		headerBuffer.put(AWSConstants.NEWLINE);
		return headerBuffer;
	}

        /**
	 * To write data to a mobile client in sequence
	 * @param data - byte array of data
	 * @param index - index of the write in sequence
	 */
	
	public void writeMobileContent(byte[] data, long index) throws IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		ByteBuffer bb = ByteBuffer.wrap(data);
		int contentLength = bb.limit();
		appendHeaderValue(AWSConstants.HDR_CONTENT_TYPE, AWSConstants.APPLICATION_VND_MS_SYNC_WBXML);
		appendHeaderValue(AWSConstants.HDR_CONTENT_LENGTH,contentLength+ "");//No I18N
		appendHeaderValue(AWSConstants.HDR_SERVER, AWSConstants.HDR_MICROSOFT_IIS_6_0);
		appendHeaderValue(AWSConstants.HDR_MS_SERVER_ACTIVESYNC, AWSConstants.VERSION_8_1);
		client.writeData(getHeaderData());
		client.setResponseHeaderPushed();
		setOutputDataSize(contentLength);
		client.writeData(bb,index);
	}

	/** 
	 * To write GZIP data on commit chunked transfer
	 * @param data - data to be gzipped
	 */ 

	public void setGZIPData(String data) throws IOException
	{
		setGZIPData(data.getBytes());
	}

	/**
	 * To write GZIP data on commit chunked transfer
	 * @param data - data to be gzipped.
	 */
	
	public void setGZIPData(byte[] data) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		GZIPOutputStream gzipos = new GZIPOutputStream(baos);
		gzipos.write(data);
		gzipos.close();
		gzip = baos.toByteArray();
	}

	/**
	 * To close the connection safely
	 */
	
	public void close() throws IOException, AWSException
	{
		try
		{
		if(this.clientId != client.getClientId())
		{
			logger.addDebugLog(Level.FINE, AWSLogConstants.CLOSE_CALLED_FOR_OLD_RESPONSE_OBJECT, HttpResponse.class.getName(),AWSLogMethodConstants.CLOSE,new Object[]{client});
			return;
		}

		if(!isActive())
		{
			logger.addDebugLog(Level.FINE, AWSLogConstants.INACTIVE_CONNECTION_CLOSE_ALREADY_DONE,  HttpResponse.class.getName(),AWSLogMethodConstants.CLOSE,new Object[]{client});
			return;
		}

		if(isReinitSet())
		{
			logger.addDebugLog(Level.FINE, AWSLogConstants.KEEPALIVE_CONNECTION_REINIT_ALREADY_DONE, HttpResponse.class.getName(),AWSLogMethodConstants.CLOSE, new Object[]{client});
			return;
		}
			
		if(!client.isResponseHeaderPushed() && !websocket)
		{
			commitChunkedTransfer();
		}

		prepareEndAWSAccessLog();

		if(errresponse || !isKeepAliveValid())
		{
			client.setCloseAfterWrite();
		}
		else
		{
			client.reinit();

			if(chunkmode)
			{
				write(ByteBuffer.allocate(0));
			}
			else
			{
				client.invokeWriteIfNeeded();
			}
		}
		}
		catch(IOException ex)
		{
			logger.addExceptionLog(Level.FINE, AWSLogConstants.IOEXCEPTION_HTTPRESPONSE_CLOSE, HttpResponse.class.getName(),AWSLogMethodConstants.CLOSE, ex);
			throw ex;
		}
		catch(AWSException e)
		{
			logger.addExceptionLog(Level.FINE, AWSLogConstants.AWSEXCEPTION_HTTPRESPONSE_CLOSE, HttpResponse.class.getName(),AWSLogMethodConstants.CLOSE, e);
			throw e;
		}
	
	}

	/** 
	 * To close the connection immediately unsafe
	 */
	
	public void forceclose() throws IOException
	{
		client.close(AWSConstants.FORCE_CLOSE);
	}

	/**
	 * To add value to the content-length header
	 * @param i - length of the data to be written
	 */
	
	public void setContentLength(long i) 
	{
		contentLength = i;
	}

	/**
	 * To add value to the content-type header
	 * @param string - type of content
	 */
	
	public void setContentType(String string) 
	{
		contentType = string;
	}

        /**
	 * To send x-frame-options header with value same-origin
	 */
	
	public void restrictXFrame()
	{
		xframerestrict = true;
	}

	/**
	 * To set last-modified header
	 * @param l - time in milliseconds 
	 */
	
	public void setLastModified(long l) 
	{
		lastModified = l;
	}

	/**
	 * To get IP address of the client
	 */
	
	public String getIPAddress()
	{
		return this.ipaddr;
	}

	/**
	 * To get IP address of the client with socket port
	 */

	public String getIpWithPort()
	{
		return client.getIPAddress();
	}

	/**
	 * To determine the type of connection secure or unsecure
	 * @return - true-secure, false-unsecure
	 */
	
	public boolean isSecuredAccess()
	{
		return client.isSecuredAccess();
	}

	/**
	 * To get failed index list for sequential write
	 * @return - list of failed index 
	 */
	
	public ArrayList getFailedIndexList()
	{
		return client.getFailedIndexList();
	}

	/**
	 * To determine if the header is present in the given list 
	 * @param headerData - a hashtable of headers key-value
	 * @param headerName - header to check presence
	 * @return - true, header present
	 * 	     false, header not present
	 */
	
	protected boolean isHeaderPresent(Hashtable headerData, String headerName)
	{
		if(headerData != null)
		{
			TreeSet ts = new TreeSet(String.CASE_INSENSITIVE_ORDER);
			ts.addAll(headerData.keySet());
			if(ts.contains(headerName))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * To send ok response - 200 with default push headers
	 */

	public void commitPushResponseHeader() throws IOException, AWSException
	{
		commitPushResponseHeader(headerMap);
	}

	/**
	 * To send ok response - 200 with customized push headers
	 */
	
	public void commitPushResponseHeader(Hashtable headerdata) throws IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		appendHeaderValue(AWSConstants.HDR_CONNECTION,AWSConstants.KEEP_ALIVE);
		appendHeaderValue(AWSConstants.HDR_CONTENT_TYPE,AWSConstants.TEXT_PLAIN_CHARSET_UTF_8);
		appendHeaderValue(AWSConstants.HDR_CONTENT_LENGTH,contentLength + "");//No I18N
		if(ConfManager.isServerHeaderEnabled())
		{
			appendHeaderValue(AWSConstants.HDR_SERVER, AWSConstants.HDR_AWSERVER);
		}
		if(xframerestrict)
		{
			appendHeaderValue(AWSConstants.HDR_X_FRAME_OPTIONS, AWSConstants.SAMEORIGIN);
		}
		this.headerMap.putAll(headerdata);
		client.writeData(getHeaderData());
		client.setResponseHeaderPushed();
	}

	/**
	 * To send ok - response with default headers
	 */
	
	public void commitResponseHeader() throws IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		addStatusLine(HttpResponseCode.OK,HttpResponseCode.OK_MSG);
		DateFormat df = new SimpleDateFormat(AWSConstants.SIMPLE_DATE_FORMAT_1);
		appendHeaderValue(AWSConstants.HDR_LAST_MODIFIED, df.format(new Date(lastModified)));
		appendHeaderValue(AWSConstants.HDR_CONTENT_TYPE, contentType);
		appendHeaderValue(AWSConstants.HDR_CONTENT_LENGTH,contentLength + "");//No I18N
		appendHeaderValue(AWSConstants.HDR_DATE, df.format(new Date()));
		appendHeaderValue(AWSConstants.HDR_CONNECTION, AWSConstants.HDR_KEEP_ALIVE);
		if(ConfManager.isServerHeaderEnabled())
                {
			appendHeaderValue(AWSConstants.HDR_SERVER, AWSConstants.HDR_AWSERVER);
		}
		if(xframerestrict)
		{
			appendHeaderValue(AWSConstants.HDR_X_FRAME_OPTIONS, AWSConstants.SAMEORIGIN);
		}
		client.writeData(getHeaderData());
		client.setResponseHeaderPushed();
	}

	/**
	 * To add a header to be sent
	 * @param name - name of the header
	 * @param value - value of the header
	 */

	public void addHeader(String name, String value)
	{
		if(name == null || value == null)
		{
			return;
		}
		headerMap.put(name, value);
	}

	/**
	 * To add a cookie to be sent
	 * @param cookie - cookie value
	 */

	public void addCookie(Cookie cookie)
	{
		cookielist.put(cookie.getName(), cookie);
	}

	/**
	 * To send OK response along with headers
	 * @param headerdata - list of header with its name-value
	 */
	
	public void commitChunkedTransfer(Hashtable headerdata) throws IOException, AWSException
	{
		commitChunkedTransfer(-1, null, headerdata, true);
	}
	
	/**
	 * To send OK response along with headers
	 * @param headerdata - list of header with its name-value
	 * @param appendheader - true - add default headers 
	false - donot add default headers
	*/

        public void commitChunkedTransfer(Hashtable headerdata,boolean appendheader) throws IOException, AWSException
        {
            commitChunkedTransfer(-1, null, headerdata, appendheader);
        }
	
	/**
         * To send OK response with headers added already
         */
                                                                                                                                                                                  
        public void commitChunkedTransfer() throws IOException, AWSException
        {
            commitChunkedTransfer(-1, null, headerMap, true);
        }
	
	/**
         * To send OK response with headers added already
         * @param appendheader - true - add default headers 
                                false - donot add default headers   
	 */
                                                                                                                                                                                  
        public void commitChunkedTransfer(boolean appendheader) throws IOException, AWSException
        {
            commitChunkedTransfer(-1, null, headerMap, appendheader);
        }
        
	/**
	 * To send a response with status code , msg and headers added beforehand
	 * @param statuscode - status code of the response
	 * @param statusmsg - status message of the response
	 */
	
	public void commitChunkedTransfer(int statuscode, String statusmsg) throws IOException, AWSException
	{
		commitChunkedTransfer(statuscode, statusmsg, headerMap, true);
	}
	
	/**
         * To send a response with status code , msg and headers added beforehand
         * @param statuscode - status code of the response
         * @param statusmsg - status message of the response
         * @param appendheader - true - add default headers                                                                                                                       
                                false - donot add default headers
	 */
        
        public void commitChunkedTransfer(int statuscode, String statusmsg,boolean appendheader) throws IOException, AWSException
        {
            commitChunkedTransfer(statuscode, statusmsg, headerMap, appendheader);
        }

	/**
	 * To send a response with status code , msg and headers 
	 * @param statuscode - status code of the response
	 * @param statusmsg - status message of the response
	 * @param headerdata - list of headers with its name-value
	 */

	public void commitChunkedTransfer(int statuscode, String statusmsg, Hashtable headerdata) throws IOException, AWSException
	{
		commitChunkedTransfer(statuscode, statusmsg, headerdata, true);
	}	

	/**
	 * To send a response with status line and headers 
	 * @param statusCode - status code of the response
	 * @param headerdata - list of headers with its name-value
	 * @param appendheader - true - add default headers                                                                                                                       
	false - donot add default headers
	*/
	
	public void commitChunkedTransfer(int statusCode, String statusMessage, Hashtable headerdata, boolean appendheader) throws IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		if(lastModified == -1)
		{
			lastModified = System.currentTimeMillis();
		}
		addStatusLine(statusCode, statusMessage);
		DateFormat df = new SimpleDateFormat(AWSConstants.SIMPLE_DATE_FORMAT_1);
		if(!isHeaderPresent(headerdata, AWSConstants.HDR_DATE))
		{
			appendHeaderValue(AWSConstants.HDR_DATE, df.format(new Date()));
		}
		if(appendheader)
		{
			if(!isHeaderPresent(headerdata, AWSConstants.HDR_LAST_MODIFIED))
			{
				appendHeaderValue(AWSConstants.HDR_LAST_MODIFIED, df.format(new Date(lastModified)));
			}
			if(!isHeaderPresent(headerdata, AWSConstants.HDR_CONTENT_TYPE))
			{
				appendHeaderValue(AWSConstants.HDR_CONTENT_TYPE, contentType);
			}
			if(isKeepAliveValid() && !isHeaderPresent(headerdata, AWSConstants.HDR_CONNECTION))
			{
				appendHeaderValue(AWSConstants.HDR_CONNECTION, AWSConstants.HDR_KEEP_ALIVE);
			}
			if(!isHeaderPresent(headerdata, AWSConstants.HDR_SERVER) && ConfManager.isServerHeaderEnabled())
			{
				appendHeaderValue(AWSConstants.HDR_SERVER, AWSConstants.HDR_AWSERVER);
			}
			if(xframerestrict)
			{
				appendHeaderValue(AWSConstants.HDR_X_FRAME_OPTIONS, AWSConstants.SAMEORIGIN);
			}
		}
		if(isKeepAliveEnabled() && !isHeaderPresent(headerdata,AWSConstants.HDR_CONTENT_LENGTH))
		{
			client.setChunkedStatus(true);
			appendHeaderValue(AWSConstants.HDR_TRANSFER_ENCODING,AWSConstants.CHUNKED);
			chunkmode=true;
		}
		this.headerMap.putAll(headerdata);
		client.writeData(getHeaderData());
		client.setResponseHeaderPushed();
		if(gzip !=null)
		{
			write(gzip);
		}
	}
	
	/**
	 * To send a OK response as a chunked data
	 */

	public void commitLiveChunkedTransfer() throws IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		lastModified = System.currentTimeMillis();
		DateFormat df = new SimpleDateFormat(AWSConstants.SIMPLE_DATE_FORMAT_1);
		appendHeaderValue(AWSConstants.HDR_LAST_MODIFIED, df.format(new Date(lastModified)));
		appendHeaderValue(AWSConstants.HDR_CONTENT_TYPE, AWSConstants.TEXT_OR_PLAIN);
		appendHeaderValue(AWSConstants.HDR_DATE, df.format(new Date()));
		appendHeaderValue(AWSConstants.HDR_CONNECTION, AWSConstants.HDR_KEEP_ALIVE);
		if(ConfManager.isServerHeaderEnabled())
                {
			appendHeaderValue(AWSConstants.HDR_SERVER, AWSConstants.HDR_AWSERVER);
		}
		if(xframerestrict)
		{
			appendHeaderValue(AWSConstants.HDR_X_FRAME_OPTIONS, AWSConstants.SAMEORIGIN);
		}
		client.setChunkedStatus(true);
		appendHeaderValue(AWSConstants.HDR_TRANSFER_ENCODING,AWSConstants.CHUNKED);
		client.writeData(getHeaderData());
		client.setResponseHeaderPushed();
	}

	private CharBuffer resizeHeaderBuffer(CharBuffer headerBuffer, int requirement)
	{
		requirement +=10; // Extra 10 space to append newline directly to headerbuffer
		CharBuffer newCB = CharBuffer.allocate(headerBuffer.capacity()+requirement);
		headerBuffer.flip();
		newCB.put(headerBuffer);
		return newCB;
	}

	private CharBuffer checkAndResizeHeaderBuffer(CharBuffer headerBuffer, int length) throws AWSException
	{
		if(headerBuffer.remaining() < length )
		{
			if( (headerBuffer.position()+length) <= ConfManager.getMaxHeaderSize())
			{
				return resizeHeaderBuffer(headerBuffer, length-headerBuffer.remaining());
			}
			else
			{
				throw new AWSException("Max Header Limit Exceeds");//No I18N
			}
		}
		return headerBuffer;
	}

	public void addStatusLine(int responseCode, String responseMessage)
	{
		if(!client.isResponseHeaderPushed())
		{
			client.setStatusLine(responseCode, responseMessage);
		}
	}

	private void appendHeaderValue(String name, String value) throws AWSException 
	{
		if(name == null || value == null)
		{
			return;
		}
		headerMap.putIfAbsent(name, value);
	}

	/**
	 * To send error respone with status code and msg
	 * @param i - status code
	 * @param msg - status msg
	 */
	
	public void sendError(int i, String msg) throws CharacterCodingException, IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		errresponse = true;
		addStatusLine(i, msg);//No I18n

		appendHeaderValue(AWSConstants.HDR_DATE,  ""+new Date());//No I18N
		client.writeData(getHeaderData(),-1,true);
		client.setResponseHeaderPushed();
	}

	/** 
	 * To send redirection response with status code and msg
	 * @param statuscode - status code
	 * @param msg - status msg
	 * @param url - Redirection url
	 */

	public void sendRedirect(int statuscode , String msg , String url) throws CharacterCodingException, IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		addStatusLine(statuscode, msg);//No I18n
		appendHeaderValue(AWSConstants.HDR_LOCATION, url);//No I18n
		if(isKeepAliveValid())
		{
			appendHeaderValue(AWSConstants.HDR_REUSE_CONNECTION, AWSConstants.VALUE_1);
		}
		client.writeData(getHeaderData(), -1, true);
		client.setResponseHeaderPushed();
	}

	/**
	 * To send a WS version error with status code and msg
	 * @param i - status code
	 * @param msg - status msg
	 */
	
	public void sendWSVersionError(int i, String msg) throws CharacterCodingException, IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		addStatusLine(i, msg);//No i18N
		appendHeaderValue(AWSConstants.HDR_DATE,  ""+new Date());//No I18N
		appendHeaderValue(AWSConstants.HDR_SEC_WEBSOCKET_VERSION,ConfManager.getSupportedWSVersions());
		client.writeData(getHeaderData(), -1);
		client.setResponseHeaderPushed();
	}

	/**
	 * To send options response
	 * @param host - host name
	 * @param reqHeaders - access-control-allow-header value
	 */
	
	public void sendOptionsResponse(String host,String reqHeaders) throws CharacterCodingException, IOException, AWSException
	{
		sendOptionsResponse(-1, null, host, reqHeaders);
	}

	/**
	 * To send options response 
	 * @param i - status code
	 * @param msg - status message
	 * @param host - host name
	 * @param reqHeaders - access-control-allow-header value
	 */
	
	public void sendOptionsResponse(int i, String msg, String host, String reqHeaders) throws CharacterCodingException, IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		if(!ConfManager.isValidDomain(host))
		{
			host = ConfManager.getValidDomains();
		}
		addStatusLine(i, msg);//No I18n
		if(host != null)
		{
			appendHeaderValue(AWSConstants.HDR_ACCESS_CONTROL_ALLOW_ORIGIN, host);
		}
		if(reqHeaders != null)
		{
			appendHeaderValue(AWSConstants.HDR_ACCESS_CONTROL_ALLOW_HEADERS, reqHeaders);
		}
		
		// For preflight request, there won't be any response content written in body, hence setting the response content as 0
		appendHeaderValue(AWSConstants.HDR_CONTENT_LENGTH,AWSConstants.VALUE_0);

		if(isKeepAliveValid())
		{
			appendHeaderValue(AWSConstants.HDR_CONNECTION, AWSConstants.HDR_KEEP_ALIVE);
		}

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
		appendHeaderValue(AWSConstants.HDR_ACCESS_CONTROL_ALLOW_METHODS, buffer.toString());
		appendHeaderValue(AWSConstants.HDR_ACCESS_CONTROL_MAX_AGE, ""+(30*60));//No I18n
		appendHeaderValue(AWSConstants.HDR_ACCESS_CONTROL_ALLOW_CREDENTIALS, AWSConstants.TRUE);
		client.writeData(getHeaderData());
		client.setResponseHeaderPushed();
	}

	/**
	 * To send options response with headers
	 * @param host - host name
	 * @param reqHeaderMap - list of header-value
	 */
	
	public void sendOptionsResponse(String host,HashMap reqHeaderMap) throws CharacterCodingException, IOException, AWSException
	{
		sendOptionsResponse(-1, null, host, reqHeaderMap);
	}

	/**
	 * To send options response with headers
	 * @param i - status code
	 * @param msg - status message
	 * @param host - host name
	 * @param reqHeaderMap - list of custom-header value
	 */
	
	public void sendOptionsResponse(int i, String msg, String host, HashMap reqHeaderMap) throws CharacterCodingException, IOException, AWSException
	{
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		if(!ConfManager.isValidDomain(host))
		{
			host = ConfManager.getValidDomains();
		}
		addStatusLine(i, msg);//No I18n
		if(reqHeaderMap != null)
		{
			Iterator itr = reqHeaderMap.keySet().iterator();
			while(itr.hasNext())
			{
				String headerkey = (String)itr.next();
				String headervalue = (String)reqHeaderMap.get(headerkey);
				if(headervalue != null)
				{
					appendHeaderValue(headerkey,headervalue);
				}
			}
		}
		client.writeData(getHeaderData());
		client.setResponseHeaderPushed();
	}

	/**
	 * To set the websocket 
	 * @param wsservlet - object of type WebSocket
	 */
	
	public void setWebSocket(WebSocket wsservlet, boolean compressionenabled, HashMap compressiondetails)
	{
		client.setWebSocket(wsservlet, compressionenabled, compressiondetails);
	}

	/**
	 * To hold read in a websocket connection
	 */

	public void holdRead() throws IOException
	{
		client.holdRead();
	}

	/**
	 * To check if read is in hold for a websocket connection
	 */

	public boolean isOnHold()
	{
		return client.isOnHold();
	}

	/**
	 * To enable read put on hold
	 */

	public void enable() throws IOException
	{
		client.enable();
	}

	/**
	 * To send upgrade to websocket response 
	 * @param wsKey - websocketkey sent in request
	 * @param wsVersion - websocket version
	 * @param wsSupportedExtensions - websocket extensions
	 * @param wsSupportedProtocols - websocket sub-protocols
	 */
	
	public void upgradeToWebsocket(String wsKey, String wsVersion, String wsSupportedExtensions,String wsSupportedProtocols) throws CharacterCodingException, IOException,NoSuchAlgorithmException,UnsupportedWSVersionException, AWSException
	{
		if(wsKey == null || wsVersion == null)
		{
			if(ConfManager.printInvalidWSParams())
			{
				throw new IOException("Invalid ws params [param:"+Util.getAccessParams(client.getParamMap())+"] [header:"+Util.getAccessHeaders(client.getHeaderMap())+"]");//No I18N
			}
			else
			{
				throw new IOException("Invalid ws params");//No I18N
			}

		}
		if(!ConfManager.isSupportedWSVersion(wsVersion))
		{
			throw new UnsupportedWSVersionException("Unsupported Version ");//No I18N
		}
		if(client.isResponseHeaderPushed())
		{
			return;
		}
		addStatusLine(HttpResponseCode.WS_UPGRADE, AWSConstants.SWITCHING_PROTOCOLS);
		appendHeaderValue(AWSConstants.HDR_CONNECTION, AWSConstants.HDR_UPGRADE);
		appendHeaderValue(AWSConstants.HDR_UPGRADE, AWSConstants.WEBSOCKET);
		appendHeaderValue(AWSConstants.HDR_SEC_WEBSOCKET_ACCEPT, getWSHandshakeKey(wsKey,wsVersion));
		if(wsSupportedExtensions!=null)
		{
			if(ConfManager.isGridEngineActive() && ConfManager.isGridPort(client.getLocalPort()))//Temporary workaround for WCP-Client
			{
				appendHeaderValue(AWSConstants.HDR_SEC_WEBSOCKET_EXTENSIONS_1, wsSupportedExtensions);
			}
			else
			{
				appendHeaderValue(AWSConstants.HDR_SEC_WEBSOCKET_EXTENSIONS, wsSupportedExtensions);
			}
		}
		if(wsSupportedProtocols!=null)
		{
			appendHeaderValue(AWSConstants.HDR_SEC_WEBSOCKET_PROTOCOL, wsSupportedProtocols);
		}
		client.writeData(getHeaderData(), -1);
		websocket=true;
		client.setResponseHeaderPushed();
		replaceAndEndTimeLine(AWSConstants.TL_READ_TIME,AWSConstants.TL_WS_CONNECT_TIME);
	}

	public void setCharacterEncodingScheme(String charset)
	{
		this.charset = charset;
	}

	private ByteBuffer getEncodedHeaderBuffer(CharBuffer headerBuffer) throws CharacterCodingException
	{
		if(encoder == null)
		{
			encoder = Charset.forName(charset).newEncoder();
		}
		return encoder.encode(headerBuffer);
	}

	private String getWSHandshakeKey(String wsKey,String wsVersion) throws NoSuchAlgorithmException
	{
		wsKey += getWSAppendKey(wsVersion);

		MessageDigest sha = MessageDigest.getInstance("SHA-1");
		sha.reset();
		sha.update(wsKey.getBytes());
		return (new String(Base64.getEncoder().encode(sha.digest())));
	}

	private String getWSAppendKey(String wsVersion)
	{
		// for version = 13
		return "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";//No I18N
	}

	/**
	 * To form a websocket frame for the data 
	 * @param data - byte array of data
	 * @param opcode - opcode - text or binary
	 * 		   Util.WS_OPCODE_TEXT - 1
	 * 		   Util.WS_OPCODE_BINARY - 2 
	 * @return - byte array of websocket frame
	 */
	
	private byte[] getWebSocketWriteFrame(byte[] data,int opcode) throws IOException, AWSException
	{
		return getWebSocketWriteFrame(data, opcode, true);
	}

	private byte[] getWebSocketWriteFrame(byte[] data,int opcode,boolean fin) throws IOException, AWSException
	{
		if(!ensureValidity(opcode, fin))
		{
			logger.addDebugLog(Level.INFO, AWSLogConstants.PROTOCOL_ERROR,HttpResponse.class.getName(),AWSLogMethodConstants.GET_WEBSOCKET_WRITE_FRAME, new Object[]{opcode, fin, isCFInProgress});
			throw new AWSException("Protocol Error - Frame Mismatch");//No I18n
		}
		return Util.getWebSocketWriteFrame(data, opcode, fin, isPerMessageDeflate(opcode), client.getDeflater());
	}

	private boolean isTextFrame(int opcode)
	{
		return opcode==Util.WS_OPCODE_TEXT;
	}

	private boolean isBinaryFrame(int opcode)
	{
		return opcode==Util.WS_OPCODE_BINARY;
	}

	private boolean isContinuationFrame(int opcode)
	{
		return opcode==Util.WS_OPCODE_CONTINUATION;
	}

	private boolean isPingFrame(int opcode)
	{
		return opcode==Util.WS_OPCODE_PING;
	}

	private boolean isPongFrame(int opcode)
	{
		return opcode==Util.WS_OPCODE_PONG;
	}

	private boolean isCloseFrame(int opcode)
	{
		return opcode==Util.WS_OPCODE_CLOSE;
	}

	void wsStreamWrite(String msg, boolean isFinalChunk) throws IOException, AWSException
	{
		if(isFinalChunk)
		{
			wsStreamWrite(msg, Util.WS_OPCODE_CONTINUATION, true);
			firstFrameSent = false;
		}
		else if(!firstFrameSent)
		{
			wsStreamWrite(msg, Util.WS_OPCODE_TEXT, false);
			firstFrameSent = true;
		}
		else
		{
			wsStreamWrite(msg, Util.WS_OPCODE_CONTINUATION, false);
		}
	}

	void wsStreamWrite(String msg, int opcode, boolean fin) throws IOException, AWSException
	{
		wsStreamWrite(ByteBuffer.wrap(msg.getBytes(AWSConstants.UTF_8)), opcode, fin, true);
	}

	void wsStreamWrite(byte[] msg, boolean isFinalChunk) throws IOException, AWSException
	{
		if(isFinalChunk)
		{
			wsStreamWrite(msg, Util.WS_OPCODE_CONTINUATION, true);
			firstFrameSent = false;
		}
		else if(!firstFrameSent)
		{
			wsStreamWrite(msg, Util.WS_OPCODE_BINARY, false);
			firstFrameSent = true;
		}
		else
		{
			wsStreamWrite(msg, Util.WS_OPCODE_CONTINUATION, false);
		}
	}

	private void wsStreamWrite(byte[] msg, int opcode, boolean fin) throws IOException, AWSException
	{
		wsStreamWrite(ByteBuffer.wrap(msg), opcode, fin, true);
	}

	private void wsStreamWrite(ByteBuffer data, int opcode, boolean fin, boolean setsize) throws IOException, AWSException
	{
		if(websocket)
		{
			data = getWebSocketWriteFrame(data, opcode, fin);
			if(setsize)
			{
				client.setOutputDataSize(data.limit());
			}
			client.writeData(data, -1);
		}
	}

	private boolean ensureValidity(int opcode, boolean fin)
	{
		if(isPingFrame(opcode) || isPongFrame(opcode) || isCloseFrame(opcode))
		{
			return true;
		}
		if(isTextFrame(opcode) || isBinaryFrame(opcode))
		{
			if(isCFInProgress == true)
			{
				return false;
			}
			if(!fin)
			{
				isCFInProgress = true;
			}
			return true;
		}
		if(isContinuationFrame(opcode))
		{
			if(fin)
			{
				isCFInProgress = false;
			}
			return true;
		}
		logger.addDebugLog(Level.SEVERE, AWSLogConstants.PROTOCOL_ERROR_PASSED_ALL_IF_AND_RETURN_FALSE,HttpResponse.class.getName(),AWSLogMethodConstants.ENSURE_VALIDITY);
		return false;
	}

	private ByteBuffer getWebSocketWriteFrame(ByteBuffer data, int opcode, boolean fin) throws IOException, AWSException
	{
		if(!ensureValidity(opcode, fin))
		{
			logger.addDebugLog(Level.INFO, AWSLogConstants.PROTOCOL_ERROR, HttpResponse.class.getName(),AWSLogMethodConstants.GET_WEBSOCKET_WRITE_FRAME,new Object[]{opcode, fin, isCFInProgress});
			throw new AWSException("Protocol Error - Frame Mismatch");//No I18n
		}

		return Util.getWebSocketWriteFrame(data, opcode, fin, isPerMessageDeflate(opcode), client.getDeflater());
	}

	public void doPong(byte[] msg, int opcode, boolean fin) throws AWSException, IOException
	{
		byte[] bb = Util.getWebSocketWriteFrame(msg, opcode, fin, false, null);//Ping/Pong cannot be compressed.
		client.setOutputDataSize(bb.length);
		client.writeData(ByteBuffer.wrap(bb));
	}

	/**
	 * To ping websocket
	 */
	
	public void ping()
	{
		client.pingWebSocket();
	}

	/**
         * To get the scheme/protocol of the connection
	 */

	public String getScheme()
	{
		return client.getScheme();
	}

	public int getResponseCode()
	{
		return client.getResponseCode();
	}

	public String getResponseMessage()
	{
		return client.getResponseMessage();
	}

	public LinkedHashMap getTimeLine()
	{
		return client.getTimeLine();
	}

	public  String getReqId()
	{
		return client.getReqId();
	}

	/**
	 * To determine if connection is websocket
	 */
	
	public boolean isWebSocket()
	{
		return websocket;
	}

	/** 
	 * update time line for internal client operations
	 * @param key - name of the operation
	 * @param stime - start time
	 * @param etime - end time
	 * @return - previous value assiciated with the key or null
	 */		
	public Object updateTimeLine(String key, long stime, long etime)
	{
		ConcurrentHashMap hm = new ConcurrentHashMap();
		hm.put(AWSConstants.START,stime);
		hm.put(AWSConstants.END,etime);

		return updateTimeLine(key,hm);
	}


	/** 
	 * update time line for internal client operations
	 * @param key - name of the operation
	 * @param value - start and end time 
	 * @return - previous value assiciated with the key or null
	 */

	private Object updateTimeLine(String key, Object value)
	{
		return client.updateTimeLine(key,value);
	}

	/**
	 * Get time line for internal client operations
	 * @param key - name of the operation
	 * @return - timeline map
	 */

	public ConcurrentHashMap getTimeLineInfo(String key)
	{
		return client.getTimeLineInfo(key);
	}

	/**
	 * To remove time line for internal client operations
	 * @param key - name of the operation
	 * @return - previous value associated with key or null 
	 */

	public ConcurrentHashMap removeTimeLineInfo(String key)
	{
		return client.removeTimeLineInfo(key);
	}

	/**
	 * To start a time line for internal client operations
	 * @param key - name of the operation
	 */

	public void startTimeLine(String key)
	{
		client.startTimeLine(key);
	}	
	
	/**
	 * To end a time line for internal client operations
	 * @param key - name of the operation
	 */

	public void endTimeLine(String key)
	{
		client.endTimeLine(key);
	}	
	
	/**
	 * To replace and end a time line for internal client operations
	 * @param oldkey - name of the operation to be replaced
	 * @param newkey - name of the operation to replace
	 */

	public void replaceAndEndTimeLine(String oldkey, String newkey)
	{
		client.replaceAndEndTimeLine(oldkey,newkey);
	}
	
	/** 
	 * To check if keepalive is valid
	 */

	public boolean isKeepAliveValid()
	{
		return (client.isKeepAliveEnabled() && client.isKeepAliveValid());
	}
	
	/**
	 * To check if keepalive is enabled
	 */

	public boolean isKeepAliveEnabled()
	{
		return client.isKeepAliveEnabled();
	}

	/** 
	 * To enable keepalive in client
	 */

	public void enableKeepAlive()
	{
		client.enableKeepAlive();
	}

	/**
	 * To set keep alive timeout
	 * @param timeout - timeout for keepalive
	 */

	public void setKeepAliveTimeout(long timeout)
	{
		client.setKeepAliveTimeout(timeout);
	}

	/**
	 * To invoke notification
	 */

	public void invokeNotification(int notification)
	{
		client.invokeNotification(notification);
	}

	/**
	 * To check if the given response object's client is the same one as in response. 
	 */ 

	public boolean isSameClient(HttpResponse responseObject)
	{
		return getClientId() == responseObject.getClientId();
	}

	/**
	 * To get client's hashcode.
	 * @return Hashcode of current AsyncWebClient object.
	 */

	public int getClientId()
	{
		return this.client.hashCode();
	}

	/**
	 * To know if the reinit has been triggered for the client
	 */

	private boolean isReinitSet()
	{
		return client.isReinitSet();
	}
	
	/**
	 * To know if the client is active or not
	 */

	public boolean isActive()
	{
		return client.isActive();
	}

	/**
	 * For Internal use only, To update servletdispatcher time.
	 * @param diff - time taken between tpe execute call and run() starting.
	 */

	public void updateServletDispatcherTime(long diff)
	{
		servletdispatchertime += diff;
	}

	/**
	 * To get the waiting time in blocking queue for request processor.
	 * @return Waiting time in the blocking queue to execute the task.
	 */

	public long getRequestProcessorTime()
	{
		return client.getRequestProcessorTime();
	}

	/**
	 * To get the waiting time in blocking queue for servlet dispatcher.
	 * @return Waiting time in the blocking queue to execute the task.
	 */

	public long getServletDispatcherTime()
	{
		return servletdispatchertime;
	}

	/**
	 * In WebSocket, to dispatch data to onMessage().
	 * @param status - false - dispatch data asynchronously.
	 *	  	   true - dispatch data synchronized.
	 *	 	   by default, value will be true. Dispatch data synchronized.
	 */

	public void setWSServletThreadSafe(boolean status)
	{
		client.setWSServletThreadSafe(status);
	}

	/**
	 * To set read limit for this particular client.
	 * @param limit - max read limit.
	 */

	public void setReadLimit(int limit)
	{
		client.setReadLimit(limit);
	}

	/**
	 * To get read limit for this particular client.
	 * @return int - readlimit
	 */

	public int getReadLimit()
	{
		return client.getReadLimit();
	}

	/**
	 * To set initial writeBuffer size for this particular client.
	 * @param limit - initial writeBuffer limit.
	 **/

	public void setWriteCapacity(int limit)
	{
		client.setWriteCapacity(limit);
	}

	/**
	 * To get writecapacity for this particular client.
	 * @return int - writecapaacity
	 **/

	public int getWriteCapacity()
	{
		return client.getWriteCapacity();
	}

	/**
	 * To set max data per socket write for this particular client.
	 * @param maxdataperwrite - max allowed data size per socket write.
	 */

	public void setMaxDataPerWrite(int maxdataperwrite)
	{
		client.setMaxDataPerWrite(maxdataperwrite);
	}

	/**
	 * Returns maxDataPerWrite for this client.
	 * @return int - maxDataPerWrite for this client.
	 */

	public int getMaxDataPerWrite()
	{
		return client.getMaxDataPerWrite();
	}

	/**
	 * To set max data per socket read for this particular client.
	 * @param wsreadlimit - read limit.
	 */

	public void setMaxDataPerWSRead(int wsreadlimit)
	{
		client.setMaxDataPerWSRead(wsreadlimit);
	}

	/**
	 * Returns maxDataPerWSRead for this client.
	 * @return int - maxDataPerWSRead for this client
	 */

	public int getMaxDataPerWSRead()
	{
		return client.getMaxDataPerWSRead();
	}

	/**
	 * Returns inittime for this client.
	 * @return long - inittime for this client
	 */

	public long getInittime()
	{
		return client.getInittime();
	}

	private boolean isPerMessageDeflate(int opcode)
	{
		return client.isPerMessageDeflate() && ! isPingFrame(opcode) && ! isPongFrame(opcode);
	}

	/**
	 * Returns value of zeroReadCount
	 * @return int - zero read count.
	 */

	public int getZeroReadCount()
	{
		return client.getZeroReadCount();
	}

	/**
	 * To set maxZeroReadCount
	 * @param count - maxZeroReadCount
	 */

	public void setMaxZeroReadCount(int count)
	{
		client.setMaxZeroReadCount(count);
	}

	/**
	 * To get maxZeroReadCount
	 * @return Max Zero Read Count.
	 */

	public int getMaxZeroReadCount()
	{
		return client.getMaxZeroReadCount();
	}

	public String getStatsRequestURL()
	{
		return client.getStatsRequestURL();
	}

	public String getWebEngineName()
	{
		return client.getWebEngineName();
	}

	public boolean isResponseHeaderPushed()
	{
		return client.isResponseHeaderPushed();
	}

	/**
	 * Strictly for internal use only.
	 */

	public void startInstrumentation(HttpRequest httpreq)
	{
		try
		{
			if(!MIAdapter.getStatus() || client.isStreamModeEnabled())
			{
				return;
			}
			String requrl = client.getRequestURL();
			if(requrl == null)
			{
				requrl = "unknown";//No I18n
			}
			if(ConfManager.isMIExcludedURL(requrl) || (websocket && !ConfManager.isMIIncludedWSURL(requrl)))
			{
				return;
			}
			LinkedHashMap timelineinfo = client.getTimeLine();
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
				String skeletonurl = client.getStatsRequestURL();
				if(skeletonurl != null)
				{
					request.setURL(skeletonurl);
				}
				else
				{
					request.setURL(client.getRequestURL());
				}
			}
			catch(Exception ex)
			{
				logger.addExceptionLog(Level.INFO, "Exception in start Instrumentation : requrl : "+requrl+", request : "+httpreq, HttpResponse.class.getName(),AWSLogMethodConstants.START_INSTRUMENTATION, ex);//No I18n
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
			logger.log(Level.INFO, "Exception in Instrumentation start : request : "+httpreq, HttpResponse.class.getName(),AWSLogMethodConstants.START_INSTRUMENTATION, exp);//No I18n
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

			String skeletonurl = client.getStatsRequestURL();
			if(skeletonurl != null)
			{
				request.setURL(skeletonurl);
			}
			else
			{
				request.setURL(client.getRequestURL());
			}

			if(client.getTimeLine() == null)
			{
				return;
			}
			LinkedHashMap timelineinfo = new LinkedHashMap(client.getTimeLine());

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
			logger.addExceptionLog(Level.INFO, "Exception in Instrumentation : httpReq : "+httpreq, HttpResponse.class.getName(),AWSLogMethodConstants.FINISH_INSTRUMENTATION,exp);//No I18n
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
				logger.addExceptionLog(Level.INFO, "Exception in Instrumentation : req.complete : httpReq : "+httpreq, HttpResponse.class.getName(),AWSLogMethodConstants.FINISH_INSTRUMENTATION,miexp);//No I18n
			}
			finally
			{
				try
				{
					InstrumentManager.finishInstrumentation();
				}
				catch(Exception ex)
				{
					logger.addExceptionLog(Level.INFO, "Exception in Instrumentation : finish : httpReq : "+httpreq,HttpResponse.class.getName(),AWSLogMethodConstants.FINISH_INSTRUMENTATION, ex);//No I18n
				}
				request = null;
			}
		}
	}

	private void prepareEndAWSAccessLog()
	{
		try
		{
			if(ConfManager.isEndAWSAccessLogEnabled())
			{
				ZLMap zlmap = new ZLMap();
				if(client.getReqId() != null)
				{
					zlmap.put(AWSConstants.REQID, client.getReqId());
				}
				if(client.getRequestURL() != null)
				{
					zlmap.put(AWSConstants.REQUEST_URI, client.getRequestURL());
				}
				if(client.getParamMap() != null)
				{
					zlmap.put(AWSConstants.PARAM, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessParams(client.getParamMap()))));
				}
				if(client.getHeaderMap() != null)
				{
					zlmap.put(AWSConstants.REQUEST_HEADER, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessHeaders(client.getHeaderMap()))));
				}
				if(headerMap != null)
				{
					zlmap.put(AWSConstants.RESPONSE_HEADER, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessResponseHeaders(headerMap))));
				}
				if(client.getResponseMessage() != null)
				{
					zlmap.put(AWSConstants.RESPONSE_MESSAGE, client.getResponseMessage());
				}
				if(client.getRequestType() != null)
				{
					zlmap.put(AWSConstants.METHOD, client.getRequestType());
				}
				zlmap.put(AWSConstants.RESPONSE_CODE, client.getResponseCode());
				zlmap.put(AWSConstants.METHOD, client.getRequestType());
				zlmap.put(AWSConstants.REMOTE_IP, client.getRawIPAddress());
				zlmap.put(AWSConstants.SERVER_PORT, client.getSocketPort());
				zlmap.put(AWSConstants.LOCAL_PORT, client.getLocalPort());
				zlmap.put(AWSConstants.THREAD_ID, Thread.currentThread().getId());
				zlmap.put(AWSConstants.THREAD_NAME, Thread.currentThread().getName());
				zlmap.put(AWSConstants.VERSION, AWSConstants.HTTP_1_1);
				zlmap.put(AWSConstants.USER_AGENT_1, HttpDataWraper.getString(client.getHeader(AWSConstants.USER_AGENT)));
				client.setEndAccessLogs(zlmap);
			}
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION_IN_FORMATTING_ACCESSLOGGER,HttpResponse.class.getName(),AWSLogMethodConstants.PREPARE_END_AWS_ACCESS_LOG, e);
		}
	}

	public long getConnectionInitTime()
	{
		return this.client.getInittime();
	}

	public long getReqQueueInsertTime()
	{
		return this.client.getReqQueueInsertTime();
	}

	public long getTimeTakenForHandshake()
	{
		return this.client.getTimeTakenForHandshake();
	}

	public boolean isNewConnection()
	{
		return this.client.isNewConnection();
	}

	void removeRequestTimeOutTracker()
	{
		client.removeFromTimeoutTracker();
	}

	/**
	 * For Internal use only, To update servletdispatcher time.
	 * @param time - time taken between on service callback.
	 */
	public void addServletTimeTaken(long time)
	{
		client.addServletTimeTaken(time);
	}

	// Start - methods for Http2
	public void printEndAWSAccessLog()
	{
	}

	public void notifyEndRequest()
        {
                if(!client.isDataflowEnabled())
                {
                        return;
                }
                if(isReinitSet())
                {
                        client.readNextRequest();
                }
                else if(client.isCloseBooleanSet())
                {
                        client.close(AWSConstants.END_REQUEST_DATAFLOW);
                }
        }

	/**
	 * To set security filter status of the request
	 */
	public void setSecurityFilterStatus(boolean status)
	{
		client.setSecurityFilterStatus(status);
	}

	public boolean isSecurityFilterDisabled()
	{
		return client.isSecurityFilterDisabled();
	}
}
