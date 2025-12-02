package com.zoho.wms.asyncweb.server.http2;

// Java import
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

// Wms import
import com.adventnet.wms.common.HttpDataWraper;
import com.adventnet.wms.common.exception.WMSException;

// AWS import
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.WebEngineLoader;
import com.zoho.wms.asyncweb.server.WebEngine;
import com.zoho.wms.asyncweb.server.AbstractWebEngine;
import com.zoho.wms.asyncweb.server.WmsSessionIdGenerator;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.exception.Http2Exception;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.http.HttpRequest;
import com.zoho.wms.asyncweb.server.http.HttpStream;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.AWSLogClientThreadLocal;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;

// dependencies import
import com.zoho.logs.common.util.LogTypes;
import com.zoho.logs.logclient.v2.LogAPI;
import com.zoho.logs.logclient.v2.json.ZLMap;

// constant imports
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.IDLE;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.RESERVED_LOCAL;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.RESERVED_REMOTE;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.OPEN;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.HALF_CLOSED_LOCAL;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.HALF_CLOSED_REMOTE;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.CLOSED;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.HEADER_SEPARATOR;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.COOKIE_SEPARATOR;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.HEADER_VALUE_SEPARATOR;

/**
 *
 *  @author durai - 11882
 *
 */

public class Http2Stream
{
	public static AsyncLogger logger = new AsyncLogger(Http2Stream.class.getName());
	protected static AsyncLogger ACCESS_LOGGER = new AsyncLogger(AWSConstants.ACCESS_LOGGER);//No I18n

	// stream variables
	private String http2ConnID = null;
	private int streamID = -1;
	private int streamState;
	private boolean closed = false;
	private boolean closeAfterWrite = false;
	private IndexTable idx = null;

	// client variables
	private String ipaddr = null;
	private int localport = 0;
	protected int sockport = 0;
	private String enginehdr = null;
	private String webengineName = null;
	private LinkedHashMap reqtimeline = null;
	private String reqid = null;
	private long inittime;
	private long reqQueueInsertTime;
	private long reqproctime = 0l;
	private boolean isOnCloseCallbackEnabled = false;
	protected long firstReadTime = -1;

	private int read_limit = ConfManager.getReadLimit();
	private int write_limit = ConfManager.getWriteLimit();
	private AtomicLong outputDataSize = new AtomicLong(0);
	private boolean writeinitiated = false;
	private AtomicLong writtenlength = new AtomicLong(0);

	// HTTP request variables
	private String reqType = null;
	private String rawUrl = null;
	private String reqUrl = null;
	private String statsReqUrl = null;
	private String scheme = null;
	private String host = null;
	private byte[] header_block = null;
	private HashMap<String, String> originalHeaderMap = null;
	private HashMap<String, String> headerMap = null;
	private HashMap<String, String> paramsMap = null;
	private ConcurrentLinkedQueue<ByteBuffer> responseDataQueue = null;

	//POST Request
	private byte[] bodyContent = null;
	private boolean postReqStreamMode = false;
	private HttpStream postReqHttpStream = null;
	private long bodyLength = 0;
	private long readBodyLength = 0;
	private long postReqHttpStreamReadLimit = 0;
	private HttpRequest request = null;
	private Http2Response response = null;

	// request state variables
	private boolean requestCompleted = false;
	private boolean headerProcessingCompleted = false;
	private boolean onHeaderCompletionCallBackSent = false;
	private boolean onCompletionCallBackSent = false;
	private long expiryTime = -1;

	private boolean read_completed = false;
	private boolean read_header_completed = false;
	private boolean end_notified = false;

	// response state variables
	private boolean dataFlowNotification = false;
	private boolean chunked = false;

	// H2 protocol variables
	private boolean clientPrefaceReceived = false;
	private boolean serverPrefaceSent = false;
	private boolean serverPushEnabled = true;
	private int pingAcknowledged = 0;
	private int maxConcurrentServerStreams = 200;
	private int responseFrameMaxSize = Http2Constants.DEFAULT_FRAME_PAYLOAD_SIZE;
	private boolean securityfilter = false;

	private int initialStreamLevelClientWindowSize = Http2Constants.DEFAULT_WINDOW_SIZE;
	private AtomicInteger clientWindowSize = new AtomicInteger(Http2Constants.DEFAULT_WINDOW_SIZE);
	private AtomicInteger serverWindowSize = new AtomicInteger(Http2Constants.DEFAULT_WINDOW_SIZE);
	private int pendingWindowUpdate = 0;
	

	private int continuationFrameCounter = 0;

