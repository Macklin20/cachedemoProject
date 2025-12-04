//$Id$
package com.zoho.wms.asyncweb.server;

// Java import
import java.io.*;
import java.nio.*;
import java.nio.channels.*;

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.*;
import java.security.cert.Certificate;
import javax.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

// Wms import
import com.zoho.wms.asyncweb.server.runtime.BandWidthTracker;
import com.zoho.wms.asyncweb.server.runtime.SuspectedIPsTracker;
import com.zoho.wms.asyncweb.server.runtime.WmsRuntimeCounters;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.http2.Http2Constants;
import com.zoho.wms.asyncweb.server.http2.ConnectionManager;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.exception.IllegalReqException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.ssl.SSLManager;
import com.zoho.wms.asyncweb.server.ssl.SSLManagerFactory;

// constant imports
import static com.zoho.wms.asyncweb.server.AWSConstants.READ;
import static com.zoho.wms.asyncweb.server.AWSConstants.WRITE;


public class AsyncWebSSLClient extends AsyncWebClient 
{

	private static final AsyncLogger LOGGER = new AsyncLogger(AsyncWebSSLClient.class.getName());

	private SSLWrapper ssl;	
	private boolean handshakecompleted = false;//NIO_WRITE
	private boolean closehsinitiated = false;

	public AsyncWebSSLClient(SelectionKey key, int port, long stime) throws IOException 
	{
		super(key,port,stime);
		ssl = new SSLWrapper(key,(SocketChannel)key.channel(),port);
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
			synchronized(writelock)
			{
				if(!connectionInProgress)
				{
					handleNewKeepaliveConnection();
					connectionInProgress = true;
				}

				if(!handshakecompleted)
				{
					startTimeLine(AWSConstants.TL_SSL_HANDSHAKE_TIME,stime);
					if (ssl.doHandshake(key)) 
					{
						
						if(!handshakecompleted)
						{
							endTimeLine(AWSConstants.TL_SSL_HANDSHAKE_TIME);
							LOGGER.addDebugLog(Level.FINE, AWSLogConstants.READ_HANDSHAKE_COMPLETED_FOR_CLIENT, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.READ_DATA,new Object[]{this, getIPAddress()});
						}
						handshakecompleted = true;//NIO_WRITE
					}
					else
					{
						return;
					}
				}

				if(ssl.getDataBuffer()!= null && isHeaderComplete(ssl.getDataBuffer()) && streammode && (reqType.equals(AWSConstants.POST_REQ) || ConfManager.isSupportedPostMethod(reqType)))
				{
					if(isHeaderCheckNeeded())
					{
						if(ConfManager.isHeaderCompletionNeeded())
						{
							notifyProcessor(StateConstants.ON_HEADER_COMPLETION);
						}
						setHeaderCheckNeeded(false);
					}

					ByteBuffer streamBB = ssl.readStreamData();

					if(streamBB == null)
					{
						if(getTimeLineInfo(AWSConstants.TL_UPLOAD_TIME)!=null)
						{
							replaceAndEndTimeLine(AWSConstants.TL_UPLOAD_TIME,AWSConstants.TL_UPLOAD_FAILURE);
						}
						throw new IOException(AWSConstants.READ_MINUS_ONE);//No I18N
					}
					streamBB.limit();
					/*if(!updateAndNotifyPostRead(streamBB,false))
					  {
					  int op = key.interestOps();
					  key.interestOps(SelectionKey.OP_READ|op);
					  intopstime = System.currentTimeMillis();
					  }*/
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
						updateAndNotifyPostRead(streamBB, false);
					}
					if(ssl.isInboundDone())
					{
						throw new IOException("SSL sslEngine error read ");//No I18N
					}
					setReadOps();
					updateRequestHeartBeat();
					streamBB.clear();
					return;
				}
				
				int appdatacount = ssl.read();
				readLength+=appdatacount;

				if(appdatacount > 0)
				{
					zeroReadCount = 0;
					updateTimeLine(AWSConstants.BYTESIN ,(long)(appdatacount));
					AWSInfluxStats.addreadlength(AWSConstants.SSL, false ,AWSConstants.GREATER);
					updateRequestHeartBeat();
				}
				else if(appdatacount < 0)
				{
					if(getTimeLineInfo(AWSConstants.TL_READ_TIME)!=null)
					{
						replaceAndEndTimeLine(AWSConstants.TL_READ_TIME,AWSConstants.TL_READ_FAILURE);
					}
					else
					{
						updateTimeLine(AWSConstants.TL_READ_FAILURE, stime, System.currentTimeMillis());
					}
					AWSInfluxStats.addreadlength(AWSConstants.SSL , false ,AWSConstants.LESSER);
					throw new IOException(AWSConstants.READ_MINUS_ONE);
				}
				else if(appdatacount == 0)
				{
					zeroReadCount ++;
					AWSInfluxStats.addreadlength(AWSConstants.SSL, false ,AWSConstants.EQUAL);
					if(!isHttp2() && maxZeroReadCount > 0 && zeroReadCount > maxZeroReadCount)
					{
						throw new AWSException("[KEY READ COUNT = 0 : MAX CROSSED]");//No I18N
					}
				}

				if(!isWebSocket() && isHeaderComplete(ssl.getDataBuffer()))
				{
					if(isHeaderCheckNeeded())
					{
						if(ConfManager.isHeaderCompletionNeeded())
						{
							notifyProcessor(StateConstants.ON_HEADER_COMPLETION);
						}
						setHeaderCheckNeeded(false);
					}
					if(streammode)
					{
						startTimeLine(AWSConstants.TL_UPLOAD_TIME);
					}
					else
					{
						
						startTimeLine(AWSConstants.TL_READ_TIME);
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
						byteBuffer = ssl.getDataBuffer();
						byteBuffer.flip();
						byte dataRF[] = new byte[byteBuffer.remaining()];
						byteBuffer.get(dataRF, 0, byteBuffer.remaining());
						byteBuffer.clear();
						ssl.clearDataBuffer();
						if(asyncframeproc)
						{
							addWSData(dataRF);
							notifyFrameProcessor();
							/*if(!frameprocessing)
							{
								AsyncFrameProcessor.process(this,ConfManager.isGridPort(this.localport));
							}*/
						}
						else
						{
							wsReadFrame.add(dataRF);
							if(wsReadFrame.isComplete())
							{
								LOGGER.addDebugLog(Level.FINE, AWSLogConstants.READFRAMECOMPLETED, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.READ_DATA,new Object[]{ipaddr,wsReadFrame.getRawPayloadLength()});
								prepareToDispatch();
								if(wsReadFrame.isClosed())
								{
									setReadOrWriteOps();
									return;
								}
							}
							else
							{
								LOGGER.addDebugLog(Level.FINE, AWSLogConstants.READFRAME_NOT_COMPLETE,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.READ_DATA, new Object[]{ipaddr});
							}
						}
					}

					AWSInfluxStats.addHttpBandWidthStats(getScheme(), isHttp2(), READ, appdatacount);
					if(isHttp2())
					{
						byteBuffer = ssl.getDataBuffer();
						byteBuffer.flip();
						byte dataRF[] = new byte[byteBuffer.remaining()];
						byteBuffer.get(dataRF, 0, byteBuffer.remaining());
						byteBuffer.clear();
						ssl.clearDataBuffer();

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
				if(!isHttp2() && requestpayloadsize > 0 && readLength > requestpayloadsize)
                                {
                                        throw new AWSException("SSL REQUEST PAYLOAD SIZE EXCEEDED "+this);//No I18N
                                }

				boolean comp = false;
				if(appdatacount > 0)
				{
					comp = isRequestComplete(ssl.getDataBuffer());
					updateRequestHeartBeat();
				}
				if (!isHttp2() && (appdatacount < 0 || comp))
				{
					if(isValidRequest())
					{
						if(streammode)
						{
							endTimeLine(AWSConstants.TL_UPLOAD_TIME);
						}
						else
						{
							endTimeLine(AWSConstants.TL_READ_TIME);
						}
						ssl.clearDataBuffer();
						notifyProcessor(StateConstants.ON_COMPLETION);
					}
					else
					{
						LOGGER.addDebugLog(Level.FINE, AWSLogConstants.INVALID_REQUEST, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.READ_DATA,new Object[]{this, getIPAddress()});
						if(streammode)
						{
							replaceAndEndTimeLine(AWSConstants.TL_UPLOAD_TIME,AWSConstants.TL_UPLOAD_FAILURE);
						}
						else
						{
							replaceAndEndTimeLine(AWSConstants.TL_READ_TIME,AWSConstants.TL_READ_FAILURE);
						}

						notifyProcessor(StateConstants.ON_WRITEFAILURE);
						close(AWSConstants.INVALID_REQUEST);
						return;
					}
				}
				setReadOrCurrentOps();
			}
		}
		catch(CancelledKeyException cke)
		{
			LOGGER.addExceptionLog(Level.FINE, AWSLogConstants.CANCELLEDKEYEXCEPTION, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.READ_DATA,new Object[]{this, getIPAddress()});
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
			LOGGER.addExceptionLog(Level.FINE,"IOException in read for client "+this+" "+getIPAddress()+" - Detail ",AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.READ_DATA, ex);//No I18N
			notifyProcessor(StateConstants.ON_WRITEFAILURE);
			throw ex;
		}		   
		
	}

