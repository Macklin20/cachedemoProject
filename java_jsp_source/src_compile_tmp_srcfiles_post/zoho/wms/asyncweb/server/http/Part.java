//$Id$
package com.zoho.wms.asyncweb.server.http;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.logging.Level;
import java.net.URLDecoder;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;
import com.zoho.wms.asyncweb.server.exception.AWSException;

public class Part
{
	private static AsyncLogger logger = new AsyncLogger(Part.class.getName());
	
	private String charset = AWSConstants.DEFAULT_CHARSET;
	private byte[] rawContent;
	private byte[] rawData;
	private Hashtable<String, String> paramMap = new Hashtable();
	private Hashtable<String, String> headers = new Hashtable();
	private Hashtable<String, Part> multipart = new Hashtable();

	private byte[] crlf = "\r\n".getBytes();//No I18n
	private byte[] body;
	private LinkedHashMap<String, String> mimeheaders;
	private Multipart mimepart;
	private String type = "";
	private String nameField;
	private String filename;

	public static final String FORM_DATA = "form-data";//No I18n

	public Part(byte[] body)
	{
		this(null, body);
	}

	public Part(LinkedHashMap<String, String> mimeheaders, byte[] body)
	{
		this.mimeheaders = mimeheaders;
		this.body = body;
	}

	public Part(Multipart multipart)
	{
		this.mimepart = multipart;
	}
	
	/** To initialize a MIME Part
	 * @param content - entire content of the part
	 * @param charset - charset 
	 */

	Part(String content,String charset) throws Exception
	{
		this(content.getBytes(charset),charset);
	}	

	/** To initialize a MIME Part
	 * @param content - entire content of the part
	 * @param charset - charset 
	 */
	
	Part(byte[] content, String charset) throws Exception
	{
		String contentData = null;
		try
		{
			this.charset = charset;
			rawContent = content;
			contentData = new String(content,charset);
			int splitIndex = contentData.indexOf("\r\n\r\n");
			if(splitIndex <= 0)
			{
				throw new Exception("Invalid content in Multipart");
			}
			String namePart = contentData.substring(0,splitIndex);
			String contentPart = contentData.substring(splitIndex+4,contentData.length()-2);
			setNameHeaders(namePart);
			setValue(contentPart);
		}
		catch(Exception ex)
		{
			if(ConfManager.isIllegalReqExceptionEnabled())
			{
				throw ex;
			}
		}
	}

	private void setNameHeaders(String namePart) throws Exception
	{
		if(namePart == null)
		{
			return;
		}

		String[] headerArray = namePart.trim().split("\r\n");
		

		for(int i=0;i<headerArray.length;i++)
		{
			String[] keyValue = headerArray[i].split(":");

			if(keyValue.length == 2)
			{
				String key = keyValue[0].trim().toLowerCase();
				String value = keyValue[1].trim();

				if(key.equals("content-disposition"))
				{
					setParameter(value);
				}

				headers.put(key,value);
			}
			else
			{
				logger.log(Level.INFO,"Undefined MIME header : "+headerArray[i],Part.class.getName(),AWSLogMethodConstants.SET_NAME_HEADERS);
			}
		}
	}

	private void setParameter(String content) throws Exception
	{
		String[] parameter = content.split(";");
		for(int i=1; i<parameter.length; i++)
		{
			String[] keyValue = parameter[i].split("=");
			
			try
			{	
				if(keyValue.length ==2)
				{	
					paramMap.put(keyValue[0].trim(), URLDecoder.decode(keyValue[1].trim().replace("\"",""),charset));
				}
				else
				{
					logger.log(Level.INFO,"Undefined MIME param : "+parameter[i],Part.class.getName(),AWSLogMethodConstants.SET_PARAMETER);
				}
			}
			catch(Exception ex)
			{
				if(ConfManager.isIllegalReqExceptionEnabled())
				{
					throw ex;
				}
			}
		}
	}


	private void setValue(String contentPart) throws Exception
	{
		rawData = contentPart.getBytes(charset);
		
		try
		{
			String contentType = getHeader(AWSConstants.CONTENT_TYPE);
			if((contentType!=null) && contentType.toLowerCase().startsWith(AWSConstants.MULTIPART))
			{
				String boundary = getHeaderParameter(contentType,AWSConstants.BOUNDARY);  
				String charset = getHeaderParameter(contentType,AWSConstants.CHARSET);

				if(boundary == null)
				{
					throw new AWSException("Invalid inner MIME header "+contentType);	//No I18N
				}

				if(charset == null)
				{
					charset = AWSConstants.DEFAULT_CHARSET;
				}

				mimepart = new Multipart(contentType, boundary, charset);
				String parts[] = (new String(rawData,charset)).trim().split("--"+boundary);
				
				for(int i = 1; i < (parts.length-1); i++)
				{
					Part part = new Part(parts[i],charset);
					if (part.getName() != null)
					{
						multipart.put(part.getName(),part);
					}
					mimepart.addPart(part);
				}
				mimepart.setPreambleContent(parts[0].replace("\r\n", ""));//No I18n
					
				if(!(parts[parts.length-1].equals("--")))
				{
					if(parts[parts.length-1].startsWith("--"))
					{
						mimepart.setEpilogueContent(parts[parts.length-1].replace("--\r\n", ""));//No I18n
					}
					else
					{
						throw new AWSException("Invalid inner MIME content closure :"+parts[parts.length-1]);//new line after closure //No I18N
					}
				}
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.INFO, " Exception ",Part.class.getName(),AWSLogMethodConstants.SET_VALUE, ex);
		}
	}

