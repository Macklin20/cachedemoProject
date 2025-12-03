package com.zoho.wms.asyncweb.server.http2;

//Java import
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

// servercommon / common imports
import com.adventnet.wms.common.exception.WMSException;

//aws import
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.DOSManager;
import com.zoho.wms.asyncweb.server.DOSMonitor;
import com.zoho.wms.asyncweb.server.AsyncWebClient;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.AsyncRequestProcessor;
import com.zoho.wms.asyncweb.server.ClientManager;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.exception.Http2Exception;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpStream;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.util.StateConstants;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;

/**
 *
 *  @author durai - 11882
 *
 */

public class Http2Connection
{
	private static AsyncLogger logger = new AsyncLogger(Http2Connection.class.getName());

	private String clientId;
	private String http2ConnID;
	private Http2Stream connectionStream;
	private ConcurrentHashMap<Integer, Http2Stream> clientStreamMap = new ConcurrentHashMap(10);
	private int lastStreamID = 0;
	private IndexTable idx = null;

	private ByteBuffer readDataBuffer = ByteBuffer.allocate(1024); //revisit 5 - initial limit and max limit of this bytebuffer
	private Object http2ReadDataLock = new Object();

	private ConcurrentLinkedQueue<byte[]> unProcessedFramesQueue = new ConcurrentLinkedQueue<byte[]>();
	private boolean frameProcessing = false;

	private ArrayList<Integer> waitingStreamList = new ArrayList<>();
	private Object connectionWindowLock = new Object();

	private boolean writeInitiated = false;
	private boolean goAwayFrameSent = false;
	private boolean connectionClosed = false;

	private AtomicLong socketReadCount = new AtomicLong();
	private AtomicLong socketWriteCount = new AtomicLong();
	private AtomicLong socketReadTime = new AtomicLong();
	private AtomicLong socketWriteTime = new AtomicLong();

	private AtomicInteger streamCount = new AtomicInteger();
	private AtomicInteger clientResetFrameCounter = new AtomicInteger();
	private AtomicInteger streamErrorCount = new AtomicInteger();

