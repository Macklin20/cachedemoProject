package com.zoho.wms.asyncweb.server.http2;

// Java import
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Level;

// Wms import
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.util.StateConstants;
import com.zoho.wms.asyncweb.server.exception.Http2Exception;


public class Http2Util
{
	public static AsyncLogger logger = new AsyncLogger(Http2Util.class.getName());

	/*
	public static String getBase64URLEncoded(String data) throws Exception
	{
		return getBase64URLEncoded(data.getBytes("UTF-8"));
	}

	public static String getBase64URLEncoded(byte[] data) throws Exception
	{
		//return new String(Base64.encodeBase64URLSafe(data));
		return null;
	}
	
	public static String getBase64URLDecoded(String data) throws Exception
	{
		return getBase64URLDecoded(data.getBytes("UTF-8"));
	}

	public static String getBase64URLDecoded(byte[] data) throws Exception
	{
		//return new String(Base64.decodeBase64(data));
		return null;
	}
	*/

	public static String getString(byte[] b)
	{
		StringBuilder sb = new StringBuilder();

		for(int i = 0; i < b.length ; i++)
		{
			sb.append((char)(b[i]&0xFF));
		}

		return sb.toString();
	}

	public static StringBuilder getStringBuilder(StringBuilder sb, byte[] b)
	{
		for(int i = 0; i < b.length ; i++)
		{
			sb.append((char)(b[i]&0xFF));
		}

		return sb;
	}