	public Http2Stream(String http2ConnID, int streamId, IndexTable idx, String ipaddr, int localport, int sockport) throws WMSException
	{
		this.http2ConnID = http2ConnID;
		this.streamID = streamId;
		this.streamState = IDLE;
		this.idx = idx;

		this.ipaddr = ipaddr;
		this.localport = localport;
		this.sockport = sockport;

		this.reqid = ""+WmsSessionIdGenerator.getUniqueId();
		this.inittime = System.currentTimeMillis();
		this.firstReadTime = System.currentTimeMillis();

		if(WebEngine.getEngineByPort(localport) != null)
		{
			// higher limit for internal access wms2.0
			read_limit = WebEngine.getEngineByPort(localport).getReadLimit() != -1 ? WebEngine.getEngineByPort(localport).getReadLimit() : ConfManager.getReadLimit();
			write_limit = WebEngine.getEngineByPort(localport).getWriteLimit() != -1 ? WebEngine.getEngineByPort(localport).getWriteLimit() : ConfManager.getWriteLimit();
		}

		if(streamId != Http2Constants.CONNECTION_STREAM_IDENTIFIER)
		{
			webengineName = AWSConstants.DEFAULT;
			reqtimeline = new LinkedHashMap();
			statsReqUrl = AWSConstants.NA;
			originalHeaderMap = new HashMap<String, String>();
			headerMap = new HashMap<String, String>();
			paramsMap = new HashMap<String, String>();
			responseDataQueue = new ConcurrentLinkedQueue<ByteBuffer>();
		}
		AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2STREAM, "h2stream_new");
	}

	public void processHeaderFrame(int payloadLength, byte flag, int streamId, ByteArrayInputStream bais) throws Exception
	{
		int paddingLength = 0;
		int otherLength = 0;

		if(streamId != this.streamID)
		{
			return;
		}

		if(Http2Util.hasPadding(flag))
		{
			paddingLength = bais.read();
			otherLength++;
		}
		if(Http2Util.hasPriority(flag))
		{
			bais.mark(bais.available());
			boolean mutuallyExclusive = (bais.read() & 0x80) == 128;
			bais.reset();

			int dependentStream = (mutuallyExclusive) ? Http2Util.get31Bits(bais) : Http2Util.getFourBytes(bais);
			processDependency(dependentStream, mutuallyExclusive);
			otherLength += 4;

			int weight = bais.read() + 1; // added plus one based on rfc
			addPriorityWeight(streamId, weight);
			otherLength++;
		}

		int header_block_length = bais.available() - paddingLength;
		try
		{
			if(Http2Util.isEndOfHeaders(flag))
			{
				String decoded_header_block = Decoder.decodeHex(bais, paddingLength, idx);
				parseHeaders(decoded_header_block);

				AWSInfluxStats.addHttp2HeaderCompressionStats("request", decoded_header_block.length(), header_block_length);
			}
			else
			{
				this.header_block = new byte[header_block_length];
				bais.read(header_block);
			}
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2Stream] - Error while processing header frame - streamid:"+streamId);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_PROCESSHEADERFRAME);
			throw ex;
		}


		if(streamState == IDLE)
		{
			streamState = OPEN;
		}
		else if(streamState == RESERVED_REMOTE)
		{
			streamState = HALF_CLOSED_LOCAL;
		}

		if(Http2Util.isEndOfHeaders(flag))
		{
			read_header_completed =true;
			processCompletedHeader();
		}

		if(Http2Util.isEndOfStream(flag))
		{
			read_completed = true;
			processRequest();
		}

		if((read_completed == true) && (read_header_completed == true))
		{
			if(streamState == OPEN)
			{
				streamState = HALF_CLOSED_REMOTE;
			}
			else if(streamState == HALF_CLOSED_LOCAL)
			{
				streamState = CLOSED;
			}
		}
	}

	public void processContinuationFrame(int payloadLength, byte flag, int streamId, ByteArrayInputStream bais) throws Exception
	{
		if(streamId != this.streamID)
		{
			return;
		}

		continuationFrameCounter++;
		try
		{
			byte[] continuation_headers = new byte[payloadLength];
			bais.read(continuation_headers);
			this.header_block = appendData(this.header_block, continuation_headers);

			if(Http2Util.isEndOfHeaders(flag)) // if END_HEADERS flag is not set, need to wait for other continuation frames before starting to decode headers
			{
				bais = new ByteArrayInputStream(this.header_block);
				String decoded_header_data = Decoder.decodeHex(bais, 0, idx);
				parseHeaders(decoded_header_data);

				AWSInfluxStats.addHttp2HeaderCompressionStats("request", decoded_header_data.length(), this.header_block.length);
			}
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception - Http2Stream] - Error while processing continuation frame - streamid:"+streamId);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_PROCESSCONTINUATIONFRAME);
			throw ex;
		}


		if(Http2Util.isEndOfHeaders(flag))
		{
			read_header_completed =true;
			processCompletedHeader();
		}

		if(Http2Util.isEndOfStream(flag))
		{
			read_completed = true;
			processRequest();
		}

		if((read_completed == true) && (read_header_completed == true))
		{
			if(streamState == OPEN)
			{
				streamState = HALF_CLOSED_REMOTE;
			}
			else if(streamState == HALF_CLOSED_LOCAL)
			{
				streamState = CLOSED;
			}
		}
	}

	public void processDataFrame(int payloadLength, byte flag, int streamId, ByteArrayInputStream bais) throws Exception
	{
		int padLength = 0;

		if(streamId != this.streamID)
		{
			return;
		}
		
		int index = 0;

		if(Http2Util.hasPadding(flag))
		{
			padLength = bais.read();
		}

		byte[] data = new byte[payloadLength - padLength];
		try
		{
			bais.read(data, 0, payloadLength - padLength);
		}
		catch (Exception ex)
		{
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_PROCESSDATAFRAME);
			throw new Http2Exception("Unable to process Data Frame");
		}

		if(data != null)
		{
			if(postReqStreamMode)
			{
				if(postReqHttpStream == null)
				{
					AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_PROCESSDATAFRAME);
					throw new Http2Exception("POST Request HttpStream Object Null");
				}
				readBodyLength += data.length;
				postReqHttpStream.write(data);

				if(postReqHttpStreamReadLimit > 0 && readBodyLength > postReqHttpStreamReadLimit)
				{
					throw new IOException("HEAVY READ - reqURL:"+reqUrl+" - length:"+ readBodyLength + " - postReqHttpStreamReadLimit:" + read_limit);
				}
			}
			else
			{
				readBodyLength += data.length;
				this.bodyContent = appendData(bodyContent, data);

//				if(read_limit > 0 && readBodyLength > read_limit) //revisit 3
//				{
//					throw new IOException("HEAVY READ - reqURL:"+reqUrl+" - length:"+ readBodyLength + " - read_limit:" + read_limit);
//				}
			}
		}

		if(Http2Util.isEndOfStream(flag))
		{
			read_completed = true;
			processRequest();
		}

		if((read_completed == true) && (read_header_completed == true))
		{
			if(streamState == OPEN)
			{
				streamState = HALF_CLOSED_REMOTE;
			}
			else if(streamState == HALF_CLOSED_LOCAL)
			{
				streamState = CLOSED;
			}
		}
	}

	public HashMap<Integer, Integer> processSettingsFrame(int payloadLength, byte flag, int streamId, ByteArrayInputStream bais) throws Http2Exception
	{
		if(Http2Util.isAck(flag))
		{
			if(payloadLength > 0)
			{
				logger.log(Level.SEVERE, "[Http2Stream - processSettingsFrame] - connection error - setting ackFlagWithNonZeroPayload");
				//goaway
				throw new Http2Exception("connection error - setting ackFlagWithNonZeroPayload");
			}
		}
		else
		{
			if(payloadLength > 0)
			{
				HashMap<Integer, Integer> settingsMap = new HashMap<>();
				int index = 0;
				for(int i = 0; i < payloadLength / 6; i++)
				{
					int settingType = Http2Util.getTwoBytes(bais);
					int value = Http2Util.getFourBytes(bais);
					processSetting(settingType, value);
					settingsMap.put(settingType, value);
				}
				return settingsMap;
			}
		}
		return null;
	}

	public void processSettingsPayload(byte[] payload)
	{
		byte[] setting  = Arrays.copyOfRange(payload,0,2);
		byte[] value = Arrays.copyOfRange(payload,2,6);
		processSetting(getValue(setting),getValue(value));
	}

	private void processSetting(int type, int value)
	{
		if(ConfManager.isHttp2LogsEnabled())
		{
			logger.log(Level.INFO, "[-----SETTINGS----- h2ID:"+http2ConnID+"-streamID:0] - "+Http2Util.getSettingType(type)+" - value:"+value);
		}

		switch(type)
		{
			case Http2Constants.SETTINGS_HEADER_TABLE_SIZE://revisit 4
//				decoder.setMaxTableSize(value);
				break;
			case Http2Constants.SETTINGS_ENABLE_PUSH:
				serverPushEnabled = (value==0)?false:true;
				break;
			case Http2Constants.SETTINGS_MAX_CONCURRENT_STREAMS:
				maxConcurrentServerStreams = value;
				break;
			case Http2Constants.SETTINGS_INITIAL_WINDOW_SIZE:
				initialStreamLevelClientWindowSize = value;
				break;
			case Http2Constants.SETTINGS_MAX_FRAME_SIZE:
				responseFrameMaxSize = value;
				break;
			case Http2Constants.SETTINGS_MAX_HEADER_LIST_SIZE://revisit 5
				break;
		}
	}

	public void processPriorityFrame(int payloadLength, byte flag, int streamId, byte[] payload)
	{
		int dependentStreamId;
		int weight;
		boolean mutuallyexclusive;

		if(streamId != this.streamID)
		{
			return;
		}

		byte[] dependentStream = Arrays.copyOfRange(payload,0,4);
		weight = payload[4]+1;

		if((dependentStream[0] & 0x80) == 128)
		{
			dependentStream[0] = (byte)(dependentStream[0] & 0x7F);
			processDependency(Http2Util.getStreamIdentifier(dependentStream),true);
			mutuallyexclusive = true;
		}
		else
		{
			processDependency(Http2Util.getStreamIdentifier(dependentStream),false);
			mutuallyexclusive = false;
		}
		dependentStreamId = Http2Util.getStreamIdentifier(dependentStream);
		addPriorityWeight(streamId,weight);
	}

	public void processResetStreamFrame(int payloadLength, byte flag, int streamId, ByteArrayInputStream bais) throws Http2Exception
	{
		if(getStreamState() == IDLE)
		{
			throw new Http2Exception("Connection Error - ResetStream Frame received for stream of IDLE state : streamId:"+streamId);
		}

		int errorCode = Http2Util.getFourBytes(bais);

		logger.log(Level.INFO, "[Http2 Connection-processResetStreamFrame-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Received Reset Stream Frame - closing stream - errorCode:"+errorCode);
		streamState = CLOSED;
	}

	public void processPingFrame(int payloadLength, byte flag, int streamId, ByteArrayInputStream bais) throws Http2Exception, IOException
	{
		if(streamId != 0)
		{
			throw new Http2Exception("Stream ID Error streamID:"+streamId+" frameType:PING");
		}

		byte[] opaqueData = new byte[bais.available()];
		bais.read(opaqueData);

		if(flag == (byte)0x0)
		{
			pingAcknowledged++;
		}
	}

	public void processWindowUpdateFrame(int payloadLength, byte flag, int streamId, ByteArrayInputStream bais)
	{
		if(streamId != this.streamID)
		{
			return;
		}

//		int incrementSize = getValue(Arrays.copyOfRange(payload,0,payloadLength));
		int incrementSize = Http2Util.get31Bits(bais);

		increaseClientWindowSize(incrementSize);
	}

	public void processGoAwayFrame(int payloadLength, byte flag, int streamId, ByteArrayInputStream bais) throws Http2Exception, IOException
	{
		if(streamId != 0)
		{
			throw new Http2Exception("Stream ID Error streamID:"+streamId+" frameType:GOAWAY");
		}

		int lastStreamId = Http2Util.get31Bits(bais);
		int errorcode = Http2Util.getFourBytes(bais);

		String additionaldebugdata = "";
		if(payloadLength > 8)
		{
			byte[] additionaldebug = new byte[bais.available()];
			bais.read(additionaldebug);
			additionaldebugdata = new String(additionaldebug);
		}
	}


	public int getHeaderIndexType(String header)
	{
		if(idx.getIndex(header) != -1)
		{
			return Http2Constants.HEADER_INDEXED;
		}
		else if(header.equalsIgnoreCase(AWSConstants.HDR_DATE) || header.equalsIgnoreCase(AWSConstants.HDR_CONTENT_LENGTH) || header.equalsIgnoreCase(AWSConstants.HDR_LAST_MODIFIED))
		{
			return Http2Constants.HEADER_WITHOUT_INDEXED;
		}
		else
		{
			return Http2Constants.HEADER_INCREMENTAL_INDEXED;
		}
	}

	public byte[] getIndexedEncodedHeader(HashMap headerMap)
	{
		byte[] encodedHeader = null;
		boolean encodeHuffman = ConfManager.isHttp2HuffmanEncodingEnabled();

		try
		{
			if(headerMap.size() > 0)
			{
				StringBuilder sb = new StringBuilder();
				Set<String> s = headerMap.keySet();
				for(String key : s)
				{
					String value = (String)headerMap.get(key);
					String header = key + HEADER_SEPARATOR + value;

					//int type = requestWrapper.getHeaderType(header);
					int type = getHeaderIndexType(header); 

					switch(type)
					{
						case Http2Constants.HEADER_INDEXED: sb.append(Encoder.getIndexedHeader(header, idx));
								break;

						case Http2Constants.HEADER_INCREMENTAL_INDEXED: sb.append(Encoder.getLiteralHeaderWithIncrementalIndexing(key.trim(),value.trim(),encodeHuffman, idx));
								break;

						case Http2Constants.HEADER_WITHOUT_INDEXED: sb.append(Encoder.getLiteralHeaderWithoutIndexing(key.trim(),value.trim(),encodeHuffman, idx));
								break;

						case Http2Constants.HEADER_NEVER_INDEXED: sb.append(Encoder.getLiteralHeaderNeverIndexed(key.trim(),value.trim(),encodeHuffman, idx));
								break;
					}
				}

				encodedHeader = Http2Util.convertHexToByteArray(sb.toString());

				AWSInfluxStats.addHttp2HeaderCompressionStats("response", headerMap.toString().length(), encodedHeader.length);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "[Http2Stream - getIndexedEncodedHeader] - Error at indexing and encoded of header block ", ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_GETINDEXEDENCODEDHEADER);
			throw ex;
		}

		return encodedHeader;
	}

	public ByteBuffer getHeaderFrame(Hashtable headerMap, byte flag) throws Exception
	{
		LinkedHashMap orderedHeaderMap = getResponseHeadersInOrder(headerMap);
		byte[] encodedHeader = getIndexedEncodedHeader(orderedHeaderMap);

		int payloadLength = (encodedHeader != null) ? encodedHeader.length : 0;
		int padlength = -1;
		int dependentStreamId = -1;
		boolean mutuallyexclusive = false;
		int weight = -1;

		if(padlength > 0)
		{
			payloadLength += padlength + 1; // one extra byte to specify padlength
		}
		if(dependentStreamId > 0)
		{
			payloadLength += 4;
		}
		if(weight > 0)
		{
			payloadLength += 1;
		}

		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE + payloadLength);
		Http2Util.setFrameLength(bb, payloadLength);
		bb.put(Http2Constants.HEADER_FRAME);
		bb.put(flag);
		Http2Util.setStreamIdentifier(bb, streamID);
		Http2Util.setHeaderPayload(bb, encodedHeader, padlength, dependentStreamId, mutuallyexclusive, weight);

		if(streamState == IDLE)
		{
			if(Http2Util.isEndOfStream(flag))
			{
				streamState = HALF_CLOSED_LOCAL;
			}
			else
			{
				streamState = OPEN;
			}
		}
		else if(streamState == RESERVED_LOCAL)
		{
			if(Http2Util.isEndOfStream(flag))
			{
				streamState = CLOSED;
			}
			else
			{
				streamState = HALF_CLOSED_REMOTE;
			}
		}
		else if(streamState == HALF_CLOSED_REMOTE)
		{
			if(Http2Util.isEndOfStream(flag))
			{
				streamState = CLOSED;
			}
		}
		else if(streamState == RESERVED_LOCAL)
		{
			if(Http2Util.isEndOfStream(flag))
			{
				streamState = CLOSED;
			}
			else if(Http2Util.isEndOfHeaders(flag))
			{
				streamState = HALF_CLOSED_REMOTE;
			}
		}

		return bb;
	}

	public ByteBuffer getContinuationFrame(HashMap headerMap, byte flag) throws Http2Exception
	{
		byte[] encodedHeader = getIndexedEncodedHeader(headerMap);

		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE + encodedHeader.length);

		Http2Util.setFrameLength(bb, encodedHeader.length);
		bb.put(Http2Constants.CONTINUATION_FRAME);
		bb.put(flag);
		Http2Util.setStreamIdentifier(bb, streamID);
		Http2Util.setContinuationPayload(bb, encodedHeader);

		//revisit 2 // handle STATE change

		return bb;
	}

	public ByteBuffer getDataFrame(byte flag) throws Http2Exception
	{
		return getDataFrame(null, flag);
	}

	public ByteBuffer getDataFrame(byte[] data) throws Http2Exception
	{
		return getDataFrame(data, Http2Constants.EMPTY_FLAG);
	}

	public ByteBuffer getDataFrame(byte[] data, byte flag) throws Http2Exception
	{
		int payloadLength = (data != null) ? data.length : 0;
		int padLength = -1; // change default value of padLength if Needed
		if(padLength > 0)
		{
			payloadLength += padLength + 1; // one extra byte to specify padlength
		}

		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE + payloadLength);
		Http2Util.setFrameLength(bb, payloadLength);
		bb.put(Http2Constants.DATA_FRAME);
		bb.put(flag);
		Http2Util.setStreamIdentifier(bb, streamID);
		Http2Util.setDataPayload(bb, data, padLength);

		if(streamState == IDLE)
		{
			if(Http2Util.isEndOfStream(flag))
			{
				streamState = HALF_CLOSED_LOCAL;
			}
		}
		else if(streamState == HALF_CLOSED_REMOTE)
		{
			if(Http2Util.isEndOfStream(flag))
			{
				streamState = CLOSED;
			}
		}

		return bb;
	}

