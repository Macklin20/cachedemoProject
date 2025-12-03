//$Id$
package com.zoho.wms.asyncweb.server.http2;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.logging.Level;

import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.http2.huffman.HuffmanEncoder;

import static com.zoho.wms.asyncweb.server.http2.Http2Constants.HEADER_SEPARATOR;

public class Encoder
{
	private static AsyncLogger logger = new AsyncLogger(Encoder.class.getName());

	public static String getIndexedHeader(int index)
	{
		String encoded = null;
		byte[] b = encodeValue(index,7);
		b[0]=(byte)(b[0] ^ 0x80);
		
		try
		{
			encoded = Http2Util.binaryToHex(b);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Encoder Exception", ex);
		}
		return encoded;
	}

	public static String getIndexedHeader(String header, IndexTable idx)
	{
		int index = idx.getIndex(header);
		return getIndexedHeader(index);
	}
	
	public static String getLiteralHeaderWithIncrementalIndexing(int index, String value, boolean huffman)
	{
		String encoded = null;
		try
		{
			byte[] ch = null;
			int length = 0;
			if(value != null)
			{
				try
				{
					if(huffman)
					{
						ch = getHuffmanEncoded(value);
					}
					else
					{
						ch = value.getBytes("us-ascii");
					}
				}
				catch(Exception ex)
				{
					logger.log(Level.SEVERE, "Encoder Exception", ex);
					return null;
				}
				length = ch.length;
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] b = encodeValue(index,6);
			b[0]=(byte)(b[0] ^ 0x40);
			baos.write(b);
			b = encodeValue(length,7);
			if(huffman)
			{
				b[0]=(byte)(b[0] ^ 0x80);
			}
			baos.write(b);
			for(int i=0; i<length;i++)
			{
				baos.write((byte)ch[i]);
			}
			b = baos.toByteArray();
			try
			{
				encoded = Http2Util.binaryToHex(b);
			}
			catch(Exception ex)
			{
				logger.log(Level.SEVERE, "Encoder Exception", ex);
			}
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Encoder Exception", ex);
		}
		return encoded;
	}

