//$Id$
package com.zoho.wms.asyncweb.server.http;

import com.zoho.wms.asyncweb.server.AWSConstants;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.constants.AWSLogMethodConstants;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Level;

public class Multipart
{
	public static final String FORM_DATA = "multipart/form-data";//No I18n
	public static final String MIXED = "multipart/mixed";//No I18n

	private AsyncLogger logger = new AsyncLogger(Multipart.class.getName());

	private byte[] crlf = "\r\n".getBytes();//No I18n
	private byte[] delimiter = "--".getBytes();
	private String charset = AWSConstants.DEFAULT_CHARSET;
	private Random random = new Random();

	private String boundary;
	private byte[] preamble;
	private byte[] epilogue;
	private String preambleData;
	private String epilogueData;
	private String multipartType;
	private ArrayList<Part> multipart = new ArrayList();
	private int contentlength;
	private String contentType = null;
	private byte[] data;

	public Multipart(String multipartType)
	{
		this.multipartType = multipartType;
		this.boundary = "AWS_Boundary_"+getRandomNumber();//No I18n
	}

	public Multipart(String multipartType, String boundary)
	{
		this.multipartType = multipartType;
		this.boundary = boundary;
	}

	public Multipart(String multipartType, String boundary, String charset)
	{
		this.multipartType = multipartType;
		this.boundary = boundary;
		this.charset = charset;
	}

	public void addPart(Part part)
	{
		multipart.add(part);
	}

	public void processMultipart(boolean isInnerPart)
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try
		{
			String ct;
			if(charset.equals(AWSConstants.DEFAULT_CHARSET))
			{
				ct = this.multipartType+"; boundary="+boundary;//No I18n
			}
			else
			{
				ct = this.multipartType+"; boundary="+boundary+"; charset="+charset;//No I18n
			}
			if (isInnerPart)
			{
				baos.write(("Content-Type: "+ct).getBytes(charset));//No I18n
				baos.write(crlf);
				baos.write(crlf);
			}
			this.contentType = ct;

			if (preamble != null && preamble.length > 0)
			{
				baos.write(preamble);
				baos.write(crlf);
			}

			Iterator<Part> iterator = multipart.iterator();
			while (iterator.hasNext())
			{
				baos.write(delimiter);
				baos.write(boundary.getBytes(charset));
				baos.write(crlf);
				Part part = iterator.next();
				if(multipartType.equals(FORM_DATA))
				{
					if(!part.isFormData())
					{
						throw new Exception("Form-data multipart should only contains form-data parts.");//No I18n
					}
				}
				baos.write(part.getData());
			}
			baos.write(delimiter);
			baos.write(boundary.getBytes(charset));
			baos.write(delimiter);
			baos.write(crlf);
			if (epilogue != null && epilogue.length > 0)
			{
				baos.write(epilogue);
			}
			baos.write(crlf);
			if (!isInnerPart)
			{
				contentlength = baos.toByteArray().length;
			}
			data = baos.toByteArray();
			logger.log(Level.FINE, "Process Multipart : data framed :: {0}",Multipart.class.getName(),AWSLogMethodConstants.PROCESS_MULTIPART, new String(data));//No I18n
		}
		catch (Exception ex)
		{
			logger.log(Level.INFO, "Exception in processMultipart : ",Multipart.class.getName(),AWSLogMethodConstants.PROCESS_MULTIPART, ex);//No I18n
		}
		finally
		{
			try
			{
				baos.close();
			}
			catch (Exception exp)
			{
			}
		}
	}

	public void setPreambleContent(byte[] preamble)
	{
		this.preamble = preamble;
	}

	public void setEpilogueContent(byte[] epilogue)
	{
		this.epilogue = epilogue;
	}

	public void setCharset(String charset)
	{
		this.charset = charset;
	}

	void setPreambleContent(String preamble)
	{
		this.preambleData = preamble;
	}

	void setEpilogueContent(String epilogue)
	{
		this.epilogueData = epilogue;
	}

	public String getPreambleData()
	{
		return preambleData;
	}

	public String getEpilogueData()
	{
		return epilogueData;
	}

	public byte[] getData()
	{
		return data;
	}

	public int getContentLength()
	{
		return this.contentlength;
	}

	public String getContentType()
	{
		return this.contentType;
	}

	public String getCharset()
	{
		return charset;
	}

	public ArrayList<Part> getMultipartData()
	{
		return multipart;
	}

	private int getRandomNumber()
	{
		return random.nextInt(10000);
	}
}
