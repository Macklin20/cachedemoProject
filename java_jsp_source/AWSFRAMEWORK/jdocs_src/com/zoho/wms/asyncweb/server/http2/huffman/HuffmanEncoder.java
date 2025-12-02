package com.zoho.wms.asyncweb.server.http2.huffman;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class HuffmanEncoder
{
        private static long clearUnMaskedBits(long number, int mask)
        {
                long binaryRepresentation = (1 << mask) - 1;
                return number & binaryRepresentation;
        }

        public static byte[] encodeHuffman(String data) throws Exception
        {
                ByteArrayOutputStream bao = new ByteArrayOutputStream();

                long mainarray = 0;
                int shiftno = 0;
                int usedbit = 0;

                for(int i = 0 ; i < data.length() ; i++)
                {
                        HuffmanCode hf = HuffmanUtil.getCode(data.charAt(i));

                        if(hf == null)
                        {
                                throw new Exception("Invalid input Data");
                        }

                        usedbit = usedbit + hf.getLength();
                        mainarray =  ((mainarray << shiftno) | hf.getValue());

                        while(usedbit >= 8)
                        {
                                bao.write((byte) (mainarray >> (usedbit - 8)));
                                usedbit = usedbit - 8;
                                mainarray = clearUnMaskedBits(mainarray, usedbit);
                        }

                        shiftno = (i<data.length()-1) ? HuffmanUtil.getCode(data.charAt(i+1)).getLength() : 0;
                }

                if(usedbit != 0)
                {
                        mainarray=(mainarray << (8 - usedbit));
                        for(int i=0;i<(8-usedbit);i++)
                        {
                                mainarray=(mainarray | (1<<i));
                        }
                        bao.write((byte)(mainarray));
                }

                return bao.toByteArray();
        }
}

