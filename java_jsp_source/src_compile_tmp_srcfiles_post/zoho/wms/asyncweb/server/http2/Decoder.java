//$Id$
package com.zoho.wms.asyncweb.server.http2;

// Java Import
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.logging.Level;

// aws import
import com.zoho.wms.asyncweb.server.ConfManager;
import com.zoho.wms.asyncweb.server.asynclogs.AsyncLogger;
import com.zoho.wms.asyncweb.server.exception.Http2Exception;
import com.zoho.wms.asyncweb.server.http2.huffman.HuffmanUtil;
import com.zoho.wms.asyncweb.server.http2.huffman.HuffmanDecoder;


// constants import
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.HEADER_SEPARATOR;

public class Decoder
{
    private static AsyncLogger logger = new AsyncLogger(Decoder.class.getName());

    public static String decodeHex(String hex, IndexTable idx) throws Http2Exception
    {
        byte[] b = null;

        try
        {
            b = Http2Util.convertHexToByteArray(hex);
        }
        catch(Exception ex)
        {
            logger.log(Level.SEVERE, "Decoder Exception", ex);
            throw ex;
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        return decodeHex(bais, 0, idx);
    }

    public static String decodeHex(ByteArrayInputStream bais, int paddingLength, IndexTable idx) throws Http2Exception
    {
        StringBuilder sb = new StringBuilder();

        while(bais.available() > paddingLength)
        {
            bais.mark(bais.available());
            int bytee = bais.read() & 0xFF;
            bais.reset();

            if(bytee>=128 && bytee<256)
            {
                processIndexedHeaderFieldRepresentation(bais, idx, sb);
                sb.append("\r\n");
            }
            else if(bytee>=64 && bytee<128)
            {
                processLiteralHeaderFieldWithIncrementalIndexing(bais, idx, sb);
                sb.append("\r\n");
            }
            else if(bytee<16)
            {
                processLiteralHeaderFieldWithoutIndexing(bais, idx, sb);
                sb.append("\r\n");
            }
            else if(bytee>=16 && bytee<32)
            {
                processLiteralHeaderFieldNeverIndexed(bais, idx, sb);
                sb.append("\r\n");
            }
            else if(bytee>=32 && bytee<64) // Dynamic Table Size Update - rfc 7541 - 6.3
            {
                processDynamicTableUpdateInstruction(bais);
            }
            else
            {
                throw new Http2Exception("Decoder Error - Invalid byte Received - bytee:"+bytee);
            }
        }

        return sb.toString();
    }

    private static StringBuilder processIndexedHeaderFieldRepresentation(ByteArrayInputStream bais, IndexTable idx, StringBuilder sb) throws Http2Exception
    {
        bais.mark(bais.available());
        if((bais.read() & 0x80) != 128)
        {
            throw new Http2Exception("Decoder Exception: bais.read():"+bais.read()+" - (bais.read() & 0x80):"+(bais.read() & 0x80));
        }
        bais.reset();

        sb.append(idx.getIndexValue(decodeValue(bais,7)));
        return sb;
    }

    private static StringBuilder processLiteralHeaderFieldWithIncrementalIndexing(ByteArrayInputStream bais, IndexTable idx, StringBuilder sb) throws Http2Exception
    {
        bais.mark(bais.available());
        int firstByte = bais.read();
        bais.reset();

        if((firstByte & 0x3F) == 0)
        {
            if(bais.available() < 3)
            {
                throw new Http2Exception("DECODER ERROR - Incomplete payload received - IncrementalIndexing - length:"+bais.available());
            }

            bais.skip(1);

            bais.mark(bais.available());
            boolean huffmanName = (((bais.read()) & 0x80) == 128)?true :false;
            bais.reset();

            int nameLength = decodeValue(bais,7);
            byte[] nameArray = new byte[nameLength];
            if(bais.available() >= nameLength)
            {
                bais.read(nameArray,0,nameLength);
            }

            String name = null;
            if(huffmanName)
            {
                name = decodeHuffman(nameArray);
            }
            else
            {
                name = Http2Util.getString(nameArray);
            }

            bais.mark(bais.available());
            boolean huffmanValue = (((bais.read()) & 0x80) == 128)?true :false;
            bais.reset();

            int valueLength = decodeValue(bais,7);
            byte[] valueArray = new byte[valueLength];
            if(bais.available() >= valueLength)
            {
                bais.read(valueArray,0,valueLength);
            }

            String value = null;
            if(huffmanValue)
            {
                value = decodeHuffman(valueArray);
            }
            else
            {
                value = Http2Util.getString(valueArray);
            }

            idx.addToDynamicTable(new IndexTable.HeaderField(name, value));
            sb.append(name + HEADER_SEPARATOR + value);
        }
        else
        {
            int index = decodeValue(bais,6);
            String name = idx.getIndexName(index);

            bais.mark(bais.available());
            boolean huffmanValue = (((bais.read()) & 0x80) == 128)?true :false;
            bais.reset();

            int valueLength = decodeValue(bais,7);
            byte[] valueArray = new byte[valueLength];
            if(bais.available() >= valueLength)
            {
                bais.read(valueArray,0,valueLength);
            }

            String value = null;
            if(huffmanValue)
            {
                value = decodeHuffman(valueArray);
            }
            else
            {
                value = Http2Util.getString(valueArray);
            }

            idx.addToDynamicTable(new IndexTable.HeaderField(name, value));
            sb.append(name + HEADER_SEPARATOR + value);
        }

        return sb;
    }

    private static StringBuilder processLiteralHeaderFieldWithoutIndexing(ByteArrayInputStream bais, IndexTable idx, StringBuilder sb) throws Http2Exception
    {
        bais.mark(bais.available());
        int firstByte = bais.read();
        bais.reset();

        if((firstByte & 0x0F) == 0)
        {
            if(bais.available() < 3)
            {
                throw new Http2Exception("DECODER ERROR - Incomplete payload received - WithoutIndexing - length:"+bais.available());
            }

            bais.skip(1);

            bais.mark(bais.available());
            boolean huffmanName = (((bais.read()) & 0x80) == 128)?true :false;
            bais.reset();

            int nameLength = decodeValue(bais,7);
            byte[] nameArray = new byte[nameLength];
            if(bais.available() >= nameLength)
            {
                bais.read(nameArray,0,nameLength);
            }

            String name = null;
            if(huffmanName)
            {
                name = decodeHuffman(nameArray);
            }
            else
            {
                name = Http2Util.getString(nameArray);
            }

            bais.mark(bais.available());
            boolean huffmanValue = (((bais.read()) & 0x80) == 128)?true :false;
            bais.reset();

            int valueLength = decodeValue(bais,7);
            byte[] valueArray = new byte[valueLength];
            if(bais.available() >= valueLength)
            {
                bais.read(valueArray,0,valueLength);
            }

            String value = null;
            if(huffmanValue)
            {
                value = decodeHuffman(valueArray);
            }
            else
            {
                value = Http2Util.getString(valueArray);
            }

            sb.append(name + HEADER_SEPARATOR + value);
        }
        else
        {
            int index = decodeValue(bais,4);
            String name = idx.getIndexName(index);

            bais.mark(bais.available());
            boolean huffmanValue = (((bais.read()) & 0x80) == 128)?true :false;
            bais.reset();

            int valueLength = decodeValue(bais,7);
            byte[] valueArray = new byte[valueLength];
            if(bais.available() >= valueLength)
            {
                bais.read(valueArray,0,valueLength);
            }

            String value = null;
            if(huffmanValue)
            {
                value = decodeHuffman(valueArray);
            }
            else
            {
                value = Http2Util.getString(valueArray);
            }

            sb.append(name + HEADER_SEPARATOR + value);
        }

        return sb;
    }

    private static StringBuilder processLiteralHeaderFieldNeverIndexed(ByteArrayInputStream bais, IndexTable idx, StringBuilder sb) throws Http2Exception
    {
        bais.mark(bais.available());
        int firstByte = bais.read();
        bais.reset();

        if((firstByte & 0x0F) == 0)
        {
            if(bais.available() < 3)
            {
                throw new Http2Exception("DECODER ERROR - Incomplete payload received - NeverIndexed - length:"+bais.available());
            }

            bais.skip(1);

            bais.mark(bais.available());
            boolean huffmanName = (((bais.read()) & 0x80) == 128)?true :false;
            bais.reset();

            int nameLength = decodeValue(bais,7);
            byte[] nameArray = new byte[nameLength];
            if(bais.available() >= nameLength)
            {
                bais.read(nameArray,0,nameLength);
            }

            String name = null;
            if(huffmanName)
            {
                name = decodeHuffman(nameArray);
            }
            else
            {
                name = Http2Util.getString(nameArray);
            }

            bais.mark(bais.available());
            boolean huffmanValue = (((bais.read()) & 0x80) == 128)?true :false;
            bais.reset();

            int valueLength = decodeValue(bais,7);
            byte[] valueArray = new byte[valueLength];
            if(bais.available() >= valueLength)
            {
                bais.read(valueArray,0,valueLength);
            }

            String value = null;
            if(huffmanValue)
            {
                value = decodeHuffman(valueArray);
            }
            else
            {
                value = Http2Util.getString(valueArray);
            }

            sb.append(name + HEADER_SEPARATOR + value);
        }
        else
        {
            int index = decodeValue(bais,4);
            String name = idx.getIndexName(index);

            bais.mark(bais.available());
            boolean huffmanValue = (((bais.read()) & 0x80) == 128)?true:false;
            bais.reset();

            int valueLength = decodeValue(bais,7);
            byte[] valueArray = new byte[valueLength];
            if(bais.available() >= valueLength)
            {
                bais.read(valueArray,0,valueLength);
            }

            String value = null;
            if(huffmanValue)
            {
                value = decodeHuffman(valueArray);
            }
            else
            {
                value = Http2Util.getString(valueArray);
            }

            sb.append(name + HEADER_SEPARATOR + value);
        }

        return sb;
    }

    private static void processDynamicTableUpdateInstruction(ByteArrayInputStream bais)
    {
        int size = decodeValue(bais, 5);

        if(size != ConfManager.getDynamicTableSize())
        {
            logger.log(Level.SEVERE, "DYNAMIC TABLE SIZE UPDATE INSTRUCTION DIFFERS FORM SERVER SENT SIZE - receivedSize:"+size+" - serverValue:"+ConfManager.getDynamicTableSize());
        }
    }

//    private static byte[] decodeHuffman(byte[] b) // version 1 //original
//    {
//        String remaining = "";
//        StringBuilder sb = new StringBuilder();
//        boolean endOctet = false;
//
//        for(int i = 0; ((i < b.length) || !remaining.equals("")); i++)
//        {
//            String str;
//
//            if(i < b.length)
//            {
//                str = String.format("%8s", Integer.toBinaryString(b[i] & 0xFF)).replace(' ', '0');
//                str = remaining + str;
//                remaining = "";
//            }
//            else if(remaining.length() > 4 && !endOctet)
//            {
//                str = remaining;
//                endOctet = true;
//            }
//            else
//            {
//                break;
//            }
//
//
//            for(int k=0, j=5; j<=str.length(); j++)
//            {
//                String encoded = str.substring(k,j);
//                int decodedASCII = IndexUtil.getHuffmanDecodedValue(encoded);
//
//
//                if(decodedASCII != -1)
//                {
//                    sb.append((char)decodedASCII);
//                    remaining = str.substring(j,str.length());
//                    if(endOctet)
//                    {
//                        k = j;
//                        j=j+4;
//                    }
//                    else
//                    {
//                        break;
//                    }
//                }
//                else if(j == str.length() && !endOctet)
//                {
//                    remaining = str;
//                }
//            }
//        }
//        return sb.toString().getBytes();
//    }

//    private static byte[] decodeHuffman(byte[] b) // version 2 //tulassi modified
//    {
//        StringBuilder remaining = new StringBuilder();
//        StringBuilder sb = new StringBuilder();
//        boolean endOctet = false;
//        int j = 5;
//
//        for(int i = 0; ((i < b.length) || remaining.length() > 0); i++)
//        {
//            StringBuilder str;
//
//            if(i < b.length)
//            {
//                str = new StringBuilder(String.format("%8s", Integer.toBinaryString(b[i] & 0xFF)).replace(' ', '0'));
//                str.insert(0,remaining);
//                remaining = new StringBuilder();
//            }
//            else if(remaining.length() > 4 && !endOctet)
//            {
//                str = remaining;
//                endOctet = true;
//            }
//            else
//            {
//                break;
//            }
//
//            for(int k=0; j<=str.length(); j++)
//            {
//                String encoded = str.substring(k,j);
//                int decodedASCII = IndexUtil.getHuffmanDecodedValue(encoded);
//
//
//                if(decodedASCII != -1)
//                {
//                    sb.append((char)decodedASCII);
//                    remaining = new StringBuilder(str.substring(j,str.length()));
//                    if(endOctet)
//                    {
//                        k = j;
//                        j=j+4;
//                    }
//                    else
//                    {
//                        j = 5;
//                        break;
//                    }
//                }
//                else if(j == str.length() && !endOctet)
//                {
//                    remaining = str;
//                }
//            }
//        }
//        return sb.toString().getBytes();
//    }


//    private static byte[] decodeHuffman(byte[] b) // version 3 //tulassi modified // working good //optimized performace
//    {
//        StringBuilder sb = new StringBuilder();
//        StringBuilder str = new StringBuilder();
//
//        for(int i = 0 ; i < b.length ; i++)
//        {
//            str.append(String.format("%8s", Integer.toBinaryString(b[i] & 0xFF)).replace(' ', '0'));
//        }
//
//        for(int k = 0, j = 5 ; j <= str.length() && k < str.length(); j++)
//        {
//            String encoded = str.substring(k,j);
//            int decodedASCII = IndexUtil.getHuffmanDecodedValue(encoded);
//
//            if(decodedASCII != -1)
//            {
//                sb.append((char)decodedASCII);
//                k = j;
//                j=j+4;
//            }
//        }
//
//        return sb.toString().getBytes();
//    }

//    private static byte[] decodeHuffman(byte[] b) //tulassi 2 // own decoder // not tested
//    {
//        StringBuilder sb = new StringBuilder();
//
//        int length = b.length * 8;
//
//        for(int k = 0, j = 5 ; j <= length && k < length; j++)
//        {
//            int decodedASCII = -1;
//            if(HuffmanUtil.isValidSplitLength(j - k));
//            {
//                long value = HuffmanDecoder.decode(b, k, j);
//                decodedASCII = HuffmanUtil.getIndex(value, j - k);
//            }
//            //String encoded = str.substring(k,j);
//            //int decodedASCII = IndexUtil.getHuffmanDecodedValue(encoded);
//
//            if(decodedASCII != -1)
//            {
//                sb.append((char)decodedASCII);
//                k = j;
//                j=j+4;
//            }
//        }
//
//        return sb.toString().getBytes();
//    }

    public static String decodeHuffman(byte[] b) // own decoder 2 // working good
    {
        return HuffmanDecoder.decode(b);
    }


//    private static byte[] decodeHuffman(byte[] b) // tcat/working good/best performance
//    {
//        StringBuilder sb = new StringBuilder();
//        ByteBuffer data = ByteBuffer.wrap(b);
//
//        try
//        {
//            HPackHuffman.decode(data, b.length, sb);
//        }
//        catch (Exception ex)
//        {
//            logger.log(Level.SEVERE, "Huffman Decoder Exception", ex);
//        }
//
//        return sb.toString().getBytes();
//    }


    private static int decodeValue(ByteArrayInputStream b, int bits)
    {
        if(!(bits > 0 && bits <=8))
        {
            return -1;
        }

        int limit = (int)(Math.pow(2,bits)-1);
        int value = b.read();

        if(value > limit)
        {
            value = value & limit;
        }

        if(value < limit)
        {
            return value;
        }
        else if(value == limit)
        {
            int i = 0;
            int nextByte;
            do
            {
                nextByte = b.read();
                value = value + (nextByte & 0x7F) * ((int)Math.pow(2,i));
                i = i + 7;
            }
            while((nextByte & 128) == 128);
        }

        return value;
    }
}
