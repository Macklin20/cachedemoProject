//$Id$

package com.zoho.wms.asyncweb.server.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Hashtable;
import java.util.HashMap;
import java.io.FileInputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.zip.Deflater;
import java.util.HashMap;

import com.adventnet.wms.servercommon.ServerUtil;

import com.adventnet.wms.common.CommonUtil;
import com.adventnet.wms.common.exception.WMSException;
import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.stats.AWSInfluxStats;

import com.zoho.instrument.*;
import com.zoho.instrument.common.*;

import com.adventnet.iam.xss.IAMEncoder;
import com.zoho.wms.asyncweb.server.exception.AWSException;

public class Util
{
	private static AsyncLogger logger = new AsyncLogger(Util.class.getName());
        private static CopyOnWriteArrayList<String> lblsslips = null;
        private static CopyOnWriteArrayList<String> lblsslipsrange = null;

	public static final int WS_OPCODE_CONTINUATION = 0;
	public static final int WS_OPCODE_TEXT = 1;
	public static final int WS_OPCODE_BINARY = 2;
	public static final int WS_OPCODE_PING = 9;
	public static final int WS_OPCODE_PONG = 0xA;
	public static final int WS_OPCODE_CLOSE = 8;

	public static final int CHUNK_MAX_LENGTH_OFFSET = 100;
	public static final String NEWLINE = "\r\n";//No I18N

	public static Properties getProperties(String propsFile)
	{
		try
		{
			logger.log(Level.INFO,"Loading props "+propsFile,Util.class.getName(),AWSLogMethodConstants.GET_PROPERTIES);//No I18N
			Properties props = new Properties();
			props.load(new FileInputStream(propsFile));
			return props;	
		}
		catch(Exception exp)
		{
			logger.log(Level.SEVERE,"Unable to load conf file "+propsFile,Util.class.getName(),AWSLogMethodConstants.GET_PROPERTIES);//No I18N
			return null;
		}
	}

        public static boolean isLBLSSLIP(String clientip)
        {

                if (!ConfManager.isSSLOffloader())
                {
                        return false;
                } 

		lblsslips = ConfManager.getLBLSSLIPsList();
		lblsslipsrange = ConfManager.getLBLSSLIPsRangeList();

                if (lblsslips != null && !lblsslips.isEmpty() && lblsslips.contains(clientip))
                {
                        return true;
                }

                if (lblsslipsrange != null && !lblsslipsrange.isEmpty())
                {
			String ipRange = "";

			try
			{
				Iterator itr = lblsslipsrange.iterator();

				while(itr.hasNext())
				{
					ipRange = (String)itr.next();
					String[] ipRangeArr = ipRange.split("-");
					if (ServerUtil.isIPInRange(clientip, ipRangeArr[0], ipRangeArr[1]))
					{
						return true;
					}
				}
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO,"UNSUPPORTED IP RANGE "+ipRange+". EXCEPTION ",Util.class.getName(),AWSLogMethodConstants.IS_LBLSSLP,ex);
			}
                }