/*
	public byte[] getPushPromiseFrame(int promisedStreamId, byte[] headerblock,byte flag)
	{
		//Hashtable headerTable = cframe.getHeaders();
		//byte[] encodedHeader = getIndexedEncodedHeader(headerTable);

		byte[] pushPromisePayload = Http2Util.getPushPromisePayload(Http2Util.getStreamIdentifier(promisedStreamId),headerblock);
		byte[] b = new byte[Http2Constants.HEADER_SIZE+pushPromisePayload.length];
		int index = 0;

		byte[] frameLength = Http2Util.getFrameLength(pushPromisePayload.length);
		System.arraycopy(frameLength,0,b,index,frameLength.length);
		index = index+frameLength.length;

		b[index++] = Http2Constants.SETTINGS_FRAME;
		b[index++] = flag;

		byte[] streamIdentifier = Http2Util.getStreamIdentifier(streamId);
		System.arraycopy(streamIdentifier,0,b,index,streamIdentifier.length);
		index = index+streamIdentifier.length;

		System.arraycopy(pushPromisePayload,0,b,index,pushPromisePayload.length);

		if(state == IDLE)
		{
			state = RESERVED_LOCAL;
		}

		return b;
	}
*/

	public ByteBuffer getAckSettingsFrame() throws Http2Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE);
		Http2Util.setFrameLength(bb, 0);
		bb.put(Http2Constants.SETTINGS_FRAME);
		bb.put(Http2Constants.ACK_FLAG);
		Http2Util.setStreamIdentifier(bb, streamID);
		return bb;
	}

	public ByteBuffer getEmptySettingsFrame() throws Http2Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE);
		Http2Util.setFrameLength(bb, 0);
		bb.put(Http2Constants.SETTINGS_FRAME);
		bb.put(Http2Constants.EMPTY_FLAG);
		Http2Util.setStreamIdentifier(bb, streamID);
		return bb;
	}

	public ByteBuffer getSettingsFrame(int settingType, int value) throws Http2Exception
	{
		HashMap<Integer, Integer> settingsMap = new HashMap<>();
		settingsMap.put(settingType, value);
		return getSettingsFrame(settingsMap);
	}

	public ByteBuffer getSettingsFrame(HashMap<Integer, Integer> settingsMap) throws Http2Exception
	{
		if(settingsMap.size() == 0)
		{
			return getEmptySettingsFrame();
		}
		int payloadLength = settingsMap.size() * 6;

		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE + payloadLength);
		Http2Util.setFrameLength(bb, payloadLength);
		bb.put(Http2Constants.SETTINGS_FRAME);
		bb.put(Http2Constants.EMPTY_FLAG);
		Http2Util.setStreamIdentifier(bb, streamID);
		Http2Util.setSettingsPayload(bb, settingsMap);

		return bb;
	}

