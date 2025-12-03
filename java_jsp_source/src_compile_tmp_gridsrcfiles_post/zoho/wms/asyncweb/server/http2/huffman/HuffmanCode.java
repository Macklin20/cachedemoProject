package com.zoho.wms.asyncweb.server.http2.huffman;

public class HuffmanCode
{
    private long value;
    private int length;
    private int index = -1;

    public HuffmanCode(long value, int length, int index)
    {
        this.value = value;
        this.length = length;
        this.index = index;
    }
    public HuffmanCode(long value, int length)
    {
        this.value = value;
        this.length = length;
    }

//    public boolean equals(Object obj)
//    {
//        if(obj == null || getClass() != obj.getClass())
//        {
//            return false;
//        }
//
//        HuffmanCode hc= (HuffmanCode)obj;
//
//        if(length == hc.getLength() && value == hc.getValue())
//        {
//            return true;
//        }
//        return false;
//    }

    public long getValue()
    {
        return value;
    }

    public int getLength()
    {
        return length;
    }

    public int getIndex()
    {
        return index;
    }
}