	public void writeData(ByteBuffer src) throws IOException, AWSException
	{
		writeData(src,-1,false);
	}

	public void writeData(ByteBuffer src,long index) throws IOException, AWSException
	{
		writeData(src,index,false);
	}

	public void writeData(ByteBuffer bb,long index, boolean error) throws IOException, AWSException
	{
		LOGGER.addDebugLog(Level.FINE, AWSLogConstants.SEND_ERROR_INVOKED_FOR_CLIENT,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.WRITE_DATA, new Object[]{this, getIPAddress()});

		synchronized(writeDataInQueueEnabled ? writeQueueLock : writelock)
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

			refilledtime = System.currentTimeMillis();
			if(refillinvoketime > 0 && ((refilledtime - refillinvoketime) > 100))
			{
				LOGGER.addDebugLog(Level.FINE, AWSLogConstants.DELAY_IN_REFILLING,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.WRITE_DATA, new Object[]{this, refilledtime - refillinvoketime});
			}

			if(ConfManager.isWriteHBEnabled())
			{
				updateRequestHeartBeat();
			}

			writeerror = error;
			writeLength+=bb.limit();
			updateTimeLine(AWSConstants.BYTESOUT , writeLength );//No I18N
			try
			{
				ssl.write(bb, index);
			}
			catch(CancelledKeyException cke)
			{
				LOGGER.addExceptionLog(Level.FINE, AWSLogConstants.CANCELLEDKEYEXCEPTION, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.WRITE_DATA ,new Object[]{this, getIPAddress()});
				if(streammode)
				{
					replaceAndEndTimeLine(AWSConstants.TL_DOWNLOAD_TIME,AWSConstants.TL_DOWNLOAD_FAILURE);
				}
				else
				{
					replaceAndEndTimeLine(AWSConstants.TL_WRITE_TIME,AWSConstants.TL_WRITE_FAILURE);
				}
				notifyProcessor(StateConstants.ON_WRITEFAILURE);
				throw new IOException("Cancelled key");//No I18N
			}
			catch(IOException iex)
			{
				LOGGER.addExceptionLog(Level.FINE, AWSLogConstants.IOEXCEPTION_FOR_CLIENT,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.WRITE_DATA, new Object[]{this, getIPAddress()});
				if(streammode)
				{
					replaceAndEndTimeLine(AWSConstants.TL_DOWNLOAD_TIME,AWSConstants.TL_DOWNLOAD_FAILURE);
				}
				else
				{
					replaceAndEndTimeLine(AWSConstants.TL_WRITE_TIME,AWSConstants.TL_WRITE_FAILURE);
				}
				notifyProcessor(StateConstants.ON_WRITEFAILURE);
				throw iex;//No I18N
			}

			if(!websocket && write_limit > 0 && writeLength>write_limit)//excluding WS as write_limit will be reached by WS easily
			{
				hackLogger.log(Level.INFO,"[HACKLOGGER - SSL HEAVY WRITE]["+toString()+"]["+ipaddr+"][Limit:"+write_limit+"][writeLength:"+writeLength+"] - \r\n--------\r\n"+bb.asCharBuffer()+"\r\n--------\r\n",AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.WRITE_DATA);// No I18N
				AWSInfluxStats.addHeavyDataStats(isWebSocket(), AWSConstants.SSL_HEAVY_WRITE);

				if(streammode)
				{
					replaceAndEndTimeLine(AWSConstants.TL_DOWNLOAD_TIME,AWSConstants.TL_DOWNLOAD_FAILURE);
				}
				else
				{
					replaceAndEndTimeLine(AWSConstants.TL_WRITE_TIME,AWSConstants.TL_WRITE_FAILURE);
				}
				SuspectedIPsTracker.heavyWrite(ipaddr);
				close(AWSConstants.SSL_HEAVY_WRITE);
			}
		}
	}

	public void holdRead() throws IOException
	{
		synchronized(writelock)
		{
			this.onhold = true;
			ssl.holdRead();
		}
	}

	public void enable() throws IOException
	{
		synchronized(writelock)
		{
			this.onhold = false;
			ssl.enable();
		}
	}

	public void readNextRequest()
	{
		synchronized(writelock)
		{
			AWSInfluxStats.addHttpConnectionReuseStat(AWSConstants.HTTP_1_1, getScheme());
			printEndAWSAccessLog(lastWriteTime - firstReadTime);
			ssl.resetBuffers();
			resetRequestData();
			setReadOps();
		}
	}

	public void updateInternalHit()
	{
	}

	public void updateExternalHit(String domain)
	{
		try
		{
			WmsRuntimeCounters.getDomainHit(domain).updateExternalHttpsHit();
		}
		catch(Exception ex)
		{
		}
	}

	//NIO_WRITE
	public void handleWrite(SelectionKey key) throws IOException , CancelledKeyException, AWSException, IllegalReqException, Exception
	{
		if(this.key == null)
		{
			this.key = key;
		}

		synchronized(writelock)
		{
			if(!handshakecompleted)
			{
				try
				{
					if(!handshakecompleted)
					{
						startTimeLine(AWSConstants.TL_SSL_HANDSHAKE_TIME);
					}

					if(ssl.doHandshake(key))
					{
						if(!handshakecompleted)
						{
							endTimeLine(AWSConstants.TL_SSL_HANDSHAKE_TIME);
							LOGGER.addDebugLog(Level.FINE, AWSLogConstants.HANDSHAKE_COMPLETED_FOR_CLIENT,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.HANDLE_WRITE, new Object[]{this, getIPAddress()});
						}
						handshakecompleted = true;
						readData(key);
					}
				}
				catch(CancelledKeyException cke)
				{
					LOGGER.addExceptionLog(Level.FINE, AWSLogConstants.CANCELLEDKEYEXCEPTION,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.HANDLE_WRITE, new Object[]{this, getIPAddress()});
					throw cke;
				}
				catch(IOException ex)
				{
					LOGGER.addExceptionLog(Level.FINE,"IOException in handleWrite for client "+this+" "+getIPAddress()+" . Yet to complete handshake - Detail ", AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.HANDLE_WRITE,ex);//No I18N
					throw new SSLHandshakeException(ex.getMessage());
				}
			}
			else
			{
				try
				{
					boolean needfill = ssl.handleWrite(key);
					if(needfill && (outputDataSize.get() > 0 || chunked))
					{
						if(!onhold)
						{
							setReadOps();
						}
						refillinvoketime = System.currentTimeMillis();
						notifyProcessor(StateConstants.ON_OUTPUTBUFFERREFILL);
					}
					else
					{
						if(writeerror)
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
							close(AWSConstants.WRITEERROR);
						}
						else
						{
							int writestatus = ssl.getWriteStatus();

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
								else if(ssl.isReinitSet())
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
								else if(ssl.isCloseOptionSet())
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
							else if(!onhold)
							{
								setReadOps();
							}
						}
					}
				}
				catch(CancelledKeyException cke)
				{
					LOGGER.addExceptionLog(Level.FINE, AWSLogConstants.CANCELLEDKEYEXCEPTION,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.HANDLE_WRITE, new Object[]{this, getIPAddress()});
					if(ssl.getWriteStatus()  != AWSConstants.WRITE_COMPLETED)
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
					throw new IOException("Cancelled Key");//No I18N
				}
				catch(IOException ex)
				{
					LOGGER.addExceptionLog(Level.FINE, "IOException in handleWrite for client "+this+" "+getIPAddress()+" - Detail ",AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.HANDLE_WRITE, ex);//No I18N
					if(ssl.getWriteStatus()  != AWSConstants.WRITE_COMPLETED)
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
	}

	public boolean isWritePending() throws IOException, AWSException
	{
		if(!this.key.isValid())
		{
			throw new IOException(AWSConstants.CLOSED);
		}
		int writestatus = ssl.getWriteStatus();
		if(writestatus == AWSConstants.WRITE_COMPLETED)
		{
			if(writeerror)
			{
				throw new IOException(AWSConstants.CLOSED);
			}
			else
			{
				if(ssl.isCloseOptionSet())
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
			if(ssl.isCloseOptionSet())
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

	public void close(String reason)
	{
		LOGGER.addDebugLog(Level.FINE, AWSLogConstants.ON_CLOSE_FOR_CLIENT+" [Reason]["+reason+"]",AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE, new Object[]{this, getIPAddress()});
		if(closed)
		{
			LOGGER.addDebugLog(Level.FINE, AWSLogConstants.CONNECTION_ALREADY_CLOSED, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE, new Object[]{getIPAddress(), this});
			return;
		}
		synchronized(writelock)
		{
			long requestTimeTaken = (firstReadTime == -1) ? -1 : ((lastWriteTime == -1) ? (System.currentTimeMillis() - firstReadTime) : (lastWriteTime - firstReadTime));
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
				LOGGER.log(Level.SEVERE, "[Exception - Http2 AsyncWebSSLClient-close-http2ConnID:"+http2ConnID+"-streamID:NA] - ERROR while closing Http2Connection");
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
					//LOGGER.log(Level.INFO, " Exception ", ex);
				}
			}
			try
			{
				if(ConfManager.isPrintIncompleteRequestEnabled(ipaddr) && getRequestURL() == null)
				{
					LOGGER.log(Level.INFO, "INCOMPLETE REQUEST (SSL): IP {0}, data received : {1}",AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE, new Object[]{getIPAddress(), new String(byteBuffer.array()).trim()});
				}
			}
			catch(Exception exp)
			{
				LOGGER.addExceptionLog(Level.SEVERE, AWSLogConstants.EXCEPTION_IN_INCOMPLETE_REQUEST_DEBUG_LOG,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE,  exp);
			}

			if(ConfManager.isCloseReadDebugEnabled(getIPAddress().split(":")[0]))
			{
				int readcount = 0;

				try
				{
					//ByteBuffer bb = ByteBuffer.allocate(10);
					readcount = ((SocketChannel)key.channel()).read(byteBuffer);
					if(readcount > 0)
					{
						BandWidthTracker.updateHttpsRead(readcount);
					}
				}
				catch(Exception ex)
				{
					LOGGER.addExceptionLog(Level.FINE, " FAILURE TO READ AT CLOSE FOR "+getIPAddress(),AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE,  ex);
				}
			
				LOGGER.log(Level.INFO, "ON CLOSE ->  client IP : {0}. Read count : {1}"+" [Reason]["+reason+"]",AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE, new Object[]{getIPAddress(), readcount});//No I18N
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

			try
			{
				updateSSLStats();
			}
			catch(Exception ex)
			{
			}

			try
			{
				try	
				{
					ssl.close();
				}
				catch(CancelledKeyException cke )
				{
					LOGGER.addExceptionLog(Level.FINE, AWSLogConstants.CANCELLEDKEYEXCEPTION,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE,  new Object[]{this, getIPAddress()});
				}
				catch(IOException ex)
				{
					LOGGER.addExceptionLog(Level.FINE,"IOException in close for client "+this+" "+getIPAddress()+", Message ",AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE,  ex);//No I18N
				}
				catch(Exception ex)
				{
					LOGGER.addExceptionLog(Level.FINE,"Exception in SSL Close "+this+" Message ",AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE,  ex);//No I18N
				}
				SocketChannel ch = null;
				try
				{
					ch = (SocketChannel)key.channel();
				}
				catch(Exception ex)
				{
					LOGGER.addExceptionLog(Level.FINE, AWSLogConstants.CHANNEL_TYPE_CASE_EXP, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE, ex);
				}
				try
				{
					key.cancel();
				}
				catch(Exception ex)
				{
					LOGGER.addExceptionLog(Level.FINE,"Exception in key cancel "+this+" , ", AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE, ex);//No I18N
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
					LOGGER.addExceptionLog(Level.FINE,"Exception in key channel close for client "+this+" "+getIPAddress()+" , ", AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE, ex);//No I18N
				}
				byteBuffer.clear();
			}
			catch(Exception e)
			{
				//LOGGER.log(Level.INFO, " Exception ", e);			
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
			if(isHeaderComplete())
			{
				printEndAWSAccessLog(requestTimeTaken);
			}
			if(!end_notified)
			{
				int writestatus = ssl.getWriteStatus();
				if(writestatus  == AWSConstants.WRITE_COMPLETED)
				{
					try
					{
						if(streammode)
						{
							endTimeLine(AWSConstants.TL_DOWNLOAD_TIME);
						}
						else
						{
							endTimeLine(AWSConstants.TL_WRITE_TIME);
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
						LOGGER.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.CLOSE, ex);
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

	public void reinit()
	{
		synchronized(writelock)
                {
			LOGGER.addDebugLog(Level.FINE, AWSLogConstants.SET_REINIT, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.REINIT, new Object[]{this});
			ssl.reinit();
		}
	}
		
	public boolean isReinitSet()
	{
		return ssl.isReinitSet();
	}

	public void setCloseAfterWrite()
	{
		synchronized(writelock)
		{
			LOGGER.addDebugLog(Level.FINE, AWSLogConstants.SET_CLOSE_BOOLEAN, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.SET_CLOSE_AFTER_WRITE, new Object[]{this});
			ssl.setCloseAfterWrite();
			invokeWriteIfNeeded();
		}
	}

	public boolean isWriteInitiated()
	{
		return ssl.writeInitiated;
	}

	public boolean isWriteComplete()
	{
		int writeStatus = ssl.getWriteStatus();
		if(writeStatus==AWSConstants.WRITE_COMPLETED)
		{
			return true;
		}
		return false;
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
				int writestatus = ssl.getWriteStatus();

				if(writestatus  == AWSConstants.WRITE_COMPLETED)
				{
					try
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
					catch(Exception ex)
					{
						LOGGER.addExceptionLog(Level.INFO, AWSLogConstants.EXCEPTION,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.INVOKE_WRITE_IF_NEEDED,  ex);
					}
				}
				else if(writestatus  == AWSConstants.WRITE_FAILURE)
				{
					close(AWSConstants.WRITESTATUS_FAILURE);
				}
				else if(writestatus == AWSConstants.WRITE_IN_PROGRESS)
				{
					setWriteOps();
				}
				else
				{
					LOGGER.addDebugLog(Level.FINE, AWSLogConstants.ABNORMAL_SCENARIO,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.INVOKE_WRITE_IF_NEEDED, new Object[]{this});
					setReadOps();
				}
			}
			catch(CancelledKeyException cke)
			{
				LOGGER.addExceptionLog(Level.FINE, AWSLogConstants.CANCELLEDKEYEXCEPTION, AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.INVOKE_WRITE_IF_NEEDED,new Object[]{this, getIPAddress()});
				close(AWSConstants.CANCELLED_KEY_EXCEPTION_WRITE);
			}
			catch(Exception ex)
			{
				LOGGER.addExceptionLog(Level.FINE, AWSLogConstants.EXCEPTION_FOR_CLIENT,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.INVOKE_WRITE_IF_NEEDED, new Object[]{this, getIPAddress()});
				close(AWSConstants.EXCEPTION_WRITE);
			}
		}
	}

	public ArrayList getFailedIndexList()
	{
		ArrayList al = null;
		synchronized(writelock)
		{
			al = ssl.getFailedIndexList();
		}
		return al;
	}

	public void setOutputDataSize(long size)
	{
		this.outputDataSize.addAndGet(size);
		ssl.setOPDataSize(size);
	}

	public boolean isCloseBooleanSet()
	{
		return ssl.isCloseBooleanSet();
	}

	public Certificate[] getPeerCertificates()
	{
		if(ssl!=null)
		{
			return ssl.getPeerCertificates();
		}

		return null;
	}

	public X509Certificate[] getPeerCertificateChain()
	{
		if(ssl!=null)
		{
			return ssl.getPeerCertificateChain();
		}

		return null;
	}

	class SSLWrapper
	{
		private String ipaddr = null;
		private SocketChannel sc;
		private SelectionKey key;
		private ByteBuffer dataBuffer;
		private SSLEngine sslEngine = null;
		private int appSize;
		private int netSize;
		private ByteBuffer inBB;
		private ByteBuffer outBB;
		private ByteBuffer handShakeBB = ByteBuffer.allocate(0);
		private HandshakeStatus sslHSStatus;
		private boolean sslHSStarted;
		private boolean sslHSComplete;
		private int writecapacity = ConfManager.getWriteCapacity();//NIO_WRITE
		private ByteBuffer writeBB = ByteBuffer.allocate(writecapacity);
		private int remoteport = -1;
		private boolean closeafterwrite = false;//NIO_WRITE
		private AtomicBoolean reinit = new AtomicBoolean();
		private final AsyncLogger logger = new AsyncLogger(SSLWrapper.class.getName());

		private long writtenlength = 0;
		private long outputDataSize = 0;
		private boolean writeInitiated = false;
		private long givenwritedatalength = 0;
     		private ConcurrentLinkedQueue<ByteIndex> indexList = new ConcurrentLinkedQueue();
		private boolean msgretryenabled = ConfManager.isMessageRetryEnabled();

		public SSLWrapper(SelectionKey key,SocketChannel sc,int port) throws IOException 
		{
			this.key = key;
			this.sc = sc;
			ipaddr = ((SocketChannel)key.channel()).socket().getInetAddress().getHostAddress();
			remoteport = ((SocketChannel)key.channel()).socket().getPort();
			sc.configureBlocking(false);
			SSLManager sslManager = SSLManagerFactory.getSSLManager();
                	sslEngine = sslManager.getSSLEngine(port,ipaddr,remoteport);

			if(ConfManager.isCipherOrderEnabled())
			{
				final SSLParameters sslParameters = sslEngine.getSSLParameters();
				sslParameters.setUseCipherSuitesOrder(true);
				sslEngine.setSSLParameters(sslParameters);
			}

			//sslEngine.setEnabledCipherSuites(new String[]{"SSL_RSA_WITH_RC4_128_SHA"});
			//sslEngine.setEnabledCipherSuites(new String[]{"TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256","TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA","TLS_RSA_WITH_AES_128_CBC_SHA256","TLS_RSA_WITH_AES_128_CBC_SHA","SSL_RSA_WITH_RC4_128_SHA","TLS_RSA_WITH_AES_128_CBC_SHA"});
			sslEngine.setUseClientMode(false);

			if(ConfManager.isClientAuthNeeded(""+port))
			{
				sslEngine.setNeedClientAuth(true);
			}

			if(ConfManager.isClientAuthWanted(""+port))
			{
				sslEngine.setWantClientAuth(true);
			}

			netSize = sslEngine.getSession().getPacketBufferSize();
			if(ConfManager.getSSLNetSize() > netSize)
			{
				netSize = ConfManager.getSSLNetSize();
			}

			appSize = sslEngine.getSession().getApplicationBufferSize();
			if(ConfManager.getSSLAppSize() > appSize)
			{
				appSize = ConfManager.getSSLAppSize();
			}

			sslHSStatus = HandshakeStatus.NEED_UNWRAP;
			sslHSComplete = false;
			inBB = ByteBuffer.allocate(netSize);
			outBB = ByteBuffer.allocate(netSize);
			outBB.position(0);
			outBB.limit(0);
			dataBuffer = ByteBuffer.allocate(appSize);
		}

		public void resetBuffers()
		{
			inBB = ByteBuffer.allocate(netSize);
			outBB = ByteBuffer.allocate(netSize);
			outBB.position(0);
			outBB.limit(0);
			dataBuffer = ByteBuffer.allocate(appSize);
			writtenlength = 0;
			outputDataSize = 0;
			writeInitiated = false;
			givenwritedatalength = 0;
			indexList = new ConcurrentLinkedQueue<ByteIndex>();
			closeafterwrite = false;
			reinit.set(false);
			writeBB = ByteBuffer.allocate(writecapacity);
		}

		private void resizeDataBB() 
		{	
			if (dataBuffer.remaining() < appSize) 
			{
				ByteBuffer bb = ByteBuffer.allocate(dataBuffer.capacity() * 2);
				dataBuffer.flip();
				bb.put(dataBuffer);
				dataBuffer = bb;
			}
		}

		public boolean isInboundDone()
		{
			return sslEngine.isInboundDone();
		}

		public void setOPDataSize(long size)
		{
			this.outputDataSize += size;
		}

		public void setCloseAfterWrite()//NIO_WRITE
		{
			this.closeafterwrite = true;
		}

		public void reinit()
		{
			reinit.set(true);
		}

		public boolean isReinitSet()
		{
			return reinit.get();
		}

		public void holdRead() throws IOException
		{
			if((key.interestOps() & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)
			{
				setWriteOps();
			}
			else
			{
				setZeroOps();
			}
		}

		public void enable() throws IOException
		{
			setReadOrCurrentOps();
		}

		public boolean isCloseBooleanSet()
		{
			return this.closeafterwrite;
		}

		private int flushData() throws IOException
		{
			int count = sc.write(outBB);
			AWSInfluxStats.addHttpBandWidthStats(getScheme(), isHttp2(), WRITE, count);
			lastWriteTime = System.currentTimeMillis();
			BandWidthTracker.updateHttpsWrite(count);
			updateWriteDataCounter(count);
			return count;
		}

		private boolean flush() throws IOException 
		{
			int written = sc.write(outBB);
			AWSInfluxStats.addHttpBandWidthStats(getScheme(), isHttp2(), WRITE, written);
			lastWriteTime = System.currentTimeMillis();
			BandWidthTracker.updateHttpsWrite(written);
			updateWriteDataCounter(written);
			return !outBB.hasRemaining();
		}

		private boolean flush(ByteBuffer scBB) throws IOException 
		{
			int count = sc.write(scBB);
			AWSInfluxStats.addHttpBandWidthStats(getScheme(), isHttp2(), WRITE, count);
			lastWriteTime = System.currentTimeMillis();
			BandWidthTracker.updateHttpsWrite(count);
			updateWriteDataCounter(count);
			return !scBB.hasRemaining();
		}

		public Certificate[] getPeerCertificates()
		{
			try
			{
				return sslEngine.getSession().getPeerCertificates();
			}
			catch(SSLPeerUnverifiedException ex)
			{
			}

			return null;
		}

		public X509Certificate[] getPeerCertificateChain()
		{
			try
			{
				return sslEngine.getSession().getPeerCertificateChain();
			}
			catch(SSLPeerUnverifiedException ex)
			{
			}

			return null;
		}

		public boolean doHandshake(SelectionKey sk) throws IOException,CancelledKeyException, AWSException
		{
			SSLEngineResult result;
			if (sslHSComplete) 
			{
				if((writeBB.position() > 0) || outBB.hasRemaining() || writeQueueDataSize.intValue() > 0)//NIO_WRITE
				{
					setWriteOps();
				}
				return sslHSComplete;
			}

			if (outBB.hasRemaining()) 
			{
				if (!flush()) 
				{
					setWriteOps();
					return false;
				}

				switch (sslHSStatus) 
				{

					case FINISHED:
						{
							sslHSComplete = true;
						}

					case NEED_UNWRAP:
						if (sk != null) 
						{
							setReadOps();
						}
						break;
					case NEED_WRAP:
						if(sk != null)
						{
							setWriteOps();
						}
						break;
				}

				return sslHSComplete;
			}

			switch (sslHSStatus) 
			{

				case NEED_UNWRAP:

					int count = sc.read(inBB);

					if (count == -1) 
					{
						sslEngine.closeInbound();
						throw new IOException("Handshake Read -1");//No I18N
					}
					else if(count > 0)
					{
						updateReadDataCounter(count);
						BandWidthTracker.updateHttpsRead(count);
					}
					
					if(!sslHSStarted && ConfManager.isProxyProtocolEnabled() && !proxyHeaderComplete && proxyHeader.isPresent(inBB))
					{
						proxyHeaderComplete = proxyHeader.process(inBB);
						if(proxyHeaderComplete)
						{
							byte[] b = Arrays.copyOfRange(inBB.array(), proxyHeader.getLength(), inBB.position());
                                                        inBB = ByteBuffer.allocate(netSize);
                                                        inBB.put(b);
							String ip = proxyHeader.getSourceIP();
							String port = proxyHeader.getSourcePort();

							if(ip!=null && port!=null)
							{
								logger.log(Level.INFO,AsyncWebSSLClient.this+" Proxy header of length "+proxyHeader.getLength()+" is processed. Replacing the client IP "+AsyncWebSSLClient.this.ipaddr+" and port "+AsyncWebSSLClient.this.sockport+" with the IP "+ip+" and port "+port+" in the proxy header.",SSLWrapper.class.getName(),AWSLogMethodConstants.DO_HANDSHAKE);
								AsyncWebSSLClient.this.ipaddr = ip;
								AsyncWebSSLClient.this.sockport = Integer.parseInt(port);
							}
							else
							{
								logger.log(Level.INFO,AsyncWebSSLClient.this+" Proxy header of length "+proxyHeader.getLength()+" is processed. Yet not using IP address mentioned(The address type must be of UNSPEC type).",SSLWrapper.class.getName(),AWSLogMethodConstants.DO_HANDSHAKE);
							}
						}
						else
						{
							logger.log(Level.INFO,AsyncWebSSLClient.this+" Proxy header of length "+proxyHeader.getLength()+" incomplete as sent by the client "+AsyncWebSSLClient.this.ipaddr+":"+AsyncWebSSLClient.this.sockport+" : "+new String(Arrays.copyOfRange(inBB.array(),0,proxyHeader.getLength()))+" . Closing the connection .",SSLWrapper.class.getName(),AWSLogMethodConstants.DO_HANDSHAKE);
							AsyncWebSSLClient.this.close(AWSConstants.CLOSE_SSL_HANDSHAKE);
							return false;
						}
					}
					else
					{
						sslHSStarted = true;
					}

	repeat:
					while (sslHSStatus == HandshakeStatus.NEED_UNWRAP) 
					{
						
						inBB.flip();
						result = sslEngine.unwrap(inBB, dataBuffer);
						inBB.compact();


						sslHSStatus = result.getHandshakeStatus();

						switch (result.getStatus()) 
						{

							case OK:
								switch (sslHSStatus) 
								{
									case NOT_HANDSHAKING:
										throw new IOException("SSL - Invalid handshake");//No I18N

									case NEED_TASK:
										sslHSStatus = doTasks();
										break;

									case FINISHED:
										{
											sslHSComplete = true;
											break repeat;
										}
								}

								break;

							case BUFFER_UNDERFLOW:

								if (sk != null) 
								{
									setReadOps();
								}
								break repeat;

							case CLOSED:
								sslEngine.closeInbound();
								
							default: 
								throw new IOException("SSL - Invalid handshake " + result.getStatus());//No I18N
						}
					}  


					if (sslHSStatus != HandshakeStatus.NEED_WRAP) 
					{
						break;
					}


				case NEED_WRAP:
					outBB.clear();
					result = sslEngine.wrap(handShakeBB, outBB);
					outBB.flip();

					sslHSStatus = result.getHandshakeStatus();

					switch (result.getStatus()) 
					{
						case OK:

							if (sslHSStatus == HandshakeStatus.NEED_TASK) 
							{
								sslHSStatus = doTasks();
							}

							if (sk != null) 
							{
								setWriteOps();
							}

							break;
							
						default: 
							throw new IOException("SSL Invalid handshake " + result.getStatus());//No I18N
					}
					break;
				case FINISHED:
					{
						sslHSComplete = true;
						break;
					}
				default: 
					throw new RuntimeException("SSL Invlaid hand shake state" + sslHSStatus);//No I18N
			} 

			return sslHSComplete;
		}


		private SSLEngineResult.HandshakeStatus doTasks() 
		{
			Runnable runnable;
			while ((runnable = sslEngine.getDelegatedTask()) != null) 
			{
				runnable.run();
			}
			return sslEngine.getHandshakeStatus();
		}

		public int read() throws IOException, AWSException
		{
			SSLEngineResult result;

			if (!sslHSComplete) 
			{
				throw new IllegalStateException();
			}

			int pos = dataBuffer.position();

			if(pos > getMaxDataPerWSRead())
			{
				hackLogger.log(Level.INFO,"[HACKLOGGER - SSL HEAVY READ]["+toString()+"]["+ipaddr+"][Limit:"+getMaxDataPerWSRead()+"][Current Buffer Position:"+pos+"]",SSLWrapper.class.getName(),AWSLogMethodConstants.READ);//No I18N
				AWSInfluxStats.addHeavyDataStats(isWebSocket(), AWSConstants.SSL_HEAVY_READ);
				throw new AWSException("Exceeds Read Limit : Current Buffer Position : "+pos);//No I18N
			}
			
			int count = sc.read(inBB);
		
			if (count == -1)
			{
				sslEngine.closeInbound();
				return -1;
			}
			else if(count > 0)
			{
				BandWidthTracker.updateHttpsRead(count);
				updateReadDataCounter(count);
			}

			do 
			{
				resizeDataBB();    
				inBB.flip();
				result = sslEngine.unwrap(inBB, dataBuffer);
				inBB.compact();

				switch (result.getStatus()) 
				{

					case BUFFER_UNDERFLOW:
				
					case OK:
						if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) 
						{
							doTasks();
						}
						break;
			
					case CLOSED:
						sslEngine.closeInbound();

					default:
						throw new IOException("SSL sslEngine error read " +result.getStatus());//No I18N
				}
			} while ((inBB.position() != 0) && result.getStatus() != Status.BUFFER_UNDERFLOW);
			return (dataBuffer.position() - pos);
		}

		public ByteBuffer readStreamData() throws IOException, AWSException
		{
			SSLEngineResult result;

			if (!sslHSComplete) 
			{
				throw new IllegalStateException();
			}

			ByteBuffer bb = ByteBuffer.allocate(appSize*2);
			int count = sc.read(inBB);

			if (count == -1)
			{
				sslEngine.closeInbound();
				AWSInfluxStats.addreadlength(AWSConstants.SSL, true ,AWSConstants.LESSER);
				return null;
			}
			else if(count > 0)
			{
				updateReadDataCounter(count);
				BandWidthTracker.updateHttpsRead(count);
				AWSInfluxStats.addreadlength(AWSConstants.SSL, true , AWSConstants.GREATER);
			}

			int loop = 0;
			do 
			{
				//resizeStreamDataBB(bb);    
				inBB.flip();
				result = sslEngine.unwrap(inBB, bb);
				inBB.compact();
				switch (result.getStatus()) 
				{

					case BUFFER_UNDERFLOW:
					case OK:
						if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) 
						{
							doTasks();
						}
						break;
					case CLOSED:
						sslEngine.closeInbound();
						break;
					default:
						throw new IOException("SSL sslEngine closed" +result.getStatus());//No I18N
				}
				if((++loop)>=50)
				{
					logger.log(Level.SEVERE,"[SSL STREAM READ - FAILURE - IN LOOP] bb details: [size- {0}, pos-{1}, limit-{2}], inBB details: [size-{3}, pos-{4}, limit-{5}], Unwrap results: {6}, Handshake results: {7}",SSLWrapper.class.getName(),AWSLogMethodConstants.READ_STREAM_DATA,new Object[]{bb.capacity(), bb.position(), bb.limit(), inBB.capacity(), inBB.position(), inBB.limit(), result.getStatus(), result.getHandshakeStatus()});//No I18N
					throw new AWSException("SSL STREAM READ - FAILURE - In loop - "+result.getStatus());//No I18N
				}
			} while ((inBB.position() != 0) && result.getStatus() != Status.BUFFER_UNDERFLOW && result.getStatus() != Status.CLOSED);

			return bb;
		}


		public ByteBuffer getDataBuffer() 
		{
			return dataBuffer;
		}

		public void clearDataBuffer()
		{
			dataBuffer.clear();
		}

		public void write(ByteBuffer src, long index) throws IOException, CancelledKeyException, AWSException //NIO_WRITE
		{
			if(!sslHSComplete)
			{
				throw new IllegalStateException("SSL Invalid Handshake state Write");
			}

			int dataSize = src.limit();
			givenwritedatalength += dataSize;
			if(!writeInitiated)
			{
				writeInitiated = true;
				outputDataSize += dataSize;
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
			addIndex(dataSize,index);
			setWriteOps();
		}

		public boolean handleWrite(SelectionKey key) throws IOException, CancelledKeyException, AWSException //NIO_WRITE
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

			boolean write = true;
			while(write)
			{
				if (outBB.hasRemaining() && !flush())
				{
					if(key != null)
					{
						setWriteOps();
					}

					if(writeBB.position() == 0 && writeQueueDataSize.intValue() <= 0)
					{
						if(writtenlength < outputDataSize)
						{
							return (true && writeInitiated);
						}
						else if(chunked)
						{
							if(writtenlength == outputDataSize)
							{
								if(isReinitSet())
								{		
									return false;
								}
								else	
								{
									return (true && writeInitiated);
								}
							}
						}
					}
					else
					{
						return false;
					}
				}

				int retValue = 0;

				SSLEngineResult result = null;
				if(writeBB.position() > 0 )
				{
					writeBB.flip();
					outBB.clear();
					result = sslEngine.wrap(writeBB,outBB);
					retValue = result.bytesConsumed();
					removeIndex(retValue);
					writtenlength += retValue;
					AWSInfluxStats.addWrittenlength(AWSConstants.SSL,AWSConstants.GREATER);
					if(result != null)
					{

						switch (result.getStatus())
						{

							case OK:
								writeBB.compact();
								boolean proceed = true;
								outBB.flip();
								while(outBB.hasRemaining() && proceed)
								{
									if(flushData() <= 0)
									{
										proceed = false;
									}
								}

								if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK)
								{
									doTasks();
								}
								break;
							case BUFFER_OVERFLOW:
								write = false;
								break;
							case BUFFER_UNDERFLOW:
								write = false;
								break;
							default:
								throw new IOException("SSL slEngine error write " +result.getStatus());//No i18N
						}
					}
				}
				else
				{
					write = false;
					AWSInfluxStats.addWrittenlength(AWSConstants.SSL,AWSConstants.LESSER_OR_EQUAL);
				}
			}
			if(writeBB.position() == 0 && writeQueueDataSize.intValue() <= 0)
			{
				if(writtenlength < outputDataSize)
				{
					return (true && writeInitiated);
				}
				else if(chunked)
				{
					if(writtenlength == outputDataSize)
					{
						if(isReinitSet())
						{		
							return false;
						}
						else	
						{
							return (true && writeInitiated);
						}
					}
				}
			}
			return false;
		}

		public int getWriteStatus() //NIO_WRITE
		{
			if(writeBB.position() == 0 && !outBB.hasRemaining() && (writtenlength == outputDataSize) && writeInitiated && writeQueueDataSize.intValue() <= 0)
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
			if(writtenlength > outputDataSize || givenwritedatalength > outputDataSize)
			{
				return AWSConstants.WRITE_FAILURE;
			}
			if(writeBB.position() > 0 || outBB.hasRemaining() || writeQueueDataSize.intValue() > 0)
			{
				return AWSConstants.WRITE_IN_PROGRESS;
			}
			return AWSConstants.WRITE_IDLE;
		}

		public boolean isCloseOptionSet()
		{
			return closeafterwrite;
		}
	
		public boolean close() throws IOException, CancelledKeyException 
		{
			try
			{
				if(!closehsinitiated)
				{
					if(!ConfManager.isSSLSessionResumptionEnabled())
					{
						sslEngine.getSession().invalidate();
					}
					if(!sslEngine.isInboundDone())
                        		{
                                		sslEngine.closeInbound();
                        		}
					sslEngine.closeOutbound();
					SSLEngineResult result = null;
					outBB.clear();
					result = sslEngine.wrap(handShakeBB, outBB);
					outBB.flip();
					closehsinitiated = true;	
					flush(outBB);
				}
				else if (outBB.hasRemaining()) 
				{
					flush(outBB);
				}
				if(outBB.hasRemaining())
				{
					setReadOrWriteOps();
					return false;
				}
				return sslEngine.isOutboundDone() && !outBB.hasRemaining();
			}
			catch(Exception ex)
			{	
				return true;
			}	
		}

		public void addIndex(int size, long index)
		{
			if(!msgretryenabled || size == 0)
			{
				return;
			}
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
				logger.addExceptionLog(Level.FINE, AWSLogConstants.EXCEPTION_IN_REMOVEINDEX,AsyncWebSSLClient.class.getName(),AWSLogMethodConstants.REMOVE_INDEX, ex);
			}
		}

		public ArrayList getFailedIndexList()
		{
			ArrayList failedlist = new ArrayList();
			Iterator itr = indexList.iterator();
			while(itr.hasNext())
			{
				ByteIndex bi = (ByteIndex)itr.next();
				if(bi.getIndex() != -1)
				{
					failedlist.add(bi.getIndex());
				}	
			}
			return failedlist;
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
	}
}
