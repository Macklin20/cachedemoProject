//$Id$
package com.zoho.wms.asyncweb.server;

// Java import
import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Arrays;
import java.util.zip.*;
import java.util.logging.Level;
import java.util.concurrent.locks.ReentrantLock;
import java.net.URLDecoder;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.net.MIIOAnalytics;
import java.net.InetAddress;

// Server common import
//import com.adventnet.wms.servercommon.ServerUtil;

// Wms import
import com.zoho.wms.asyncweb.server.runtime.SuspectedIPsTracker;
import com.zoho.wms.asyncweb.server.runtime.BandWidthTracker;
import com.zoho.wms.asyncweb.server.runtime.WmsRuntimeCounters;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.ssl.SSLStartUpTypes;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.http.HttpStream;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpResponse;
import com.zoho.wms.asyncweb.server.http.WebSocket;
import com.zoho.wms.asyncweb.server.http2.Http2Connection;
import com.zoho.wms.asyncweb.server.http2.Http2Constants;
import com.zoho.wms.asyncweb.server.http2.ConnectionManager;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.exception.IllegalReqException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.asyncprocessor.AsyncFrameProcessor;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.adventnet.wms.common.HttpDataWraper;
import com.adventnet.wms.common.exception.WMSException;

import com.zoho.logs.common.util.LogTypes;
import com.zoho.logs.logclient.v2.LogAPI;
import com.zoho.logs.logclient.v2.json.ZLMap;

// constant imports
import static com.zoho.wms.asyncweb.server.AWSConstants.READ;
import static com.zoho.wms.asyncweb.server.AWSConstants.WRITE;


public class AsyncWebClient
{

	private static AsyncLogger logger = new AsyncLogger(AsyncWebClient.class.getName());
	protected static AsyncLogger ACCESSLOGGER = new AsyncLogger(AWSConstants.ACCESS_LOGGER);//No I18n
	protected static AsyncLogger hackLogger = new AsyncLogger(AWSConstants.HACKLOG);

	public SelectionKey key;
	protected String ipaddr = "unknown";
	protected int localport = 0;
	protected int sockport = 0;
	protected int read_limit = ConfManager.getReadLimit(); 
	protected int write_limit = ConfManager.getWriteLimit();
	protected long rucount = 0;
	protected ByteBuffer byteBuffer = null;
	protected ByteBuffer streamBuffer = null;
	protected int buffLength = 0;

	protected long lastreqheartbeattime = 0;
	protected long readwaittime = ConfManager.getSoTimeout();
	protected long requestmaxidletime = ConfManager.getRequestMaxIdleTime();

	protected long readLength = 0;
	protected long writeLength = 0;
	protected boolean reqComplete = false;
	private boolean headerComplete = false;
	private boolean responseHeaderPushed = false;
	private int headerLength = 0;
	private int proxyHeaderLength = 0;
	protected boolean proxyHeaderComplete = false;
	private long bodyLength = 0;
	protected String reqType = null;
	private String rawUrl = null;
	private String reqUrl = null;
	private String version = null;
	private HashMap originalHeaderMap = new HashMap();
	private HashMap headerMap = new HashMap();
	private HashMap paramsMap = new HashMap();
	private ArrayList<HashMap<String,String>> accessCookie = null;
	private boolean securityfilter = false;

	protected long refillinvoketime = -1;
	protected long refilledtime = -1;

	private byte[] bodyContent = null;
	private int ackstate = -1;
	public HttpStream httpstream = null;
	protected boolean streammode = false;
	protected boolean dataflow = false;
	public boolean isPingResponseEnabled = !ConfManager.discardPingResponse();
	private long readBodyLength = 0;
	protected boolean closed = false;
	private boolean headerCheckNeeded = true;
	protected Object writelock = new Object();//NIO_WRITE
	protected boolean writeDataInQueueEnabled = ConfManager.isWriteDataInQueueEnabled();
	protected Object writeQueueLock = new Object();
	protected ConcurrentLinkedQueue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
	protected AtomicInteger writeQueueDataSize = new AtomicInteger(0);
	private int writecapacity = ConfManager.getWriteCapacity();
	private ByteBuffer writeBB = ByteBuffer.allocate(writecapacity);
	private int max_outbb_size = ConfManager.getMaxDataPerWrite();
	private int maxdataperwsread = ConfManager.getMaxDataPerWSRead();
	private boolean servletInvoked = false;

	protected boolean closeAfterWrite = false;
	protected AtomicLong outputDataSize = new AtomicLong(0);
	private long writtenlength = 0;
	private boolean writeInitiated = false;
	protected boolean writeerror = false;

	protected boolean end_notified = false;
	protected long streamreadlimit = -1;
	protected long requestpayloadsize = ConfManager.getReqPayloadSize();
	private	ConcurrentLinkedQueue<ByteIndex> indexList = new ConcurrentLinkedQueue();
	private boolean msgretryenabled = ConfManager.isMessageRetryEnabled();
	private boolean enablereqheartbeat = ConfManager.isRequestHeartBeatMonitorEnabled();

	protected boolean onhold = false;
	protected boolean websocket = false;
	protected boolean http2 = false;
	protected String http2ConnID = null;
	protected Inflater inflater;
	protected Deflater deflater;
	protected WebSocketReadFrame wsReadFrame;
	protected WebSocket wsServlet = null;
	protected ProxyHeader proxyHeader = new ProxyHeader();
	protected ConcurrentLinkedQueue<Object> wsPayloads = null;
	protected boolean wsClosed = false;
	protected boolean writable = true;
	protected boolean reset = false;
	protected boolean isPerMessageDeflate = false;
	protected HashMap compressionDetails = null;
	private LinkedHashMap reqtimeline = new LinkedHashMap();
	private byte[] pingMessage;
	private byte[] pongMessage;

	protected boolean chunkencoding = false;	
	protected boolean chunked = false;
	private boolean keepalive = false;
	private long keepalivetimeout = ConfManager.getKeepaliveTime();
	private long inittime;
	private AtomicBoolean reinit = new AtomicBoolean();
	final protected static char[] HEXARRAY = "0123456789ABCDEF".toCharArray();
	private ReentrantLock wsprocessorlock = new ReentrantLock();
	protected String reqid = null;

	private int responseCode = -1;
	private String responseMessage = null;

	private ArrayList<Object> clist = null;
	private ArrayList<Object> plist = null;
	private int continuationFrame = -1;
	private long reqproctime = 0l;
	private long reqqueueinserttime = 0l;
	private boolean wsservletthreadsafe = ConfManager.isWSServletThreadSafe();
	protected int zeroReadCount = 0;
	protected int maxZeroReadCount = ConfManager.getMaxZeroReadCount();
	private String statsReqUrl = AWSConstants.NA;
	private String enginehdr = null;
	private String webengineName = null;
	private String statsHost = null;
	private Hashtable sslStats = null;
	protected boolean connectionInProgress = false;
	private long clientId = 0l;
	protected long expiryTime = -1;

	protected boolean isNewConn = true;
	private long timetakenforhandshake = 0l;

	protected boolean asyncframeproc = ConfManager.isAsyncFrameProcEnabled();
	protected boolean frameprocessing = false;
	private Object wsframeprocesslock = new Object();
	private Object wsdatalock = new Object();
	private ArrayList wsdatalist = null;
	protected long firstReadTime = -1;
	protected long lastWriteTime = -1;
	private long servletTimeTaken = -1;
	private ZLMap endaccesslogszlmap = null;

	public AsyncWebClient(SelectionKey key,int port,long stime) throws IOException 
	{
		this.inittime = stime;
		this.key = key;
		this.localport=port;
		try
                {
                        this.reqid = ""+WmsSessionIdGenerator.getUniqueId();
                        AWSLogClientThreadLocal.setLoggingProperties(""+this.reqid);
                }
                catch(WMSException ex)
                {
			logger.log(Level.INFO,"Exception while setting reqid"+ex, AsyncWebClient.class.getName(),AWSLogMethodConstants.ASYNCWEBCLIENT);                
                }
		if(WebEngine.getEngineByPort(port) != null)
		{
			// higher limit for internal access wms2.0
			read_limit = WebEngine.getEngineByPort(port).getReadLimit() != -1 ? WebEngine.getEngineByPort(port).getReadLimit() : ConfManager.getReadLimit();
			write_limit = WebEngine.getEngineByPort(port).getWriteLimit() != -1 ? WebEngine.getEngineByPort(port).getWriteLimit() : ConfManager.getWriteLimit();
		}
		
		byteBuffer = ByteBuffer.allocate(read_limit);

		try
		{		
			ipaddr = ((SocketChannel)key.channel()).socket().getInetAddress().getHostAddress();
		}
		catch(Exception e)
		{
			ipaddr = "unknown";
		}

		try
		{
			sockport = ((SocketChannel)key.channel()).socket().getPort();
		}catch(Exception exp)
		{
			sockport = -1;
		}

		this.ackstate = StateConstants.REQUEST_IN_PROCESS;
		updateTimeLine(AWSConstants.TL_CREATION_TIME, stime, System.currentTimeMillis());
		updateLastAccessTime(readwaittime, false);
		if(ConfManager.isCloseReadDebugEnabled(ipaddr))
		{
			logger.log(Level.INFO, "ON CONNECT -> client ip {0}", AsyncWebClient.class.getName(),AWSLogMethodConstants.ASYNCWEBCLIENT, new Object[]{getIPAddress()});
		}
		connectionInProgress = true;
		AWSInfluxStats.addClientConnectionStats(getScheme());
	}

	public void setPingResponse(boolean status)
	{
		isPingResponseEnabled = status;
	}

	public String getReqId()
	{
		return this.reqid;
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

	public int getResponseCode()
	{
		return responseCode;
	}

	public String getResponseMessage()
	{
		return responseMessage;
	}

	public synchronized boolean acquireWSProcessorLock()
	{
		return wsprocessorlock.tryLock();
	}

	public boolean isWSProcessorLocked()
	{
		return wsprocessorlock.isHeldByCurrentThread();
	}

	public synchronized void releaseWSProcessorLock()
	{
		wsprocessorlock.unlock();
		if((isActive()) && (wsPayloads.size() > 0))
		{
			notifyProcessor(-1);
		}
	}		

	public void setReuseConnection()
	{
		synchronized(writelock)
		{
			this.reset = true;
		}
	}

	public void setReuseConnection(boolean value)
	{
		synchronized(writelock)
		{
			this.reset = value;
		}
	}

	public void setResponseHeaderPushed()
	{
		this.responseHeaderPushed = true;
	}

	public boolean isResponseHeaderPushed()
	{
		return this.responseHeaderPushed;
	}

	public void setStatusLine(int responseCode, String responseMessage)
	{
		if(responseCode != -1)
		{
			this.responseCode = responseCode;
		}
		if(responseMessage != null)
		{
			this.responseMessage = responseMessage;
		}
	}

	protected boolean isReuse()
	{
		return this.reset && ConfManager.isReuseEnabled() && !websocket;
	}
	
	public void readNextRequest()
	{
		synchronized(writelock)
		{
			AWSInfluxStats.addHttpConnectionReuseStat(AWSConstants.HTTP_1_1, getScheme());
			printEndAWSAccessLog(lastWriteTime - firstReadTime);
			resetRequestData();
			setReadOps();			
		}
	}

	public void exportTimeLine()
	{
		reqtimeline=null;
	}
	
	protected void setReadOps()
	{
		this.key.interestOps(SelectionKey.OP_READ);
	}

	protected void setReadOrCurrentOps()
	{
		key.interestOps(key.interestOps()|SelectionKey.OP_READ);
	}

	protected void setWriteOps()
	{
		if(websocket)
		{
			setReadOrWriteOps();
		}
		else
		{
			this.key.interestOps(SelectionKey.OP_WRITE);
		}
	}

	protected void setReadOrWriteOps()
	{
		this.key.interestOps(SelectionKey.OP_READ|SelectionKey.OP_WRITE);
	}

	protected void setZeroOps()
	{
		this.key.interestOps(0);
	}

	public void setAccessCookie(ArrayList<HashMap<String,String>> accessCookie)
	{
		this.accessCookie = accessCookie;
	}

	protected void resetRequestData()
	{
		synchronized(writelock)
		{
			isNewConn = false;
			long stime = System.currentTimeMillis();
		
			exportTimeLine();

			rucount=0;
			byteBuffer = ByteBuffer.allocate(read_limit);
			buffLength=0;

			readLength=0;
			writeLength=0;
			reqComplete=false;
			headerComplete=false;
			securityfilter = false;
			headerCheckNeeded=true;
			headerLength=0;
			bodyLength=0;
			reqType=null;
			rawUrl=null;
			reqUrl=null;
			version=null;
			headerMap = new HashMap();
			paramsMap = new HashMap();
			originalHeaderMap = new HashMap();

			refillinvoketime = -1;
			refilledtime = -1;

			bodyContent = null;
			ackstate = StateConstants.REQUEST_KEEPALIVE;
			httpstream = null;
			streammode = false;
			readBodyLength = 0;
			writeBB = ByteBuffer.allocate(writecapacity);

			writeQueue = new ConcurrentLinkedQueue<>();
			writeQueueDataSize.set(0);

			servletInvoked = false;
			dataflow = false;
			outputDataSize.set(0);
			writtenlength = 0;
			writeInitiated = false;
			writeerror = false;
			closeAfterWrite = false;
			reinit.set(false);
			keepalive = false;
			chunked = false;

			end_notified = false;
			streamreadlimit = -1;
			requestpayloadsize = ConfManager.getReqPayloadSize();
			indexList = new ConcurrentLinkedQueue<ByteIndex>();

			try
			{
				if(inflater!=null)
				{
					inflater.end();
				}
			}
			catch(Exception ex)
			{
			}
			
			try
			{
				if(deflater!=null)
				{
					deflater.end();
				}
			}
			catch(Exception ex)
			{
			}
			
			inflater=null;
			deflater=null;
			clist = null;
			plist = null;
			asyncframeproc = false;
			wsdatalist = null;
			wsservletthreadsafe = true;
			websocket = false;
			wsServlet = null;
			wsPayloads = null;
			wsClosed = false;
			writable = true;
			responseHeaderPushed = false;
			reset = false;
			closeAfterWrite = false;

			responseCode = -1;
			responseMessage = null;
			firstReadTime = -1;
			lastWriteTime = -1;
			servletTimeTaken = -1;

			reqtimeline = new LinkedHashMap();

			connectionInProgress = false;
			removeFromRequestHeartBeatTracker();
			updateLastAccessTime(keepalivetimeout, true);
			clientId++;

			updateTimeLine(AWSConstants.TL_RESET_TIME,stime,System.currentTimeMillis());//No I18N

			setReadOrWriteOps();
			try
			{
				this.reqid = ""+WmsSessionIdGenerator.getUniqueId();
				AWSLogClientThreadLocal.setLoggingProperties(""+this.reqid);
			}
			catch(Exception ex)
			{
				logger.addExceptionLog(Level.INFO,AWSLogConstants.EXCEPTION_WHILE_SETTING_LOG_ID, AsyncWebClient.class.getName(),AWSLogMethodConstants.RESET_REQUEST_DATA,ex);
			}
		}
	}

	protected void handleNewKeepaliveConnection()
	{
		this.ackstate = StateConstants.REQUEST_IN_PROCESS;
		updateLastAccessTime(readwaittime, true);
	}

	public void readData(SelectionKey key) throws IOException , CancelledKeyException, AWSException, IllegalReqException, Exception
	{
		long stime = System.currentTimeMillis();
		if(firstReadTime == -1)
		{
			firstReadTime = stime;
		}
		if(this.key == null)//NIO_WRITE
		{
			this.key = key;
		}
		
		try
		{
			int count = -1;
			synchronized(writelock)
			{
				if(!connectionInProgress)
				{
					handleNewKeepaliveConnection();
					connectionInProgress = true;
				}
				if(byteBuffer != null && streammode && (reqType.equals(AWSConstants.POST_REQ) || ConfManager.isSupportedPostMethod(reqType)) && isHeaderComplete(byteBuffer))
				{
					notifyHeaderComplete();
					try
					{
						readStreamData(key);
					}
					catch(IOException ex)
					{
						logger.log(Level.INFO,"Exception in Read Stream ", AsyncWebClient.class.getName(),AWSLogMethodConstants.READ_DATA,ex);
						notifyProcessor(StateConstants.ON_WRITEFAILURE);
						throw ex;
					}
					return;
				}

				if(byteBuffer.hasRemaining())
				{

					try
					{
						count = ((SocketChannel)key.channel()).read(byteBuffer);
					}
					catch(IOException ex)
					{
						logger.addExceptionLog(Level.FINE, "Exception in Read "+this+" , Details ", AsyncWebClient.class.getName(),AWSLogMethodConstants.READ_DATA, ex);//No I18N
						throw ex;
					}
				
				}
				else
				{				
					if(byteBuffer.position() < maxdataperwsread)
					{
						ByteBuffer newBB = ByteBuffer.allocate(maxdataperwsread);	
						byteBuffer.flip();
						newBB.put(byteBuffer);
						byteBuffer = newBB;
						setReadOps();
						return;
					}
					else
					{
						hackLogger.log(Level.INFO,"[HACKLOGGER - HEAVY READ]["+toString()+"]["+ipaddr+"][Limit:"+maxdataperwsread+"][Current Buffer Position:"+byteBuffer.position()+" , "+rucount+"]", AsyncWebClient.class.getName(),AWSLogMethodConstants.READ_DATA);//No I18N
						AWSInfluxStats.addHeavyDataStats(isWebSocket(), AWSConstants.HEAVY_READ);
						SuspectedIPsTracker.heavyRead(ipaddr);
						// introduced newly in wms2.0 verify
						close(AWSConstants.HEAVY_READ);
						return;
					}
				}

				

				if(readLength>0)
				{
					rucount++;
				}

				if ( count > 0) 
				{
					BandWidthTracker.updateHttpRead(count);
					zeroReadCount = 0;
					updateReadDataCounter(count);
					AWSInfluxStats.addreadlength(AWSConstants.NONSSL, false ,AWSConstants.GREATER);

					if(!isHttp2() && !isWebSocket() && isHeaderComplete(byteBuffer))
					{
						notifyHeaderComplete();
						if(streammode)
						{
							startTimeLine(AWSConstants.TL_UPLOAD_TIME,stime);
						}
						else
						{
							startTimeLine(AWSConstants.TL_READ_TIME,stime);
						}
					}

					if(reqComplete)
					{
						if(streammode)
						{
							endTimeLine(AWSConstants.TL_UPLOAD_TIME);
						}
						else
						{
							endTimeLine(AWSConstants.TL_READ_TIME);
						}
						
						if(isWritePending())
						{

							handleWrite(key);
						}

						if(isWebSocket())
						{
							byteBuffer.flip();
							byte dataRF[] = new byte[byteBuffer.remaining()];
							byteBuffer.get(dataRF, 0, byteBuffer.remaining());
							byteBuffer.clear();
							if(asyncframeproc)
							{
								addWSData(dataRF);
								notifyFrameProcessor();
								/*if(!frameprocessing)
								{
									AsyncFrameProcessor.process(this, ConfManager.isGridPort(this.localport));
								}*/
							}
							else
							{
								wsReadFrame.add(dataRF);
								if(wsReadFrame.isComplete())
								{
									prepareToDispatch();
									if(wsReadFrame.isClosed())
									{
										setReadOrWriteOps();
										return;
									}
								}
							}
						}

						AWSInfluxStats.addHttpBandWidthStats(getScheme(), isHttp2(), READ, count);
						if(isHttp2())
						{
							byteBuffer.flip();
							byte dataRF[] = new byte[byteBuffer.remaining()];
							byteBuffer.get(dataRF, 0, byteBuffer.remaining());
							byteBuffer.clear();

							getHttp2Connection().addHttp2Data(dataRF);
							updateLastAccessTime(ConfManager.getHttp2ConnectionTimeout(), true);
							setReadOrCurrentOps();
							updateRequestHeartBeat();
							return;
						}

						setReadOrCurrentOps();
						updateRequestHeartBeat();
						return;
					}

	 				readLength+=count;
					if(!isHttp2() && requestpayloadsize > 0 && readLength > requestpayloadsize)
					{
						throw new AWSException("REQUEST PAYLOAD SIZE EXCEEDED "+this);//No I18N
					}

					updateTimeLine(AWSConstants.BYTESIN , readLength);
					buffLength = buffLength + count;

					reqComplete = isRequestComplete(byteBuffer);

					if(!isHttp2() && reqComplete)
					{
						if(streammode)
						{
							endTimeLine(AWSConstants.TL_UPLOAD_TIME);
						}
						else
						{
							endTimeLine(AWSConstants.TL_READ_TIME);
						}
						
						byteBuffer = ByteBuffer.allocate(read_limit);
						buffLength = 0;

						notifyProcessor(StateConstants.ON_COMPLETION);					
					}
					else
					{					
						setReadOps();
					}

					updateRequestHeartBeat();
				}
				else if ( count < 0)
				{
					replaceAndEndTimeLine(AWSConstants.TL_READ_TIME,AWSConstants.TL_READ_FAILURE);
					notifyProcessor(StateConstants.ON_WRITEFAILURE);
					AWSInfluxStats.addreadlength(AWSConstants.NONSSL, false ,AWSConstants.LESSER);
					throw new IOException(AWSConstants.READ_MINUS_ONE);
				}
				else if(count == 0)
				{
					zeroReadCount ++;
					AWSInfluxStats.addreadlength(AWSConstants.NONSSL, false ,AWSConstants.EQUAL);
					if(!isHttp2() && maxZeroReadCount > 0 && zeroReadCount > maxZeroReadCount)
					{
						throw new AWSException("[KEY READ COUNT = 0 : MAX CROSSED]");//No I18N
					}
				}
				setReadOrCurrentOps();
			}
		}
		catch(CancelledKeyException cke)
		{
			logger.addExceptionLog(Level.FINE, AWSLogConstants.CANCELLEDKEYEXCEPTION, AsyncWebClient.class.getName(),AWSLogMethodConstants.READ_DATA, new Object[]{this, getIPAddress()});
			if(streammode)
			{
				replaceAndEndTimeLine(AWSConstants.TL_UPLOAD_TIME,AWSConstants.TL_UPLOAD_FAILURE);
			}
			else
			{
				replaceAndEndTimeLine(AWSConstants.TL_READ_TIME,AWSConstants.TL_READ_FAILURE);
			}
			
			notifyProcessor(StateConstants.ON_WRITEFAILURE);
			throw new IOException("Cancelled Key");//No I18N
		}
		catch(IOException ex)
		{
			if(streammode)
			{
				replaceAndEndTimeLine(AWSConstants.TL_UPLOAD_TIME,AWSConstants.TL_UPLOAD_FAILURE);
			}
			else
			{
				replaceAndEndTimeLine(AWSConstants.TL_READ_TIME,AWSConstants.TL_READ_FAILURE);
			}
			logger.addExceptionLog(Level.FINE, "IOException in read for client "+this+" "+getIPAddress()+" - Detail ", AsyncWebClient.class.getName(),AWSLogMethodConstants.READ_DATA, ex);//No I18N
			notifyProcessor(StateConstants.ON_WRITEFAILURE);
			throw ex;
		}
	}

	public void notifyFrameProcessor()
	{
		if(!frameprocessing && !wsdatalist.isEmpty())
		{
			AsyncFrameProcessor.process(this);
		}
	}

	protected void addWSData(byte[] data)
	{
		synchronized(wsdatalock)
		{
			wsdatalist.add(data);
		}
	}

	private byte[] removeWSData()
	{
		synchronized(wsdatalock)
		{
			if(wsdatalist.isEmpty())
			{
				frameprocessing = false;
				return null;
			}
			return (byte[])wsdatalist.remove(0);
		}
	}

	public void doFrameProcess()
	{
		synchronized(wsframeprocesslock)
		{
			frameprocessing = true;
			try
			{
				byte[] wsdata = null;
				while((wsdata = removeWSData()) != null)
				{
					wsReadFrame.add(wsdata);
					if(wsReadFrame.isComplete())
					{
						prepareToDispatch();
						if(wsReadFrame.isClosed())
						{
							setReadOrWriteOps();
							return;
						}
					}
				}
			}
			catch(IOException iex)
			{
				close(AWSConstants.IOEXCEPTION_IN_FRAMEPROCESSING);
			}
			catch(Exception ex)
			{
			}
			finally
			{
				frameprocessing = false;
			}
		}
	}

	private void sendPongFrame(Object data)
	{
		try
		{
			if(data == null)
			{
				if(data instanceof String)
				{
					data = AWSConstants.EMPTY_STRING;
				}
				else
				{
					data = new byte[0];
				}
			}

			if(data instanceof String)
			{
				wsServlet.doPong(((String) data).getBytes(AWSConstants.UTF_8));
			}
			else if(data instanceof byte[])
			{
				wsServlet.doPong((byte[]) data);
			}
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION_WHILE_SENDING_PONG_FRAME, AsyncWebClient.class.getName(),AWSLogMethodConstants.SEND_PONG_FRAME, ex);
		}
	}

