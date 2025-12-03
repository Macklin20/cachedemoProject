package com.zoho.wms.asyncweb.server.http2.huffman;

public class HuffmanDecoder
{
    public static int extractBits(int num ,int index)
    {
        if(index == 0)
        {
            return num;
        }
        else if(index == 1)
        {
            return 0x7f & num;
        }
        else if(index == 2)
        {
            return 0x3f & num;
        }
        else if(index == 3)
        {
            return 0x1f & num;
        }
        else if(index == 4)
        {
            return 0x0f & num;
        }
        else if(index == 5)
        {
            return 0x07 & num;
        }
        else if(index == 6)
        {
            return 0x03 & num;
        }
        else
        {
            return 0x01 & num;
        }
    }

    public static int extractBits(int num ,int start, int end)
    {
        return (((255>>start) & num) >> (8-end));
    }

	public static long decodeHuffman(byte[] b,int start,int end)
    {
        int startByte = start/8;
        int startBit = start%8;
        int endByte = end/8;
        int endBit = end%8;

        long combinedByte = 0;

        int size = endByte - startByte ;
        int i = startByte;

        while(size!=0)
        {
            if(size==endByte - startByte)
            {
                combinedByte = combinedByte | extractBits((b[i]>0) ? b[i++] : b[i++]+256,startBit);
            }
            else
            {
                combinedByte = combinedByte | extractBits((b[i]>0) ? b[i++] : b[i++]+256,0);
            }
            size--;

            combinedByte = (size==0) ? (combinedByte << endBit) : (combinedByte << 8);
        }

        combinedByte = combinedByte | ((endByte<b.length) ? (((b[endByte]<0) ? b[endByte]+256 : b[endByte]) >> (8-endBit)) : 0);

        if((startByte - endByte) == 0)
        {
            combinedByte = 0 | (extractBits(b[i],startBit, endBit));
        }

        return combinedByte;
    }

	public static String decode(byte[] b)
    {
        StringBuilder sb = new StringBuilder();
        int length = b.length * 8;

        for(int k = 0, j = 5 ; j <= length && k < length; j++)
        {
            int decodedASCII = -1;
            if(HuffmanUtil.isValidSplitLength(j - k))
            {
                long value = decodeHuffman(b, k, j);
                decodedASCII = HuffmanUtil.getIndex(value, j - k);
            }

            if(decodedASCII != -1)
            {
                sb.append((char)decodedASCII);
                k = j;
                j=j+4;
            }
        }
        return sb.toString();
    }
}


	