	private String getHeaderParameter(String headerValue, String parameter)
	{
		String[] parameters = headerValue.split(";");
		for(int i=1; i<parameters.length; i++)
		{
			if(parameters[i].contains(parameter))
			{
				String[] keyValue = parameters[i].split("=");
				
				if(keyValue.length ==2)
				{	
					return keyValue[1].trim().replace("\"","");
				}
				else
				{
					logger.log(Level.INFO,"UNDEFINED HEADER PARAM : "+parameters[i],Part.class.getName(),AWSLogMethodConstants.GET_HEADER_PARAMETER);
				}
			}
		}
		return null;
	}


	/** To get a inner MIME part
	 * @param name - name of the part 
	 * @return - part
	 */
	
	public Part getPart(String name)
	{
		return multipart.get(name);
	}

	/** To get a inner MIME parts
	 * @return - hashtable of parts
	 */
	
	public Hashtable getParts()
	{
		return multipart;
	}

	/** To get a headers within this part
	 * @return - headers
	 */
	
	public Hashtable getHeaders()
	{
		return headers;
	}

	/** To get value of the header 
	 * @param name - name of the part 
	 * @return - header value
	 */
	
	public String getHeader(String name)
	{
		return headers.get(name.toLowerCase());
	}

	/** To get a name of this part
	 * @return - part name
	 */
	
	public String getName()
	{
		return paramMap.get(AWSConstants.NAME);
	}

	/** To get a name of the file in this part
	 * @return - file name
	 */
	
	public String getFileName()
	{
		return paramMap.get("filename");
	}

	/** To get content type of this part
	 * @return - content type
	 */
	
	public String getContentType()
	{
		String contentType = headers.get(AWSConstants.CONTENT_TYPE);
		if(contentType != null)
		{
			return contentType;
		}
		return AWSConstants.TEXT_OR_PLAIN;	
	}
	
	/** To get value of this part
	 * @return - part value
	 */
	
	public String getValue() throws Exception
	{
		return new String(rawData,charset);
	}

	/** To get value of this part as bytes
	 * @return - part value as raw bytes
	 */
	
	public byte[] getRawData()
	{
		return rawData;
	}

	/** To get input stream of value 
	 * @return - input stream
	 */

	public InputStream getInputStream()
	{
		return new ByteArrayInputStream(rawData);
	}

	/** To write value to a file
	 * @param path - path of the file
	 */

	public void write(String path)
	{
		try
		{
			//logger.log(Level.INFO,"Writing to FILE : "+path+" "+rawData.length);
			File file = new File(path);
			FileOutputStream fos = new FileOutputStream(file);
			InputStream is = getInputStream();

			try
			{
				byte[] b = new byte[100];
				for(int i=0; (i=is.read(b)) > 0; i=0)
				{
					fos.write(b,0,i);
					fos.flush();
				}

			}
			catch(Exception exe)
			{
				throw exe;
			}
			finally
			{
				is.close();
				fos.close();
			}
		}
		catch(Exception ex)
		{
 			logger.log(Level.INFO, " Exception ",Part.class.getName(),"write", ex);
		}
	}
	
	/**
	 * To get entire raw content of the part
	 * @return - content as raw bytes
	 */

	public byte[] getRawContent()
	{
		return rawContent;
	}

	public Multipart getMultipart()
	{
		return mimepart;
	}

	/**
	 * To get toString value of the part 
	 * @return - description of the part
	 */
	
	public String toString()
	{
		return "Params : "+paramMap+", contentType: "+getContentType()+", value: "+rawData.length+", Parts: "+multipart.size()+", headers : "+headers;//No I18n
	}

	private byte[] frameMultipart() throws Exception
	{
		if (body == null || body.length == 0)
		{
			throw new Exception("Multipart body should not be null");//No I18n
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try
		{
			if(type.equals(FORM_DATA))
			{
				if(nameField == null)
				{
					throw new Exception("For formdata, name param is mandatory");//No I18n
				}
				baos.write(("Content-Disposition: "+FORM_DATA+"; name=\""+nameField+"\"").getBytes());//No I18n
				if(filename != null)
				{
					baos.write(("; filename=\""+filename+"\"").getBytes());//No I18n
				}
				baos.write(crlf);
			}
			if (mimeheaders != null && mimeheaders.size() > 0)
			{
				Iterator<String> iterator = mimeheaders.keySet().iterator();
				while(iterator.hasNext())
				{
					String key = iterator.next();
					baos.write((key+": "+mimeheaders.get(key)).getBytes());//No I18n
					baos.write(crlf);
				}
			}
			baos.write(crlf);
			baos.write(body);
			baos.write(crlf);
			return baos.toByteArray();
		}
		catch (Exception ex)
		{
			logger.log(Level.INFO, "Exception in frameMultipart : ",Part.class.getName(),AWSLogMethodConstants.FRAME_MULTIPART, ex);//No I18n
		}
		finally
		{
			try
			{
				baos.close();
			}
			catch(Exception ex)
			{
			}
		}
		return new byte[0];
	}

	byte[] getData() throws Exception
	{
		if (mimepart != null)
		{
			mimepart.processMultipart(true);
			return mimepart.getData();
		}
		return frameMultipart();
	}

	public void setType(String type)
	{
		this.type = type;
	}

	public void setName(String name)
	{
		this.nameField = name;
	}

	public void setFileName(String filename)
	{
		this.filename = filename;
	}

	public void setFormData(String type,String name)
	{
		setFormData(type, name, null);
	}

	public void setFormData(String type,String name, String filename)
	{
		this.type = type;
		this.nameField = name;
		this.filename = filename;
	}

	boolean isFormData()
	{
		return (type.equals(FORM_DATA)) || (mimepart != null);
	}
}