	public static String binaryToHex(byte[] bArray)
	{
		StringBuilder sb = new StringBuilder();
		for(byte b : bArray)
		{
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	public static byte[] convertHexToByteArray(String s)
	{
		int len = s.length();
		byte[] b = new byte[len / 2];
		for (int i = 0; i < len; i += 2)
		{
			b[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
		}
		return b;
	}

	public static int getLengthInOctets(String value)
	{
		byte[] b = new byte[0];
		try
		{
			b=value.getBytes("us-ascii");
		}
		catch(Exception ex)
		{
//			ex.printStackTrace();
		}
		return b.length;
	}

	public static int getTwoBytes(ByteArrayInputStream bais)
	{
		return ((bais.read() & 0xFF) << 8) + (bais.read() & 0xFF);
	}

	public static int getThreeBytes(ByteArrayInputStream bais)
	{
		return ((bais.read() & 0xFF) << 16) + ((bais.read() & 0xFF) << 8) + (bais.read() & 0xFF);
	}

	public static int getFourBytes(ByteArrayInputStream bais)
	{
		return ((bais.read() & 0xFF) << 24) + ((bais.read() & 0xFF) << 16) + ((bais.read() & 0xFF) << 8) + (bais.read() & 0xFF);
	}

	public static int get31Bits(ByteArrayInputStream bais)
	{
		return ((bais.read() & 0x7F) << 24) + ((bais.read() & 0xFF) << 16) + ((bais.read() & 0xFF) << 8) + (bais.read() & 0xFF);
	}

	public static int getTwoBytes(byte[] input, int firstByte)
	{
		return ((input[firstByte] & 0xFF) << 8) + (input[firstByte + 1] & 0xFF);
	}

	public static int getThreeBytes(byte[] input, int firstByte)
	{
		return ((input[firstByte] & 0xFF) << 16) + ((input[firstByte + 1] & 0xFF) << 8) + (input[firstByte + 2] & 0xFF);
	}

	public static int getFourBytes(byte[] input, int firstByte)
	{
		return ((input[firstByte] & 0xFF) << 24) + ((input[firstByte + 1] & 0xFF) << 16) + ((input[firstByte + 2] & 0xFF) << 8) + (input[firstByte + 3] & 0xFF);
	}

	public static int get31Bits(byte[] input, int firstByte)
	{
		return ((input[firstByte] & 0x7F) << 24) + ((input[firstByte + 1] & 0xFF) << 16) + ((input[firstByte + 2] & 0xFF) << 8) + (input[firstByte + 3] & 0xFF);
	}

	public static int getTwoBytes(ByteBuffer bb)
	{
		return ((bb.get() & 0xFF) << 8) + (bb.get() & 0xFF);
	}

	public static int getThreeBytes(ByteBuffer bb)
	{
		return ((bb.get() & 0xFF) << 16) + ((bb.get() & 0xFF) << 8) + (bb.get() & 0xFF);
	}

	public static int getFourBytes(ByteBuffer bb)
	{
		return ((bb.get() & 0xFF) << 24) + ((bb.get() & 0xFF) << 16) + ((bb.get() & 0xFF) << 8) + (bb.get() & 0xFF);
	}

	public static int get31Bits(ByteBuffer bb)
	{
		return ((bb.get() & 0x7F) << 24) + ((bb.get() & 0xFF) << 16) + ((bb.get() & 0xFF) << 8) + (bb.get() & 0xFF);
	}

	public static ByteBuffer setFrameLength(ByteBuffer bb, int length) throws Http2Exception
	{
		if(length >= 0 && length <= Http2Constants.MAXIMUM_FRAME_PAYLOAD_SIZE)
		{
			bb.put((byte)(length >>> 16));
			bb.put((byte)(length >>> 8));
			bb.put((byte)(length & 0xFF));
			return bb;
		}
		else
		{
			throw new Http2Exception("Invalid Frame Payload Length:"+length);
		}
	}

	public static byte[] getStreamIdentifier(int identifier)
	{
		if((identifier & 0x8000) !=0)
		{
			return null;
		}
		
		byte[] b = new byte[4];
		b[0]=(byte)(identifier >>> 24);
		b[1]=(byte)(identifier >>> 16);
		b[2]=(byte)(identifier >>> 8);
		b[3]=(byte)(identifier & 0xFF);

		return b;
	}

	public static ByteBuffer setStreamIdentifier(ByteBuffer bb, int identifier)
	{
		if((identifier & 0x8000) !=0)
		{
			return null;
		}

		bb.put((byte)(identifier >>> 24));
		bb.put((byte)(identifier >>> 16));
		bb.put((byte)(identifier >>> 8));
		bb.put((byte)(identifier & 0xFF));

		return bb;
	}

	public static int getStreamIdentifier(byte[] b)
	{
		if((b[0] & 0x80) == 128)
		{
			return -1;
		}

        	if (b.length == 4) 
		{
        		return ((b[0] & 0xFF) << 24 | (b[1] & 0xFF) << 16 | (b[2] & 0xFF) << 8 | (b[3] & 0xFF));
		}
		
		return -1;
	}

//	public static byte[] getDataPayload(byte[] data)
//	{
//		return getDataPayload(data,-1);
//	}
//
//	public static byte[] getDataPayload(byte[] data, int padlength)
//	{
//		if(data == null)
//		{
//			return new byte[0];
//		}
//
//		if(padlength == -1)
//		{
//			return data;
//		}
//
//		if(padlength < 0 || padlength > 256)
//		{
//			return null;
//		}
//
//		int index = 0;
//		byte b[] =  new byte[data.length+padlength+1];
//		b[index++] = (byte)(padlength & 0xFF);
//		System.arraycopy(data,0,b,index,data.length);
//		index = index+data.length;
//
//		for(int i=0 ; i < padlength; i++)
//		{
//			b[index++] = (byte)0x0;
//		}
//
//		return b;
//	}

	public static ByteBuffer setDataPayload(ByteBuffer bb, byte[] data, int padlength) throws Http2Exception
	{
		if(data == null)
		{
			return bb;
		}

		if(padlength == -1)
		{
			bb.put(data);
			return bb;
		}

		if(padlength < 0 || padlength > 256)
		{
			throw new Http2Exception("Invalid PadLength:"+padlength);
		}

		bb.put((byte)(padlength & 0xFF));  // padlength
		bb.put(data);  // data
		for(int i=0 ; i < padlength; i++)
		{
			bb.put((byte)0x0); // padding
		}

		return bb;
	}

//	public static byte[] getHeaderPayload(byte[] headerblock) throws Exception
//	{
//		return getHeaderPayload(headerblock, -1, null, false, -1);
//	}
//
//	public static byte[] getHeaderPayload(byte[] headerblock, int padlength, byte[] dependentStreamId, boolean mutuallyexclusive, int weight) throws Exception
//	{
//		if(headerblock == null)
//		{
//			return new byte[0];
//		}
//
//		if(padlength == -1)
//		{
//			return headerblock;
//		}
//
//		if(padlength < 0 || padlength > 255)
//		{
//			return null;
//		}
//
//		int index = 0;
//		int dependentStreamIdLength = (dependentStreamId != null) ? 4 :0;
//		int weightLength = (weight > 0) ? 1 : 0;
//		byte b[] =  new byte[headerblock.length+padlength+dependentStreamIdLength+weightLength+1];
//		b[index++] = (byte)(padlength & 0xFF);
//
//		if(getStreamIdentifier(dependentStreamId) == -1)
//		{
//			return null;
//		}
//
//		if(mutuallyexclusive)
//		{
//			dependentStreamId[0] =(byte)(dependentStreamId[0] ^ 0x8F);
//		}
//
//		System.arraycopy(dependentStreamId,0,b,index,dependentStreamId.length);
//		index = index+dependentStreamId.length;
//
//		if(weight<0 && weight>255)
//		{
//			throw new Exception("Invalid weight for Priority - weight:"+weight);
//		}
//
//		b[index++] = (byte)weight;
//
//		System.arraycopy(headerblock,0,b,index,headerblock.length);
//		index = index+headerblock.length;
//
//		for(int i=0 ; i < padlength; i++)
//		{
//			b[index++] = (byte)0x0;
//		}
//
//		return b;
//	}

	public static ByteBuffer setHeaderPayload(ByteBuffer bb, byte[] headerblock, int padlength, int dependentStreamIdentifier, boolean mutuallyexclusive, int weight) throws Exception
	{
		if(headerblock == null)
		{
			return bb;
		}

		if(padlength == -1)
		{
			bb.put(headerblock);
			return bb;
		}

		if(padlength < 0 || padlength > 255)
		{
			throw new Http2Exception("Invalid PadLength:"+padlength);
		}
		byte[] dependentStreamId = getStreamIdentifier(dependentStreamIdentifier);
		if(getStreamIdentifier(dependentStreamId) < 0)
		{
			throw new Http2Exception("Invalid Dependenent StreamID:"+padlength);
		}
		if(weight<0 && weight>255)
		{
			throw new Exception("Invalid weight for Priority - weight:"+weight);
		}

		bb.put((byte) (padlength & 0xFF));
		if(mutuallyexclusive)
		{
			dependentStreamId[0] =(byte)(dependentStreamId[0] ^ 0x8F); // revisit 1
		}
		bb.put(dependentStreamId);
		bb.put((byte) weight);
		bb.put(headerblock);
		for(int i=0 ; i < padlength; i++)
		{
			bb.put((byte) 0x0);
		}

		return bb;
	}

//	public static byte[] getPriorityPayload(byte[] dependentStreamId, int weight) throws Exception
//	{
//		return getPriorityPayload(dependentStreamId,weight,false);
//	}
//
//	public static byte[] getPriorityPayload(byte[] dependentStreamId, int weight, boolean mutuallyexclusive) throws Exception
//	{
//		int index = 0;
//
//		if(getStreamIdentifier(dependentStreamId) == -1)
//		{
//			return null;
//		}
//
//		byte b[] =  new byte[dependentStreamId.length+1];
//
//		if(mutuallyexclusive)
//		{
//			dependentStreamId[0] =(byte)(dependentStreamId[0] ^ 0x8F);
//		}
//
//		System.arraycopy(dependentStreamId,0,b,index,dependentStreamId.length);
//		index = index+dependentStreamId.length;
//
//		if(weight<0 && weight>255)
//		{
//			throw new Exception("Invalid weight for Priority - weight:"+weight);
//		}
//
//		b[index++] = (byte)weight;
//
//		return b;
//	}

//	public static byte[] getRstPayload(byte[] error) throws Exception
//	{
//		if(error.length != 4)
//		{
//			throw new Exception("Invalid size for Reset Stream Payload");
//		}
//		return error;
//	}
//
//	public static byte[] getRstPayload(byte error)
//	{
//		byte[] b = new byte[4];
//		b[0] = (byte)0x00;
//		b[1] = (byte)0x00;
//		b[2] = (byte)0x00;
//		b[3] = (byte)(error & 0xFF);
//		return b;
//	}

	public static ByteBuffer setResetPayload(ByteBuffer bb, int errorCode)
	{
		bb.put((byte) ((errorCode & 0xFF000000) >> 24));
		bb.put((byte) ((errorCode & 0xFF0000) >> 16));
		bb.put((byte) ((errorCode & 0xFF00) >> 8));
		bb.put((byte) (errorCode & 0xFF));

		return bb;
	}


	public static ByteBuffer setSettingsPayload(ByteBuffer bb, HashMap<Integer, Integer> settingsMap)
	{
		if(settingsMap == null || settingsMap.size()==0)
		{
			return bb;
		}

		for(int settingKey : settingsMap.keySet())
		{
			int settingValue = settingsMap.get(settingKey);

			bb.put((byte) ((settingKey & 0xFF00) >> 8));
			bb.put((byte) (settingKey & 0xFF));

			bb.put((byte) ((settingValue & 0xFF000000) >> 24));
			bb.put((byte) ((settingValue & 0xFF0000) >> 16));
			bb.put((byte) ((settingValue & 0xFF00) >> 8));
			bb.put((byte) (settingValue & 0xFF));
		}
		return bb;
	}

//	public static byte[] getSettingsPayload(HashMap<Integer, Integer> settingsMap)
//	{
//		byte[] settingsPayload = new byte[settingsMap.size() * 6];
//
//		int index = 0;
//		for(int settingKey : settingsMap.keySet())
//		{
//			int settingValue = settingsMap.get(settingKey);
//			byte[] payload = getSettingsPayload(settingKey, settingValue);
//			System.arraycopy(payload, 0, settingsPayload, index, 6);
//			index += 6;
//		}
//
//		return settingsPayload;
//	}
//
//	public static byte[] getSettingsPayload(int parameter, int val)
//	{
//		byte[] identifier = new byte[2];
//		identifier[0] = (byte)(0x00);
//		identifier[1] = (byte)(parameter & 0xFF);
//
//		byte[] value = new byte[4];
//		value[0] = (byte)(val >>> 24);
//		value[1] = (byte)(val >>> 16);
//		value[2] = (byte)(val >>> 8);
//		value[3] = (byte)(val & 0xFF);
//
//		return getSettingsPayload(identifier, value);
//	}
//
//	public static byte[] getSettingsPayload(byte[] identifier, byte[] value)
//	{
//		if((identifier.length != 2) || (value.length != 4))
//		{
//			return null;
//		}
//
//		byte[] b = new byte[6];
//		b[0] = (byte)(identifier[0] & 0xFF);
//		b[1] = (byte)(identifier[1] & 0xFF);
//		b[2] = (byte)(value[0] & 0xFF);
//		b[3] = (byte)(value[1] & 0xFF);
//		b[4] = (byte)(value[2] & 0xFF);
//		b[5] = (byte)(value[3] & 0xFF);
//
//		return b;
//	}
	
//	public static byte[] getPushPromisePayload(byte[] promisedStreamId, byte[] headerBlock)
//	{
//		return getPushPromisePayload(-1,promisedStreamId,headerBlock);
//	}
//
//	public static byte[] getPushPromisePayload(int padlength, byte[] promisedStreamId, byte[] headerBlock)
//	{
//		if(padlength > 255)
//		{
//			return null;
//		}
//
//		int index = 0;
//		int promisedStreamIdLength = (promisedStreamId != null) ? 0 : 1;
//		byte b[] =  new byte[headerBlock.length+promisedStreamIdLength+padlength+1];
//
//		if(padlength >= 0)
//		{
//			b[index++] = (byte)(padlength & 0xFF);
//		}
//
//		if(getStreamIdentifier(promisedStreamId) == -1)
//		{
//			return null;
//		}
//
//		promisedStreamId[0] =(byte)(promisedStreamId[0] ^ 0x7F);
//
//		System.arraycopy(promisedStreamId,0,b,index,promisedStreamId.length);
//		index = index+promisedStreamId.length;
//
//		System.arraycopy(headerBlock,0,b,index,headerBlock.length);
//		index = index+headerBlock.length;
//
//		for(int i=0 ; i < padlength; i++)
//		{
//			b[index++] = (byte)0x0;
//		}
//
//		return b;
//	}

//	public static byte[] getPingPayload()
//	{
//		byte[] b = new byte[8];
//		b[0] = (byte)0x0;
//		b[1] = (byte)0x0;
//		b[2] = (byte)0x0;
//		b[3] = (byte)0x0;
//		b[4] = (byte)0x0;
//		b[5] = (byte)0x0;
//		b[6] = (byte)0x0;
//		b[7] = (byte)0x0;
//
//		return b;
//	}

	public static ByteBuffer setPingPayload(ByteBuffer bb)
	{
		for(int i=0; i<8; i++)
		{
			bb.put((byte)0x0);
		}

		return bb;
	}

//	public static byte[] getGoAwayPayload(byte[] laststreamid, byte errorcode, byte[] data)
//	{
//		byte[] error = new byte[4];
//		error[0] = (byte)0x0;
//		error[1] = (byte)0x0;
//		error[2] = (byte)0x0;
//		error[3] = errorcode;
//
//		return getGoAwayPayload(laststreamid, error, data);
//	}
//
//	public static byte[] getGoAwayPayload(byte[] laststreamid, byte[] errorcode, byte[] data)
//	{
//		if( laststreamid.length != 4 || errorcode.length != 4 )
//		{
//			return null;
//		}
//
//		int index = 0;
//		byte b[] =  new byte[laststreamid.length+errorcode.length+data.length];
//
//		laststreamid[0] = (byte) (laststreamid[0] & 0x7F);
//
//		System.arraycopy(laststreamid,0,b,index,laststreamid.length);
//		index = index+laststreamid.length;
//
//		System.arraycopy(errorcode,0,b,index,errorcode.length);
//		index = index+errorcode.length;
//
//		System.arraycopy(data,0,b,index,data.length);
//
//		return b;
//	}

	public static ByteBuffer setGoAwayPayload(ByteBuffer bb, byte[] laststreamid, byte errorcode, String debugData)
	{
		laststreamid[0] = (byte) (laststreamid[0] & 0x7F);
		bb.put(laststreamid);

		bb.put((byte)0x0);
		bb.put((byte)0x0);
		bb.put((byte)0x0);
		bb.put(errorcode);

		bb.put(debugData.getBytes());

		return bb;
	}

//	public static byte[] getWindowUpdatePayload(int windowSize) throws Http2Exception
//	{
//		if(windowSize < 1 || windowSize > Http2Constants.MAXIMUM_WINDOW_SIZE)
//		{
//			throw new Http2Exception("Invalid Size for Window Update Payload - size:"+windowSize);
//		}
//
//		byte[] b = new byte[4];
//		b[0] = (byte)(windowSize >>> 24);
//		b[1] = (byte)(windowSize >>> 16);
//		b[2] = (byte)(windowSize >>> 8);
//		b[3] = (byte)(windowSize & 0xFF);
//
//		return b;
//	}

	public static ByteBuffer setWindowUpdatePayload(ByteBuffer bb, int windowSize) throws Http2Exception
	{
		if(windowSize < 1 || windowSize > Http2Constants.MAXIMUM_WINDOW_SIZE)
		{
			throw new Http2Exception("Invalid Size for Window Update Payload - size:"+windowSize);
		}

		bb.put((byte)(windowSize >>> 24));
		bb.put((byte)(windowSize >>> 16));
		bb.put((byte)(windowSize >>> 8));
		bb.put((byte)(windowSize & 0xFF));

		return bb;
	}

//	public static byte[] getContinuationPayload(byte[] headerblock)
//	{
//		if(headerblock == null)
//		{
//			return new byte[0];
//		}
//
//		return headerblock;
//	}

	public static ByteBuffer setContinuationPayload(ByteBuffer bb, byte[] headerblock)
	{
		if(headerblock != null)
		{
			bb.put(headerblock);
		}
		return bb;
	}




//	public static int getFrameLength(byte[] b)
//	{
//		if(b.length == 3)
//		{
//			return ((b[0] & 0xFF) << 16 | (b[1] & 0xFF) << 8 | (b[2] & 0xFF));
//		}
//		return -1;
//	}
//
//	public static byte[] getFrameLength(int num)
//	{
//		//return getFrameLength(num,Http2Constants.DEFAULT_FRAME_PAYLOAD_SIZE);
//		return getFrameLength(num,Http2Constants.MAXIMUM_FRAME_PAYLOAD_SIZE);
//	}
//
//	public static byte[] getFrameLength(int num, int limit)
//	{
//		byte[] data = null;
//		if(num >= 0 && num <= limit)
//		{
//			data = new byte[3];
//			data[0] = (byte)(num >>> 16);
//			data[1] = (byte)(num >>> 8);
//			data[2] = (byte)(num & 0xFF);
//		}
//		return data;
//	}



	public static void logHttp2Frame(ByteBuffer bb, boolean isSendingFrame, String http2ConnID)
	{
		if(!ConfManager.isHttp2FrameLogsEnabled())
		{
			return;
		}

		bb.flip();
		byte[] arr = new byte[bb.remaining()];
		bb.get(arr);
		logHttp2Frame(arr, isSendingFrame, http2ConnID);
	}

	public static void logHttp2Frame(byte[] b, boolean isSendingFrame, String http2ConnID)
	{
		if(!ConfManager.isHttp2FrameLogsEnabled())
		{
			return;
		}

		int index = 0;
//		int payloadLength = Http2Util.getFrameLength(Arrays.copyOfRange(b,index,index+3));
		int payloadLength = Http2Util.getThreeBytes(b, index);
		int frameType = b[index+3];
		byte flag = b[index+4];
//		int streamID = Http2Util.getStreamIdentifier(Arrays.copyOfRange(b,index+5,index+Http2Constants.HEADER_SIZE));
		int streamID = Http2Util.get31Bits(b, index+5);
		byte[] payload = Arrays.copyOfRange(b,index+Http2Constants.HEADER_SIZE,index+Http2Constants.HEADER_SIZE+payloadLength);

		logHttp2Frame(payloadLength, frameType, flag, streamID, payload, isSendingFrame, http2ConnID);
	}

	public static void logHttp2Frame(int payloadLength, int frameType, byte flag, int streamID, byte[] payload, boolean isSendingFrame, String http2ConnID)
	{
		StringBuilder msg = new StringBuilder();

		if(frameType == Http2Constants.HEADER_FRAME && !isSendingFrame)
		{
			msg.append(Http2Util.getNewLine(10));
			msg.append("\nHttp2");
			msg.append("\nHttp2");
			msg.append("\nHttp2");
			msg.append("\nHttp2");
			msg.append("\nHttp2");

			msg.append("\nHttp2");
			msg.append("\nHttp2");
			msg.append("\nHttp2");
			msg.append("\nHttp2");
			msg.append("\nHttp2");
		}

		if(isSendingFrame == true)
		{
			msg.append("^^^ Sending Http2 ");
		}
		else
		{
			msg.append("vvv Received Http2 ");
		}

		msg.append(getFrameType(frameType));
		msg.append("   "+http2ConnID);
		msg.append("   streamID:"+streamID+"  length:"+payloadLength);

		if(frameType == Http2Constants.WINDOW_UPDATE_FRAME)
		{
			int incrementSize = Http2Stream.getValue(Arrays.copyOfRange(payload,0,payloadLength));
			msg.append("  windowSize:"+incrementSize);
		}

		if(frameType == Http2Constants.RESET_STREAM_FRAME)
		{
			int errorCode = Http2Util.getFourBytes(payload, 0);
			msg.append("  ERRORCODE:"+errorCode);
		}

		if(isEndOfHeaders(flag))
		{
			msg.append("  ,has EndOfHeaders Flag");
		}
		if(isEndOfStream(flag) && frameType!=Http2Constants.SETTINGS_FRAME)
		{
			msg.append("  ,has EndOfStream Flag");
		}
		if(isAck(flag) && frameType==Http2Constants.SETTINGS_FRAME)
		{
			msg.append("  ,has Ack Flag");
		}
		if(hasPadding(flag))
		{
			msg.append("  ,hasPadding Flag");
		}
		if(hasPriority(flag))
		{
			msg.append("  ,hasPriority Flag");
		}

		logger.log(Level.INFO, msg.toString());
	}

	public static String getFlags(byte flag, int type)
	{
		String msg = "";

		if(isEndOfHeaders(flag))
		{
			msg+=",EndOfHeaders Flag";
		}
		if(isEndOfStream(flag) && type!=Http2Constants.SETTINGS_FRAME)
		{
			msg+=",EndOfStream Flag";
		}
		if(isAck(flag) && type==Http2Constants.SETTINGS_FRAME)
		{
			msg+=",Ack Flag";
		}
		if(hasPadding(flag))
		{
			msg+=",Padding Flag";
		}
		if(hasPriority(flag))
		{
			msg+=",Priority Flag";
		}

		return msg;
	}

	public static String getFrameType(int type)
	{
		switch(type)
		{
			case Http2Constants.DATA_FRAME: return "DATA_FRAME";
			case Http2Constants.HEADER_FRAME: return "HEADER_FRAME";
			case Http2Constants.PRIORITY_FRAME: return "PRIORITY_FRAME";
			case Http2Constants.RESET_STREAM_FRAME: return "RESET_STREAM_FRAME";
			case Http2Constants.SETTINGS_FRAME: return "SETTINGS_FRAME";
			case Http2Constants.PUSH_PROMISE_FRAME: return "PUSH_PROMISE_FRAME";
			case Http2Constants.PING_FRAME: return "PING_FRAME";
			case Http2Constants.GOAWAY_FRAME: return "GOAWAY_FRAME";
			case Http2Constants.WINDOW_UPDATE_FRAME: return "WINDOW_UPDATE_FRAME";
			case Http2Constants.CONTINUATION_FRAME: return "CONTINUATION_FRAME";
			default: return "INVALID_FRAME_TYPE";
		}
	}

	public static String getAckStateType(int type)
	{
		switch (type)
		{
			case StateConstants.REQUEST_IN_PROCESS: return "REQUEST_IN_PROCESS";
			case StateConstants.REQUEST_ACKNOWLEDGED: return "REQUEST_ACKNOWLEDGED";
			case StateConstants.REQUEST_KEEPALIVE: return "REQUEST_KEEPALIVE";
			case StateConstants.ON_HEADER_COMPLETION: return "ON_HEADER_COMPLETION";
			case StateConstants.ON_DATA: return "ON_DATA";
			case StateConstants.ON_COMPLETION: return "ON_COMPLETION";
			case StateConstants.ON_OUTPUTBUFFERREFILL: return "ON_OUTPUTBUFFERREFILL";
			case StateConstants.ON_WRITECOMPLETE: return "ON_WRITECOMPLETE";
			case StateConstants.ON_WRITEFAILURE: return "ON_WRITEFAILURE";
			case StateConstants.ON_CLOSE: return "ON_CLOSE";
			case StateConstants.ON_PING: return "ON_PING";
			case StateConstants.ON_PONG: return "ON_PONG";
			default: return "INVALID_STATE_CONSTANTS";
		}
	}

	public static String getSettingType(int type)
	{
		switch(type)
		{
			case Http2Constants.SETTINGS_HEADER_TABLE_SIZE: return "SETTINGS_HEADER_TABLE_SIZE";
			case Http2Constants.SETTINGS_ENABLE_PUSH: return "SETTINGS_ENABLE_PUSH";
			case Http2Constants.SETTINGS_MAX_CONCURRENT_STREAMS: return "SETTINGS_MAX_CONCURRENT_STREAMS";
			case Http2Constants.SETTINGS_INITIAL_WINDOW_SIZE: return "SETTINGS_INITIAL_WINDOW_SIZE";
			case Http2Constants.SETTINGS_MAX_FRAME_SIZE: return "SETTINGS_MAX_FRAME_SIZE";
			case Http2Constants.SETTINGS_MAX_HEADER_LIST_SIZE: return "SETTINGS_MAX_HEADER_LIST_SIZE";
			default: return "INVALID_SETTING_TYPE";
		}
	}

	public static String getSize(long bytes)
	{
		String size = "";

		long kb = bytes/1024;
		long mb = bytes/(1024*1024);
		long gb = bytes/(1024*1024*1024);

		if(bytes<1024)
		{
			size += bytes+" bytes ";
			return "("+size+")";
		}
		if(kb<1024)
		{
			size += kb+" kb " + (bytes%1024)+" bytes";
			return "("+size+")";
		}
		if(mb<1024)
		{
			size += mb+" mb " + (kb%1024)+" kb " + (bytes%1024)+" bytes";
			return "("+size+")";
		}
		if(gb<1024)
		{
			size += gb+" gb " +(mb%1024)+" mb " + (kb%1024)+" kb " + (bytes%1024)+" bytes";
			return "("+size+")";
		}
		return " !!! SIZE ERROR !!!";
	}

	public static boolean isEndOfStream(byte flag)
	{
		return (flag & Http2Constants.END_STREAM_FLAG) != 0;
	}

	public static boolean isAck(byte flag)
	{
		return (flag & Http2Constants.ACK_FLAG) != 0;
	}

	public static boolean isEndOfHeaders(byte flag)
	{
		return (flag & Http2Constants.END_HEADERS_FLAG) != 0;
	}

	public static boolean hasPadding(byte flag)
	{
		return (flag & Http2Constants.PADDED_FLAG) != 0;
	}

	public static boolean hasPriority(byte flag)
	{
		return (flag & Http2Constants.PRIORITY_FLAG) != 0;
	}

	public static String getByteArrayToPrint(byte[] b)
	{
		int len = (b.length <=5000)?b.length:500;

		StringBuffer sb = new StringBuffer();
		sb.append("{");
		for (int i=0; i<len; i++)
		{
			sb.append(b[i] + ",");
		}
		sb.append("}");
		return sb.toString();
	}

	public static String getNewLine(int n)
	{
		StringBuffer sb = new StringBuffer();
		for(int i=0; i<n; i++)
		{
			sb.append("\n");
		}
		return sb.toString();
	}

	public static String getCharSeq(int n, String ch)
	{
		StringBuffer sb = new StringBuffer();
		for(int i=0; i<n; i++)
		{
			sb.append(ch);
		}
		return sb.toString();
	}

	public static String getStackToPrint(Stack<String> s)
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\n");

		for(int i=0; i<s.size(); i++)
		{
			String t = s.get(i);
			sb.append(" --> " + (i) + " = " + t + "\n");
		}

		return sb.toString();
	}

	public static String getVectorToPrint(Vector v)
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\n");

		int i = 0;
		Iterator itr = v.iterator();
		while(itr.hasNext())
		{
			sb.append(" --> " + (i++) + " = " + itr.next() + "\n");
		}

		return sb.toString();
	}

	public static String getListToPrint(List list)
	{
		StringBuffer sb = new StringBuffer();
		sb.append("\n");

		int i = 0;
		for (Object obj : list)
		{
			String str = (String) obj;
			sb.append(" --> " + (i++) + " = " + str + "\n");
		}

		return sb.toString();
	}

	public static Queue<byte[]> getChuckedByteArrays(byte[] source, int chunkSize)
	{
		Queue<byte[]> chunked_ByteArrays = new LinkedList<>();
		byte[] chunk = null;

		int blockCount = (source.length + chunkSize - 1) / chunkSize;
		for (int i = 1; i < blockCount; i++)
		{
			int idx = (i - 1) * chunkSize;
			chunk = Arrays.copyOfRange(source, idx, idx + chunkSize);
			chunked_ByteArrays.add(chunk);
		}

		// Last chunk
		int end = -1;
		if (source.length % chunkSize == 0)
		{
			end = source.length;
		}
		else
		{
			end = source.length % chunkSize + chunkSize * (blockCount - 1);
		}
		chunk = Arrays.copyOfRange(source, (blockCount - 1) * chunkSize, end);
		chunked_ByteArrays.add(chunk);

		return chunked_ByteArrays;
	}
}