	public Http2Connection(String http2ConnID, String clientId) throws Exception
	{
		try
		{
			if(ConfManager.isHttp2LogsEnabled())
			{
				logger.log(Level.INFO, Http2Util.getCharSeq(20, "Http2\n")+"[Http2 Connection-new-h2ID:"+http2ConnID+"-streamID:0]");
			}

			this.http2ConnID = http2ConnID;
			this.clientId = clientId;

			idx = new IndexTable();
			connectionStream = new Http2Stream(http2ConnID, Http2Constants.CONNECTION_STREAM_IDENTIFIER, idx, getIPAddress(), getLocalPort(), getSocketPort());
			sendConnectionPreface();

			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2CONN, "h2con_new");
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Connection-new-h2ID:"+http2ConnID+"-streamID:0] - Error while initializing Http2Connection", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2CONN);
			closeConnection();
			throw ex;
		}
	}

	public Http2Stream registerStream(int streamID) throws Http2Exception, WMSException, Exception
	{
		if(clientStreamMap.containsKey(streamID))
		{
			return clientStreamMap.get(streamID);
		}
		else
		{
			if(clientStreamMap.size() > ConfManager.getMaxAllowedConcurrentClientStream())
			{
				logger.log(Level.SEVERE, "[Http2 Connection-registerStream-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Maximum Allowed Concurrent Client Stream Reached - clientStreamMapSize:"+clientStreamMap.size()+" MAX ALLOWED:"+ConfManager.getMaxAllowedConcurrentClientStream());
				sendGoAwayFrame(Http2Constants.PROTOCOL_ERROR, "Max allowed Concurrent Client Streams reached, New Connection should be Initiated");
				closeConnection();
				return null;
			}

			Http2Stream newStream = new Http2Stream(http2ConnID, streamID, idx, getRawIPAddress(), getLocalPort(), getSocketPort());
			newStream.setClientWindowSize(getInitialStreamLevelClientWindowSize());
			newStream.updateLastAccessTime(ConfManager.getStreamTimeout(), false);
			clientStreamMap.put(streamID, newStream);
			streamCount.incrementAndGet();

			if(streamID > 1)
			{
				AWSInfluxStats.addHttpConnectionReuseStat(Http2Constants.HTTP2_VERSION, getScheme());
			}
			if(streamID >= ConfManager.getMaxAllowedClientStream() * 2) // multiplied by 2 since only odd numbers are client initiated streams
			{
				logger.log(Level.SEVERE, "[Http2 Connection-registerStream-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Maximum Allowed Client Stream Reached - clientStreamMapSize:"+clientStreamMap.size()+" - Current Stream ID:"+streamID+" - MAX ALLOWED:"+ConfManager.getMaxAllowedClientStream());
				sendGoAwayFrame(Http2Constants.PROTOCOL_ERROR, "Max allowed Client Streams reached, New Connection should be Initiated");
				closeConnection();
				return null;
			}

			return newStream;
		}
	}

	public Http2Stream getStream(int streamID)
	{
		if(streamID == Http2Constants.CONNECTION_STREAM_IDENTIFIER)
		{
			return getConnectionStream();
		}
		return clientStreamMap.get(streamID);
	}

	public Http2Stream removeStream(int streamID)
	{
		return clientStreamMap.remove(streamID);
	}

	public void addHttp2Data(byte[] data) throws Exception
	{
		if( ! ConfManager.isHttp2Enabled())
		{
			closeConnection();
		}

		if(connectionClosed)
		{
			throw new Exception("ReadData called for closed Http2Connection");
		}

		try
		{
			if(data == null || goAwayFrameSent)
			{
				return;
			}

			synchronized (http2ReadDataLock)
			{
				if(readDataBuffer.remaining() < data.length) // To check buffer has available space for new data, else resizing
				{
					int newcapacity = -1;
					if(((readDataBuffer.position() + data.length) < readDataBuffer.capacity()*2) && ((readDataBuffer.position() + data.length) <= ConfManager.getHttp2ReadDataBufferLimit()))
					{
						newcapacity = readDataBuffer.capacity() * 2;
					}
					else
					{
						newcapacity = readDataBuffer.position() + data.length;
					}
					if(newcapacity > ConfManager.getHttp2ReadDataBufferLimit())
					{
						throw new AWSException("Http2 readData Buffer exhaust: newcapacity:"+newcapacity+" - limit:"+ConfManager.getHttp2ReadDataBufferLimit());//No I18N
					}

					ByteBuffer newBB = ByteBuffer.allocate(newcapacity);
					readDataBuffer.flip();
					newBB.put(readDataBuffer);
					readDataBuffer = newBB;
				}

				readDataBuffer.put(data);
				readDataBuffer.flip();

				while (readDataBuffer.remaining() >= Http2Constants.HEADER_SIZE)
				{
					readDataBuffer.mark();
					int frameLength = Http2Constants.HEADER_SIZE + Http2Util.getThreeBytes(readDataBuffer);
					readDataBuffer.reset();

					if (readDataBuffer.remaining() >= frameLength) // framelength = HEADER_SIZE (9) + payloadLength
					{
						byte[] frameData = new byte[frameLength];
						readDataBuffer.get(frameData);
						unProcessedFramesQueue.add(frameData);
					}
					else
					{
						break;
					}
				}
				readDataBuffer.compact();
			}

			if(unProcessedFramesQueue.size() > 0 && !frameProcessing)
			{
				Http2FrameProcessor.process(http2ConnID);
			}
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Connection-addHttp2Data-h2ID:"+http2ConnID+"-streamID:0] - Error while splitting http2 frames", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2CONN_ADDHTTP2DATA);
			throw ex;
		}
	}

	public void notifyProcessFrame()
	{
		if(frameProcessing || unProcessedFramesQueue.isEmpty())
		{
			return;
		}

		frameProcessing = true;
		try
		{
			while( unProcessedFramesQueue != null && !unProcessedFramesQueue.isEmpty())
			{
				processFrame(unProcessedFramesQueue.remove());
			}
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Connection-notifyProcessFrame-h2ID:"+http2ConnID+"-streamID:0] - Error while processing frame", ex);
		}
		finally
		{
			frameProcessing = false;
		}
	}

	private void processFrame(byte[] b) throws Exception
	{
		ByteArrayInputStream bais = new ByteArrayInputStream(b);

		int payloadLength = Http2Util.getThreeBytes(bais);
		int type = bais.read();
		byte flag = (byte) bais.read();
		int streamID = Http2Util.get31Bits(bais);

		try
		{
			//log and stat for frame traffic
			Http2Util.logHttp2Frame(b, false, http2ConnID);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2FRAME, "read", Http2Util.getFrameType(type));

			if(streamID < 0)
			{
				throw new Http2Exception("CONNECTION ERROR : Negative Stream ID - streamID:"+streamID);
			}
			else if(streamID == 0)
			{
				Http2Stream stream = getConnectionStream();

				if(stream.getStreamState() == Http2Constants.IDLE)
				{
					switch(type)
					{
						case Http2Constants.SETTINGS_FRAME:
						{
							HashMap<Integer, Integer> setttingsMap = stream.processSettingsFrame(payloadLength, flag, streamID, bais);
							if( ! Http2Util.isAck(flag))
							{
								sendSettingsAck();
								if(setttingsMap!=null && setttingsMap.containsKey(Http2Constants.SETTINGS_HEADER_TABLE_SIZE)) //revisit 2
								{
									writeFrame(Http2Constants.CONNECTION_STREAM_IDENTIFIER, Http2Constants.SETTINGS_FRAME, connectionStream.getSettingsFrame(Http2Constants.SETTINGS_HEADER_TABLE_SIZE, ConfManager.getDynamicTableSize()));
								}
							}
							break;
						}
						case Http2Constants.PING_FRAME:
						{
							stream.processPingFrame(payloadLength, flag, streamID, bais);
							if( ! Http2Util.isAck(flag))
							{
								sendPingAck();
							}
							break;
						}
						case Http2Constants.GOAWAY_FRAME:
						{
							stream.processGoAwayFrame(payloadLength, flag, streamID, bais);
							closeConnection();
							break;
						}
						case Http2Constants.WINDOW_UPDATE_FRAME:
						{
							stream.processWindowUpdateFrame(payloadLength, flag, streamID, bais);
							notifyWaitingStreams();
							break;
						}
						default:
						{
							throw new Http2Exception("Invalid Frame Type : "+type+" streamID : "+streamID);//No I18N
						}
					}
				}
				else
				{
					throw new Http2Exception("Connection stream polluted - streamID:"+streamID+" - state:"+stream.getStreamState());//No I18N
				}
			}
			else
			{
				Http2Stream stream = getStream(streamID);
				if(stream == null)
				{
					if(type == Http2Constants.HEADER_FRAME)
					{
						stream = registerStream(streamID);
						updateLastStreamID(streamID);
					}
					else if(type == Http2Constants.PRIORITY_FRAME)  // priority frame deprecated as per rfc 9113
					{
						return;
					}
					else
					{
						if(streamID > lastStreamID)
						{
							throw new Http2Exception("Connection Error - unexpected frame for IDLE stream - streamID:"+streamID+" - type:"+Http2Util.getFrameType(type)); // Only Header or Priority Frame should be sent on a IDLE stream
						}
						else
						{
							logger.log(Level.INFO, "[Http2 Connection-processFrame-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Stream unavailable - received frame type:"+Http2Util.getFrameType(type));
							return;
						}
					}
				}

				if(stream == null)
				{
					return;
				}

				switch(type)
				{
					case Http2Constants.HEADER_FRAME:
					{
						stream.processHeaderFrame(payloadLength, flag, streamID, bais);
						notifyProcessor(streamID);
						break;
					}
					case Http2Constants.CONTINUATION_FRAME:
					{
						if(validateContinuationFrameLimit(streamID))
						{
							stream.processContinuationFrame(payloadLength, flag, streamID, bais);
							notifyProcessor(streamID);
						}
						break;
					}
					case Http2Constants.DATA_FRAME:
					{
						stream.processDataFrame(payloadLength, flag, streamID, bais);

						if(payloadLength > 0)
						{
							stream.reduceServerWindowSize(payloadLength);
							connectionStream.reduceServerWindowSize(payloadLength);

							sendWindowUpdatesToClient(Http2Constants.CONNECTION_STREAM_IDENTIFIER, payloadLength);
							sendWindowUpdatesToClient(streamID, payloadLength);
						}
						notifyProcessor(streamID);
						break;
					}
					case Http2Constants.WINDOW_UPDATE_FRAME:
					{
						stream.processWindowUpdateFrame(payloadLength, flag, streamID, bais);
						notifyWindowUpdate(streamID);
						break;
					}
					case Http2Constants.RESET_STREAM_FRAME:
					{
						if(isResetFrameOverloaded(streamID))
						{
							return;
						}
						clientResetFrameCounter.incrementAndGet();
						stream.processResetStreamFrame(payloadLength, flag, streamID, bais);
						stream.close();
						break;
					}
//					case Http2Constants.PUSH_PROMISE_FRAME: //deprecated
//					{
//						stream.processPushPromiseFrame(payloadLength, flag, streamID, payload);
//						break;
//					}
					case Http2Constants.PRIORITY_FRAME:
					{
//						stream.processPriorityFrame(payloadLength, flag, streamID, payload); // priority frame deprecated as per rfc 9113
						break;
					}
					default:
					{
						throw new Http2Exception("Invalid Frame Type : "+type+" streamID : "+streamID);//No I18N
					}
				}

				stream.updateLastAccessTime(ConfManager.getStreamTimeout(), true);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Connection-processFrame] - Error while processing frame - streamID:"+streamID+" - "+Http2Util.getFrameType(type)+" - payloadLength:"+payloadLength+"  flag:"+flag, ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2CONN_PROCESSFRAME);
		}
	}

	public boolean isResetFrameOverloaded(int streamID)
	{
		if((getStreamCount()  > ConfManager.getResetFrameLimit()) && ((getClientResetFrameCount()/getStreamCount())*100) > ConfManager.getResetFrameLimitPercent())
		{
			logger.log(Level.INFO, "[Http2  Connection-isResetFrameOverloaded-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Reset Frame exceeded the limit in the current Http2Connection [StreamCount: "+getStreamCount()+"  ResetFrame Count :"+getClientResetFrameCount()+"]");
			sendGoAwayFrame(Http2Constants.ENHANCE_YOUR_CALM,"Reset Frame overload");
			closeConnection();
			return true;
		}
		return false;
	}

	public boolean validateContinuationFrameLimit(int streamID) throws Exception
	{
		Http2Stream stream = getStream(streamID);
		if(stream.getContinuationFrameCounter() >= ConfManager.getContinuationFrameLimit())
		{
			logger.log(Level.INFO, "[Http2  Connection-processFrame-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Continuation frame limit have exceeded the limit in the particular stream");
			sendStreamResetFrame(streamID,stream.getResetStreamFrame(Http2Constants.ENHANCE_YOUR_CALM));
			handleStreamErrorCount();
			return false;
		}
		return true;
	}


	public void handleStreamErrorCount()
	{
		streamErrorCount.incrementAndGet();
		if(streamErrorCount.get() >= ConfManager.getStreamErrorLimit())
		{
			logger.log(Level.SEVERE, "[Http2  Connection-handleStreamErrorCount-h2ID:"+http2ConnID+"-streamID:0] - Error count occured in multiple streams has reached more than the given limit");
			sendGoAwayFrame(Http2Constants.ENHANCE_YOUR_CALM,"Error count occured in multiple streams has reached more than the given limit");
			closeConnection();
		}
	}

	public void notifyProcessor(int streamID) throws IOException
	{
		Http2Stream stream = getStream(streamID);

		if(stream.isPostReqStreamModeEnabled())
		{
			HttpStream postReq_HttpStream = stream.getPostReqHttpStream();

			if(ConfManager.isHeaderCompletionNeeded() && !stream.isOnHeaderCompletionCallBackSent() && stream.isHeaderProcessingCompleted())
			{
//				sendWindowUpdatesToClient(streamID, (1024*1024*1)); // revisit 1- increasing the server Window Size only for stream servlet case
				dispatchNewRequest(streamID, StateConstants.ON_HEADER_COMPLETION);
				stream.setOnHeaderCompletionCallBackSent(true);
			}
			
			if(!stream.isOnCompletionCallbackSent() && stream.isHeaderProcessingCompleted())
			{
				dispatchNewRequest(streamID, StateConstants.ON_COMPLETION);
				stream.setOnCompletionCallBackSent(true);	
			}

			if(postReq_HttpStream.isPaused())
			{
				return;
			}

			if(postReq_HttpStream.isAvailable())
			{
				postReq_HttpStream.pause();
				dispatchNewRequest(streamID, StateConstants.ON_DATA);
			}
		}
		else if(stream.isReqComplete())
		{
			dispatchNewRequest(streamID, StateConstants.ON_COMPLETION);
		}
	}

	public void dispatchNewRequest(int streamID, int ackstate)
	{
		updateLastStreamID(streamID);
		Http2Stream stream = getStream(streamID);
		try
		{
			HttpRequest req = stream.getHttpRequest(ackstate);
			Http2Response res = stream.getHttpResponse(http2ConnID, req.getRemoteAddr());

			addDosEntry(req);

			String reqType = req.getRequestType();
			if(!reqType.trim().equalsIgnoreCase(AWSConstants.GET_REQ) && !reqType.trim().equalsIgnoreCase(AWSConstants.POST_REQ) && !reqType.trim().equalsIgnoreCase(AWSConstants.OPTIONS_REQ) && !ConfManager.isSupportedRequestType(reqType))
			{
				new AsyncLogger(AWSConstants.HACKLOG).log(Level.INFO,"NOT A GET/POST/POST-STREAM SERVLET/OPTIONS REQUEST --------- REQUEST TYPE = "+reqType+" , URL "+req.getRequestURL()+" , FROM "+req.getRemoteAddr());//No I18N

				res.sendError(HttpResponseCode.METHOD_NOT_ALLOWED, "Method Not Allowed");
				res.close();
				return;
			}

			req.setStatsRequestUrl(stream.getStatsRequestURL());
			res.setReqId(stream.getReqId());
			res.setTimeLine(stream.getTimeLine());
			res.setStreamMode(stream.isPostReqStreamModeEnabled());
			res.setFirstReadTime(stream.getFirstReadTime());
			stream.removeTimeLine();
			stream.setReqQueueInsertTime(System.currentTimeMillis());
			stream.updateReqProcTime(0);
			getClient().updateExternalHit(req.getHost());

			if(ConfManager.isHttp2LogsEnabled())
			{
				logger.log(Level.INFO, "[Http2 Connection - dispatchNewRequest] - streamID:"+streamID+" - url:"+req.getRequestURL()+" - reqType:"+reqType+"- ackstate:"+Http2Util.getAckStateType(ackstate)+" - webengine:"+req.getEngineName()+" - localport:"+req.getLocalPort()+"   isSSL:"+req.isSSLDefault()+" isPlain:"+req.isPlain() + "  client:"+getClient().toString() + " http2ConnID:"+http2ConnID);
			}

			AsyncRequestProcessor.dispatchHttp2Request(req.getEngineName(), req, res);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "HTTP2Error --> Exception - Unable to create/dispatch Http2 Request/Response - streamID="+streamID+" - URL:"+stream.getRequestURL(), ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2CONN_DISPATCHNEWREQUEST);
			closeConnection();
		}
	}

	private void addToWaitingStreamList(int streamID)
	{
		synchronized (connectionWindowLock)
		{
			if(!waitingStreamList.contains(streamID))
			{
				waitingStreamList.add(streamID);
			}
		}
	}

	private void notifyWaitingStreams() throws Exception
	{
		ArrayList<Integer> tempList = null;
		synchronized (connectionWindowLock)
		{
			tempList = waitingStreamList;
			waitingStreamList = new ArrayList<>();
		}

		while (!tempList.isEmpty())
		{
			writeResponseDataFrames(tempList.remove(0));
		}
	}

	private void notifyWindowUpdate(int streamID) throws Exception
	{
		Http2Stream stream = getStream(streamID);

		if( ! stream.isResponseDataQueueEmpty())
		{
			writeResponseDataFrames(streamID);
		}
	}

	public void writeResponseDataFrames(int streamID) throws Exception
	{
		writeResponseDataFrame(streamID, null);
	}

	public synchronized void writeResponseDataFrame(int streamID, ByteBuffer dataFrame) throws Exception
	{
		Http2Stream stream = getStream(streamID);

		if(stream == null)
		{
			throw new Http2Exception("Http2Stream null - streamID:"+streamID);
		}
		if(stream.isClosed())
		{
			throw new Http2Exception("Http2Stream closed - streamID:"+streamID);
		}

		if(dataFrame != null)
		{
			stream.addToResponseDataQueue(dataFrame);
		}

		while(!stream.isClosed() && !stream.isResponseDataQueueEmpty())
		{
			int payloadSize = stream.getDataFrameResponseQueue().limit() - Http2Constants.HEADER_SIZE;
			if(stream.getClientWindowSize() >= payloadSize)
			{
				if(connectionStream.getClientWindowSize() >= payloadSize)
				{
					writeFrame(streamID, Http2Constants.DATA_FRAME, stream.removeDataFrameResponseQueue());
				}
				else
				{
					addToWaitingStreamList(streamID);
					break;
				}
			}
			else
			{
				break;
			}
		}
		
		if(isDataQueueDrained(streamID))
		{
			int state = stream.getRequestState();
			if(stream.isDataFlowNotificationEnabled() && (!stream.isCloseAfterWrite() || !stream.isEndNotified())) //If dataflow is set and end is not yet notified.
			{
				if(state == StateConstants.ON_OUTPUTBUFFERREFILL)
				{
					dispatchNewRequest(streamID, StateConstants.ON_OUTPUTBUFFERREFILL);
				}
				else if(state == StateConstants.ON_WRITECOMPLETE )
				{
					stream.setEndNotified();
					dispatchNewRequest(streamID, StateConstants.ON_WRITECOMPLETE);
				}
				else if(state == StateConstants.ON_WRITEFAILURE)
				{
					stream.setEndNotified();
					dispatchNewRequest(streamID, StateConstants.ON_WRITEFAILURE);
				}
			}
			
			if(!stream.isClosed() && stream.isCloseAfterWrite())//If stream is not closed and response.close() is called.
			{
				writeFrame(streamID, Http2Constants.DATA_FRAME, stream.getDataFrame(Http2Constants.END_STREAM_FLAG));
			}
		}
	}

	public void writeWindowUpdateFrame(int streamID, int windowSize) throws Exception
	{
		Http2Stream stream = getStream(streamID);
		stream.increaseServerWindowSize(windowSize);
		writeFrame(streamID, Http2Constants.WINDOW_UPDATE_FRAME, stream.getWindowUpdateFrame(windowSize));
	}

	public void writeFrame(int streamID, int frameType, ByteBuffer frameData) throws Exception
	{
		if (connectionClosed)
		{
			throw new Http2Exception("Http2 Connection closed");
		}

		if(frameData == null)
		{
			throw new Http2Exception("FrameData is null");
		}

		validateFrame(streamID, frameType);

		if(streamID > 0)
		{
			Http2Stream stream = getStream(streamID);
			if(stream == null || stream.isClosed())
			{
				if (ConfManager.isHttp2LogsEnabled())
				{
					logger.log(Level.WARNING, "[Http2Error - stream null/closed]["+(stream==null)+"] - type:"+frameType+" - streamID:"+streamID);
				}
				throw new Http2Exception("Stream Null streamID:"+streamID);
			}

			switch(frameType)
			{
				case Http2Constants.DATA_FRAME:
				{
					int payloadSize = frameData.limit() - Http2Constants.HEADER_SIZE;
					stream.addWrittenLength(payloadSize);
					if(payloadSize > 0)
					{
						reduceClientWindowSize(streamID, payloadSize);
					}
					break;
				}
				case Http2Constants.HEADER_FRAME:
                                {
                                        int payloadSize = frameData.limit();
                                        stream.addWrittenLength(payloadSize);
                                }
			}

			stream.updateLastAccessTime(ConfManager.getStreamTimeout(), true);
			stream.updateLastWriteTime();
			if(stream.getStreamState() == Http2Constants.CLOSED)
			{
				stream.close();
			}
		}

		//log and stat for frame traffic
		Http2Util.logHttp2Frame(frameData.duplicate(), true, http2ConnID);
		AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2FRAME, "write", Http2Util.getFrameType(frameType));

		writeData(frameData);
	}

	public void writeData(ByteBuffer bb)
	{
		try
		{
			AsyncWebClient client = getClient();
			if(client == null)
			{
				logger.log(Level.SEVERE, "Http2 Stream - writeData - Client Null");
				throw new AWSException("Client Null");
			}

			bb.flip();
			if(writeInitiated)
			{
				client.setOutputDataSize(bb.limit());
			}
			else
			{
				writeInitiated = true;
			}
			client.writeData(bb);
			closeConnectionIfNeeded();
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "Http2Exception - Http2Stream-writeData - Unable to write Data to Client", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2CONN_WRITEDATA);
			closeConnection();
		}
	}

	private void sendConnectionPreface() throws Exception
	{
		HashMap<Integer, Integer> settingsMap = new HashMap<>();
		settingsMap.put(Http2Constants.SETTINGS_MAX_CONCURRENT_STREAMS, ConfManager.getMaxAllowedConcurrentClientStream());
		settingsMap.put(Http2Constants.SETTINGS_INITIAL_WINDOW_SIZE, ConfManager.getInitialStreamLevelServerWindowSize());
		settingsMap.put(Http2Constants.SETTINGS_MAX_FRAME_SIZE, ConfManager.getRequestFrameMaxSize());
		settingsMap.put(Http2Constants.SETTINGS_HEADER_TABLE_SIZE, ConfManager.getDynamicTableSize());
		writeFrame(Http2Constants.CONNECTION_STREAM_IDENTIFIER, Http2Constants.SETTINGS_FRAME, connectionStream.getSettingsFrame(settingsMap));
		connectionStream.setServerPrefaceSent(true);

		writeWindowUpdateFrame(Http2Constants.CONNECTION_STREAM_IDENTIFIER, (ConfManager.getInitialStreamLevelServerWindowSize() * 5) - Http2Constants.DEFAULT_WINDOW_SIZE);
	}

	private void sendSettingsAck() throws Exception
	{
		writeFrame(Http2Constants.CONNECTION_STREAM_IDENTIFIER, Http2Constants.SETTINGS_FRAME, connectionStream.getAckSettingsFrame());
	}

	private void sendPingAck() throws Exception
	{
		writeFrame(Http2Constants.CONNECTION_STREAM_IDENTIFIER, Http2Constants.PING_FRAME, connectionStream.getPingAckFrame());
	}

	private void sendWindowUpdatesToClient(int streamID, int size) throws Exception
	{
		Http2Stream stream = getStream(streamID);
		int streamWindowUpdatePending = stream.getPendingWindowUpdate() + size;
		if(streamWindowUpdatePending >= (stream.getServerWindowSize() / 2))
		{
			writeWindowUpdateFrame(streamID, streamWindowUpdatePending);
			stream.setPendingWindowUpdate(0);
		}
		else
		{
			stream.setPendingWindowUpdate(streamWindowUpdatePending);
		}
	}

	public void sendGoAwayFrame()
	{
		sendGoAwayFrame(Http2Constants.NO_ERROR, "");
	}

	public void sendGoAwayFrame(byte error, String debugData)
	{
		try
		{
			goAwayFrameSent = true;
			writeFrame(Http2Constants.CONNECTION_STREAM_IDENTIFIER, Http2Constants.GOAWAY_FRAME, connectionStream.getGoAwayFrame(lastStreamID, error, debugData));
		}
		catch (Exception ex)
		{
			goAwayFrameSent = false;
			logger.log(Level.SEVERE, "[Exception - Http2 Connection-sendGoAwayFrame-h2ID:"+http2ConnID+"-streamID:0] - Error while sending goAwayFrame", ex);
		}
	}

	public void sendStreamResetFrame(int streamID, ByteBuffer frameData)
	{
		try
		{
			writeFrame(streamID, Http2Constants.RESET_STREAM_FRAME, frameData);
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 sendStreamResetFrame-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Error while sending resetStreamFrame", ex);
		}
	}

	private void updateLastStreamID(int streamID)
	{
		if(streamID > lastStreamID)
		{
			lastStreamID = streamID;
		}
	}

	public void invokeNotification(int streamID, int notification)
	{
		try
		{
			notifyProcessor(streamID);
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2Connection - invokeNotification] - Error while invoking servlet dispatcher", ex);
		}
	}

	public void reduceClientWindowSize(int streamID, int size) throws Http2Exception
	{
		getStream(streamID).reduceClientWindowSize(size);
		connectionStream.reduceClientWindowSize(size);
	}

	public void setClientPrefaceReceived(boolean value)
	{
		connectionStream.setClientPrefaceReceived(value);
	}

	public void validateFrame(int streamID, int frameType) throws Http2Exception
	{
		if(streamID < 0)
		{
			throw new Http2Exception("Invalid Stream ID:"+streamID);
		}
		else if(streamID == 0)
		{
			if(frameType != Http2Constants.SETTINGS_FRAME && frameType != Http2Constants.PING_FRAME && frameType != Http2Constants.GOAWAY_FRAME && frameType != Http2Constants.WINDOW_UPDATE_FRAME)
			{
				throw new Http2Exception("Invalid Frame for Connection Stream streamID:"+streamID+" - frameType:"+Http2Util.getFrameType(frameType));
			}
		}
		else
		{
			if(frameType != Http2Constants.HEADER_FRAME && frameType != Http2Constants.CONTINUATION_FRAME && frameType != Http2Constants.DATA_FRAME && frameType != Http2Constants.WINDOW_UPDATE_FRAME && frameType != Http2Constants.RESET_STREAM_FRAME && frameType != Http2Constants.PUSH_PROMISE_FRAME && frameType != Http2Constants.PRIORITY_FRAME)
			{
				throw new Http2Exception("Invalid Frame for Request Stream streamID:"+streamID+" - frameType:"+Http2Util.getFrameType(frameType));
			}
		}
	}

	public void addDosEntry(HttpRequest req) throws AWSException
	{
		if(!ConfManager.isDOSEnabled())
		{
			return;
		}

		DOSMonitor doss = DOSManager.getDOSByEngineName(req.getEngineName());
		if(doss!=null && req.getState() == StateConstants.ON_COMPLETION)
		{
			if(doss.isIPBlocked(req.getRemoteAddr()))
			{
				new AsyncLogger("doslogger").finer("Ignoring request from blocked ip "+req.getRemoteAddr(), AsyncRequestProcessor.class.getName(),AWSLogMethodConstants.HANDLE_HTTP_DOMAIN_DISPATCHER);//No I18n
				throw new AWSException("DOS Manager - Ignoring request from blocked ip "+req.getRemoteAddr());
			}
			else
			{
				if(doss.isSuspectedIP(req.getRemoteAddr()))
				{
					doss.doURLHit(req.getRemoteAddr(), req.getRequestURL());
				}
			}
		}
	}


	public boolean isClientPrefaceReceived()
	{
		return connectionStream.isClientPrefaceReceived();
	}

	private Http2Stream getConnectionStream()
	{
		return connectionStream;
	}

	public int getResponseFrameMaxSize()
	{
		return connectionStream.getResponseFrameMaxSize();
	}

	public int getInitialStreamLevelClientWindowSize()
	{
		return connectionStream.getInitialStreamLevelClientWindowSize();
	}



	public String getStatsRequestURL(int streamID)
	{
		return getStream(streamID).getStatsRequestURL();
	}

	public String getRequestURL(int streamID)
	{
		return getStream(streamID).getRequestURL();
	}

	public String getRequestType(int streamID)
	{
		return getStream(streamID).getRequestType();
	}

	public boolean isStreamModeEnabled(int streamID)
	{
		return getStream(streamID).isPostReqStreamModeEnabled();
	}

	public String getWebEngineName(int streamID)
	{
		return getStream(streamID).getWebEngineName();
	}

	public String getScheme()
	{
		return getClient().getScheme();
	}

	public void setInputDataSize(int streamID, long size) throws IOException
	{
		Http2Stream stream = getStream(streamID);
		stream.setInputDataSize(size);

		stream.getPostReqHttpStream().resume();
		if(stream.getPostReqHttpStream().isAvailable())
		{
			notifyProcessor(streamID);
		}
	}

	public boolean isStreamClosed(int streamID)
	{
		Http2Stream stream = getStream(streamID);
		if(stream != null)
		{
			return stream.isClosed();
		}
		return true;
	}



	public boolean isSSL()
	{
		return getClient().isSSL();
	}

	public String getIPAddress()
	{
		return getClient().getIPAddress();
	}

	public String getRawIPAddress()
	{
		return getClient().getRawIPAddress();
	}

	public int getLocalPort()
	{
		return getClient().getLocalPort();
	}

	public int getSocketPort()
	{
		return getClient().getSocketPort();
	}

	public long getActiveStreamCount()
	{
		return clientStreamMap.size();
	}

	public long getUnProcessedFramesQueueSize()
	{
		return unProcessedFramesQueue.size();
	}

	public long getWaitingStreamListSize()
	{
		return waitingStreamList.size();
	}

	public String getClientId()
	{
		return clientId;
	}

	public AsyncWebClient getClient()
	{
		return ClientManager.getClient(clientId);
	}

	public void closeConnectionIfNeeded()
	{
		if(goAwayFrameSent && getActiveStreamCount() == 0)
		{
			closeConnection();
		}
	}

	public void closeConnection()
	{
		closeConnection(false);
	}

	public void closeConnection(boolean clientClosed)
	{
		try
		{
			if(connectionClosed)
			{
				return;
			}

			AsyncWebClient client = getClient();
			if(client != null && !goAwayFrameSent)
			{
				sendGoAwayFrame();
			}
			connectionClosed = true;

			if(!clientClosed && client != null)
			{
				if(client.getSelectionKey().isValid() && client.isWritePending())
				{
					client.handleWrite(client.getSelectionKey());
				}
				client.close(AWSConstants.CLOSE_HTTP2CONNECTION);
			}

			if(idx != null)
			{
				idx.close();
			}

			if(connectionStream != null)
			{
				connectionStream.close();
			}

			if(clientStreamMap != null)
			{
				Enumeration<Integer> enu = clientStreamMap.keys();
				while(enu.hasMoreElements())
				{
					getStream(enu.nextElement()).close();
				}
			}

			idx = null;
			connectionStream = null;
			clientStreamMap = null;
			streamErrorCount = null;
			streamCount = null;
			clientResetFrameCounter = null;
			socketReadCount = null;
			socketWriteCount = null;
			socketReadTime = null;
			socketWriteTime = null;
			unProcessedFramesQueue = null;
			waitingStreamList = null;
			connectionWindowLock = null;
			readDataBuffer = null;
			http2ReadDataLock = null;

			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2CONN, "h2con_close");
			if(ConfManager.isHttp2LogsEnabled())
			{
				logger.log(Level.INFO, "[Http2 Connection-close-h2ID:"+http2ConnID+"-streamID:0]", new Exception("HTTP2CONNECTION CLOSE CALLED - h2ID:"+http2ConnID));
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2 Connection-close-h2ID:"+http2ConnID+"-streamID:0]", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2CONN_CLOSECONNECTION);
		}
	}

	public void setSocketTimeTakenStats(long readtime, long writetime, int readCount, int writeCount)
	{
		if(readCount > 0)
		{
			socketReadTime.addAndGet(readtime);
			socketReadCount.addAndGet(readCount);
		}
		if(writeCount > 0)
		{
			socketWriteTime.addAndGet(writetime);
			socketWriteCount.addAndGet(writeCount);
		}
	}

	public long getAvgSocketReadTime()
	{
		try
		{
			return (socketReadTime.longValue()/socketReadCount.longValue());
		}
		catch(ArithmeticException e)
		{
			return 0;
		}
	}

	public long getAvgSocketWriteTime()
	{
		try
		{
			return (socketWriteTime.longValue()/socketWriteCount.longValue());
		}
		catch(ArithmeticException e)
		{
                        return 0;
		}
	}

	public void resetSocketTimeStats()
	{
		socketReadCount.set(0); 
		socketWriteCount.set(0);
		socketReadTime.set(0);
		socketWriteTime.set(0);
	}

	public boolean isDataQueueDrained(int streamID)
	{
		Http2Stream stream = getStream(streamID);
		return (stream.isResponseDataQueueEmpty() && stream.isDataFrameQueueEmpty());
	}

	public boolean isConnectionClosed()
	{
		return connectionClosed;
	}
	
	private int getStreamCount()
	{
		return streamCount.get();
	}

	private int getClientResetFrameCount()
	{
		return clientResetFrameCounter.get();
	}
}