	private Object processStrData() throws IOException
	{
		StringBuilder sb = new StringBuilder();
		for(Object data : clist)
		{
			sb.append((String)data);
		}
		clist = new ArrayList();
		return sb.toString();
	}

	private Object processBinaryData() throws IOException
	{
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		try
		{
			for(Object data : clist)
			{
				outputStream.write((byte[]) data);
			}
			clist = new ArrayList();
			return outputStream.toByteArray();
		}
		finally
		{
			outputStream.close();
		}
	}

	private Object processContinuationData() throws IOException
	{
		if(continuationFrame == Util.WS_OPCODE_TEXT)
		{
			return processStrData();
		}
		if(continuationFrame == Util.WS_OPCODE_BINARY)
		{
			return processBinaryData();
		}
		return null;
	}

	protected void prepareToDispatch() throws IOException, AWSException
	{
		boolean process = false;
		while(true)
		{
			logger.addDebugLog(Level.FINE, AWSLogConstants.DISPATCHING_PAYLOAD, AsyncWebClient.class.getName(),AWSLogMethodConstants.PREPARE_TO_DISPATCH, new Object[]{ipaddr});
			if(wsReadFrame.isClosed())
			{
				logger.addDebugLog(Level.INFO, AWSLogConstants.CONNECTION_CLOSED, AsyncWebClient.class.getName(),AWSLogMethodConstants.PREPARE_TO_DISPATCH);
				wsClosed = true;
				process=true;
				//insertIntoQueue();
				break;
			}
			else if(wsReadFrame.isError())
			{
				logger.addDebugLog(Level.INFO, AWSLogConstants.CONNECTION_ERROR, AsyncWebClient.class.getName(),AWSLogMethodConstants.PREPARE_TO_DISPATCH);
				wsClosed = true;
				process=true;
				//insertIntoQueue();
				break;
			}

			Object rdata = wsReadFrame.getRawPayload();
			if(wsReadFrame.isPingFrame())
			{
				logger.addDebugLog(Level.FINE, AWSLogConstants.RECEIVE_PING_FRAME, AsyncWebClient.class.getName(),AWSLogMethodConstants.PREPARE_TO_DISPATCH);
				wsServlet.updateWSPingTime();
				if(rdata == null)
				{
					// If need to store multiple ping/pong message, have a separate queue and don't disturb actual data's queue.
					pingMessage = new byte[0];
					notifyProcessor(StateConstants.ON_PING);
				}
				else
				{
					pingMessage = (byte[]) rdata;
					notifyProcessor(StateConstants.ON_PING);
				}
				if(isPingResponseEnabled)
				{
					sendPongFrame(rdata);
				}
			}
			else if(wsReadFrame.isPongFrame())
			{
				logger.addDebugLog(Level.FINE, AWSLogConstants.RECEIVED_PONG_FRAME, AsyncWebClient.class.getName(),AWSLogMethodConstants.PREPARE_TO_DISPATCH);
				if(rdata == null)
				{
					pongMessage = new byte[0];
					notifyProcessor(StateConstants.ON_PONG);
				}
				else
				{
					pongMessage = (byte[]) rdata;
					notifyProcessor(StateConstants.ON_PONG);
				}
			}
			else if(rdata!=null)
			{
				if(wsReadFrame.isContinuationFrame())
				{
					clist.add(rdata);
					if(wsReadFrame.isLastFrame())
					{
						Object cobj = processContinuationData();
						continuationFrame = -1;
						if(cobj != null)
						{
							plist.add(cobj);
							process = true;
						}
						else
						{
							logger.log(Level.SEVERE, "Continuation frames processed should not be null. Kindly check.", AsyncWebClient.class.getName(),AWSLogMethodConstants.PREPARE_TO_DISPATCH);//No I18n
						}
					}
				}
				else
				{
					plist.add(rdata);
					process=true;
				}
			}
			else
			{
				// Empty data - null
				break;
			}

			byte[] unconsumed = wsReadFrame.getUnconsumed();
			wsReadFrame.reset();
			if(unconsumed!=null)
			{
				logger.addDebugLog(Level.FINE, AWSLogConstants.READFRAME_HAS_CONSUMED_DATA_SIZE, AsyncWebClient.class.getName(),AWSLogMethodConstants.PREPARE_TO_DISPATCH, new Object[]{ipaddr,unconsumed.length});
				wsReadFrame.add(unconsumed);
				if(!wsReadFrame.isComplete())
				{
					logger.addDebugLog(Level.FINE, AWSLogConstants.READFRAME_INCOMPLETE_UNCONSUMED_DATA, AsyncWebClient.class.getName(),AWSLogMethodConstants.PREPARE_TO_DISPATCH,new Object[]{ipaddr});
					break;
				}
			}
			else
			{
				break;
			}
		}
		if(process)
		{
			if(plist.size()>0)
			{
				logger.addDebugLog(Level.FINE, AWSLogConstants.PREPARE_TO_BATCH_PROCESS,AsyncWebClient.class.getName(),AWSLogMethodConstants.PREPARE_TO_DISPATCH, new Object[]{ipaddr, plist.size()});
			}
			while(plist.size() > 0)
			{
				wsPayloads.add((Object) plist.remove(0));
			}
			notifyProcessor(-1);
		}
	}

	public void handlePing()
	{
		try
		{
			if(wsServlet != null)
			{
				wsServlet.onPingMessage(pingMessage);
			}
		}
		catch(Exception ex)
		{
		}
	}

	public void handlePong()
	{
		try
		{
			if(wsServlet != null)
			{
				wsServlet.onPongMessage(pongMessage);
			}
		}
		catch(Exception ex)
		{
		}
	}

	public boolean isWSClosed()
	{
		return wsClosed;
	}

	public Object pollWSPayload()
	{
		return wsPayloads.poll();
	}

	public void handleWSRead(byte[] data) throws IOException
	{
		wsServlet.handleRead(data);
	}

	public void handleWSRead(String data) throws IOException
	{
		wsServlet.handleRead(data);
	}

	public void handleWSWriteAck(long index) throws IOException
	{
		wsServlet.onWriteAck(index);
	}

	public void handleWSWriteFailure() throws Exception
	{
		wsServlet.onWriteFailure(wsServlet.request, wsServlet.response);
	}

	public HashMap getRequestHeaders()
	{
		if(wsServlet != null)
		{
			return wsServlet.getHeaders();
		}
		return headerMap;
	}

	public String getWSEngineName()
	{
		if(wsServlet != null)
		{
			return wsServlet.getEngineName();	
		}
		return null;
	}

	public long getServletInvokeTime()
	{
		return (System.currentTimeMillis() - refillinvoketime);
	}

	public void readStreamData(SelectionKey key) throws IOException, AWSException
	{
		ByteBuffer streamBB = ByteBuffer.allocate(read_limit);
		int count = ((SocketChannel)key.channel()).read(streamBB);

		if(readLength>0)
		{
			rucount++;
		}

		if ( count > 0) 
		{
			BandWidthTracker.updateHttpRead(count);
			updateReadDataCounter(count);
			readLength+=count;
			buffLength = buffLength + count;
			AWSInfluxStats.addreadlength(AWSConstants.NONSSL, true ,AWSConstants.GREATER);

			if(chunkencoding)
			{
				appendToStreamBuffer(streamBB);

				while(true)
				{	
					int chunkLength = getChunkLength(streamBuffer);
					if(chunkLength == -1)
					{
						setReadOps();
						copyPending(streamBuffer);
						break;
					}
					else if(chunkLength == 0)
					{
						break;
					}
					if(chunkLength > 0)
					{
						ByteBuffer wrapdata = getNextChunk(chunkLength);
						if(wrapdata!=null)
						{
							byte[] data = wrapdata.array();
							httpstream.write(data);
							notifyProcessor(StateConstants.ON_DATA);
							//updateAndNotifyPostRead(wrapdata,true);
						}					
						else
						{
							break;
						}
					}
				}
			}
			else
			{
				/*if(!updateAndNotifyPostRead(streamBB,false))
				  {
				  this.key.interestOps(SelectionKey.OP_READ);
				  }*/
				updateAndNotifyPostRead(streamBB, false);
			}

			setReadOps();
			streamBB.clear();
			updateRequestHeartBeat();
		}
		else if ( count < 0)
		{
			AWSInfluxStats.addreadlength(AWSConstants.NONSSL, true ,AWSConstants.LESSER);
			logger.addDebugLog(Level.FINE, AWSLogConstants.READING_MINUS_ONE_ON_STREAM_MODE, AsyncWebClient.class.getName(),AWSLogMethodConstants.READ_STREAM_DATA ,new Object[]{this, getIPAddress()});
			notifyProcessor(StateConstants.ON_WRITEFAILURE);
			close(AWSConstants.DATA_UNAVAILABLE_TO_READ);	
		}
		else
		{
			AWSInfluxStats.addreadlength(AWSConstants.NONSSL, true , AWSConstants.EQUAL);
		}
	}

	protected int getChunkLength(ByteBuffer bb)
	{
		bb.flip();
                boolean chunklengthpresent = false;
                int chunkcharcount = 0;   
                int remaining = streamBuffer.remaining();
                for(int i=bb.position(),counter=2; counter < remaining && !chunklengthpresent;i++,chunkcharcount++)
                {       
                        if(bb.get(i) == '\r' && bb.get(i+1) == '\n')
                        {       
                                chunklengthpresent = true;
				break;
                        }
                        counter = counter + 2;
                }
                if(chunklengthpresent)
                {       
                        byte[] lendata = new byte[chunkcharcount];
                        bb.get(lendata);
                        bb.get(new byte[2]);
                        String chunklen = new String(lendata);
                        int chunklength = Integer.parseInt(chunklen.trim(),16);
                        if(chunklength == 0)
                        {       
                                if(streammode && isLastChunk(bb))
                                {       
					httpstream.setEOCF();
					notifyProcessor(StateConstants.ON_DATA);
					endTimeLine(AWSConstants.TL_UPLOAD_TIME);
					this.ackstate = StateConstants.REQUEST_ACKNOWLEDGED;
                                }
                        }
                        return (chunklength);
                }
                return -1;
        }

