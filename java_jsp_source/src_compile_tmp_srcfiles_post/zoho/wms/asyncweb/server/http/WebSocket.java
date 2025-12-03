//$Id$
package com.zoho.wms.asyncweb.server.http;

//Java import
import java.io.*;
import java.util.HashMap;
import java.util.Arrays;
import java.util.logging.Level;
import java.nio.ByteBuffer;

import com.adventnet.wms.common.ProtocolErrors;
import com.adventnet.wms.common.WMSTypes;
import com.zoho.wms.asyncweb.server.WebEngine;
import com.zoho.wms.asyncweb.server.exception.UnsupportedWSVersionException;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.util.HttpResponseCode;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.WSRequestTimeoutListener;
import com.zoho.wms.asyncweb.server.WSListenerFactory;
import com.zoho.wms.asyncweb.server.exception.AWSException;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogConstants;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.util.Util;
import com.zoho.wms.asyncweb.server.WSPingTimeOutListener;

public abstract class WebSocket extends com.zoho.wms.asyncweb.server.http.HttpServlet
{
	private static AsyncLogger logger = new AsyncLogger(WebSocket.class.getName());

	public HttpRequest request;
	public HttpResponse response;
	public String prd = null;
	private int type = -1;
	private long expiretime = 0l;
	String appname = null;
	private long wspingexpiretime = -1l;
	private long wspinginterval = -1l;
	
	/**
	 * To handle incoming websocket request
	 * @param req - request
	 * @param res - response
	 */
	
	public final void service(HttpRequest req, HttpResponse res) throws IOException 
	{
		try
		{

			logger.addDebugLog(Level.FINE, AWSLogConstants.GS_WEBSOCKET_DETAILS,  WebSocket.class.getName(),AWSLogMethodConstants.SERVICE,new Object[]{req.isWebSocket(), req.getWebSocketKey(), req.getWebSocketVersion(), req.getWebSocketProtocol(), req.getWebSocketExtension(), req.getEngineName(), req.getRequestURL()});

			if(req.isWebSocket())
			{
				if(ConfManager.isWSOffloaderEnabled() || verifyToUpgrade(req,res))
				{
					res.upgradeToWebsocket(req.getWebSocketKey(),req.getWebSocketVersion(),req.getWebSocketSupportedExtension(), getWSSupportedProtocols(req.getWebSocketProtocol()));
					this.wspinginterval = ConfManager.getWSPingTimeOutInterval();
					if(this.wspinginterval >= 0)
					{
						this.wspingexpiretime = System.currentTimeMillis() + this.wspinginterval;
						WSPingTimeOutListener.TRACKER.touch(this.wspingexpiretime, this);
					}
				}
				else
				{
					res.sendError(HttpResponseCode.BAD_REQUEST, HttpResponseCode.BAD_REQUEST_MSG);
					res.close();
					return;
				}
			}

			request = req;
			response = res;
			response.enable();

			try
			{
				prd = req.getHeader(AWSConstants.X_WSINFO).split("-")[0];//No 18N
				type = Integer.parseInt(req.getHeader(AWSConstants.X_WSINFO).split("-")[1].trim());//No I18N
			}
			catch(Exception ex)
			{
			}
			appname = req.getEngineName();
			onConnect(req,res);
			res.setRequestState(StateConstants.REQUEST_ACKNOWLEDGED);
			res.removeRequestTimeOutTracker();
			if(ConfManager.getWSRequestTimeout() > 0)
			{
				expiretime = System.currentTimeMillis()+ConfManager.getWSRequestTimeout();
				WSRequestTimeoutListener.TRACKER.touch(expiretime,this);
			}
		}
		catch(UnsupportedWSVersionException uwex)
		{
			try
			{
				res.sendWSVersionError(HttpResponseCode.BAD_REQUEST,"Unsupported");//No I18N
				res.close();
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",WebSocket.class.getName(),AWSLogMethodConstants.SERVICE, ex);
			}
		}
		catch(Exception exp)
		{
			logger.log(Level.INFO, " Exception ",WebSocket.class.getName(),AWSLogMethodConstants.SERVICE, exp);
			//res.sendError(404,"File Not Found");
			try
			{
				res.sendError(ProtocolErrors.WS_HANDSHAKE_FAIL,"Protocol Error");//No I18N
				res.close();
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",WebSocket.class.getName(),AWSLogMethodConstants.SERVICE, ex);
			}
		}
			
	}