                return false;

        }

	public static Hashtable getWmsIdSessionsMap(String rkey,Hashtable sessionsmap)
	{
		Hashtable smap = new Hashtable();
		StringTokenizer str = new StringTokenizer(rkey,",");
		while(str.hasMoreTokens())
		{
			String wmsid = str.nextToken();
			
			String sidstr = (String)sessionsmap.get(wmsid);
			if(sidstr != null)
			{
				smap.put(wmsid,sidstr);
			}
		}
		return smap;
	}
	
	public static boolean isNewLine(byte[] b)
	{
		if(b.length == 2 && b[0] == '\r' && b[1] == '\n')
		{
			return true;
		}
		return false;
	}

        public static void checkPrd(String prd) throws WMSException
        {       
                if(prd.length() != 2)
                {               
                        throw new WMSException("invalid Product code");//No I18N
                }       
        }     

	public static boolean isStreamMode(String value)
	{
		if(value != null && value.trim().equals(AWSConstants.VALUE_1))
		{
			return true;
		}	
		return false;
	}

	public static String getHtml(Hashtable hash) throws Exception
	{
		return getHtml(hash, false,false);
	}

	private static String getHtml(Hashtable hash, boolean isrecur , boolean encodeneeded) throws Exception
        {
                String html = "<table "+((!isrecur) ? "style='border:1px solid black;'" : "width=100%")+">";
                boolean firstrow = true;
                List keysList = Collections.list(hash.keys());
                try{Collections.sort(keysList);}catch(Exception e){}
                for(Object key : keysList)
                {
                        html +="<tr>";
                        Object value = hash.get(key);
                        html = html + "<td "+(!firstrow ? "style='border-top:1px solid black;border-right:1px solid black;'":"style='border-right:1px solid black;'")+">"+key+"</td>";
                        if(value instanceof Hashtable)
                        {
                                html = html + "<td "+(!firstrow ? "style='border-top:1px solid black;'":"")+">"+getHtml((Hashtable) value, true , encodeneeded)+"</td>";
                        }
                        else if(value instanceof List)
                        {
                                html = html + "<td "+(!firstrow ? "style='border-top:1px solid black;'":"")+">"+getHtml((List) value, true , encodeneeded)+"</td>";
                        }else
                        {
                                html = html + "<td "+(!firstrow ? "style='border-top:1px solid black;'":"")+">&nbsp;"+(encodeneeded ? (IAMEncoder.safeEncodeHTML(""+value)) : value)+"</td>";
                        }
                        html +="</tr>";
                        firstrow = false;
                }
                html +="</table>";
                return html;
        }

        
        private static String getHtml(List list, boolean isrecur , boolean encodeneeded) throws Exception
        {
                String html = "<table "+((!isrecur) ? "style='border:1px solid black;'" : "width=100%")+">";
                boolean firstrow = true;
                int index = 0;
                try{Collections.sort(list);}catch(Exception e){}
                for(Object value : list)
                {
                        html +="<tr>";
                        html = html + "<td "+(!firstrow ? "style='border-top:1px solid black;border-right:1px solid black;'":"style='border-right:1px solid black;'")+">"+index+"</td>";
                        index++;
                        if(value instanceof Hashtable)
                        {
                                html = html + "<td "+(!firstrow ? "style='border-top:1px solid black;'":"")+">"+getHtml((Hashtable) value, true, encodeneeded)+"</td>";
                        }
                        else if(value instanceof List)
                        {
                                html = html + "<td "+(!firstrow ? "style='border-top:1px solid black;'":"")+">"+getHtml((List) value, true, encodeneeded)+"</td>";
                        }else
                        {
                                html = html + "<td "+(!firstrow ? "style='border-top:1px solid black;'":"")+">&nbsp;"+(encodeneeded ? (IAMEncoder.safeEncodeHTML(""+value)) : value)+"</td>";
                        }
                        html +="</tr>";
                        firstrow = false;
                }
                html +="</table>";
                return html;
        }

	public static ByteBuffer getWebSocketWriteFrame(ByteBuffer data, int opcode , boolean fin, boolean isPerMessageDeflate, Deflater deflater) throws IOException, AWSException
	{
		if(isPerMessageDeflate)
		{
			try
			{
				data = data.array().length==0?data:deflate(deflater, data);
			}
			catch(Exception ex)
			{
				throw new IOException("Websocket Compression Error : "+ex.getMessage());//No I18n
			}
		}

		int length = data.limit()-data.position();
		int bbsize = length;
		bbsize ++;
		if(length < 126)
		{
			bbsize += 1;
		}
		else if(length <= 65535)
		{
			bbsize += 3;
		}
		else if(length > 65535)
		{
			bbsize += 9;
		}
		ByteBuffer bb = ByteBuffer.allocate(bbsize);
		if(fin)
		{
			bb.put(isPerMessageDeflate ? (byte) (0xC0 | opcode) : (byte) (0x80 | opcode)) ;
		}
		else
		{
			bb.put(isPerMessageDeflate ? (byte) (0x40 | opcode) : (byte) (0x00 | opcode)) ;
		}
		if(length < 126)
		{
			bb.put((byte)length);
		}
		else if(length <= 65535)
		{
			bb.put((byte)126);
			byte[] b = new byte[2];
			b[0] = (byte)((length >>> 8));
			b[1] = (byte)(length & 0xFF);
			bb.put(b);
		}
		else if(length > 65535)
		{
			bb.put((byte)127);
			byte[] b = new byte[8];
			long blength = new Long(length).longValue();
			b[0] = (byte)(blength >>> 56);
			b[1] = (byte)(blength >>> 48);
			b[2] = (byte)(blength >>> 40);
			b[3] = (byte)(blength >>> 32);
			b[4] = (byte)(blength >>> 24);
			b[5] = (byte)(blength >>> 16);
			b[6] = (byte)(blength >>> 8);
			b[7] = (byte)(blength & 0xFF);
			bb.put(b);
		}

		bb.put(data);
		bb.flip();
		return bb;
	}

	public static byte[] getWebSocketWriteFrame(byte[] data, int opcode, boolean fin, boolean isPerMessageDeflate, Deflater deflater) throws IOException, AWSException
	{
		if(isPerMessageDeflate)
		{
			try
			{
				data = data.length==0?data:deflate(deflater, data);
			}
			catch(Exception ex)
			{
				logger.log(Level.INFO, " Exception ",Util.class.getName(),AWSLogMethodConstants.GET_WEBSOCKET_WRITE_FRAME, ex);
				throw new IOException("Websocket Compression Error : "+ex.getMessage());//No I18n
			}

		}
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		if(fin)
		{
			bao.write(isPerMessageDeflate ? (byte) (0xC0 | opcode) : (byte) (0x80 | opcode));
		}
		else
		{
			bao.write(isPerMessageDeflate ? (byte) (0x40 | opcode) : (byte) (0x00 | opcode));
		}

		long length = data.length;
		if(length < 126)
		{
			bao.write((byte)length);
		}
		else if(length <= 65535)
		{
			bao.write((byte)126);
			byte[] b = new byte[2];
			b[0] = (byte)((length >>> 8));
			b[1] = (byte)(length & 0xFF);
			bao.write(b);
		}
		else if(length > 65535)
		{
			bao.write((byte)127);
			byte[] b = new byte[8];
			b[0] = (byte)(length >>> 56);
			b[1] = (byte)(length >>> 48);
			b[2] = (byte)(length >>> 40);
			b[3] = (byte)(length >>> 32);
			b[4] = (byte)(length >>> 24);
			b[5] = (byte)(length >>> 16);
			b[6] = (byte)(length >>> 8);
			b[7] = (byte)(length & 0xFF);
			bao.write(b);
		}

		bao.write(data);
		bao.flush();
		bao.close();
		return bao.toByteArray();
	}

	private static ByteBuffer deflate(Deflater deflater, ByteBuffer bb) throws IOException
	{
		byte b[] = new byte[bb.remaining()];
		bb.get(b,0,b.length);
		return ByteBuffer.wrap(deflate(deflater, b));
	}

	private static byte[] deflate(Deflater deflater, byte[] data) throws IOException
	{
		if(deflater == null || data == null)
		{
			return data;
		}

		long stime = System.currentTimeMillis();
		int orgSize = data.length;

		deflater.setInput(data);
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
		byte[] buffer = new byte[ConfManager.getWSDeflateBufferSize()];
		while (true)
		{
			int count = deflater.deflate(buffer,0,buffer.length, Deflater.SYNC_FLUSH);
			if(count<=0) { break;}
			outputStream.write(buffer, 0, count);
		}

		outputStream.close();
		byte[] output = outputStream.toByteArray();
		byte[] bb = new byte[output.length-4];
		System.arraycopy(output, 0, bb, 0, output.length-4);
		output = bb;
		AWSInfluxStats.updateWSCompressionStats(AWSConstants.AWS_DEFLATE, orgSize, output.length, System.currentTimeMillis() - stime);
		return output;
	}

	public static int getMaxThreadCount(int maxThreadCount, int minThreadCount)
	{
		return maxThreadCount>=minThreadCount ? maxThreadCount : minThreadCount;
	}

	public static int getMaxThreadCreationLimit(int maxThreadCreationLimit, int maxThreadPoolCount)
	{
		return maxThreadCreationLimit <= 0 ? maxThreadPoolCount : maxThreadCreationLimit;
	}

	public static int getMinWebEngineNetData(HashMap appinfo)
	{
		String value = (String)appinfo.get("netdata");
		if(value!= null)
		{
			return Integer.parseInt(value) <=0 ? ConfManager.getNetDataProcessorCount() : Integer.parseInt(value);
		}
		else
		{
			return ConfManager.getNetDataProcessorCount();
		}
	}

	public static int getMaxWebEngineNetData(int minpoolsize, HashMap appinfo)
	{
		String value = (String)appinfo.get("maxnetdata");
		if(value != null)
		{
			return Integer.parseInt(value) >= minpoolsize ? Integer.parseInt(value) : minpoolsize ;
		}
		else
		{
			return minpoolsize;
		}	

	}

	public static int getWebEngineNetDataMaxThreadCreationLimit(int maxpoolsize, HashMap appinfo)
	{
		String value = (String)appinfo.get("maxthreadcreationlimit");
		if(value != null)
		{
			return Integer.parseInt(value) >= maxpoolsize ? Integer.parseInt(value) : maxpoolsize;
		}
		else
		{
			return maxpoolsize;
		}
	}
	
	public static int getWebEngineNetDataKATime(HashMap appinfo)
	{
		String value = (String)appinfo.get("keepalivetime");
		if(value != null)
		{
			return Integer.parseInt(value);
		}
		return (int)(ConfManager.getNetDataProcessorKeepaliveTime());
	}

	public static ArrayList getFormattedList(HashMap map)
	{
		ArrayList list = new ArrayList();
		Iterator iterator = map.keySet().iterator();
		while(iterator.hasNext())
		{
			HashMap formattedMap = new HashMap();
			String key = (String) iterator.next();
			formattedMap.put(AWSConstants.NAME, key);
			formattedMap.put(AWSConstants.VALUE, map.get(key));
			list.add(formattedMap);
		}
		return list;
	}

	public static HashMap getAccessParams(HashMap paramsMap)
	{
		ArrayList restrictedParams = ConfManager.getAccessRestrictedParams();

		if(restrictedParams.isEmpty())
		{
			return paramsMap;
		}

		HashMap params = (HashMap)paramsMap.clone();
		Iterator itr = restrictedParams.iterator();

		while(itr.hasNext())
		{
			String key = (String) itr.next();
			if(params.get(key)!=null)
			{
				params.put(key, AWSConstants.RESTRICTED);
			}
		}

		return params;
	}

	public static HashMap getAccessHeaders(HashMap headerMap)
	{
		ArrayList accessHeaders = ConfManager.getAccessHeaders();

		HashMap headers = (HashMap)headerMap.clone();
		Iterator itr = headers.keySet().iterator();

		while(itr.hasNext())
		{
			String key = (String) itr.next();
			if(!accessHeaders.contains(key))
			{
				headers.put(key, AWSConstants.RESTRICTED);
			}
		}

		return headers;
	}

	public static HashMap getAccessResponseHeaders(Hashtable headerMap)
	{
		ArrayList accessHeaders = ConfManager.getAccessResponseHeaders();
		HashMap headers = new HashMap<>(headerMap);
		Iterator itr = headers.keySet().iterator();

		while(itr.hasNext())
		{
			String key = (String) itr.next();
			if(!accessHeaders.contains(key))
			{
				headers.put(key, AWSConstants.RESTRICTED);
			}
		}
		return headers;
	}
}