        protected boolean isLastChunk(ByteBuffer bb)
        {
                try
                {
                        int i = bb.position()-2;
                        if(bb.get(i) == '\r' && bb.get(i+1) == '\n' && bb.get(i+2) == '\r' && bb.get(i+3) == '\n')
                        {
                                return true;
                        }
                }
                catch(Exception e)
                {
                }
                return false;
        
	}
	
	protected ByteBuffer getNextChunk(int chunkLength) throws IOException, AWSException
	{
		byte[] data = new byte[chunkLength];
		int remaining = streamBuffer.remaining();
		
		if(remaining >= chunkLength+2)
		{
			streamBuffer.get(data);
			byte[] newline = new byte[2];
			streamBuffer.get(newline);
			if(!Util.isNewLine(newline))
			{
				throw new AWSException("CHUNKED TRANSFER PROTOCOL ERROR - NOT A NEW LINE "+new String(newline));//NO I18N
			}
			copyPending(streamBuffer);
			return ByteBuffer.wrap(data);
		}
		else
		{
			copyPending(Integer.toHexString(chunkLength), streamBuffer);
		}

		return null;
	}

	protected void copyPending(String size, ByteBuffer bb)
        {
                int rem = bb.remaining();
                if(rem == 0){ bb.clear(); return; } 
                byte[] remdata = new byte[rem];
                bb.get(remdata);
                bb.clear();
		bb.put((size+"\r\n").getBytes());
                bb.put(remdata);
                remdata = null;
        }

	protected void copyPending(ByteBuffer bb)
        {
                int rem = bb.remaining();
                if(rem == 0){ bb.clear(); return; } 
                byte[] remdata = new byte[rem];
                bb.get(remdata);
                bb.clear();
                bb.put(remdata);
                remdata = null;
        }

	protected void appendToStreamBuffer(ByteBuffer bb) throws IOException, AWSException
	{
		bb.flip();
		byte[] b = new byte[bb.limit()];
		bb.get(b);
		appendToStreamBuffer(b);
	}

	protected void appendToStreamBuffer(byte[] data) throws IOException, AWSException
	{
		if((streamBuffer.limit() - streamBuffer.position()) > data.length)
		{
			streamBuffer.put(data);
		}
		else if(streamBuffer.position()+data.length < (maxdataperwsread+read_limit+Util.CHUNK_MAX_LENGTH_OFFSET))
		{
			if(streamBuffer.position() == 0)
			{
				streamBuffer = ByteBuffer.allocate(data.length);
				streamBuffer.put(data);
			}
			else
			{
				streamBuffer.flip();
				byte[] remdata = new byte[streamBuffer.limit()];
				streamBuffer.get(remdata);
				streamBuffer = ByteBuffer.allocate(remdata.length+data.length);
				streamBuffer.put(remdata);
				streamBuffer.put(data);
			}
		}
		else
                {
                        throw new AWSException("Chunk stream greater than the max set size : "+(streamBuffer.position()+data.length)+" vs "+maxdataperwsread);//No I18N
                }
	}	

	public void setOutputDataSize(long size)
	{
		this.outputDataSize.addAndGet(size);
	}

	public void setInputDataSize(long size) throws IOException
	{
		synchronized(writelock)
		{
			this.streamreadlimit = size;
			logger.addDebugLog(Level.FINE, AWSLogConstants.INPUT_DATA_SIZE_SET_FOR_CLIENT,AsyncWebClient.class.getName(),AWSLogMethodConstants.SET_INPUT_DATA_SIZE, new Object[]{this, size});
			httpstream.resume();
			if(httpstream.isAvailable())
			{
				notifyProcessor(StateConstants.ON_DATA);
			}
			setReadOps();
		}
	}

	public void setReqPayloadSize(long size)
	{
		this.requestpayloadsize = size;
	}
	
	public void setWriteLimit(int writelimit)
	{
		write_limit = writelimit;
	}

	public int getWriteLimit()
	{
		return write_limit;
	}
	
	public void reinit()
	{
		reinit.set(true);
	}

	public boolean isReinitSet()
	{
		return reinit.get();
	}

	public void invokeWriteIfNeeded()
	{
		synchronized(writelock)
		{
			try
			{
				if(!this.key.isValid())
				{
					close(AWSConstants.KEY_INVALID);
					return;
				}
				int writestatus = getWriteStatus();
		
				if(writestatus == AWSConstants.WRITE_FAILURE)
				{
					close(AWSConstants.WRITESTATUS_FAILURE);
				}
				if(writestatus  == AWSConstants.WRITE_COMPLETED)
				{
					if(writeerror)
					{
						close(AWSConstants.WRITEERROR);
					}
					else
					{
						setReadOrWriteOps();
					}
				}
				else if(writestatus == AWSConstants.WRITE_IN_PROGRESS)
				{
					setWriteOps();
				}
				else
				{
					setReadOps();
					logger.addDebugLog(Level.FINE, AWSLogConstants.ABNORMAL_SCENARIO,AsyncWebClient.class.getName(),AWSLogMethodConstants.INVOKE_WRITE_IF_NEEDED,new Object[]{this});
				}

			}
			catch(CancelledKeyException cke)
			{
				logger.addExceptionLog(Level.FINE, AWSLogConstants.CANCELLEDKEYEXCEPTION_FOR_CLIENT_IN_SETCLOSEAFTERWRITE,AsyncWebClient.class.getName(),AWSLogMethodConstants.INVOKE_WRITE_IF_NEEDED, new Object[]{this});
				close(AWSConstants.CANCELLED_KEY_EXCEPTION_WRITE);
			}
			catch(Exception ex)
			{
				logger.addExceptionLog(Level.FINE,"Exception for client in setCloseAfterWrite "+this+" , ",AsyncWebClient.class.getName(),AWSLogMethodConstants.INVOKE_WRITE_IF_NEEDED,ex);//No I18N
			}

		}
	}

	public void setCloseAfterWrite()//NIO_WRITE
	{
		synchronized(writelock)
		{
			this.closeAfterWrite = true;
			invokeWriteIfNeeded();
		}
	}