	/**
         * To Update wspinginterval by passing timeinterval in ms
         */

        public void updateWSPingIntervalinMilliSeconds(long wstimeinterval)
        {
                if( wstimeinterval > 15000 )
		{
                	if(this.wspinginterval >= 0)
                	{
				this.wspinginterval = wstimeinterval;
                        	this.wspingexpiretime = System.currentTimeMillis() + this.wspinginterval;
                        	WSPingTimeOutListener.TRACKER.touch(this.wspingexpiretime, this);
                	}

			else
			{
				this.wspinginterval = wstimeinterval;
			}
		}
        }

	/**
	 * To check if response is on hold
	 * @return - true - hold , false - not on hold
	 */
	
	public boolean isOnHold()
	{
		return response.isOnHold();
	}

	/**
	 * To get expire time for the current request
	 * @return - expirytime in milliseconds
	 */
	
	public long getExpireTime()
	{
		return this.expiretime;
	}

	/**
	 * To get response for the current request
	 * @return - response 
	 */
	
	public HttpResponse getResponse()
	{
		return this.response;
	}

	public HttpRequest getRequest()
	{
		return this.request;
	}

	/** 
	 * To check if timeout entry is valid
	 * @return true - valid, false - invalid
	 */
	
	public boolean isInvalidTimeoutEntry(long time)
	{
		return (this.expiretime != time);
	}

	/**
	 * To handle string data read from websocket
	 * @param data - string data
	 */
	
	public final void handleRead(String data) throws IOException
	{
		updateWSPingTime();
		if(data.equals(WMSTypes.NOP))
		{
			if(response!=null && !ConfManager.isNOPForwardToEngine())
			{
				response.ping();
				return;
			}
		}
		onMessage(data);
	}

	/**
	 * To handle binary data read from websocket
	 * @param data - binary data
	 */
	
	public final void handleRead(byte[] data) throws IOException
	{
		updateWSPingTime();
		onMessage(data);
	}

	/**
	 * To handle websocket close
	 */
	
	public final void handleClose()
	{
		try
		{
			try
			{
				if(ConfManager.isWSPrdListenerFactoryEnabled())
				{
					String ukey = prd+"_"+request.getWnetAddress();
					WSListenerFactory.remove(ukey,type,this);
				}
			}
			catch(Exception ex)
			{
			}
			try
			{
				WSRequestTimeoutListener.TRACKER.remove(expiretime,this);
			}
			catch(Exception ex)
			{
			}
			try
			{
				if(this.wspinginterval >= 0)
				{
					WSPingTimeOutListener.TRACKER.remove(this.wspingexpiretime,this);
				}
			}
			catch(Exception ex)
			{
			}
			response = null; // don't remove this else it will be deadlock when impl try to close this
			onClose();
		}
		catch(Exception e)
		{
		}
		finally
		{
			request = null;  // null at the end so that request parameters can be used on onClose
		}
	}

	/**
	 * To write string data to client
	 * @param data - data to be written
	 */
	
	public void write(String data) throws IOException, AWSException
	{
		response.write(data);
	}

	/**
	 * To write binary data to client
	 * @param data - byte array of data 
	 */
	
	public void write(byte[] data) throws IOException, AWSException
	{
		response.writeBinary(data);
	}

	/**
	 * To write binary data to client
	 * @param data - byte array of data 
	 * @param offset - start index of byte array
	 * @param length - length of the bytes to be copied from start index
	 */
	
	public void write(byte[] data,int offset, int length) throws IOException, AWSException
	{
		response.writeBinary(Arrays.copyOfRange(data, offset, offset+length));
	}

	/**
	 * To write sequential binary data to client with index
	 * @param data - byte array of data 
	 * @param index - write sequence
	 */
	
	public void seqWrite(byte[] data, long index) throws IOException, AWSException
	{
		response.seqWrite(data, index);
	}