/*
	public ByteBuffer getPriorityFrame(int dependentStreamId, int weight, boolean mutuallyExclusive) throws Exception
	{
		byte[] priorityPayload = Http2Util.getPriorityPayload(Http2Util.getStreamIdentifier(dependentStreamId), weight, mutuallyExclusive);

		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE + priorityPayload.length);
		Http2Util.setFrameLength(bb, priorityPayload.length);
		bb.put(Http2Constants.PRIORITY_FRAME);
		bb.put(Http2Constants.EMPTY_FLAG);
		Http2Util.setStreamIdentifier(bb, streamID);
		bb.put(priorityPayload);
		return bb;
	}
*/

	public ByteBuffer getPingFrame() throws Http2Exception
	{
		return getPingFrame(Http2Constants.EMPTY_FLAG);
	}

	public ByteBuffer getPingAckFrame() throws Http2Exception
	{
		return getPingFrame(Http2Constants.ACK_FLAG);
	}

	private ByteBuffer getPingFrame(byte flag) throws Http2Exception
	{
		if(streamID != 0)
		{
			throw new Http2Exception("Stream ID Error - streamID:"+ streamID +" frameType:PING");
		}

		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE + 8);
		Http2Util.setFrameLength(bb, 8);
		bb.put(Http2Constants.PING_FRAME);
		bb.put(flag);
		Http2Util.setStreamIdentifier(bb, streamID);
		Http2Util.setPingPayload(bb);
		return bb;
	}

	public ByteBuffer getWindowUpdateFrame(int windowSize) throws Http2Exception
	{
		return getWindowUpdateFrame(windowSize, this.streamID);
	}

	public ByteBuffer getWindowUpdateFrame(int windowSize, int streamID) throws Http2Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE + 4);
		Http2Util.setFrameLength(bb, 4);
		bb.put(Http2Constants.WINDOW_UPDATE_FRAME);
		bb.put(Http2Constants.EMPTY_FLAG);
		Http2Util.setStreamIdentifier(bb, streamID);
		Http2Util.setWindowUpdatePayload(bb, windowSize);

		return bb;
	}

	public ByteBuffer getResetStreamFrame(int errorCode) throws Exception
	{
		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE + 4);
		Http2Util.setFrameLength(bb, 4);
		bb.put(Http2Constants.RESET_STREAM_FRAME);
		bb.put(Http2Constants.EMPTY_FLAG);
		Http2Util.setStreamIdentifier(bb, streamID);
		Http2Util.setResetPayload(bb, errorCode);

		if(streamState == RESERVED_LOCAL)
		{
			streamState = CLOSED;
		}

		return bb;
	}

	public ByteBuffer getGoAwayFrame(int lastStreamId, byte errorCode, String debugData) throws Http2Exception
	{
		if(streamID != 0)
		{
			throw new Http2Exception("Stream ID Error - streamID:"+ streamID +" frameType:GOAWAY");
		}

		int payloadLength = 4 + 4 + debugData.getBytes().length; // lastStreamID(4) + Error Code(4) + DebugData(*)

		ByteBuffer bb = ByteBuffer.allocate(Http2Constants.HEADER_SIZE + payloadLength);
		Http2Util.setFrameLength(bb, payloadLength);
		bb.put(Http2Constants.GOAWAY_FRAME);
		bb.put(Http2Constants.EMPTY_FLAG);
		Http2Util.setStreamIdentifier(bb, streamID);
		Http2Util.setGoAwayPayload(bb, Http2Util.getStreamIdentifier(lastStreamId), errorCode, debugData);

		return bb;
	}

	public void processDependency(int dependentStream, boolean exclusive)
	{
	}

	public void addPriorityWeight(int streamId, int weight)
	{
	}

	private void parseHeaders(String raw_headers) throws Exception
	{
		String[] array = raw_headers.split("\r\n");

		for(String line : array)
		{
			String[] keyValue =  line.split(HEADER_SEPARATOR,2);
			if(keyValue.length == 2)
			{
				if(originalHeaderMap.containsKey(keyValue[0].trim()))
				{
					if(keyValue[0].trim().equals(AWSConstants.COOKIE))
					{
						keyValue[1] = originalHeaderMap.get(AWSConstants.COOKIE) + COOKIE_SEPARATOR + keyValue[1].trim();
					}
					else
					{
						keyValue[1] = originalHeaderMap.get(keyValue[0].trim()) + HEADER_VALUE_SEPARATOR + keyValue[1].trim();
					}
				}

				originalHeaderMap.put(keyValue[0].trim(), keyValue[1].trim());
			}
			else
			{
				AWSInfluxStats.addHackAttemptStats(AWSConstants.INVALID_REQ_HEADERS);
				throw new Exception("MALFORMWRONGHEADER : "+line);
			}
		}
	}

	private void processRequest() throws Exception
	{
		if(!headerProcessingCompleted && read_header_completed)
		{
			processCompletedHeader();
		}

		if(read_header_completed && headerProcessingCompleted && read_completed)
		{
			requestCompleted = true;
			printAccessLog();
		}
	}

	private void processCompletedHeader() throws Exception
	{
		try
		{
			this.host = originalHeaderMap.get(Http2Constants.AUTHORITY_PSEUDO_HEADER); // needed before adding host header to headerMap
			for(String key : originalHeaderMap.keySet())
			{
				if( ! isPseudoHeader(key))
				{
					headerMap.put(key.toLowerCase(), originalHeaderMap.get(key));
				}
			}
			headerMap.put(AWSConstants.HOST, this.host); //as per rfc scheme pseudo header of h2 is a replacement for host header of http/1.1

			if(headerMap.containsKey(AWSConstants.AWS_ENGINE_HEADER))
			{
				enginehdr = getHeader(AWSConstants.AWS_ENGINE_HEADER); // needed before calling getServletURI method, which will find webengine name
			}
			else if(enginehdr != null)
			{
				enginehdr = null;
			}

			this.reqType = originalHeaderMap.get(Http2Constants.METHOD_PSEUDO_HEADER);
			this.scheme = originalHeaderMap.get(Http2Constants.SCHEME_PSEUDO_HEADER);
			this.rawUrl = getRelativePath(originalHeaderMap.get(Http2Constants.PATH_PSEUDO_HEADER), this.host);
			this.reqUrl = processQueryString(rawUrl);
			this.statsReqUrl = getServletURI(reqUrl); // webengine name is set in this method

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
				logger.addExceptionLog(Level.FINE, "Exception in lb_ssl_ip replacement : for the reqUrl : "+reqUrl+", IP : "+getIPAddress(), exp);//No I18n
			}

			// POST Request Streaming Mode Handling
			if(Util.isStreamMode((String)headerMap.get(ConfManager.getStreamModeHeader())) || Util.isStreamMode((String)paramsMap.get(ConfManager.getStreamModeHeader())) || (ConfManager.isStreamModeEnabled() && isPostRequest(reqType.trim()) && isStreamServlet(statsReqUrl)))
			{
				postReqStreamMode = true;
				if(postReqStreamMode && postReqHttpStream==null)
				{
					if(getHeader(AWSConstants.HDR_CONTENT_LENGTH)!=null)
					{
						bodyLength = Long.parseLong(getHeader(AWSConstants.HDR_CONTENT_LENGTH));
						if(bodyLength > 0)
						{
							postReqHttpStream = new HttpStream(bodyLength, reqUrl);
						}
					}
					else
					{
						postReqHttpStream = new HttpStream(reqUrl);
					}
					postReqHttpStream.setHttp2StreamID(streamID);
					postReqHttpStream.setHttp2ConnectionID(http2ConnID);
					
				}
			}

			if(reqType.equals(AWSConstants.POST_REQ) || ConfManager.isSupportedPostMethod(reqType))
			{
				if(!postReqStreamMode)
				{
					if(getHeader(AWSConstants.HDR_CONTENT_LENGTH)!=null)
					{
						bodyLength = Long.parseLong(getHeader(AWSConstants.HDR_CONTENT_LENGTH));
						if(bodyLength < 0)
						{
							//sendError(HttpResponseCode.LENGTH_REQUIRED,HttpResponseCode.LENGTH_REQUIRED_MSG); //revisit
							throw new AWSException("Content Length set to negative");//No I18N
						}
					}
					else
					{
						//sendError(HttpResponseCode.LENGTH_REQUIRED,HttpResponseCode.LENGTH_REQUIRED_MSG); //revisit
						throw new AWSException("Content Length Header Not Present");//No I18N
					}
				}
			}

			headerProcessingCompleted = true;
//			logger.log(Level.INFO, "[Http2 Stream-processCompletedHeader-h2ID:"+http2ConnID+"-streamID:"+ streamID +"] - reqType:"+reqType+" - reqUrl:"+reqUrl+" - scheme:"+scheme+" - isPostReqStreamMode:"+postReqStreamMode+" - bodyLength:"+bodyLength);
			processRequest();
		}
		catch (Exception ex)
		{
			logger.log(Level.SEVERE, "[Exception][Http2Stream - processCompletedHeader] - Error while processing Headers");
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_PROCESSCOMPLETEDHEADER);
			throw ex;
		}
	}

	private String getRelativePath(String url, String host)
	{
		try
		{	
			host = host.replaceFirst(":.*$", AWSConstants.EMPTY_STRING);
			if(url.startsWith("http://"+host))
			{
				return url.replaceAll("http://"+host, AWSConstants.EMPTY_STRING);
			}
			else if(url.startsWith("https://"+host))
			{
				return url.replaceAll("https://"+host, AWSConstants.EMPTY_STRING);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Http2Exception - unable to get path pseudo header, given url:"+url+" - host:"+host, ex);
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_GETRELATIVEPATH);
		}
		return url;
	}

	private String processQueryString(String rawUrl)
	{
		int pos = rawUrl.indexOf('?');
		if(pos<=0)
		{
			String reqUrl = rawUrl.substring(1);
			if(reqUrl.equals(AWSConstants.EMPTY_STRING))
			{
				reqUrl= ConfManager.getDefaultURL();
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
					logger.log(Level.SEVERE, " Exception --> rawurl:"+rawUrl+", qString:"+qString+", ", exp);
					AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_UPDATEPARAMMAP);
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
				logger.addExceptionLog(Level.SEVERE, AWSLogConstants.EXCEPTION_IN_UPDATE_PARAM, new Object[]{key, value, paramsMap});
				AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_GETPARAMSTRING);
				return getParameter(key);
			}
		}
		return value;
	}

	private String getServletURI(String uri)
	{
		try
		{
			AbstractWebEngine engine = null;
			WebEngineLoader engineLoader = ConfManager.getWebEngineLoader();
			if(engineLoader != null)
			{
				String engineName = engineLoader.getEngineName(this.getHttpRequest());
				if(engineName != null)
				{
					engine = WebEngine.getEngineByAppName(engineName);
				}
			}
			if(engine == null)
			{
				engine = WebEngine.getEngine(host, localport);
			}
			this.webengineName = engine.getAppName();
			return engine.getServletURI(uri);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Exception in get servlet uri :: "+uri, ex);//No I18n
			AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2EXP, Http2Constants.EXP_H2STREAM_GETSERVLETURI);
		}
		return uri;
	}

	private boolean isPseudoHeader(String headerKey)
	{
		if(headerKey.equals(Http2Constants.METHOD_PSEUDO_HEADER) || headerKey.equals(Http2Constants.SCHEME_PSEUDO_HEADER) || headerKey.equals(Http2Constants.AUTHORITY_PSEUDO_HEADER) || headerKey.equals(Http2Constants.PATH_PSEUDO_HEADER) || headerKey.equals(Http2Constants.STATUS_PSEUDO_HEADER))
		{
			return true;
		}
		return false;
	}

	public HttpRequest getHttpRequest() throws IOException
	{
		return new HttpRequest(reqType, reqUrl, rawUrl, Http2Constants.HTTP2_VERSION, getIPAddress(), headerMap, originalHeaderMap, paramsMap, bodyContent, -1, postReqHttpStream, localport, scheme, webengineName);
	}

	public HttpRequest getHttpRequest(int ackstate) throws IOException
	{
		if(request != null)
		{
			request.setState(ackstate);
			return request;
		}
		else
		{
			HttpRequest req = new HttpRequest(reqType, reqUrl, rawUrl, Http2Constants.HTTP2_VERSION, getIPAddress(), headerMap, originalHeaderMap, paramsMap, bodyContent, ackstate, postReqHttpStream, localport, scheme, webengineName);
			if(requestCompleted || (postReqStreamMode && isHeaderProcessingCompleted()))
			{
				this.request = req;
				clearRequestData();
			}
			return req;
		}
	}

	public Http2Response getHttpResponse(String http2ConnID, String ipaddr)
	{
		if(response == null)
		{
			response = new Http2Response(http2ConnID, streamID, ipaddr, request);
		}
		return response;
	}

	private void clearRequestData()
	{
		ipaddr = null;
		enginehdr = null;
		webengineName = null;
//		reqtimeline = null; removed once response created and moved to response obj
//		reqid = null; not available in request or response object

		reqType = null;
		rawUrl = null;
		reqUrl = null;
//		statsReqUrl = null; not available in request or response object
		scheme = null;
		host = null;
		originalHeaderMap = null;
		headerMap = null;
		paramsMap = null;

		bodyContent = null;
		this.header_block = null;
//		postReqHttpStream = null; // no duplicate object, same object is used in Http2Stream and HttpRequest
	}

	public void clearData()
	{
		idx = null;

		ipaddr = null;
		enginehdr = null;
		webengineName = null;
		reqtimeline = null;

		reqid = null;

		reqType = null;
		rawUrl = null;
		reqUrl = null;
		statsReqUrl = null;
		scheme = null;
		host = null;
		originalHeaderMap = null;
		headerMap = null;
		paramsMap = null;
//		responseDataQueue = null; // method is called from Http2Response.close(), this queue contains unsent data, so this alone cleared in Http2Stream.close()

		bodyContent = null;
		postReqHttpStream = null;

		request = null;
		response = null;
	}

	public void setReadLimit(int readlimit)
	{
		read_limit = readlimit;
	}

	public int getReadLimit()
	{
		return read_limit;
	}

	public void setWriteLimit(int writelimit)
	{
		write_limit = writelimit;
	}

	public int getWriteLimit()
	{
		return write_limit;
	}

	public boolean isWriteInitiated()
	{
		return writeinitiated;
	}

	public void setWriteInitiated(boolean bool)
	{
		this.writeinitiated = bool;
	}

	public void setOutputDataSize(long size)
	{
		this.outputDataSize.addAndGet(size);
	}

	public long getOutputDataSize()
	{
		return this.outputDataSize.get();
	}

	public int getRequestState()
	{
		if(!chunked && writtenlength.get() == outputDataSize.get())
		{
			return StateConstants.ON_WRITECOMPLETE;
		}
		else if(chunked && isCloseAfterWrite())
		{
			return StateConstants.ON_WRITECOMPLETE;
		}
		else if(writtenlength.get() > outputDataSize.get())
		{
			return StateConstants.ON_WRITEFAILURE;
		}
		else if(!end_notified)
		{
			return StateConstants.ON_OUTPUTBUFFERREFILL;
		}
		else
		{
			return StateConstants.ON_WRITEFAILURE;
		}
	}

	public void setEndNotified()
	{
		end_notified = true;
	}

	public boolean isEndNotified()
	{
		return end_notified;
	}

	public void addWrittenLength(long size)
	{
		writtenlength.addAndGet(size);
	}

	public void setInputDataSize(long size)
	{
		this.postReqHttpStreamReadLimit = size;
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

	private boolean isPostRequest(String reqType)
	{
		return reqType.equalsIgnoreCase("POST") || ConfManager.isSupportedPostMethod(reqType);
	}

	public boolean isPostReqStreamModeEnabled()
	{
		return postReqStreamMode;
	}

	private String getHeader(String headerKey) // private method since headerMap is removed once HttpRequest is created and won't be available
	{
		return (String)headerMap.get(headerKey.trim().toLowerCase());
	}

	private boolean isParamPresent(String key)
	{
		return paramsMap.containsKey(key.trim());
	}

	private String getParameter(String key)
	{
		return (String) paramsMap.get(key.trim());
	}

	private void addParam(String key, String value)
	{
		paramsMap.put(key.trim(), value.trim());
	}

	public String getRequestURL()
	{
		return request.getRequestURL();
	}

	public String getRequestType()
	{
		return request.getRequestType();
	}

	public String getScheme()
	{
		return request.getScheme();
	}

	public String getStatsRequestURL()
	{
		return statsReqUrl;
	}

	public boolean isReqComplete()
	{
		return requestCompleted;
	}

	public int getStreamID()
	{
		return this.streamID;
	}

	public int getStreamState()
	{
		return streamState;
	}

	public HttpStream getPostReqHttpStream()
	{
		return this.postReqHttpStream;
	}

	public void resetPingAcknowledged()
	{
		pingAcknowledged = 0;
	}

	public int getPingAcknowledged()
	{
		return pingAcknowledged;
	}

	public int getMaximumConcurrentServerStreams()
	{
		return maxConcurrentServerStreams;
	}

	public static int getValue(byte[] b)
	{
		int value = 0;

		for(int i=b.length-1,j=0; i>=0 ; i--,j++)
		{
			value = value + ((b[j] & 0xFF) << 8*i);
		}

		return value;
	}

	public void increaseClientWindowSize(int size)
	{
//		logger.log(Level.INFO, "[Http2 Stream-increaseClientWindowSize-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Increasing Client Window with a size of "+size+" - old size:"+clientWindowSize+" - current size"+(clientWindowSize + size));
		setClientWindowSize(clientWindowSize.get() + size);
	}

	public void reduceClientWindowSize(int size)
	{
//		logger.log(Level.INFO, "[Http2 Stream-reduceClientWindowSize-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Decreasing Client Window with a size of "+size+" - old size:"+clientWindowSize+" - current size"+(clientWindowSize - size));
		setClientWindowSize(clientWindowSize.get() - size);
	}

	public void setClientWindowSize(int size)
	{
		if(size > 3*1024 && size <= Http2Constants.MAXIMUM_WINDOW_SIZE)
		{
			clientWindowSize.set(size);
//			logger.log(Level.WARNING, "[Http2 Stream-setClientWindowSize-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - CLIENT WINDOW SIZE CHANGED TO:"+size);
		}
		else
		{
			logger.log(Level.WARNING, "[Http2 Stream-setClientWindowSize-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - CLIENT WINDOW SIZE CANNOT BE SET LESS THAN 3072 bytes(3 kb) OR GREATER THAN 2147483647 bytes(2 gb) - Given size is:"+size+" - SIZE KEPT UNALTERED AT :"+clientWindowSize.get());
		}
	}

	public void increaseServerWindowSize(int size)
	{
//		logger.log(Level.INFO, "[Http2 Stream-increaseServerWindowSize-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Increasing Server Window with a size of "+size+" - old size:"+serverWindowSize+" - current size"+(serverWindowSize + size));
		setServerWindowSize(serverWindowSize.get() + size);
	}

	public void reduceServerWindowSize(int size)
	{
//		logger.log(Level.INFO, "[Http2 Stream-reduceServerWindowSize-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - Decreasing Server Window with a size of "+size+" - old size:"+serverWindowSize+" - current size"+(serverWindowSize - size));
		setServerWindowSize(serverWindowSize.get() - size);
	}

	public void setServerWindowSize(int size)
	{
		if(size > 3*1024 && size <= Http2Constants.MAXIMUM_WINDOW_SIZE)
		{
			serverWindowSize.set(size);
//			logger.log(Level.WARNING, "[Http2 Stream-setServerWindowSize-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - SERVER WINDOW SIZE CHANGED TO:"+size);
		}
		else
		{
			logger.log(Level.WARNING, "[Http2 Stream-setServerWindowSize-h2ID:"+http2ConnID+"-streamID:"+streamID+"] - SERVER WINDOW SIZE CANNOT BE SET LESS THAN 3072 bytes(3 kb) OR GREATER THAN 2147483647 bytes(2 gb) - Given size is:"+size+" - SIZE KEPT UNALTERED AT :"+serverWindowSize.get());
		}
	}

	public int getClientWindowSize()
	{
		return clientWindowSize.get();
	}

	public int getServerWindowSize()
	{
		return serverWindowSize.get();
	}

	public int getInitialStreamLevelClientWindowSize()
	{
		return initialStreamLevelClientWindowSize;
	}

	public int getPendingWindowUpdate()
	{
		return pendingWindowUpdate;
	}

	public void setPendingWindowUpdate(int size)
	{
		pendingWindowUpdate = size;
	}

	public int getResponseFrameMaxSize()
	{
		return responseFrameMaxSize;
	}

	public boolean isReadCompleted()
	{
		return read_completed;
	}

	public boolean isReadHeaderCompleted()
	{
		return read_header_completed;
	}

	public boolean isClientPrefaceReceived()
	{
		return this.clientPrefaceReceived;
	}

	public void setClientPrefaceReceived(boolean value)
	{
		this.clientPrefaceReceived = value;
	}

	public boolean isServerPrefaceSent()
	{
		return this.serverPrefaceSent;
	}

	public void setServerPrefaceSent(boolean value)
	{
		this.serverPrefaceSent = value;
	}

	public boolean isServerPushEnabled()
	{
		return this.serverPushEnabled;
	}

	public void setStreamState(int streamState)
	{
		this.streamState = streamState;
	}

	private byte[] appendData(byte[] first, byte[] second)
	{
		if(first == null)
		{
			return second;
		}
		if(second == null)
		{
			return first;
		}

		byte[] result = new byte[first.length + second.length];
		System.arraycopy(first, 0, result, 0, first.length);
		System.arraycopy(second, 0, result, first.length, second.length);

		return result;
	}

	private LinkedHashMap getResponseHeadersInOrder(Hashtable map) throws Exception
	{
		LinkedHashMap orderedMap = new LinkedHashMap();
		if(map.containsKey(Http2Constants.STATUS_PSEUDO_HEADER))
		{
			orderedMap.put(Http2Constants.STATUS_PSEUDO_HEADER, map.remove(Http2Constants.STATUS_PSEUDO_HEADER));
		}
		else
		{
			throw new Http2Exception("Missing Status Pseudo Header");
		}
		orderedMap.putAll(map);
		return orderedMap;
	}

	public void addToResponseDataQueue(ByteBuffer frame)
	{
		responseDataQueue.add(frame);
	}

	public ByteBuffer getDataFrameResponseQueue()
	{
		return responseDataQueue.peek();
	}

	public ByteBuffer removeDataFrameResponseQueue()
	{
		return responseDataQueue.poll();
	}

	public boolean isResponseDataQueueEmpty()
	{
		return responseDataQueue.isEmpty();
	}

	public int getResponseDataQueueSize()
	{
		return responseDataQueue.size();
	}

	public boolean isDataFrameQueueEmpty()
        {
                return response.isDataFrameQueueEmpty();
        }

	public boolean isHeaderProcessingCompleted()
	{
		return headerProcessingCompleted;
	}

	public boolean isOnHeaderCompletionCallBackSent()
	{
		return onHeaderCompletionCallBackSent;
	}

	public void setOnHeaderCompletionCallBackSent(boolean onHeaderCompletionCallBackSent)
	{
		this.onHeaderCompletionCallBackSent = onHeaderCompletionCallBackSent;
	}

	public String getWebEngineName()
	{
		return request.getEngineName();
	}

	public void setDataFlowNotification(boolean status)
	{
		dataFlowNotification = status;
	}

	public boolean isDataFlowNotificationEnabled()
	{
		return dataFlowNotification;
	}

	public void updateLastAccessTime(long waitTime, boolean update)
	{
		if(isClosed())
		{
			return;
		}

		if(update)
		{
			long preExpiryTime = expiryTime;
			expiryTime = System.currentTimeMillis() + waitTime;
			StreamTimeOutListener.TRACKER.update(preExpiryTime, expiryTime, this);
		}
		else
		{
			expiryTime = System.currentTimeMillis() + waitTime;
			StreamTimeOutListener.TRACKER.touch(expiryTime,this);
		}
	}

	public long getExpireTime()
	{
		return this.expiryTime;
	}

	public boolean isInvalidStreamTimeoutEntry(long time)
	{
		return (expiryTime != time);
	}

	public void removeFromTimeoutTracker()
	{
		StreamTimeOutListener.TRACKER.remove(this.expiryTime,this);
	}

	public String getReqId()
	{
		return reqid;
	}

	public long getInittime()
	{
		return inittime;
	}

	public void setReqQueueInsertTime(long time)
	{
		this.reqQueueInsertTime = time;
	}

	public long getReqQueueInsertTime()
	{
		return reqQueueInsertTime;
	}

	public void updateReqProcTime(long diff)
	{
		this.reqproctime += diff;
	}

	public long getRequestProcessorTime()
	{
		return this.reqproctime;
	}

	public void setOnCloseCallbackEnabled()
	{
		isOnCloseCallbackEnabled = true;
	}

	public boolean isOnCloseCallbackEnabled()
	{
		return isOnCloseCallbackEnabled;
	}

	public long getFirstReadTime()
	{
		return this.firstReadTime;
	}

	public void updateLastWriteTime()
	{
		if(response != null)
		{
			response.updateLastWriteTime();
		}
	}

	public void printAccessLog()
	{
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
					ACCESS_LOGGER.log(Level.SEVERE, getAccessLog());
				}
			}
		}
		catch(Exception ex)
		{
			logger.addExceptionLog(Level.SEVERE, "Exception in accesslog : URL : "+reqUrl+", IP : "+getIPAddress(), ex);//No I18n
		}
	}

	private String getAccessLog()
	{
		AWSLogClientThreadLocal.setLoggingProperties(reqid);
		HashMap loginfo = new HashMap();
		try
		{
			loginfo.put(AWSConstants.REQUEST_URI, reqUrl);
			loginfo.put(AWSConstants.PARAM, Util.getFormattedList(Util.getAccessParams(paramsMap)));
			loginfo.put(AWSConstants.HEADER, Util.getFormattedList(Util.getAccessHeaders(headerMap)));
			loginfo.put(AWSConstants.METHOD, reqType);
			loginfo.put(AWSConstants.REMOTE_IP, ipaddr);
			loginfo.put(AWSConstants.SERVER_PORT, sockport);
			loginfo.put(AWSConstants.USER_AGENT_1, getHeader(AWSConstants.USER_AGENT));

			loginfo.put(Http2Constants.HTTP2_CONN_ID, http2ConnID);
			loginfo.put(Http2Constants.HTTP2_STREAM_ID, streamID);
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION_IN_FORMATTING_ACCESSLOGGER, e);
		}
		return HttpDataWraper.getString(loginfo);
	}

	private void printDefaultAccessLog()
	{
		try
		{
			ZLMap zlmap = new ZLMap();
			zlmap.put(AWSConstants.CUSTOM_FIELD_REQUEST_ID, reqid);
			zlmap.put(AWSConstants.REQUEST_URI, reqUrl);
			zlmap.put(AWSConstants.PARAM, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessParams(paramsMap))));
			zlmap.put(AWSConstants.HEADER, HttpDataWraper.getString(Util.getFormattedList(Util.getAccessHeaders(headerMap))));
			zlmap.put(AWSConstants.METHOD, reqType);
			zlmap.put(AWSConstants.REMOTE_IP, ipaddr);
			zlmap.put(AWSConstants.SERVER_PORT, sockport);
			zlmap.put(AWSConstants.USER_AGENT_1, HttpDataWraper.getString(getHeader(AWSConstants.USER_AGENT)));

			zlmap.put(Http2Constants.HTTP2_CONN_ID, http2ConnID);
			zlmap.put(Http2Constants.HTTP2_STREAM_ID, streamID);

			LogAPI.log(LogTypes.AWS_ACCESS_LOG_STRING, zlmap);
		}
		catch(Exception e)
		{
			logger.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION_IN_FORMATTING_ACCESSLOGGER, e);
		}
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

	public void removeTimeLine()
	{
		reqtimeline = null;
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


	public boolean isClosed()
	{
		return closed;
	}

	public void setCloseAfterWrite(boolean value)
	{
		closeAfterWrite = value;
	}

	public boolean isCloseAfterWrite()
	{
		return closeAfterWrite;
	}

	public void close()
	{
		if(closed)
		{
			return;
		}
		if(ConfManager.isHttp2LogsEnabled())
		{
			logger.log(Level.INFO, "[Http2 Stream-close-h2ID:"+http2ConnID+"-streamID:"+ streamID +"]");
		}
		AWSInfluxStats.addHttp2Stats(Http2Constants.STATS_H2STREAM, "h2stream_close");

		ConnectionManager.removeStream(http2ConnID, streamID);
		removeFromTimeoutTracker();

		clearData();
		responseDataQueue = null;

		streamState = CLOSED;
		closed = true;
	}

	
	public int getContinuationFrameCounter()
	{
		return continuationFrameCounter;
	}

	public boolean isOnCompletionCallbackSent()
	{
		return onCompletionCallBackSent;
	}

	public void setOnCompletionCallBackSent(boolean onCompletionCallBackSent)
	{
		this.onCompletionCallBackSent = onCompletionCallBackSent;
	}

	private boolean isStreamServlet(String url)
	{
		AbstractWebEngine engine = WebEngine.getEngineByAppName(webengineName);
		return engine.isStreamServlet(url);
	}

	public void setSecurityFilterStatus(boolean status)
	{
		this.securityfilter = status;
	}

	public boolean isSecurityFilterDisabled()
	{
		return this.securityfilter;
	}

	public void setChunked(boolean ischunked) 
	{
		this.chunked = ischunked;
	}

	public boolean isChunked()
	{
		return chunked;
	}
}