	public void setWebSocket(WebSocket wsservlet, boolean isCompressionEnabled, HashMap compressionDetails)
	{
		this.websocket = true;
		asyncframeproc = ConfManager.isAsyncFrameProcEnabled() && WebEngine.getEngineByAppName(this.webengineName).isAsyncFrameProcEnabled();
		clist = new ArrayList<>();
		plist = new ArrayList<>();
		wsdatalist = new ArrayList<>();
		wsservletthreadsafe = ConfManager.isWSServletThreadSafe() && WebEngine.getEngineByAppName(this.webengineName).isWSServletThreadSafe();

		if(isCompressionEnabled)
		{
			// as of now we are supporting :  permessage-deflate
			this.isPerMessageDeflate = isCompressionEnabled;
			this.compressionDetails = compressionDetails; // for future tuning

			inflater = new Inflater(true);
			deflater = new Deflater(Deflater.HUFFMAN_ONLY,true);
		}

		if(WebEngine.getEngineByPort(this.localport) != null)
		{
			// increasing write limit for websocket communication in wms.
			write_limit = WebEngine.getEngineByPort(this.localport).getWriteLimit() != -1 ? WebEngine.getEngineByPort(this.localport).getWriteLimit() : ConfManager.getWriteLimit();
		}

		try
		{
			holdRead();
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.FINE, "Unable to put client on hold "+this+" , Message ",AsyncWebClient.class.getName(),AWSLogMethodConstants.SET_WEBSOCKET, ex);//No I18N
		}
		this.wsServlet = wsservlet;
		wsReadFrame = new WebSocketReadFrame();
		wsPayloads = new ConcurrentLinkedQueue();
	}

	public boolean isPerMessageDeflate()
	{
		return isPerMessageDeflate;
	}

	public void holdRead() throws IOException
	{
		synchronized(writelock)
		{
			this.onhold = true;
			if((key.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)
			{
				setWriteOps();
			}
			else
			{
				setZeroOps();
			}
		}
	}

	public void enable() throws IOException
	{
		synchronized(writelock)
		{
			this.onhold = false;
			setReadOrCurrentOps();
		}
	}

	public boolean isOnHold()
	{
		return this.onhold;
	}

	public boolean isWebSocket()
	{
		return this.websocket;
	}

	public boolean isCloseBooleanSet()
	{
		return this.closeAfterWrite;
	}

	public boolean isWritePending() throws IOException, AWSException
	{
		if(!this.key.isValid())
		{
			throw new IOException(AWSConstants.CLOSED);
		}

		int writestatus = getWriteStatus();
		if(writestatus == AWSConstants.WRITE_COMPLETED)
		{
			if(writeerror)
			{
				throw new IOException(AWSConstants.CLOSED);
			}
			else
			{
				if(closeAfterWrite)
				{
					throw new IOException(AWSConstants.CLOSED);
				}
				else
				{
					return false;
				}
			}
		}
		else if(writestatus == AWSConstants.WRITE_FAILURE)
		{
			throw new IOException(AWSConstants.CLOSED);
		}
		else if(writestatus == AWSConstants.WRITE_IDLE)
		{
			if(closeAfterWrite)
			{
				throw new IOException(AWSConstants.CLOSED);
			}
			else
			{
				return false;
			}
		}
		return true;
	}


	protected int getWriteStatus()
	{
		if(writeBB.position() == 0 && writtenlength == outputDataSize.get() && writeInitiated && writeQueueDataSize.intValue() <= 0)
		{
			if(chunked)
			{
				if(isReinitSet())
				{
					return AWSConstants.WRITE_COMPLETED;
				}
				else	
				{
					return AWSConstants.WRITE_IN_PROGRESS_CHUNKED;
				}
			}
			else
			{
				return AWSConstants.WRITE_COMPLETED;
			}
		}
		if(writtenlength > outputDataSize.get())
		{
			return AWSConstants.WRITE_FAILURE;
		}
		if(writeBB.position() > 0 || writeQueueDataSize.intValue() > 0)
		{
			return AWSConstants.WRITE_IN_PROGRESS;
		}
		return AWSConstants.WRITE_IDLE;
	}

	public boolean isWriteComplete()
	{
		int writeStatus = getWriteStatus();
		if(writeStatus==AWSConstants.WRITE_COMPLETED)
		{
			return true;
		}
		return false;
	}

	public boolean isWriteInitiated()
	{
		return writeInitiated;
	}

	protected boolean isValidRequest()
	{
		if(reqUrl == null || headerMap == null || headerMap.size() == 0)
		{
			return false;
		}
		return true;
	}

	public void writeData(ByteBuffer src) throws IOException, AWSException
	{
		writeData(src,-1,false);
	}

	public void writeData(ByteBuffer src,long index) throws IOException, AWSException
	{
		writeData(src,index,false);
	}

	public void writeData(ByteBuffer src,long index, boolean error) throws IOException, AWSException
	{
		synchronized(writeDataInQueueEnabled ? writeQueueLock : writelock)//NIO_WRITE
		{
			if(!isWebSocket())
			{
				if(streammode)
				{
					startTimeLine(AWSConstants.TL_DOWNLOAD_TIME);
				}
				else
				{
					startTimeLine(AWSConstants.TL_WRITE_TIME);
				}
			}

			if(closed || !writable || !key.isValid())
			{
				writable = false;
				close(AWSConstants.KEY_INVALID);
				AWSLogClientThreadLocal.setLoggingProperties(this.reqid);
				throw new AWSException(AWSConstants.INVALID_KEY_SPACE+getIPAddress()+" "+this);//No I18N
			}

			try
			{
				refilledtime = System.currentTimeMillis();
				if(refillinvoketime > 0 && ((refilledtime - refillinvoketime) > 100))
				{
					logger.addDebugLog(Level.FINE, AWSLogConstants.DELAY_IN_REFILLING, AsyncWebClient.class.getName(),AWSLogMethodConstants.WRITE_DATA,new Object[]{this, refilledtime - refillinvoketime});
				}

				if(ConfManager.isWriteHBEnabled())
				{
					updateRequestHeartBeat();
				}

				int dataSize = src.limit();
				writeLength += dataSize;
				writeerror = error;
				if(writeerror)
				{
					logger.addDebugLog(Level.FINE, AWSLogConstants.SEND_ERROR_INVOKED_FOR_CLIENT, AsyncWebClient.class.getName(),AWSLogMethodConstants.WRITE_DATA, new Object[]{this, getIPAddress()});
				}

				updateTimeLine(AWSConstants.BYTESOUT , writeLength);
				if(!writeInitiated)
				{
					writeInitiated = true;
					outputDataSize.addAndGet(dataSize);
				}

				if(writeDataInQueueEnabled)
				{
					addDataInQueue(src);
				}
				else
				{
					if((writeBB.position() + dataSize) > getMaxDataPerWrite())
					{
						throw new IOException("Exceeded Max Data Per Write - writeBB position:"+writeBB.position()+" -  dataSize:"+dataSize+" - limit:"+getMaxDataPerWrite());
					}
					writeBB = appendDataToWriteBB(src, writeBB);
				}
				addIndex(dataSize, index);
				setWriteOps();
			}
			catch(IOException iex)
			{
				logger.addExceptionLog(Level.FINE, "IOException in writeData for client "+this+" "+getIPAddress()+" Message ",  AsyncWebClient.class.getName(),AWSLogMethodConstants.WRITE_DATA,iex);//No I18N
				if(streammode)
				{
					replaceAndEndTimeLine(AWSConstants.TL_DOWNLOAD_TIME,AWSConstants.TL_DOWNLOAD_FAILURE);
				}
				else
				{
					replaceAndEndTimeLine(AWSConstants.TL_WRITE_TIME,AWSConstants.TL_WRITE_FAILURE);
				}
				close(AWSConstants.IOEXCEPTION_WRITE);
				throw iex;
			}
		}

		if(!websocket && write_limit > 0 && writeLength> write_limit)
		{
			hackLogger.log(Level.INFO,"[HACKLOGGER - HEAVY WRITE]["+toString()+"]["+ipaddr+"][Limit:"+write_limit+"][writeLength:"+writeLength+"] - \r\n--------\r\n"+src.asCharBuffer()+"\r\n--------\r\n", AsyncWebClient.class.getName(),AWSLogMethodConstants.WRITE_DATA);// No I18N
			AWSInfluxStats.addHeavyDataStats(isWebSocket(), AWSConstants.HEAVY_WRITE);

			if(streammode)
			{
				replaceAndEndTimeLine(AWSConstants.TL_DOWNLOAD_TIME,AWSConstants.TL_DOWNLOAD_FAILURE);
			}
			else
			{
				replaceAndEndTimeLine(AWSConstants.TL_WRITE_TIME,AWSConstants.TL_WRITE_FAILURE);
			}
			SuspectedIPsTracker.heavyWrite(ipaddr);
			close(AWSConstants.HEAVY_WRITE);
		}
	}

	protected void addDataInQueue(ByteBuffer src)throws IOException
	{
		if(src.limit() > getMaxDataPerWrite())
		{
			throw new IOException("Exceeded Max Data Per Write -  size:"+src.limit()+" - limit:"+getMaxDataPerWrite());
		}
		writeQueueDataSize.addAndGet(src.limit());
                ByteBuffer bb = ByteBuffer.allocate(src.limit());
                bb.put(src);
                bb.flip();
		writeQueue.add(bb);
	}

	public ByteBuffer appendDataToWriteBB(ByteBuffer src, ByteBuffer writeBB)
	{
		if((writeBB.capacity() - writeBB.position()) >= src.limit())
		{
			writeBB.put(src);
			return writeBB;
		}
		else
		{
			ByteBuffer bb = ByteBuffer.allocate(writeBB.position() + src.limit());
			writeBB.flip();
			bb.put(writeBB);
			bb.put(src);
			return bb;
		}
	}

	public void handleWrite(SelectionKey key) throws IOException , CancelledKeyException, AWSException, Exception//NIO_WRITE
	{
		if(this.key == null)
		{
			this.key = key;
		}
		synchronized(writelock)
		{
			try
			{
				if(writeDataInQueueEnabled)
				{
					while(!writeQueue.isEmpty())
					{
						// peek the first BB and check if size of (current writeBB size + new BB size) exceeds max data per write, if exceeds break the loop and perform socket write, else poll from queue and append to writeBB
						ByteBuffer src = writeQueue.peek();
						if((writeBB.position() + src.limit()) > getMaxDataPerWrite())
						{
							break;
						}

						src = writeQueue.poll();
						writeQueueDataSize.addAndGet(-1 * src.limit());
						writeBB = appendDataToWriteBB(src, writeBB);
					}
				}

				writeBB.flip();
				SocketChannel sk = (SocketChannel)key.channel();
				boolean proceed = true;
				while(proceed)
				{
					int written = sk.write(writeBB);
					AWSInfluxStats.addHttpBandWidthStats(getScheme(), isHttp2(), WRITE, written);
					lastWriteTime = System.currentTimeMillis();
					BandWidthTracker.updateHttpWrite(written);
					updateWriteDataCounter(written);

					removeIndex(written);
					writtenlength += written;
					if(written <= 0)
					{
						proceed = false;
						AWSInfluxStats.addWrittenlength(AWSConstants.NONSSL,AWSConstants.LESSER_OR_EQUAL);
					}
					else
					{
						try
						{
							AWSInfluxStats.addWrittenlength(AWSConstants.NONSSL,AWSConstants.GREATER);
							Thread.sleep(1);//TODO: NEED REWORK
						}
						catch(Exception ex)
						{
							logger.addExceptionLog(Level.FINE,this+" Exception in sleep during write ", AsyncWebClient.class.getName(),AWSLogMethodConstants.HANDLE_WRITE,ex);//No I18N
						}
					}
				}
				writeBB.compact();
				if(writeerror && writeBB.position() <= 0 && writeQueueDataSize.intValue() <= 0)
				{
					if(streammode)
					{
						endTimeLine(AWSConstants.TL_DOWNLOAD_TIME);
					}
					else
					{
						endTimeLine(AWSConstants.TL_WRITE_TIME);
					}

					notifyProcessor(StateConstants.ON_WRITEFAILURE);
					close(AWSConstants.WRITEFAILURE);
				}
				else
				{
					int writestatus = getWriteStatus();

					if(writestatus == AWSConstants.WRITE_COMPLETED)
					{
						if(streammode)
						{
							endTimeLine(AWSConstants.TL_DOWNLOAD_TIME);
						}
						else
						{
							endTimeLine(AWSConstants.TL_WRITE_TIME);
						}

						if(http2)
						{
							setReadOps();
						}
						else if(isReinitSet())
						{
							if(dataflow)
                                                        {
                                                                notifyProcessor(StateConstants.ON_WRITECOMPLETE);
                                                        }								
							else
							{
								readNextRequest();
							}	
						}
						else if(closeAfterWrite)
						{
							close(AWSConstants.RESPONSE_WRITTEN);
						}
						else
						{
							notifyProcessor(StateConstants.ON_WRITECOMPLETE);

							if(isReuse())
							{
								setZeroOps();
							}
							else if(!onhold)
							{
								setReadOps();
							}
						}
					}
					else if(writestatus == AWSConstants.WRITE_FAILURE)
					{
						if(streammode)
						{
							replaceAndEndTimeLine(AWSConstants.TL_DOWNLOAD_TIME,AWSConstants.TL_DOWNLOAD_FAILURE);
						}
						else
						{
							replaceAndEndTimeLine(AWSConstants.TL_WRITE_TIME,AWSConstants.TL_WRITE_FAILURE);
						}  
								
						notifyProcessor(StateConstants.ON_WRITEFAILURE);
						close(AWSConstants.WRITEFAILURE);
					}
					else if(writestatus == AWSConstants.WRITE_IN_PROGRESS)
					{
						setWriteOps();
					}
					else
					{
						if(writestatus != AWSConstants.WRITE_IN_PROGRESS_CHUNKED) //for chunked case, to write final chunk, we need to ignore this status, while calling write itself, WRITE interest ops will be set and it'll be written
						{
							notifyProcessor(StateConstants.ON_OUTPUTBUFFERREFILL);
							refillinvoketime = System.currentTimeMillis();
							if(!onhold)
							{
								setReadOps();
							}
						}
					}
				}
			}
			catch(CancelledKeyException cex)
			{
				logger.addExceptionLog(Level.FINE, AWSLogConstants.CANCELLEDKEYEXCEPTION_IN_HANDLEWRITE, AsyncWebClient.class.getName(),AWSLogMethodConstants.HANDLE_WRITE, new Object[]{this, getIPAddress(), cex.getMessage()});
				if(writtenlength < outputDataSize.get())
				{
					if(streammode)
					{
						replaceAndEndTimeLine(AWSConstants.TL_DOWNLOAD_TIME,AWSConstants.TL_DOWNLOAD_FAILURE);
					}
					else
					{
						replaceAndEndTimeLine(AWSConstants.TL_WRITE_TIME,AWSConstants.TL_WRITE_FAILURE);
					}

					notifyProcessor(StateConstants.ON_WRITEFAILURE);
				}
				throw cex;
			}
			catch(IOException ex)
			{
				logger.addExceptionLog(Level.FINE, "IOException in handleWrite for client "+this+" "+getIPAddress()+" Message ",  AsyncWebClient.class.getName(),AWSLogMethodConstants.HANDLE_WRITE,ex);//No I18N
				if(writtenlength < outputDataSize.get())
				{
					if(streammode)
					{
						replaceAndEndTimeLine(AWSConstants.TL_DOWNLOAD_TIME,AWSConstants.TL_DOWNLOAD_FAILURE);
					}
					else
					{
						replaceAndEndTimeLine(AWSConstants.TL_WRITE_TIME,AWSConstants.TL_WRITE_FAILURE);
					}
					
					notifyProcessor(StateConstants.ON_WRITEFAILURE);
				}
				throw ex;
			}
		}
	}


	public void setRequestState(int state)
	{
		this.ackstate = state;
	}

	public boolean isActive()
	{
		return !closed;
	}

	public int getRequestState()
	{
		return this.ackstate;
	}

	public void setDataFlowNotification(boolean status)
	{
		dataflow = status;
	}

	public void notifyWSWriteFailure()
	{
		notifyProcessor(StateConstants.ON_WRITEFAILURE);
	}
	
	protected synchronized void notifyWSWriteAck(long index)
	{
		try
		{
			AsyncRequestProcessor.processWSWriteAck(this, index);
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,  AsyncWebClient.class.getName(),AWSLogMethodConstants.NOTIFY_WS_WRITE_ACK,ex);
		}
	}

	protected synchronized void notifyProcessor(int state) //throws IOException
	{
		try
		{
			if(http2)
			{
				logger.log(Level.INFO, "[Http2 AsyncWebClient-notifyProcessor-h2ID:"+http2ConnID+"-streamID:NA] - "+state+" http/1 notifyProcessor called");
				return;
			}
			//!headerComplete added --> [reason] while dataflow is true and the client's keepalive expired, the server reads -1 and notify 'write failure' for an invalid request
			if(streammode)
			{
				if(end_notified || !headerComplete)
				{
					return;
				}
				if(state==StateConstants.ON_HEADER_COMPLETION)
                                {
					if(httpstream.isPaused())
                                        {
                                                return;
                                        }
                                        httpstream.pause();
					if(!servletInvoked)
                                        {
                                                synchronized(writelock)
                                                {
                                                        setZeroOps();
                                                }
                                                servletInvoked = true;
                                        }
					httpstream.setHeaderThreadStartTime();
				}
				else if(state==StateConstants.ON_DATA)
                                {
                                        if(httpstream.isPaused())
                                        {
                                                return;
                                        }
                                        httpstream.pause();
					if(!servletInvoked)
                                        {
                                                synchronized(writelock)
                                                {
                                                        setZeroOps();
                                                }
                                                servletInvoked = true;
                                        }
                                        httpstream.setDataThreadStartTime();
				}
				if(state==StateConstants.ON_WRITECOMPLETE || state==StateConstants.ON_WRITEFAILURE || state==StateConstants.ON_OUTPUTBUFFERREFILL)
                                {
					if(state==StateConstants.ON_WRITECOMPLETE || state==StateConstants.ON_WRITEFAILURE)
					{
						end_notified = true;
					}
					if(!dataflow)
					{
						return;
					}
                                }
                        	
			}
			else if(websocket)
			{
				if(!headerComplete || state==StateConstants.ON_DATA)
				{
					return;
				}
				if(state == -1 && wsprocessorlock.isLocked())
                        	{
                                	return;
                        	}
				if(state==StateConstants.ON_WRITECOMPLETE || state==StateConstants.ON_WRITEFAILURE || state==StateConstants.ON_OUTPUTBUFFERREFILL)
                                {
                                        if(state==StateConstants.ON_WRITECOMPLETE || state==StateConstants.ON_WRITEFAILURE)
                                        {
                                                end_notified = true;
                                        }
                                        if(!dataflow)
                                        {
                                                return;
                                        }
                                }

			}
			else 
			{
				if(end_notified || !headerComplete || state==StateConstants.ON_DATA)
				{
					return;
				}
				if(state==StateConstants.ON_WRITECOMPLETE || state==StateConstants.ON_WRITEFAILURE || state==StateConstants.ON_OUTPUTBUFFERREFILL)
                                {
                                        if(state==StateConstants.ON_WRITECOMPLETE || state==StateConstants.ON_WRITEFAILURE)
                                        {
                                                end_notified = true;
                                        }
                                        if(!dataflow)
                                        {
                                                return;
                                        }
                                }

			}

			AsyncRequestProcessor.process(this,state);
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception ", AsyncWebClient.class.getName(),AWSLogMethodConstants.NOTIFYPROCESSOR, ex);
		}
	}

	public SelectionKey getSelectionKey()
	{
		return key;
	}

	public String getIPAddress()
	{
		return this.ipaddr+":"+this.sockport;
	}

	public String getRawIPAddress()
	{
		return ipaddr;
	}

	public int getSocketPort()
	{
		return sockport;
	}

	public String getScheme()
	{
		if(ConfManager.isSSLPort(localport))
		{
			if(websocket)
			{
				return AWSConstants.WSS;
			}
			return AWSConstants.HTTPS;
		}
		
		if(websocket)
		{
			return AWSConstants.WS;
		}
		return AWSConstants.HTTP;
	}

	public boolean isSSL()
	{
		return ConfManager.isSSLPort(localport);
	}

	public int getLocalPort()
	{
		return this.localport;
	}

	protected void updateReadDataCounter(long count)
	{
		if(ConfManager.isDataStatsEnabled())
		{
			DataCountUpdater.COUNTER.updateRead(count);
		}
		if(ConfManager.isWMSServer())
		{
			try
                	{
                        	MIIOAnalytics.addRX(InetAddress.getByName(ConfManager.getIPAddress()),localport,count);
                	}
                	catch(Exception excp)
                	{
                	}
		}
	}

	protected void updateWriteDataCounter(long count)
	{
		if(ConfManager.isDataStatsEnabled())
		{
			DataCountUpdater.COUNTER.updateWrite(count);
		}
		if(ConfManager.isWMSServer())
                {
			try
                	{
                        	MIIOAnalytics.addTX(InetAddress.getByName(ipaddr),localport,count);
                	}
                	catch(Exception excp)
                	{
                	}
                }
	}

	protected void updateLastAccessTime(long waitTime, boolean update)
	{
		if(update)
		{
			long preExpiryTime = expiryTime;
			expiryTime = System.currentTimeMillis() + waitTime;
			RequestTimeOutListener.TRACKER.update(preExpiryTime, expiryTime, this);
		}
		else
		{
			expiryTime = System.currentTimeMillis() + waitTime;
			RequestTimeOutListener.TRACKER.touch(expiryTime,this);
		}
	}

	protected void updateRequestHeartBeat()
	{
		if(!enablereqheartbeat)
		{
			return;
		}

		boolean update = (lastreqheartbeattime > 0);

		if(update)
		{
			if(System.currentTimeMillis()-lastreqheartbeattime < ConfManager.getIgnoreHBPeriod())
			{
				return;
			}

			long prevvalue = lastreqheartbeattime+requestmaxidletime;
			this.lastreqheartbeattime = System.currentTimeMillis();
			RequestHeartBeatMonitor.TRACKER.update(prevvalue,lastreqheartbeattime+requestmaxidletime,this);
		}
		else
		{
			this.lastreqheartbeattime = System.currentTimeMillis();
			RequestHeartBeatMonitor.TRACKER.touch(lastreqheartbeattime+requestmaxidletime,this);
		}
	}

	protected long getExpireTime()
	{
		return this.expiryTime;
	}

	protected boolean isInvalidSoTimeoutEntry(long time)
	{
		return (expiryTime != time ); 
	}

	protected boolean isInvalidHeartBeatTimeoutEntry(long time)
	{
		return ((lastreqheartbeattime + requestmaxidletime) != time ); 
	}

	protected long getMaxAllowedHeartBeatTime()
	{
		return (this.lastreqheartbeattime + requestmaxidletime);
	}

	public void removeFromTimeoutTracker()
	{
		RequestTimeOutListener.TRACKER.remove(this.expiryTime,this);
	}

	public void enableHearBeatTracker()
	{
		enablereqheartbeat = true;
		updateRequestHeartBeat();
	}

	public void disableAndRemoveFromRequestHeartBeatTracker()
	{
		removeFromRequestHeartBeatTracker();
		enablereqheartbeat = false;
	}

	protected void removeFromRequestHeartBeatTracker()
	{
		if(!enablereqheartbeat)
		{
			return;
		}
		RequestHeartBeatMonitor.TRACKER.remove(this.lastreqheartbeattime+requestmaxidletime,this);
	}

	private String getAccessLog()
	{
		AWSLogClientThreadLocal.setLoggingProperties(reqid);
		HashMap loginfo = new HashMap();
		try
		{
			if(reqUrl != null)
			{
				loginfo.put(AWSConstants.REQUEST_URI, reqUrl);
			}
			if(paramsMap != null)
			{
				loginfo.put(AWSConstants.PARAM, Util.getFormattedList(Util.getAccessParams(paramsMap)));
			}
			if(headerMap != null)
			{
				loginfo.put(AWSConstants.HEADER, Util.getFormattedList(Util.getAccessHeaders(headerMap)));
			}
			if(reqType != null)
			{
				loginfo.put(AWSConstants.METHOD, reqType);
			}
			loginfo.put(AWSConstants.REMOTE_IP, ipaddr);
			loginfo.put(AWSConstants.SERVER_PORT, sockport);
			if(getHeader(AWSConstants.USER_AGENT) != null)
			{
				loginfo.put(AWSConstants.USER_AGENT_1, getHeader(AWSConstants.USER_AGENT));
			}
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION_IN_FORMATTING_ACCESSLOGGER,  AsyncWebClient.class.getName(),AWSLogMethodConstants.GET_ACCESS_LOG,e);
		}
		return HttpDataWraper.getString(loginfo);
	}


	private void printDefaultAccessLog()
	{
		try
		{
			ZLMap zlmap = new ZLMap();
			if(reqid != null)
			{
				zlmap.put(AWSConstants.CUSTOM_FIELD_REQUEST_ID, reqid);
			}
			if(reqUrl != null)
			{
				zlmap.put(AWSConstants.REQUEST_URI, reqUrl);
			}
			if(paramsMap != null)
			{
				zlmap.put(AWSConstants.PARAM, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessParams(paramsMap))));
			}
			if(headerMap != null)
			{
				zlmap.put(AWSConstants.HEADER, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessHeaders(headerMap))));
			}
			if(reqType != null)
			{
				zlmap.put(AWSConstants.METHOD, reqType);
			}
			zlmap.put(AWSConstants.REMOTE_IP, ipaddr);
			zlmap.put(AWSConstants.SERVER_PORT, sockport);
			if(getHeader(AWSConstants.USER_AGENT) != null)
			{
				zlmap.put(AWSConstants.USER_AGENT_1, HttpDataWraper.getString(getHeader(AWSConstants.USER_AGENT)));
			}
			LogAPI.log(LogTypes.AWS_ACCESS_LOG_STRING, zlmap);
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION_IN_FORMATTING_ACCESSLOGGER, AsyncWebClient.class.getName(),AWSLogMethodConstants.PRINT_DEFAULT_ACCESS_LOG,e);
		}
	}

	protected void printEndAWSAccessLog(long requestTimeTaken)
	{
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
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION_IN_FORMATTING_ACCESSLOGGER, AsyncWebClient.class.getName(),AWSLogMethodConstants.PRINT_END_AWS_ACCESS_LOG, e);
		}
	}

	protected boolean isRequestComplete(ByteBuffer bb) throws IOException, AWSException, IllegalReqException
	{
		try
		{
			AWSInfluxStats.addHttpBandWidthStats(getScheme(), isHttp2(), READ, bb.position());
			if(ConfManager.isHttp2Enabled() && isHttp2Req(bb))
			{
				setAsHttp2Client();

				byte dataRF[] = new byte[bb.remaining()];
				bb.get(dataRF, 0, bb.remaining());
				bb.clear();
				getHttp2Connection().addHttp2Data(dataRF);

				headerComplete = true;
				reqComplete = true;
				return true;
			}
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "Http2Error - Unable to initialize Http2Connection", ex);
		}

		if(!isHeaderComplete(bb) || bb.position() == 0)
		{
			if(reqComplete && getWriteStatus() == AWSConstants.WRITE_IN_PROGRESS)
			{
				synchronized(writelock)
				{
					setWriteOps();
				}
			}
			return false;
		}
		HashMap headerClone = (HashMap)headerMap.clone();
		headerClone.remove(AWSConstants.HDR_COOKIE);//No I18N
		headerClone.remove(AWSConstants.COOKIE);//No I18N

		if(reqType.equals(AWSConstants.GET_REQ) || reqType.equals(AWSConstants.OPTIONS_REQ) || reqType.equals(AWSConstants.DELETE_REQ) || reqType.equals(AWSConstants.HEAD_REQ) || isSupportedGetMethod(reqType))
		{
			processGetRequest(rawUrl);
			logger.addDebugLog(Level.FINE, AWSLogConstants.NEW_REQUEST, AsyncWebClient.class.getName(),AWSLogMethodConstants.IS_REQUEST_COMPLETE, new Object[]{this, this.key, sockport, reqType, rawUrl, headerClone, paramsMap, getRequestURL()});
			printAccessLog();

			reqComplete = true;
			return true;
		}
		else if(reqType.equals(AWSConstants.POST_REQ) || ConfManager.isSupportedPostMethod(reqType))
		{
			if(!streammode)
			{
				if(getHeader(AWSConstants.HDR_CONTENT_LENGTH)!=null)
				{
					bodyLength = Long.parseLong(getHeader(AWSConstants.HDR_CONTENT_LENGTH));
					if(bodyLength < 0)
					{
						sendError(HttpResponseCode.LENGTH_REQUIRED,HttpResponseCode.LENGTH_REQUIRED_MSG);
						throw new AWSException("Content Length set to negative");//No I18N
					}
				}
				else
				{
					sendError(HttpResponseCode.LENGTH_REQUIRED,HttpResponseCode.LENGTH_REQUIRED_MSG);
					throw new AWSException("Content Length Header Not Present");//No I18N
				}
			}

			if(chunkencoding)
			{
				printAccessLog();
				if(streammode)
				{
					byte[] bodyData = new byte[bb.position() - headerLength];
					for(int i= headerLength,j=0; i < bb.position() && j < (maxdataperwsread-headerLength); i++)
					{
						bodyData[j++] = bb.get(i);
					}	
		
					appendToStreamBuffer(bodyData);
					
					while(true)
					{	
						int chunkLength = getChunkLength(streamBuffer);
						if(chunkLength == -1)
						{
							setReadOps();
							copyPending(streamBuffer);
							break;
						}
						else if(chunkLength == 0)
						{
							break;
						}
						if(chunkLength > 0)
						{
							ByteBuffer wrapdata = getNextChunk(chunkLength);
							if(wrapdata!=null)
							{
								byte[] data = wrapdata.array();
								httpstream.write(data);
								notifyProcessor(StateConstants.ON_DATA);
								//updateAndNotifyPostRead(wrapdata,true);
							}
							else
							{
								break;
							}
						}
					}
				
					//fallback for non-header completion case.
					notifyProcessor(StateConstants.ON_COMPLETION);					
				}
				else
				{
					sendError(HttpResponseCode.BAD_REQUEST, HttpResponseCode.BAD_REQUEST_MSG);
					throw new AWSException("Chunk mode supported only for stream requests");	//No I18N
				}
			}
			else
			{
				if(bodyLength <= (bb.position() - headerLength))
				{
					int size = (int)bodyLength;
					byte[] bodyData = new byte[size];
					for(int i= headerLength,j=0; i < bb.position() && j < bodyLength; i++)
					{
						bodyData[j++] = bb.get(i);
					}
					bodyContent = bodyData;
					processPostRequest(rawUrl,bodyData);
					logger.addDebugLog(Level.FINE, AWSLogConstants.AWS_DEBUG_STRING, AsyncWebClient.class.getName(),AWSLogMethodConstants.IS_REQUEST_COMPLETE,  new Object[]{this, this.key, sockport, paramsMap, headerClone, getRequestURL()});
					printAccessLog();
					if(streammode)
					{
						ByteBuffer wrapdata = ByteBuffer.wrap(bodyData);
						wrapdata.position(wrapdata.limit());
						updateAndNotifyPostRead(wrapdata,true);
						this.ackstate = StateConstants.REQUEST_ACKNOWLEDGED;
					}
					reqComplete = true;
					logger.addDebugLog(Level.FINE, AWSLogConstants.REQCOMPLETE,  AsyncWebClient.class.getName(),AWSLogMethodConstants.IS_REQUEST_COMPLETE, new Object[]{this, reqComplete});
					return true;
				}
				else
				{
					logger.addDebugLog(Level.FINE, AWSLogConstants.AWS_DEBUG_STRING,  AsyncWebClient.class.getName(),AWSLogMethodConstants.IS_REQUEST_COMPLETE, new Object[]{this, this.key, sockport, paramsMap, headerClone, getRequestURL()});
					printAccessLog();
					if(streammode)
					{
						byte[] bodyData = new byte[bb.position() - headerLength];
						for(int i= headerLength,j=0; i < bb.position() && j < bodyLength; i++)
						{
							bodyData[j++] = bb.get(i);
						}

						ByteBuffer wrapdata = ByteBuffer.wrap(bodyData);
						wrapdata.position(wrapdata.limit());
						updateAndNotifyPostRead(wrapdata,true);
						//fallback for non-header completion case
						notifyProcessor(StateConstants.ON_COMPLETION);					
					}
				}
			}
		}
		else
		{
			logger.log(Level.SEVERE,"UNSUPPORTED METHOD CALLED BY HOST "+getIPAddress()+", "+this+" : "+reqType, AsyncWebClient.class.getName(),AWSLogMethodConstants.IS_REQUEST_COMPLETE);
			throw new AWSException("UNSUPPORTED METHOD CALLED "+getIPAddress()+", "+this+" : "+reqType);	//No I18N
		}
		return false;
	}

	private void printAccessLog() throws AWSException
	{
		try
		{
			if(ConfManager.isReplaceLBIpsEnabled() || Util.isLBLSSLIP(ipaddr))
			{
				String hdrip = getHeader(ConfManager.getClientIPHeader());
				if(hdrip != null)
				{
					ipaddr = hdrip;
				}
			}
		}
		catch(Exception exp)
		{
			logger.addExceptionLog(Level.FINE, "Exception in lb_ssl_ip replacement : for the reqUrl : "+reqUrl+", IP : "+getIPAddress(), AsyncWebClient.class.getName(),AWSLogMethodConstants.PRINT_ACCESS_LOG,  exp);//No I18n
		}

		try
		{
			if(ConfManager.isAccessLogsEnabled() && !ConfManager.isAccessLogExcludedURL(reqUrl))
			{
				if(ConfManager.isSasLogFormatEnabled())
				{
					printDefaultAccessLog(); //for the teams who are not using log4j.properties
				}
				else
				{
					ACCESSLOGGER.log(Level.SEVERE, getAccessLog());
				}
			}
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.SEVERE, "Exception in accesslog : URL : "+reqUrl+", IP : "+getIPAddress(),AsyncWebClient.class.getName(),AWSLogMethodConstants.PRINT_ACCESS_LOG, ex);//No I18n
		}
	}

	protected boolean updateAndNotifyPostRead(ByteBuffer bb,boolean wrap) throws IOException, AWSException
	{
		int position = bb.position();
		if(!(position>0))
		{
			return false;
		}
			
		bb.flip();
		
		if((bodyLength - readBodyLength) <= position)
		{
			int size = (int)(bodyLength - readBodyLength);
			byte[] bodyData = new byte[size];
			bb.get(bodyData,0,bodyData.length);
			readBodyLength += bodyData.length;

			httpstream.write(bodyData);
			notifyProcessor(StateConstants.ON_DATA);

			endTimeLine(AWSConstants.TL_UPLOAD_TIME);//No I18N

			this.ackstate = StateConstants.REQUEST_ACKNOWLEDGED;
			return true;
		}
		else
		{
			byte[] bodyData = new byte[position];
			bb.get(bodyData,0,bodyData.length);

			readBodyLength += bodyData.length;
			httpstream.write(bodyData);
			notifyProcessor(StateConstants.ON_DATA);
		}

		if(streamreadlimit != -1 && readBodyLength > streamreadlimit)
		{
			throw new AWSException("HEAVY READ "+this);//No I18N
		}

		return false;
	}

	protected boolean isHeaderComplete(ByteBuffer bb) throws IOException, AWSException, IllegalReqException
	{
		if(bb.position() == 0)
		{
			return false;
		}
		if(headerComplete)
		{
			return true;
		}

		if(ConfManager.isProxyProtocolEnabled() && !proxyHeaderComplete && proxyHeader.isPresent(bb))
		{
			proxyHeaderComplete = proxyHeader.process(bb);
			if(proxyHeaderComplete)
			{
				proxyHeaderLength = proxyHeader.getLength();
				String ip = proxyHeader.getSourceIP();
                                String port = proxyHeader.getSourcePort();

                                if(ip!=null && port!=null)
                                {
                                        logger.log(Level.INFO,this+" Proxy header of length "+proxyHeaderLength+" is processed. Replacing the client IP "+this.ipaddr+" and port "+this.sockport+" with the IP "+ip+" and port "+port+" in the proxy header.", AsyncWebClient.class.getName(),AWSLogMethodConstants.IS_HEADER_COMPLETE);
                                        this.ipaddr = ip;
                                        this.sockport = Integer.parseInt(port);
                                }
                                else
                                {
                                        logger.log(Level.INFO,this+"  Proxy header of length "+proxyHeaderLength+" is processed. Yet not using IP address mentioned(The address type must be of UNSPEC type).", AsyncWebClient.class.getName(),AWSLogMethodConstants.IS_HEADER_COMPLETE);
                                }
			}
			else
			{
				logger.log(Level.INFO,this+" Proxy header of length "+proxyHeader.getLength()+" incomplete as sent by the client "+ipaddr+":"+sockport+" : "+new String(Arrays.copyOfRange(bb.array(),0,proxyHeader.getLength()))+" . Closing the connection .", AsyncWebClient.class.getName(),AWSLogMethodConstants.IS_HEADER_COMPLETE);
				return false;
			}
		}
		
		for(int i=proxyHeaderLength;(i<=(bb.position()-4) && !headerComplete);i++)
		{
			if(bb.get(i) == '\r' && bb.get(i+1) == '\n' && bb.get(i+2) == '\r' && bb.get(i+3) == '\n')	
			{
				headerComplete = true;
				headerLength = i +4;
			}
		}

		
		if(headerComplete)
		{
			processCompletedHeader(bb);
		}
		return headerComplete;
	}

	private void processCompletedHeader(ByteBuffer bb) throws IOException, AWSException, IllegalReqException
	{
		int reqHeaderLength = headerLength - proxyHeaderLength;
		byte[] headerData = new byte[reqHeaderLength];
		for(int i=0;i<reqHeaderLength;i++)
		{
			headerData[i] = bb.get(proxyHeaderLength+i);
		}

		BufferedReader br = new BufferedReader(new StringReader(new String(headerData, StandardCharsets.ISO_8859_1)));
		String line = br.readLine();
		String[] tokens = line.split(" ");
		reqType = tokens[0];

		if(!reqType.trim().equalsIgnoreCase(Http2Constants.HTTP2_REQ_TYPE) && !reqType.trim().equalsIgnoreCase(AWSConstants.GET_REQ) && !reqType.trim().equalsIgnoreCase(AWSConstants.POST_REQ) && !reqType.trim().equalsIgnoreCase(AWSConstants.OPTIONS_REQ) &&  !reqType.trim().equalsIgnoreCase(AWSConstants.DELETE_REQ) && !isSupportedRequestType(reqType))
		{
			AWSInfluxStats.addHackAttemptStats(AWSConstants.INVALID_REQ_TYPE);
			if(tokens.length > 1)
			{
				hackLogger.log(Level.INFO,"[HACKLOGGER - NOT A GET/POST/OPTIONS/DELETE REQUEST][ REQUEST TYPE = "+reqType+" , URL "+tokens[1]+" , FROM "+ipaddr+" ]", AsyncWebClient.class.getName(),AWSLogMethodConstants.PROCESS_COMPLETED_HEADER);//No I18N
			}
			else
			{
				hackLogger.log(Level.INFO,"[HACKLOGGER - NOT A GET/POST/OPTIONS/DELETE REQUEST][ REQUEST TYPE = "+reqType+" , FROM "+ipaddr+" ]", AsyncWebClient.class.getName(),AWSLogMethodConstants.PROCESS_COMPLETED_HEADER);//No I18N
			}
			sendError(HttpResponseCode.METHOD_NOT_ALLOWED,HttpResponseCode.METHOD_NOT_ALLOWED_MSG);
			throw new AWSException("Not HTTP GET/POST/OPTIONS/DELETE");//No I18N
		}
		try
		{
			rawUrl = tokens[1];
			version = tokens[2];
		}
		catch(Exception e)
		{
			throw new IllegalReqException("rawurl or version is not present, line:"+line);
		}

		updateHeaders(br);
		rawUrl = getRelativePath(rawUrl);
		reqUrl = processQueryString(rawUrl);
		statsReqUrl = getServletURI(reqUrl);
		addAWSCreationTimeInflux();
		updateSSLStats();
		if(headerMap.get(ConfManager.getStreamModeHeader())!=null || paramsMap.get(ConfManager.getStreamModeHeader())!=null || (ConfManager.isStreamModeEnabled() && isPostRequest(reqType.trim())) )
		{
			streammode = (Util.isStreamMode((String)headerMap.get(ConfManager.getStreamModeHeader())) || Util.isStreamMode((String)paramsMap.get(ConfManager.getStreamModeHeader())));
			if(!streammode && ConfManager.isStreamModeEnabled() && isStreamServlet(statsReqUrl))
			{
				streammode = true;
			}

			if(streammode && streamBuffer == null)
			{
				streamBuffer = ByteBuffer.allocate(read_limit);
			}

			if(streammode && httpstream==null)
			{
				try
				{
					if(getHeader(AWSConstants.HDR_CONTENT_LENGTH)!=null)
					{
						bodyLength = Long.parseLong(getHeader(AWSConstants.HDR_CONTENT_LENGTH));
						if(bodyLength > 0)
						{
							httpstream = new HttpStream(bodyLength, reqUrl);
						}
						return;
					}
				}
				catch(Exception ex)
				{}
				
				try
				{
					if(getHeader(AWSConstants.HDR_TRANSFER_ENCODING)!=null)
					{
						chunkencoding = getHeader(AWSConstants.HDR_TRANSFER_ENCODING).equals(AWSConstants.CHUNKED);
						if(chunkencoding)
						{
							httpstream = new HttpStream(reqUrl);
						}	
						return;
					}
				}
				catch(Exception ex)
				{}

				sendError(HttpResponseCode.LENGTH_REQUIRED,HttpResponseCode.LENGTH_REQUIRED_MSG);
				throw new AWSException("Content Length Header Not Present");//No I18N
			}
		}
	}

	private String getServletURI(String uri)
	{
		try
		{
			AbstractWebEngine engine = null;
			WebEngineLoader engineLoader = ConfManager.getWebEngineLoader();
			if(engineLoader != null)
			{
				String engineName = engineLoader.getEngineName(this.getHttpRequest(-1));
				if(engineName != null)
				{
					engine = WebEngine.getEngineByAppName(engineName);
				}
			}
			if(engine == null)
			{
				engine = WebEngine.getEngine(getHost(), localport);
			}
			this.webengineName = engine.getAppName();
			return engine.getServletURI(uri);
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, "Exception in get servlet uri :: "+uri, AsyncWebClient.class.getName(),AWSLogMethodConstants.GET_SERVLET_URI, ex);//No I18n
		}
		return uri;
	}

	private boolean isStreamServlet(String url)
	{
		AbstractWebEngine engine = WebEngine.getEngineByAppName(webengineName);
		return engine.isStreamServlet(url);
	}

	private String getRelativePath(String url)
	{
		try
		{
			String host = getHeader(AWSConstants.HOST);//No I18N
			host = host.replaceFirst(":.*$", AWSConstants.EMPTY_STRING);

			if(url.startsWith("http://"+host))
			{	
				return url.replaceAll("http://"+host, AWSConstants.EMPTY_STRING);//No I18N
			}
			else if(url.startsWith("https://"+host))
			{	
				return url.replaceAll("https://"+host, AWSConstants.EMPTY_STRING);//No I18N
			}
		}
		catch(Exception ex)
		{
			//logger.log(Level.INFO, " Exception ", ex);
		}
		return url;
	}

	private void updateHeaders(BufferedReader br)
	{
		try
		{
			String line = null;
			while(((line = br.readLine())!=null) && !line.equals(AWSConstants.EMPTY_STRING))
			{
				String[] tokens = line.split(":",2);
				if(tokens.length==2)
				{
					addOriginalHeader(tokens[0], tokens[1].trim());
					addHeader(tokens[0].toLowerCase(), tokens[1].trim());
				}
				else
				{
					AWSInfluxStats.addHackAttemptStats(AWSConstants.INVALID_REQ_HEADERS);
					hackLogger.log(Level.INFO,"[HACKLOGGER - MALFORMWRONGHEADER]["+ipaddr+"] "+line);//No I18n
				}
			}
			if(isHeaderPresent(AWSConstants.AWS_ENGINE_HEADER))
			{
				enginehdr = getHeader(AWSConstants.AWS_ENGINE_HEADER);
			}
			else if(enginehdr != null)
			{
				enginehdr = null;
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception "+this, AsyncWebClient.class.getName(),AWSLogMethodConstants.UPDATE_HEADERS, ex);
		}
	}

	public String getHeader(String headerKey)
	{
		return (String)headerMap.get(headerKey.toLowerCase());
	}

	private void processGetRequest(String rawUrl) throws IOException
	{
		//reqUrl = processQueryString(rawUrl);
	}

	private void processPostRequest(String rawUrl,byte[] bodyContent) throws IOException
	{
		//reqUrl = processQueryString(rawUrl); //CHECK IF ITS NEEDED FOR POST
		try
		{
			if(getHeader(AWSConstants.HDR_CONTENT_TYPE).toLowerCase().contains(AWSConstants.APPLICATION_X_WWW_URLENCODED))
			{
				updateParamMap(new String(bodyContent));
			}
		}
		catch(Exception ex)
		{
		}
	}

	private String processQueryString(String rawUrl)
	{
		int pos = rawUrl.indexOf('?');
		if(pos<=0)
		{
			String reqUrl = rawUrl.substring(1);
			if(reqUrl.equals(AWSConstants.EMPTY_STRING)) 
			{
				reqUrl=ConfManager.getDefaultURL();			
			}
			return reqUrl;
		}

		String reqUrl = rawUrl.substring(1,pos);
		String qString = rawUrl.substring(pos+1);
		updateParamMap(qString);
		return reqUrl;
	}

	private void updateParamMap(String qString)
	{
		if(!qString.trim().equals(AWSConstants.EMPTY_STRING))
		{
			String[] params = qString.split("&");
			for(int i=0;i<params.length;i++)
			{
				try
				{
					String param[] = params[i].split("=",2);
					if(param.length==2)
					{
						addParam(param[0], getParamString(param[0], URLDecoder.decode(param[1], AWSConstants.UTF_8)));
					}
					else if(param.length==1)
					{
						addParam(param[0], getParamString(param[0], AWSConstants.EMPTY_STRING));
					}
				}
				catch(Exception exp)
				{
					if(ConfManager.isIllegalReqExceptionEnabled())
					{
						logger.log(Level.INFO, " Exception --> rawurl:"+getRawRequestURL()+", ipaddr:"+ipaddr+", qString:"+qString+", ", AsyncWebClient.class.getName(),AWSLogMethodConstants.UPDATE_PARAM_MAP, exp);
					}
				}
			}
		}	
	}

	private String getParamString(String key, String value)
	{
		if(isParamPresent(key))
		{
			ArrayList<String> list = new ArrayList<>();
			try
			{
				list = (ArrayList) HttpDataWraper.getObject(getParameter(key));
				list.add(value);
				return HttpDataWraper.getString(list);
			}
			catch(ClassCastException cex)
			{
				list.add(getParameter(key));
				list.add(value);
				return HttpDataWraper.getString(list);
			}
			catch(Exception ex)
			{
				logger.addExceptionLog(Level.SEVERE, AWSLogConstants.EXCEPTION_IN_UPDATE_PARAM,AsyncWebClient.class.getName(),AWSLogMethodConstants.GET_PARAM_STRING, new Object[]{key, value, paramsMap});
				return getParameter(key);
			}
		}
		return value;
	}

	public HttpStream getHttpStream()
	{
		return httpstream;
	}

	public String getRequestType()
	{
		return reqType;
	}

	public String getRequestVersion()
	{
		return version;
	}

	public String getRequestURL()
	{
		return reqUrl;
	}

	public String getRawRequestURL()
	{
		return rawUrl;
	}

	public String getStatsRequestURL()
	{
		return statsReqUrl;
	}

	public String getEngineHdr()
	{
		return enginehdr;
	}

	public String getWebEngineName()
	{
		return this.webengineName;
	}

	String getHost()
	{
		try
		{
			String host= getHeader(AWSConstants.HOST);//No I18n
			if(host != null)
			{
				this.statsHost = host.replaceFirst(":.*$", AWSConstants.EMPTY_STRING);
				return this.statsHost;
			}
			return this.statsHost;
		}catch(Exception exp)
		{
			return AWSConstants.DEFAULT;//No I18N
		}
	}

	public boolean isHeaderComplete()
	{
		return headerComplete;
	}

	public HashMap getHeaderMap()
	{
		return headerMap;
	}

	public void addHeader(String key, String value)
	{
		headerMap.put(key, value);
	}

	public boolean isHeaderPresent(String key)
	{
		return headerMap.containsKey(key);
	}

	public HashMap getOriginalHeaderMap()
	{
		return originalHeaderMap;
	}

	public void addOriginalHeader(String key, String value)
	{
		originalHeaderMap.put(key, value);
	}

	public HashMap getParamMap()
	{
		return paramsMap;
	}

	public String getParameter(String key)
	{
		return (String) paramsMap.get(key);
	}

	public void addParam(String key, String value)
	{
		paramsMap.put(key, value);
	}

	public boolean isParamPresent(String key)
	{
		return paramsMap.containsKey(key);
	}

	public byte[] getRawBodyContent()
	{
		return bodyContent;
	}

	public boolean isSecuredAccess()
	{
		return (ConfManager.isHttpsPort(localport) && ConfManager.getSSLStartupType(localport) == SSLStartUpTypes.DEFAULT);
	}

	public void setChunkedStatus(boolean status)
	{
		chunked = status;
	}

	public void enableKeepAlive()
	{
		this.keepalive = true;
		setReuseConnection();
	}

	public void setKeepAliveTimeout(long timeout)
	{
		this.keepalivetimeout = timeout;
	}

	public boolean isKeepAliveEnabled()
	{
		return ConfManager.isKeepAliveEnabled() && keepalive  && !websocket;
	}

	public boolean isKeepAliveValid()
	{
		return ConfManager.isKeepAliveEnabled() && (System.currentTimeMillis()-inittime)<keepalivetimeout  && !websocket;
	}

	/*public String toString()
	  {
	  return this.ipaddr+ " ext ["+(System.currentTimeMillis()-getLastAccessTime())+"] rlen = "+ServerUtil.getMemSize(readLength)+" wlen = "+ServerUtil.getMemSize(writeLength)+" reqc = "+reqComplete;
	  }*/

	public boolean isReadCompleted()
	{
		return reqComplete;
	}

	public void close(String reason)
	{
		logger.addDebugLog(Level.FINE, AWSLogConstants.ON_CLOSE_FOR_CLIENT+" [Reason]["+reason+"]",AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE, new Object[]{this, getIPAddress()});
		if(closed)
		{
			logger.addDebugLog(Level.FINE, AWSLogConstants.CONNECTION_ALREADY_CLOSED, AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE, new Object[]{getIPAddress(), this});
			return;
		}
		synchronized(writelock)
		{
			closed = true;

			try
			{
				ClientManager.removeClient(this);
				if(isHttp2() && http2ConnID != null)
				{
					ConnectionManager.unRegisterConnection(http2ConnID);
					http2ConnID = null;
				}
			}
			catch (Exception ex)
			{
				logger.log(Level.SEVERE, "[Exception - Http2 AsyncWebClient-close-http2ConnID:"+http2ConnID+"-streamID:NA] - ERROR while closing Http2Connection");
			}

			long requestTimeTaken = (firstReadTime == -1) ? -1 : ((lastWriteTime == -1) ? (System.currentTimeMillis() - firstReadTime) : (lastWriteTime - firstReadTime));
			try
			{
				if(ConfManager.isPrintIncompleteRequestEnabled(ipaddr) && reqUrl == null)
				{
					logger.log(Level.INFO, "INCOMPLETE REQUEST : IP {0}, data received : {1}", AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE, new Object[]{getIPAddress(), new String(byteBuffer.array()).trim()});
				}
			}
			catch(Exception exp)
			{
				logger.addExceptionLog(Level.FINE, AWSLogConstants.EXCEPTION_IN_INCOMPLETE_REQUEST_DEBUG_LOG,AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE,  exp);//No I18n
			}
			if(ConfManager.isCloseReadDebugEnabled(ipaddr))
			{
				int readcount = 0;
				ByteBuffer bb = ByteBuffer.allocate(10);
				try
				{
					readcount = ((SocketChannel)key.channel()).read(bb);
					if(readcount > 0)
					{
						BandWidthTracker.updateHttpRead(readcount);
					}
				}
				catch(IOException ex)
				{
					logger.addExceptionLog(Level.FINE, " FAILURE TO READ AT CLOSE FOR "+getIPAddress(),AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE,  ex);
				}
				finally
				{
					bb = null;
				}

				logger.log(Level.INFO, "ON CLOSE -> client IP {0}, Read count {1}"+" [Reason]["+reason+"]", AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE, new Object[]{getIPAddress(), readcount});//No I18N
			}

			try
			{
				if(inflater!=null)
				{
					inflater.end();
				}
			}
			catch(Exception ex)
			{
			}
			
			try
			{
				if(deflater!=null)
				{
					deflater.end();
				}
			}
			catch(Exception ex)
			{
			}

			if(streammode && httpstream != null && !httpstream.isExceptionSet())
			{
				httpstream.setThrowException();
				try
				{
					notifyProcessor(StateConstants.ON_DATA);
				}
				catch(Exception ex)
				{
					logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE,  ex);
				}
			}
			try
			{
				if(key!=null)
				{
					SocketChannel ch = null;
					try
					{
						ch = (SocketChannel)key.channel();
					}
					catch(Exception ex)
					{
						logger.addExceptionLog(Level.FINE,AWSLogConstants.CHANNEL_TYPE_CASE_EXP,AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE,  ex);//No I18N
					}
					try
					{
						key.cancel();
					}
					catch(Exception ex)
					{
						logger.addExceptionLog(Level.FINE, "Exception in key cancel "+this,AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE,  ex);//No I18N
					}
					try
					{
						if(ch.isOpen())
						{
							ch.close();
						}
					}
					catch(Exception ex)
					{
						logger.addExceptionLog(Level.FINE, "Exception in key channel close for client "+this+" "+getIPAddress(),AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE,  ex);//No I18N
					}
				}
				byteBuffer.clear();
				try
				{
					if(continuationFrame != -1)
					{
						logger.log(Level.FINE, "Incompleted Read Frame got closed.", AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE);//No I18n
					}
				}
				catch(Exception aex)
				{
				}
			}
			catch(Exception e)
			{
			}
			try
			{
				if(isWebSocket())
				{
					clearWebSocket();
				}
			}
			catch(Exception ex)
			{
			}
			removeFromTimeoutTracker();
			removeFromRequestHeartBeatTracker();
			if(headerComplete)
			{
				printEndAWSAccessLog(requestTimeTaken);
			}
			if(!end_notified)
			{
				int writestatus = getWriteStatus();
				if(writestatus  == AWSConstants.WRITE_COMPLETED)
				{
					try
					{
						if(streammode)
						{
							endTimeLine(AWSConstants.TL_DOWNLOAD_TIME);//No I18N
						}
						else
						{
							endTimeLine(AWSConstants.TL_WRITE_TIME);//No I18N
						}
						
						if(writeerror)
						{
							notifyProcessor(StateConstants.ON_WRITEFAILURE);
						}
						else
						{
							notifyProcessor(StateConstants.ON_WRITECOMPLETE);
						}	
					}
					catch(Exception ex)
					{
						logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION, AsyncWebClient.class.getName(),AWSLogMethodConstants.CLOSE, ex);
					}
				}
				else
				{
					if(streammode)
					{
						replaceAndEndTimeLine(AWSConstants.TL_DOWNLOAD_TIME,AWSConstants.TL_DOWNLOAD_FAILURE);
					}
					else
					{
						replaceAndEndTimeLine(AWSConstants.TL_WRITE_TIME,AWSConstants.TL_WRITE_FAILURE);
					}
					notifyProcessor(StateConstants.ON_WRITEFAILURE);
				}
				end_notified = true;
			}
			exportTimeLine();
			AWSLogClientThreadLocal.clear();
		}
	}
		
	protected void clearWebSocket()
	{
		if(wsReadFrame!=null)
		{
			wsReadFrame.clear();
		}
		wsReadFrame=null;
		if(wsServlet!=null)
		{
			wsServlet.handleClose();
			removeWSListener(wsServlet);
		}
		//wsServlet=null;
	}

	private void removeWSListener(WebSocket wsServlet)
	{
		if(!ConfManager.isWSPrdListenerFactoryEnabled())
		{
			return;
		}
		String prd = wsServlet.getWSPrd();
		int type = wsServlet.getWSType();
		if(prd == null)
		{
			return;
		}
		String ukey = prd+"_"+getIPAddress();
		WSListenerFactory.remove(ukey,type,wsServlet);
	}

	public boolean isHeaderCheckNeeded()
	{
		return this.headerCheckNeeded;
	}

	public void setHeaderCheckNeeded(boolean headerCheckNeeded)
	{
		this.headerCheckNeeded = headerCheckNeeded;
	}

	public boolean isWriteDataInQueueEnabled()
	{
		return this.writeDataInQueueEnabled;
	}

	protected void notifyHeaderComplete()
	{
		if(headerCheckNeeded)
		{
			if(ConfManager.isHeaderCompletionNeeded())
			{
				notifyProcessor(StateConstants.ON_HEADER_COMPLETION);
			}
			headerCheckNeeded=false;
		}
	}

	public void updateExternalHit(String domain)
	{
		try
		{
			WmsRuntimeCounters.getDomainHit(domain).updateExternalHttpHit();
		}
		catch(Exception ex)
		{
		}
	}

	private void sendError(int i, String msg)
	{
		try
		{
			HttpResponse httpresponse = new HttpResponse(this, this.getSelectionKey(), ipaddr, this.getClientId());
			httpresponse.sendError(i, msg);
			httpresponse.close();
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,AsyncWebClient.class.getName(),AWSLogMethodConstants.SEND_ERROR,  ex);
		}
	}

	public void invokeNotification(int notification)
	{
		notifyProcessor(notification);
	}

	public HttpResponse getHttpResponse()
	{
		if(isWebSocket() && wsServlet!=null)
		{
			return wsServlet.getResponse();
		}
		return null;
	}

	protected boolean isChunkedStream()
	{
		try
		{
			String  chunkheader = getHeader(ConfManager.getChunkedStreamTimeoutHeader());
			long timeout = Long.parseLong(chunkheader);

			return (timeout > 0);
		}
		catch(Exception ex)
		{
			return false;
		}
	}

	public boolean isStreamModeEnabled()
	{
		return streammode;
	}

	private boolean isPostRequest(String reqType)
	{
		return reqType.equalsIgnoreCase("POST") || ConfManager.isSupportedPostMethod(reqType);
	}

	public void addIndex(int size, long index)
	{
		if(!msgretryenabled || size == 0)
		{
			return;
		}
		/*ByteIndex bi = new ByteIndex(size,index);
		if(bi == null)
		{
			try
			{
				AWSInfluxStats.addHeavyDataStats(true, "addIndex");
				throw new AWSException(" Exception thrown in AsyncWebClient - addIndex method ");
			}
			catch(AWSException e)
			{
				logger.log(Level.INFO,"AsyncWebClient-addIndex requrl: "+getRequestURL()+" Client entity: "+this,e);
			}
			return ; 
		}
		indexList.addLast(bi);*/
                indexList.add(new ByteIndex(size,index));

	}
	public void removeIndex(int size)
	{
		try
		{
			if(!msgretryenabled || size == 0)
			{
				return;
			}
			if(indexList.size() == 0)
			{
				return;
			}
			ByteIndex bi = indexList.peek();
			int firstIdxSize = bi.getSize();
			long firstIdxIndex = bi.getIndex();

			if(firstIdxSize == size)
			{
				indexList.poll();
				if(isWebSocket() && firstIdxIndex != -1)
				{
					notifyWSWriteAck(firstIdxIndex);
				}
			}
			else if(firstIdxSize < size)
			{
				indexList.poll();
				if(isWebSocket() && firstIdxIndex != -1)
				{
					notifyWSWriteAck(firstIdxIndex);
				}
				removeIndex(size - firstIdxSize);
			}
			else if(size < firstIdxSize)
			{
				bi.deductSize(size);
			}
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.FINE, AWSLogConstants.EXCEPTION_IN_REMOVEINDEX,AsyncWebClient.class.getName(),AWSLogMethodConstants.REMOVE_INDEX,  ex);//No I18N
		}
	}

	public ArrayList getFailedIndexList()
	{
		ArrayList failedlist = new ArrayList();
		synchronized(writelock)
		{
			Iterator itr = indexList.iterator();
			while(itr.hasNext())
			{
				ByteIndex bi = (ByteIndex)itr.next();
				if(bi.getIndex() != -1)
				{
					failedlist.add(bi.getIndex());
				}	
			}
		}
		return failedlist;
	}

	public Deflater getDeflater()
	{
		return this.deflater;
	}

	class ByteIndex
	{
		int size;
		long index;

		public ByteIndex(int size, long index)
		{
			this.size = size;
			this.index = index;
		}

		public int getSize()
		{
			return this.size;
		}

		public long getIndex()
		{
			return this.index;
		}

		public void deductSize(int size)
		{
			this.size -= size;
		}
	}

	public void pingWebSocket()
	{
		try
		{
			if(!isWebSocket())
			{
				return;
			}
			byte d[] =new byte[2];
			d[0]=(byte)0x81;
			d[1]=(byte)0;
			setOutputDataSize(d.length);
			writeData(ByteBuffer.wrap(d),-1l);
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.INFO, ipaddr+"AWS --> Error while pinging ", AsyncWebClient.class.getName(),AWSLogMethodConstants.PING_WEBSOCKET, e);
		}
	}

	public void sendCloseFrame()
	{
		try
		{	
			if(!isWebSocket())
			{
				return;
			}
			byte d[] =new byte[2];
			d[0]=(byte)0x88;
			d[1]=(byte)0x00;
			setOutputDataSize(d.length);
			writeData(ByteBuffer.wrap(d),-1l);
			setCloseAfterWrite();
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.INFO, ipaddr+"AWS --> Error while sending close frame ",AsyncWebClient.class.getName(),AWSLogMethodConstants.SEND_CLOSE_FRAME,  e);//No I18N
		}

	}

	private String bytesToHex(byte[] bytes) 
	{
		char[] hexChars = new char[bytes.length * 2];
		for ( int j = 0; j < bytes.length; j++ ) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEXARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEXARRAY[v & 0x0F];
		}
		return new String(hexChars);
	}	

	private byte[] inflate(byte data[]) throws IOException,DataFormatException
	{
		ByteArrayOutputStream outputStream = null;
		try
		{
			long stime = System.currentTimeMillis();
			int compSize=data.length;
			byte[] bb = new byte[data.length+4];
			System.arraycopy(data, 0, bb, 0, data.length);
			System.arraycopy(AWSConstants.inflater_constant_byte_array, 0, bb, data.length, 4);
			data = bb;

			inflater.setInput(data);
			outputStream = new ByteArrayOutputStream(data.length);
			byte[] buffer = new byte[ConfManager.getWSInflateBufferSize()];
			while(true)
			{
				int count = inflater.inflate(buffer,0,buffer.length);
				if(count<=0){break;}
				outputStream.write(buffer, 0, count);
			}
			byte[] output = outputStream.toByteArray();
			AWSInfluxStats.updateWSCompressionStats(AWSConstants.AWS_INFLATE, compSize, output.length, System.currentTimeMillis() - stime);
			return output;
		}
		finally
		{
			try
			{
				if(outputStream != null)
				{
					outputStream.close();
				}
			}
			catch(Exception e)
			{
			}
		}
	}

	void updateReqProcTime(long diff)
	{
		this.reqproctime += diff;
	}

	public long getRequestProcessorTime()
	{
		return this.reqproctime;
	}

	void setReqQueueInsertTime(long time)
	{
		this.reqqueueinserttime = time;
	}

	public long getReqQueueInsertTime()
	{
		return this.reqqueueinserttime;
	}

	public void setWSServletThreadSafe(boolean status)
	{
		wsservletthreadsafe = status;
	}

	public boolean isWSServletThreadSafe()
	{
		return wsservletthreadsafe;
	}

	public void setReadLimit(int limit)
	{
		read_limit = limit;
	}

	public int getReadLimit()
	{
		return read_limit;
	}

	public void setWriteCapacity(int limit)
	{
		writecapacity = limit;
	}

	public int getWriteCapacity()
	{
		return writecapacity;
	}

	public void setMaxDataPerWrite(int maxdataperwrite)
	{
		max_outbb_size = maxdataperwrite;
	}

	public int getMaxDataPerWrite()
	{
		return max_outbb_size;
	}

	public void setMaxDataPerWSRead(int wsreadlimit)
	{
		maxdataperwsread = wsreadlimit;
	}

	public int getMaxDataPerWSRead()
	{
		return maxdataperwsread;
	}

	public long getInittime()
	{
		return inittime;
	}

	public int getZeroReadCount()
	{
		return zeroReadCount;
	}

	public void setMaxZeroReadCount(int count)
	{
		maxZeroReadCount = count;
	}

	public int getMaxZeroReadCount()
	{
		return maxZeroReadCount;
	}

	private void addAWSCreationTimeInflux()
	{
		try
		{
			if(ConfManager.isQOSEnabled())
			{
				if(getTimeLineInfo(AWSConstants.TL_CREATION_TIME) != null)
				{
					ConcurrentHashMap hm = getTimeLineInfo(AWSConstants.TL_CREATION_TIME);
					long stime = (long) hm.get(AWSConstants.START);
					long endtime = (long) hm.get(AWSConstants.END);
					if(isSSL())
					{
						AWSInfluxStats.addAWSRequestStats(statsReqUrl, responseCode, webengineName, AWSConstants.SSL_CONNECT_TIME, endtime - stime, 1);
					}
					else
					{
						AWSInfluxStats.addAWSRequestStats(statsReqUrl, responseCode, webengineName, AWSConstants.CONNECT_TIME, endtime - stime, 1);
					}
				}
				else if(getTimeLineInfo(AWSConstants.TL_RESET_TIME) != null)
				{
					ConcurrentHashMap hm = getTimeLineInfo(AWSConstants.TL_RESET_TIME);
					long stime = (long) hm.get(AWSConstants.START);
					long endtime = (long) hm.get(AWSConstants.END);
					if(isSSL())
					{
						AWSInfluxStats.addAWSRequestStats(statsReqUrl, responseCode, webengineName, AWSConstants.SSL_RESET_TIME_STATS, endtime - stime, 1);
					}
					else
					{
						AWSInfluxStats.addAWSRequestStats(statsReqUrl, responseCode, webengineName, AWSConstants.RESETTIME, endtime - stime, 1);
					}
				}
			}
		}
		catch(Exception ex)
		{
		}
	}

	protected void updateSSLStats()
	{
		try
		{
			if (sslStats == null || sslStats.isEmpty())
			{
				return;
			}
			AbstractWebEngine engine = null;
			String appname = null;
			String uri = null;
			try
			{
				engine = WebEngine.getEngine(getHost(), getLocalPort());
				appname = engine.getAppName();
			}
			catch (Exception ex)
			{
			}
			try
			{
				uri = getStatsRequestURL();
				if(uri != null && !engine.isURLPresent(uri))
				{
					uri = engine.getServletURI(uri);
				}
			}
			catch (Exception ex)
			{
			}
			try
			{
				AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_TASKQUEUE, (Long) sslStats.get(AWSConstants.TASKQUEUEDTIME), (Integer) sslStats.get(AWSConstants.COUNT));
				AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_PROCESSING, (Long) sslStats.get(AWSConstants.PROCESSING_TIME), (Integer) sslStats.get(AWSConstants.COUNT));
				if((Integer) sslStats.get(AWSConstants.READCOUNT) > 0)
				{
					AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_READDATA, (Long) sslStats.get(AWSConstants.READTIME), (Integer) sslStats.get(AWSConstants.READCOUNT));
				}
				if((Integer) sslStats.get(AWSConstants.WRITECOUNT) > 0)
				{
					AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_WRITEDATA, (Long) sslStats.get(AWSConstants.WRITETIME), (Integer) sslStats.get(AWSConstants.WRITECOUNT));
				}
				AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.NETDATA_TOTALTIME, (Long) sslStats.get(AWSConstants.TASKQUEUEDTIME) + (Long) sslStats.get(AWSConstants.PROCESSING_TIME), (Integer) sslStats.get(AWSConstants.COUNT));
				if(getTimeLineInfo(AWSConstants.TL_SSL_HANDSHAKE_TIME) != null)
				{
					ConcurrentHashMap hm = getTimeLineInfo(AWSConstants.TL_SSL_HANDSHAKE_TIME);
					long stime = (long) hm.get(AWSConstants.START);
					long endtime = (long) hm.get(AWSConstants.END);
					this.timetakenforhandshake = endtime - stime;
					AWSInfluxStats.addAWSRequestStats(uri, responseCode, appname, AWSConstants.SSL_HANDSHAKE_TIME_STATS, endtime - stime, 1);
				}
			}
			catch (Exception ex)
			{
			}
			finally
			{
				sslStats = null;
			}
		}
		catch(Exception ex)
		{
		}
	}

	public void addSSLStats(Long taskQueuedTime, Long processingTime, Long readtime, Long writetime, Integer readCount, Integer writeCount, Integer count)
	{
		if(sslStats == null)
		{
			sslStats = new Hashtable();
		}
		sslStats.put(AWSConstants.TASKQUEUEDTIME, getStatsValue(AWSConstants.TASKQUEUEDTIME, taskQueuedTime));
		sslStats.put(AWSConstants.PROCESSING_TIME, getStatsValue(AWSConstants.PROCESSING_TIME, processingTime));
		sslStats.put(AWSConstants.READTIME, getStatsValue(AWSConstants.READTIME, readtime));
		sslStats.put(AWSConstants.WRITETIME, getStatsValue(AWSConstants.WRITETIME, writetime));
		sslStats.put(AWSConstants.READCOUNT, getStatsValue(AWSConstants.READCOUNT, readCount));
		sslStats.put(AWSConstants.WRITECOUNT, getStatsValue(AWSConstants.WRITECOUNT, writeCount));
		sslStats.put(AWSConstants.COUNT, getStatsValue(AWSConstants.COUNT, count));
	}

	private Long getStatsValue(String key, Long value)
	{
		if(sslStats.containsKey(key))
		{
			return ((Long) sslStats.get(key)) + value;
		}
		return value;
	}

	private Integer getStatsValue(String key, Integer value)
	{
		if(sslStats.containsKey(key))
		{
			return ((Integer) sslStats.get(key)) + value;
		}
		return value;
	}

	public long getClientId()
	{
		return clientId;
	}

	public HttpRequest getHttpRequest(int state) throws IOException
	{
		if(isWebSocket() && wsServlet != null)
		{
			return wsServlet.getRequest();
		}
		return new HttpRequest(reqType, reqUrl, rawUrl, version, getIPAddress(), headerMap, originalHeaderMap, paramsMap, bodyContent, state, httpstream, localport, getScheme(), webengineName);
	}

	class WebSocketReadFrame
	{
		private int wread_limit = read_limit;
		private ByteBuffer readFrame = ByteBuffer.allocate(wread_limit);

		private int stage = 0;
		private int bytesToRead = 1;

		private int opcode = -1;
		private boolean isMasked = false;
		private byte[] mask;
		private int maskIndex=0;
		private int payloadSize = -1;
		private boolean completed = false;
		private boolean error = false;
		private boolean fclosed = false;
		private int lastReadPosition = 0;
		private int lastPosition = 0;
		private boolean isCompressed=false;
		private boolean fin = true;

		private Object rawPayload = null;

		public void reset()
		{
			stage = 0;
			bytesToRead = 1;

			opcode = -1;
			isMasked = false;
			mask = null;
			maskIndex = 0;
			payloadSize = -1;
			completed = false;
			error = false;
			fclosed = false;
			lastReadPosition = 0;
			lastPosition = 0;
			isCompressed = false;
			fin = true;

			rawPayload = null;

			wread_limit = read_limit;
			readFrame.clear();

			if(wread_limit != readFrame.limit())
			{
				readFrame = ByteBuffer.allocate(wread_limit);
			}
		}

		public boolean process() throws IOException, AWSException
		{
			lastPosition = readFrame.position();
			readFrame.flip();
			readFrame.position(lastReadPosition);
			int dataInHand = readFrame.limit() - readFrame.position();
			//logger.log(Level.FINE,ipaddr+"GS --> RF process start stage="+stage+" btr="+bytesToRead+" dih="+dataInHand+" lastReadPosition="+lastReadPosition+" lastPosition="+lastPosition);//No I18N
			if(dataInHand< bytesToRead ) { readFrame.limit(wread_limit); readFrame.position(lastPosition);  return false; }

			switch(stage)
			{

				case 0:	// reading opcode
					//logger.log(Level.FINE,ipaddr+"GS --> entering stage 0");//No I18N
					byte header = readFrame.get();
					opcode = (header & 0x0F); // last 4 bits opcode
					this.fin = ((header & 0x80)!=0);
					boolean rsv1 = ((header & 0x40)!=0);
					boolean ctrl = ((header & 0x08)!=0);
					if(fin && !ctrl && rsv1)
					{
						if(!ConfManager.isWebSocketCompressionEnabled())
						{
							throw new AWSException("Websocket Invalid read frame : Compressed data on Uncompressed Stream");	//No I18N
						}
						this.isCompressed = true;
						//logger.log(Level.FINE,this+ipaddr+"GS --> message compressed opcode="+opcode);
					}
					/*else
					{
						logger.log(Level.FINE,this+ipaddr+"GS --> Frame Error fin="+fin+" rsv1="+rsv1+" ctrl="+ctrl+" header="+header);
					}*/
					if(opcode==8) 
					{
						completed=true;
						fclosed=true;
						return false;
					}

					if(!fin && opcode != Util.WS_OPCODE_CONTINUATION)//1st C.Frame -- fin - false, opcode - text/binary
					{
						continuationFrame = opcode;
					}
					else if(continuationFrame != -1 && opcode != Util.WS_OPCODE_CONTINUATION)// Until fin = true, we should get C.Frame
					{
						if(isPingFrame() || isPongFrame() || isCloseFrame())
						{
							logger.addDebugLog(Level.FINE, AWSLogConstants.PING_PONG_CLOSE_FRAME_RECEIVED, AsyncWebClient.class.getName(),AWSLogMethodConstants.PROCESS, new Object[]{opcode});//No I18n
						}
						else
						{
							logger.log(Level.SEVERE, "Protocol Error : fin : {0}, opcode : {1}, Initial Frame : {2}",WebSocketReadFrame.class.getName(),AWSLogMethodConstants.PROCESS, new Object[]{fin, opcode, continuationFrame});
							throw new AWSException("Frame MisMatch Error");//No I18n
						}
					}

					stage = 1;
					bytesToRead =1;
					break;	
				case 1:	// reading payload size
					//logger.log(Level.FINE,ipaddr+"GS --> entering stage 1");//No I18N
					byte payloadByte = readFrame.get();
					isMasked = (payloadByte >> 7 & 0x1) == 1; // most significant bit is has mask 
					payloadSize = (payloadByte & 0x7F);
					//logger.log(Level.FINE,ipaddr+"GS --> isMasked="+isMasked+" Payload Size "+payloadSize);//No I18N
					if(payloadSize < 126)	// payload size < 126 bytes
					{
						if(isMasked)
						{
							stage = 3;
							bytesToRead=4;
						}
						else
						{
							stage = 4;
							bytesToRead=payloadSize;
						}
					}
					else if(payloadSize == 126)	// payload size >=126 and < 64 KB
					{
						stage = 2;
						bytesToRead = 2;
					}
					else if(payloadSize == 127)	// payload size >64 KB
					{
						stage = 2;
						bytesToRead = 8;
					}
					break;
				case 2:	// reading extended size 2 / 4 bytes to calculate total payload size
					//logger.log(Level.FINE,ipaddr+"GS --> entering stage 2 - Payload Size "+payloadSize+" Bytes to read "+bytesToRead);//No I18N
					byte extnByte[] = new byte[bytesToRead];
					readFrame.get(extnByte);
					payloadSize = 0;
					for (int i = 0; i < bytesToRead; i++) 
					{
						payloadSize = (payloadSize << 8) | (extnByte[i] & 0xFF);
					}
					//logger.log(Level.FINE,ipaddr+"GS --> entering stage 2 - Processed payload size "+payloadSize+" Masked "+isMasked);//No I18N
					if(isMasked)
					{
						stage = 3;
						bytesToRead=4;
					}
					else
					{
						stage = 4;
						bytesToRead=payloadSize;
					}

					break;
				case 3:	// if has mask then read mask value 4 bytes
					//logger.log(Level.FINE,ipaddr+"GS --> entering stage 3");//No I18N
					mask = new byte[4];
					readFrame.get(mask);
					stage = 4;
					bytesToRead=payloadSize;
					break;
				case 4:	// read exact payload
					//logger.log(Level.FINE,ipaddr+"GS --> entering stage 4 - "+payloadSize);//No I18N
					if(payloadSize > 0)
					{	
						byte[] payload = new byte[payloadSize];
						readFrame.get(payload);
						if(isMasked)	// if has mask then XOR every byte of payload with mask value
						{
							for(int i = 0; i < payload.length; i++)
							{
							// XOR 
								payload[i] = (byte) (payload[i] ^ mask[maskIndex]);
								maskIndex = (maskIndex + 1) % 4;
							}
						}
						if(isCompressed)
						{
							try
							{
								if(isTextFrame())
								{
									rawPayload=new String(inflate(payload));
								}
								else
								{
									rawPayload=inflate(payload);
								}
							}
							catch(DataFormatException dfe)
							{
								throw new AWSException("Websocket Decompression Error : "+dfe.getMessage());	//No I18N
							}
						}
						else
						{
							if(isTextFrame())
							{
								rawPayload = new String(payload);
							}
							else
							{
								rawPayload = payload;
							}
						}
					}

					completed = true;
					stage = 5;
					bytesToRead=0;
					//logger.log(Level.FINE,ipaddr+"GS --> payload readFully = "+payloadSize);//No I18N
					break;
				case 5:	// Read Frame completed these unconsumed data needs to be handled as a separate readframe
					//logger.log(Level.FINE,ipaddr+"GS --> entering stage 5");//No I18N
					//logger.log(Level.FINE,ipaddr+"GS --> reading more than readFrame");//No I18N
					break;
			}

			dataInHand = readFrame.limit() - readFrame.position();
			lastReadPosition = readFrame.position();
			readFrame.limit(wread_limit);
			readFrame.position(lastPosition);
			//logger.log(Level.FINE,ipaddr+"GS --> RF process end stage="+stage+" btr="+bytesToRead+" dih="+dataInHand+" lastReadPosition="+lastReadPosition+" lastPosition="+lastPosition);//No I18N
			return (dataInHand>= bytesToRead ) && !completed;
		}

		public Object getRawPayload()
		{
			return this.rawPayload;
		}

		public int getRawPayloadLength()
		{
			try
			{
				if(isTextFrame())
				{
					return ((String)rawPayload).length();
				}
				else 
				{
					return ((byte[])rawPayload).length;
				}
			}
			catch(Exception ex)
			{
			}
			return 0;

		}

		public boolean isTextFrame()
		{
			if(fin && (opcode == Util.WS_OPCODE_TEXT || continuationFrame == Util.WS_OPCODE_TEXT))
			{
				return true;
			}
			if(!fin && continuationFrame == Util.WS_OPCODE_TEXT)
			{
				return true;
			}
			return false; 
		}
	
		public boolean isComplete()
		{
			return this.completed;
		}

		public boolean isPingFrame()
		{
			return opcode == Util.WS_OPCODE_PING;
		}

		public boolean isPongFrame()
		{
			return opcode == Util.WS_OPCODE_PONG;
		}

		public boolean isCloseFrame()
		{
			return opcode == Util.WS_OPCODE_CLOSE;
		}

		public boolean isContinuationFrame()
		{
			if(!fin)
			{
				return true;
			}
			if(fin && opcode == Util.WS_OPCODE_CONTINUATION)
			{
				return true;
			}
			return false;
		}

		public boolean isLastFrame()
		{
			return fin;
		}

		public boolean isClosed()
		{
			return this.fclosed;
		}

		public boolean isError()
		{
			return this.error;
		}

		public byte[] getUnconsumed()
		{
			byte[] unconsumed = null;
			//int remaining = readFrame.remaining();
			readFrame.position(lastReadPosition);
			int dataInHand = lastPosition - readFrame.position();
			if(dataInHand > 0)
			{
				logger.addDebugLog(Level.FINE, AWSLogConstants.HASUNCONSUMED_DATA,WebSocketReadFrame.class.getName(),AWSLogMethodConstants.GET_UNCONSUMED,  new Object[]{ipaddr, dataInHand});
				unconsumed = new byte[dataInHand];
				readFrame.get(unconsumed);
			}

			return unconsumed;
		}

		public void add(byte[] data) throws IOException, AWSException
		{
			int newcapacity = -1;
			try
			{
				if(data==null)
				{
					return;
				}
				logger.addDebugLog(Level.FINE, AWSLogConstants.AWS_RF_ADD,WebSocketReadFrame.class.getName(),AWSLogMethodConstants.ADD,  new Object[]{ipaddr, data.length});
				if(readFrame.remaining() < data.length)
				{
					if((readFrame.position() + data.length) < readFrame.capacity()*2)
					{
						newcapacity = readFrame.capacity()*2;
						if((newcapacity > maxdataperwsread) && ((readFrame.position() + data.length) <= maxdataperwsread))
						{
							newcapacity = maxdataperwsread;
						}
					}
					else
					{
						newcapacity = readFrame.position() + data.length;
					}
					if(newcapacity > maxdataperwsread)
					{
						throw new AWSException("Buffer exhaust");//No I18N
					}
					ByteBuffer newBB = ByteBuffer.allocate(newcapacity);	
					readFrame.flip();
					newBB.put(readFrame);
					readFrame = newBB;
					wread_limit = newBB.capacity();
				}
				readFrame.put(data);
				while(process())
				{
					logger.addDebugLog(Level.FINE, AWSLogConstants.USELESS_CODE_CHECK,WebSocketReadFrame.class.getName(),AWSLogMethodConstants.ADD);
				}
			}
			catch(Exception e)
			{
				error = true;
				completed=true;
				logger.log(Level.SEVERE, "[Exception] AWS DEBUG --> rf:"+readFrame+", data.length:"+data.length+", newcapacity:"+newcapacity+", macdataperwsread:"+maxdataperwsread+", lastPosition:"+lastPosition+", lastReadPosition:"+lastReadPosition+", bytesToRead:"+bytesToRead+", wread_limit:"+wread_limit+", lastpayloadSize:"+payloadSize+", this:"+this,WebSocketReadFrame.class.getName(),AWSLogMethodConstants.ADD, e);//Need to debug BufferUnderFlow exception case
			}
		}

		public void clear()
		{
			try
			{
				rawPayload = null;
				readFrame.clear();
			}
			catch(Exception e)
			{
			}
		}
	}

	public boolean isHttp2()
	{
		return this.http2;
	}

	protected boolean isHttp2Req(ByteBuffer bb)
	{
		bb.flip();
		byte dataRF[] = new byte[24];
		bb.get(dataRF, 0, 24);

		if(Arrays.equals(dataRF, Http2Constants.CLIENT_PREFACE_HEX_STRING))
		{
			return true;
		}
		else
		{
			bb.rewind();
			bb.compact();
			return false;
		}
	}

	public String getHttp2ConnID()
	{
		return http2ConnID;
	}

	public Http2Connection getHttp2Connection()
	{
		return ConnectionManager.getConnection(http2ConnID);
	}

	public void setAsHttp2Client() throws Exception
	{
		if(http2 == true)
		{
			logger.log(Level.INFO, "[Http2Error] - Multiple initialization of Http2Connection");
			return;
		}

		http2ConnID = ConnectionManager.registerConnection(this);
		http2 = true;

		reqtimeline = null;
	}

	public void setSocketTimeTakenStats(long readtime, long writetime, int readCount, int writeCount)
	{
		try
		{
			if(getHttp2Connection() != null)
			{
				getHttp2Connection().setSocketTimeTakenStats(readtime, writetime, readCount, writeCount);
			}
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 AsyncWebClient-setSocketTimeTakenStats-h2ID:"+http2ConnID+"-streamID:NA]", ex);
		}
	}

	class ProxyHeader
	{

		String version;
		String protocol;
		String sourceAddress;
		String destinationAddress;
		String sourcePort;
		String destinationPort;
		int length;
		boolean completed = false;

		public boolean process(ByteBuffer bb) throws IOException, AWSException
		{
			if(isV1(bb))
			{
				return processV1(bb);
			}
			else
			{
				return processV2(bb);
			}
		}

		public boolean processV1(ByteBuffer bb) throws IOException, AWSException
		{
			if(completed)
			{
				return true;
			}

			for(int i=0; i<=bb.position()-2; i++)
			{
				if(bb.get(i)=='\r' && bb.get(i+1)=='\n')
				{
					completed = true;
					length = i+2;
					break;
				}
			}

			if(completed)
			{
				byte[] proxyHeaderBuf = new byte[length];	

				for(int i=0; i<length;i++)
				{
					proxyHeaderBuf[i]=bb.get(i);
				}
						
				String proxyHeader = new String(proxyHeaderBuf,"US-ASCII");
				
				if(proxyHeader.length() > 107 || proxyHeader.length() < 15)
				{
					logger.log(Level.SEVERE," Proxy header v1 with unsuitable length : "+proxyHeader.length(),ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V1);
					throw new AWSException(" Proxy header v1 with unsuitable length : "+proxyHeader.length());//No I18N
				}

				version = "v1";
				String[] tokens = proxyHeader.split(" ");

				if(tokens.length == 2)
				{
					protocol = tokens[1];
				}
				else if(tokens.length == 6)
				{
					protocol = tokens[1];
					sourceAddress = tokens[2];
					destinationAddress = tokens[3];
					sourcePort = tokens[4];
					destinationPort = tokens[5];
				}
				else
				{
					logger.log(Level.SEVERE," Proxy header v1 with unsupported format "+proxyHeader,ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V1);
					throw new AWSException(" Proxy header v1 with unsupported format "+proxyHeader);//No I18N
				}

				if(!(protocol.equalsIgnoreCase("TCP4") || protocol.equalsIgnoreCase("TCP6") || protocol.equalsIgnoreCase("UNKNOWN")))
				{
					logger.log(Level.SEVERE," Proxy header v1 with unsupported protocol : "+protocol,ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V1);
					throw new AWSException(" Proxy header v1 with unsupported protocol : "+protocol);//No I18N
				}
			}

			return completed;

		}

		public boolean processV2(ByteBuffer bb) throws IOException, AWSException
		{

			if(completed)
			{
				return true;
			}

			int position = 12; //First 12 bits are like syntax for v2
			byte addressType = (byte)0x00;
			int addrLength = 0;	

			if(bb.position() >= 16)
			{
				
				byte version = (byte)(bb.get(position) & (byte)0xF0);
				
				if(version!=(byte)0x20)
				{
					logger.log(Level.SEVERE," Proxy header v2 with unsupported version : "+(version>>4),ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V2);
					throw new AWSException(" Proxy header v2 with unsupported version : "+(version>>4));//No I18N
				}
				
				byte proxy = (byte)(bb.get(position) & (byte)0x0F);
		
				if(!(proxy==(byte)0x00 || proxy==(byte)0x01)) //0x00 - LOCAL, 0x01 - normal
				{
					logger.log(Level.SEVERE,"Unsupported  Proxy header v2 type : "+proxy,ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V2);
					throw new AWSException("Unsupported  Proxy header v2 type : "+proxy);//No I18N
				}

				addressType = (byte)(bb.get(++position) & (byte)0xFF);	
				
				if(!(addressType==(byte)0x00 || addressType==(byte)0x11 || addressType==(byte)0x12 || addressType==(byte)0x21 ||  addressType==(byte)0x22 || addressType==(byte)0x31 || addressType==(byte)0x32))// 0x00 - UNSPEC, 0x11 - TCP over IPV4, 0x12 - UDP Over IPV4, 0x21 - TCP over IPV6, 0x22 - UDP over IPV6, 0x31 - UNIX stream, 0x32 - UNIX datagram
				{
					logger.log(Level.SEVERE,"Unsupported  Proxy header v2 address type : "+addressType,ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V2);
					throw new AWSException("Unsupported  Proxy header v2 address type : "+addressType);//No I18N
				}
			
				addrLength = ((bb.get(++position)&0xFF)<<8) | (bb.get(++position)&0xFF);
				length=addrLength+16;	

				if(bb.position() >= length)
				{
					completed=true;
				}
			}
			
			if(completed)
			{

				//Address length will be calculated in different sizes separately. Extra lengths will be skipped.
				if(addressType==(byte)0x00)
				{
					logger.addDebugLog(Level.FINE, AWSLogConstants.IGNORING_PROXY_HEADER_ADDRESS_V2_UNSPEC,ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V2);
				}
				else if(addressType==(byte)0x11 || addressType==(byte)0x12)
				{
					// TCP/UDP over IPV4
					if(addrLength >= 12)
					{
						position++;
						sourceAddress = getIPV4Address(getSubArray(bb, position, 4));
						position = position+4;
						destinationAddress = getIPV4Address(getSubArray(bb, position, 4));
						position = position+4;
						sourcePort = getPort(getSubArray(bb, position, 2));
						position = position+2;
						destinationPort = getPort(getSubArray(bb, position, 2));
					}
					else
					{
						logger.log(Level.SEVERE," Proxy header v2, Insufficent length for type TCP/UDP over IPv4 : "+addrLength,ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V2);
						throw new AWSException(" Proxy header v2, Insufficent length for type TCP/UDP over IPv4 : "+addrLength);//No I18N
					}
				}
				else if(addressType==(byte)0x21 || addressType==(byte)0x22)
				{
					// TCP/UDP over IPV6
					if(addrLength >= 36)
					{
						position++;
						sourceAddress = getIPV6Address(getSubArray(bb, position, 16));
						position = position+16;
						destinationAddress = getIPV6Address(getSubArray(bb, position, 16));
						position = position+16;
						sourcePort = getPort(getSubArray(bb, position, 2));
						position = position+2;
						destinationPort = getPort(getSubArray(bb, position, 2));
					}
					else
					{
						logger.log(Level.SEVERE," Proxy header v2, Insufficent length for type TCP/UDP over IPv6 : "+addrLength,ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V2);
						throw new AWSException(" Proxy header v2, Insufficent length for type TCP/UDP over IPv6 : "+addrLength);//No I18N
					}
				}
				else if(addressType==(byte)0x31 || addressType==(byte)0x32)
				{
					logger.log(Level.SEVERE,"SOCK Address in PROXY header. Not suitable for AWS. ",ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V2);
					throw new AWSException("SOCK Address in PROXY header. Not suitable for AWS. ");//No I18N 

					// SOCK_STREAM/SOCK DGRAM over AF_UNIX protocol family. Not suitable for AWS.
					/*if(addrLength >= 216)
					{
						position++;
						sourceAddress = new String(getSubArray(bb, position, 108));
						position = position+108;
						destinationAddress = new String(getSubArray(bb, position, 108));
					}
					else
					{
						logger.log(Level.SEVERE," Proxy header v2, Insufficent length for type UNIX stream/datagram : "+addrLength);
						throw new AWSException(" Proxy header v2, Insufficent length for type UNIX stream/datagram : "+addrLength);
					}*/
				}	
				else
				{
					logger.log(Level.SEVERE,"Unsupported proxy header V2 address type : "+addressType,ProxyHeader.class.getName(),AWSLogMethodConstants.PROCESS_V2);
					throw new AWSException("Unsupported proxy header V2 address type : "+addressType);	//No I18N
				}
			}

			return completed;
		}
		
		private byte[] getSubArray(ByteBuffer bb, int startIndex, int bytesToBeRead)
		{
			byte b[]= new byte[bytesToBeRead];
			
			for(int i=0; i<bytesToBeRead; i++)
			{
				b[i] = (byte)(bb.get(startIndex+i) & 0xFF);
			}
		
			return b;
		}
			

		public String getSourceIP()
		{
			return sourceAddress;
		}

		public String getSourcePort()
		{
			return sourcePort;
		}

		public String getDestinationIP()
		{
			return destinationAddress;
		}
		
		public String getDestinationPort()
		{
			return destinationPort;
		}
		
		public boolean isPresent(ByteBuffer bb)
		{
			if(isV1(bb) || isV2(bb))
			{
				return true;
			}
	
			return false;
		}

		public boolean isV1(ByteBuffer bb)
		{

			if(bb.position() < 8)
			{
				return false;
			}
			
			return bb.get(0)==(byte)0x50 && bb.get(1)==(byte)0x52 && bb.get(2)==(byte)0x4F && bb.get(3)==(byte)0x58 && bb.get(4)==(byte)0x59;
		}
		

		public boolean isV2(ByteBuffer bb)
		{
			if(bb.position() < 16)
			{
				return false;
			}	
			
			return bb.get(0)==(byte)0x0D && bb.get(1)==(byte)0x0A && bb.get(2)==(byte)0x0D && bb.get(3)==(byte)0x0A && bb.get(4)==(byte)0x00 && bb.get(5)==(byte)0x0D && bb.get(6)==(byte)0x0A && bb.get(7)==(byte)0x51 && bb.get(8)==(byte)0x55 && bb.get(9)==(byte)0x49 && bb.get(10)==(byte)0x54 && bb.get(11)==(byte)0x0A;
		}

		public boolean isComplete()
		{
			return completed;
		}

		public int getLength()
		{
			return length;
		}	

		private String getIPV4Address(byte[] buf)
		{
			if(buf.length != 4)
			{
				return null;
			}

			StringBuilder str = new StringBuilder();
			str.append(""+(buf[0]&0xFF));
			str.append("."+(buf[1]&0xFF));
			str.append("."+(buf[2]&0xFF));
			str.append("."+(buf[2] &0xFF));
			return str.toString();
		}

		private String getPort(byte[] buf)
		{
			if(buf.length != 2)
			{
				return null;
			}

			return ""+((buf[0]&0xFF)<<8 | (buf[1]&0xFF));
		}

		private String getIPV6Address(byte[] buf)
		{
			if(buf.length != 16)
			{
				return null;
			}

			StringBuilder str = new StringBuilder();

			for(int i=0; i<16; i=i+2)
			{
				str.append(""+binaryToHex(Arrays.copyOfRange(buf,i,i+2)));
				if(i!=14)
				{	
					str.append(":");
				}
			}

			return str.toString();
		}
		
		private String binaryToHex(byte[] buf)
		{
			StringBuilder sb = new StringBuilder();
			for(byte b : buf)
			{
				sb.append(String.format("%02x", b));//No I18N
			}
			return sb.toString();
		}
	}

	public boolean isNewConnection()
	{
		return isNewConn;
	}

	public long getTimeTakenForHandshake()
	{
		return this.timetakenforhandshake;
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

	public boolean isSupportedGetMethod(String reqType)
	{
		if(WebEngine.getEngineByPort(localport) != null && WebEngine.getEngineByPort(localport).getSupportedGetMethods() != null)
		{
			return WebEngine.getEngineByPort(localport).isSupportedGetMethod(reqType);
		}
		else
		{
			return ConfManager.isSupportedGetMethod(reqType);
		}
	}

	public boolean isSupportedPostMethods(String reqType)
	{
		if(WebEngine.getEngineByPort(localport) != null && WebEngine.getEngineByPort(localport).getSupportedPostMethods() != null)
		{
			return WebEngine.getEngineByPort(localport).isSupportedPostMethod(reqType);
		}
		else
		{
			return ConfManager.isSupportedPostMethod(reqType);
		}
	}

	public boolean isSupportedRequestType(String reqType)
	{
		AbstractWebEngine engine = WebEngine.getEngineByPort(localport);

		if(engine == null)
		{
			return ConfManager.isSupportedRequestType(reqType);
		}
		else if((engine.getSupportedGetMethods() == null || (engine.getSupportedGetMethods() != null && !engine.isSupportedGetMethod(reqType))) && (engine.getSupportedPostMethods() == null || (engine.getSupportedPostMethods() != null && !engine.isSupportedPostMethod(reqType))))
                {
                        return ConfManager.isSupportedRequestType(reqType);
                }
                else
                {
                        return engine.isSupportedRequestType(reqType);
                }
	}

	public void setEndAccessLogs(ZLMap zlmap)
	{
		endaccesslogszlmap = zlmap;
	}

	public boolean isDataflowEnabled()
	{
		return dataflow;
	}

	public void setSecurityFilterStatus(boolean status)
	{
		this.securityfilter = status;
	}

	public boolean isSecurityFilterDisabled()
	{
		return this.securityfilter;
	}
}