	/**
	 * To write sequential String data to client with index
	 * @param data - String data
	 * @param index - write sequence
	 */

	public void seqWrite(String data, long index) throws IOException, AWSException
	{
		response.seqWrite(data, index);
	}

	/**
	 * To write sequential binary data to client with index
	 * @param data - byte array of data 
	 * @param offset - start index of byte array
	 * @param length - length of the bytes to be copied from start index
	 * @param index - write sequence
	 */
	
	public void write(byte[] data,int offset, int length, long index) throws IOException, AWSException
	{
		response.seqWrite(Arrays.copyOfRange(data, offset, offset+length), index);
	}

	/**
	 * This function will be called on receiving a request from the client 
	 * @param req - request from the client
	 * @param res - response to be sent to client
	 */
	
	public abstract void onConnect(HttpRequest req, HttpResponse res) throws IOException;
	
	/**
	 * This function will be called on reading data from the client 
	 * @param data - data read
	 */
	
	public abstract void onMessage(String data) throws IOException;

	/**
	 * This function will be called on reading data from the client 
	 * @param data - data read
	 */
	
	public void onMessage(byte[] data) throws IOException
	{
	}

	/**
	 * Callback method will be called upon receiving Ping Frame 
	 * @param data - Application comes along with the ping frame.
	 */

	public abstract void onPingMessage(byte[] data) throws IOException;

	/**
	 * Callback method will be called upon receiving Pong Frame
	 * @param data - Application comes along with the pong frame.
	 */

	public abstract void onPongMessage(byte[] data) throws IOException;

	/**
	 * This function will be called before upgrading to WebSocket.
	 * @param req - request from the client
	 * @param res - response to be sent to client
	 */

	public boolean verifyToUpgrade(HttpRequest req, HttpResponse res) throws IOException
	{
		return true;
	}

	/**
	 * This function will be called to acknowledge a write index in sequence
	 * @param index - written index
	 */

	public void onWriteAck(long index) throws IOException
	{
	}
	
	/**
	 * This function will be called on connection close
	 */

	public abstract void onClose() throws IOException;

	/**
	 * This function will be called on request timeout
	 */
	
	public void onTimeout() throws IOException, AWSException
	{
		//may need to add default response for timeout
		if(response!=null)
		{
			response.close();
		}
	}

	/**
	 * Callback function which will be called on WebSocket Ping timeout 
	 */

	public void onPingTimeOut() throws IOException, AWSException
	{
		//may need to add default response for timeout
		if(response!=null)
		{
			response.close();
		}
	}

	/**
	 * To close the connection safely
	 */
	
	public void close() throws IOException, AWSException
	{
		if(response!=null)
		{
			response.close();
		}
	}

	/**
	 * To get WebSocket product code from x-wsinfo header
	 * @return - wsprd
	 */
	
	public String getWSPrd()
	{
		return prd;
	}

	/**
	 * To get Websocket type from x-wsinfo header
	 * @return - wstype
	 */
	
	public int getWSType()
	{
		return type;
	}

	/**
	 * To get local port of this request.
	 * @return - port
	 */

	public int getLocalPort()
	{
		return request.getLocalPort();
	}

	/**
	 * To get the connection type of this request
	 * @return - connection type
	 */

	public int getConnectionType()
	{
		return request.getConnectionType();
	}

	/**
	 * To check whether this request is on SSL default mode(ie local decryption)
	 * @return boolean
	 */

	public boolean isSSLDefault()
	{
		return request.isSSLDefault();
	}

	/**
	 * To check whether this request is on SSL offloader mode(ie L7 decryption)
	 * @return boolean
	 */

	public boolean isSSLOffloader()
	{
		return request.isSSLOffloader();
	}

	/**
	 * To check whether this request is on non-ssl mode 
	 * @return boolean
	 */

	public boolean isPlain()
	{
		return request.isPlain();
	}

	/**
	 * To get the engine name associated with the websocket
	 * @return - engine name
	 */
	
	public String getEngineName()
	{
		return appname;
	}

	/**
	 * To get the headers list associated with the request
	 * @return - header list
	 */
	