	public static String getLiteralHeaderWithIncrementalIndexing(String name, String value, boolean huffman, IndexTable idx)
	{
		String encoded = null;
		try
		{
			int index = idx.getIndex(name+HEADER_SEPARATOR);
			if(index != -1)
			{
				return getLiteralHeaderWithIncrementalIndexing(index,value,huffman);
			}
			byte[] nameArray = null;
			byte[] valueArray = null;
			int nameLength = 0;
			int valueLength = 0;

			if(name != null && value != null)
			{
				try
				{
					if(huffman)
					{
						nameArray = getHuffmanEncoded(name);
						valueArray = getHuffmanEncoded(value);
					}
					else
					{
						nameArray = name.getBytes("us-ascii");
						valueArray = value.getBytes("us-ascii");
					}
				}
				catch(Exception ex)
				{
					logger.log(Level.SEVERE, "Encoder Exception", ex);
					return null;
				}
				
				nameLength = nameArray.length;
				valueLength = valueArray.length;
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write((byte)0x40);

			byte[] b = encodeValue(nameLength,7);
			if(huffman)
			{
				b[0]=(byte)(b[0] ^ 0x80);
			}
			baos.write(b);

			for(int i=0; i<nameLength;i++)
			{
				baos.write((byte)nameArray[i]);
			}

			b = encodeValue(valueLength,7);
			if(huffman)
			{
				b[0]=(byte)(b[0] ^ 0x80);
			}
			baos.write(b);

			for(int i=0; i<valueLength;i++)
			{
				baos.write((byte)valueArray[i]);
			}
			
			b=baos.toByteArray();
			encoded = Http2Util.binaryToHex(b);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Encoder Exception", ex);
		}
		return encoded;
	}

	public static String getLiteralHeaderWithoutIndexing(int index, String value, boolean huffman)
	{
		String encoded = null;
		try
		{
			byte[] ch = null;
			int length = 0;
			if(value != null)
			{
				try
				{
					if(huffman)
					{
						ch = getHuffmanEncoded(value);
					}
					else
					{
						ch = value.getBytes("us-ascii");
					}
				}
				catch(Exception ex)
				{
					logger.log(Level.SEVERE, "Encoder Exception", ex);
					return null;
				}
				length = ch.length;
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] b = encodeValue(index,4);
			baos.write(b);
			b = encodeValue(length,7);
			if(huffman)
			{
				b[0]=(byte)(b[0] ^ 0x80);
			}
			baos.write(b);
			for(int i=0; i<length;i++)
			{
				baos.write((byte)ch[i]);
			}
			b = baos.toByteArray();
			encoded = Http2Util.binaryToHex(b);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Encoder Exception", ex);
		}
		return encoded;
	}

	public static String getLiteralHeaderWithoutIndexing(String name, String value, boolean huffman, IndexTable idx)
	{
		String encoded = null;
		try
		{
			int index = idx.getIndex(name+HEADER_SEPARATOR);
			if(index != -1)
			{
				return getLiteralHeaderWithIncrementalIndexing(index,value,huffman);
			}
			byte[] nameArray = null;
			byte[] valueArray = null;
			int nameLength = 0;
			int valueLength = 0;

			if(name != null && value != null)
			{
				try
				{
					if(huffman)
					{
						nameArray = getHuffmanEncoded(name);
						valueArray = getHuffmanEncoded(value);
					}
					else
					{
						nameArray = name.getBytes("us-ascii");
						valueArray = value.getBytes("us-ascii");
					}
				}
				catch(Exception ex)
				{
					logger.log(Level.SEVERE, "Encoder Exception", ex);
					return null;
				}
				
				nameLength = nameArray.length;
				valueLength = valueArray.length;
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write((byte)0x00);

			byte[] b = encodeValue(nameLength,7);
			if(huffman)
			{
				b[0]=(byte)(b[0] ^ 0x80);
			}
			baos.write(b);

			for(int i=0; i<nameLength;i++)
			{
				baos.write((byte)nameArray[i]);
			}

			b = encodeValue(valueLength,7);
			if(huffman)
			{
				b[0]=(byte)(b[0] ^ 0x80);
			}
			baos.write(b);

			for(int i=0; i<valueLength;i++)
			{
				baos.write((byte)valueArray[i]);
			}
			
			b=baos.toByteArray();
			encoded = Http2Util.binaryToHex(b);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Encoder Exception", ex);
		}
		return encoded;
	}

	public static String getIncrementalHeaderFieldNeverIndexed(int index,String value,boolean huffman)
	{
		String encoded = null;
		try
		{
			byte[] ch = null;
			int length = 0;
			if(value != null)
			{
				try
				{
					if(huffman)
					{
						ch = getHuffmanEncoded(value);
					}
					else
					{
						ch = value.getBytes("us-ascii");
					}
				}
				catch(Exception ex)
				{
					logger.log(Level.SEVERE, "Encoder Exception", ex);
					return null;
				}
				length = ch.length;
			}
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] b = encodeValue(index,4);
			b[0]=(byte)(b[0] ^ 0x10);
			baos.write(b);
			b = encodeValue(length,7);
			if(huffman)
			{
				b[0]=(byte)(b[0] ^ 0x80);
			}
			baos.write(b);
			for(int i=0; i<length;i++)
			{
				baos.write((byte)ch[i]);
			}
			b = baos.toByteArray();
			encoded = Http2Util.binaryToHex(b);
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Encoder Exception", ex);
		}
		return encoded;
	}

	public static String getLiteralHeaderNeverIndexed(String name, String value, boolean huffman, IndexTable idx)
	{
		String encoded = null;
		try
		{
			int index = idx.getIndex(name+HEADER_SEPARATOR);
			if(index != -1)
			{
				return getLiteralHeaderWithIncrementalIndexing(index,value,huffman);
			}
			byte[] nameArray = null;
			byte[] valueArray = null;
			int nameLength = 0;
			int valueLength = 0;

			if(name != null && value != null)
			{
				try
				{
					if(huffman)
					{
						nameArray = getHuffmanEncoded(name);
						valueArray = getHuffmanEncoded(value);
					}
					else
					{
						nameArray = name.getBytes("us-ascii");
						valueArray = value.getBytes("us-ascii");
					}
				}
				catch(Exception ex)
				{
					logger.log(Level.SEVERE, "Encoder Exception", ex);
					return null;
				}
				
				nameLength = nameArray.length;
				valueLength = valueArray.length;
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write((byte)0x10);

			byte[] b = encodeValue(nameLength,7);
			if(huffman)
			{
				b[0]=(byte)(b[0] ^ 0x80);
			}
			baos.write(b);

			for(int i=0; i<nameLength;i++)
			{
				baos.write((byte)nameArray[i]);
			}

			b = encodeValue(valueLength,7);
			if(huffman)
			{
				b[0]=(byte)(b[0] ^ 0x80);
			}
			baos.write(b);

			for(int i=0; i<valueLength;i++)
			{
				baos.write((byte)valueArray[i]);
			}
			
			b=baos.toByteArray();
			encoded = Http2Util.binaryToHex(b);
			
		}
		catch(Exception ex)
		{
			logger.log(Level.SEVERE, "Encoder Exception", ex);
		}
		
		return encoded;
	}
	
//	private static byte[] getHuffmanEncoded(String encoded) // version 1 //original // working good
//	{
//		StringBuilder sb = new StringBuilder();
//		for(int i = 0; i < encoded.length() ; i++)
//		{
//			int a = (int) encoded.charAt(i);
//			sb.append(IndexUtil.getHuffmanEncodedValue(a));
//		}
//
//		String result = sb.toString();
//		int toPad = 8 - (result.length() % 8);
//		toPad = (toPad == 8) ?0 : toPad;
//
//		if( toPad > 0)
//		{
//			for(int i = 0; i<toPad ; i++)
//			{
//				result = result+"1";
//			}
//		}
//
//		byte[] b = new byte[result.length()/8];
//
//
//		for(int i=0, j=8; j <= result.length() ; i=i+8,j=j+8)
//		{
//			b[i/8] = (byte)Integer.parseInt(result.substring(i,j),2);
//		}
//
//		return b;
//	}

//	private static byte[] getHuffmanEncoded(String encoded) // tulassi modified // working good
//	{
//		StringBuilder sb = new StringBuilder();
//		for(int i = 0; i < encoded.length() ; i++)
//		{
//			int a = (int) encoded.charAt(i);
//			sb.append(IndexUtil.getHuffmanEncodedValue(a));
//		}
//
//		StringBuilder result = sb;
//		int toPad = 8 - (result.length() % 8);
//		toPad = (toPad == 8) ?0 : toPad;
//
//		if( toPad > 0)
//		{
//			for(int i = 0; i<toPad ; i++)
//			{
//				//result = result+"1";
//				result.append("1");
//			}
//		}
//
//		byte[] b = new byte[result.length()/8];
//
//
//		for(int i=0, j=8; j <= result.length() ; i=i+8,j=j+8)
//		{
//			b[i/8] = (byte)Integer.parseInt(result.substring(i,j),2);
//		}
//
//		return b;
//	}

//	private static byte[] getHuffmanEncoded(String encoded) // version 3 // tcat // working good // best performance
//	{
//		StringBuilder sb = new StringBuilder();
//		for(int i = 0; i < encoded.length() ; i++)
//		{
//			int a = (int) encoded.charAt(i);
//			sb.append(IndexUtil.getHuffmanEncodedValue(a));
//		}
//
//		StringBuilder result = sb;
//		int toPad = 8 - (result.length() % 8);
//		toPad = (toPad == 8) ?0 : toPad;
//
//		if( toPad > 0)
//		{
//			for(int i = 0; i<toPad ; i++)
//			{
//				//result = result+"1";
//				result.append("1");
//			}
//		}
//
//		byte[] b = new byte[result.length()/8];
//
//
//		for(int i=0, j=8; j <= result.length() ; i=i+8,j=j+8)
//		{
//			b[i/8] = (byte)Integer.parseInt(result.substring(i,j),2);
//		}
//
//		return b;
//	}

//	private static byte[] getHuffmanEncoded(String encoded) // version 3 // tcat // working
//	{
//		ByteBuffer bb = ByteBuffer.allocate(encoded.length() * 2);
//		boolean status = HPackHuffman.encode(bb, encoded, true);
//
//		bb.flip();
//		byte[] b = new byte[bb.limit()];
//		bb.get(b, 0, bb.limit());
//		return b;
//	}

	private static byte[] getHuffmanEncoded(String encoded) throws Exception // version 4 // own encoder // working good
	{
		byte[] b = HuffmanEncoder.encodeHuffman(encoded);
		return b;
	}


	private static byte[] encodeValue(int value, int bits)
	{
		if(!(bits > 0 && bits <=8))
		{
			return null;
		}

		int limit = (int)(Math.pow(2,bits)-1);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		 
		if(value < limit)
		{
			baos.write((byte)value); 
		}
		else
		{	
			value = value - limit;
			baos.write((byte)limit);
			
			while(value > 128)
			{
				baos.write((byte)((value%128)+128));
				value = value/128;
			}

			baos.write((byte)value);
		}
		
		return baos.toByteArray();
	}
}