	public HashMap getHeaders()
	{
		return request.getHeaders();
	}

	/** 
	 * To get supported sub-protocols for the websocket connection(Need to be overwritten)
	 * @param wsSupportedProtocols - sub-protocols supported by client
	 * 
	 * @return - sub-protocols choosen
	 */

	public String getWSSupportedProtocols(String wsSupportedProtocols)
	{
		return null;
	}

	public long getWSPingExpireTime()
	{
		return this.wspingexpiretime;
	}

	public void updateWSPingTime()
	{
		if(this.wspinginterval >= 0 && this.wspingexpiretime > 0)
		{
			long oldpingtime = this.wspingexpiretime;
			this.wspingexpiretime = System.currentTimeMillis() + this.wspinginterval;
			WSPingTimeOutListener.TRACKER.update(oldpingtime, this.wspingexpiretime, this);
		}
	}

	public boolean isValidWSPingTimeoutEntry(long time)
	{
		return (this.wspingexpiretime != time);
	}

	/**
	 * Invoke this method to send data as WebSocket Continuation Frames.
	 * @param msg - Application data to be sent over websocket. Will send data as TextFrames
	 * @param isFinalChunk - false, while sending 1st to (n-1) chunks.
				 true, for nth chunk, i.e., if it is the last chunk.
	 */

	protected final void write(String msg, boolean isFinalChunk) throws IOException, AWSException
	{
		if(response == null)
		{
			logger.log(Level.INFO, "Response is Null. Can't able to initiate streamWrite.",WebSocket.class.getName(),AWSLogMethodConstants.WRITE);//No I18n
			return;
		}

		response.wsStreamWrite(msg, isFinalChunk);
	}

	/**
	 * Invoke this method to send data as WebSocket Continuation Frames. i.e., send data in chunks
	 * @param data - Application data to be sent over websocket. Will send data as BinaryFrames
	 * @param isFinalChunk - false, while sending 1st to (n-1) chunk.
				 true, for nth chunk, i.e., if it is the last chunk.
	 */

	protected final void write(byte[] data, boolean isFinalChunk) throws IOException, AWSException
	{
		if(response == null)
		{
			logger.log(Level.INFO, "Response is Null. Can't able to initiate streamWrite.",WebSocket.class.getName(),AWSLogMethodConstants.WRITE);//No I18n
			return;
		}

		response.wsStreamWrite(data, isFinalChunk);
	}

	/**
	 * Invoke this method to send PingFrame with Empty Ping Message.
	 */

	protected final void doPing() throws IOException, AWSException
	{
		doPing(AWSConstants.EMPTY_STRING);
	}

	/**
	 * Invoke this method to send PingFrame along with Application data.
	 * @param pingMsg - Application data to be sent along with ping frame.
	 */

	protected final void doPing(String pingMsg) throws IOException, AWSException
	{
		if(response == null)
		{
			logger.log(Level.FINE, "Response is null. Can't send Ping Message.",WebSocket.class.getName(),AWSLogMethodConstants.DO_PING);
			return;
		}
		if(pingMsg == null || pingMsg.length() <= 0)
		{
			pingMsg = AWSConstants.EMPTY_STRING;
		}
		response.wsStreamWrite(pingMsg, Util.WS_OPCODE_PING, true);
	}

	protected final void doPong() throws IOException, AWSException
	{
		doPong(AWSConstants.EMPTY_STRING);
	}

	protected final void doPong(String pongMsg) throws IOException, AWSException
	{
		if(response == null)
		{
			logger.log(Level.FINE, "Response is null. Can't send Ping Message.",WebSocket.class.getName(),AWSLogMethodConstants.DO_PING);
			return;
		}
		if(pongMsg == null || pongMsg.length() <= 0)
		{
			pongMsg = AWSConstants.EMPTY_STRING;
		}
		response.wsStreamWrite(pongMsg, Util.WS_OPCODE_PONG, true);
	}

	/**
         * this method is to send pong in wcp connection
         */

	public final void doPong(byte[] pongMsg) throws IOException, AWSException
	{
		response.doPong(pongMsg, Util.WS_OPCODE_PONG, true);
	}
}

