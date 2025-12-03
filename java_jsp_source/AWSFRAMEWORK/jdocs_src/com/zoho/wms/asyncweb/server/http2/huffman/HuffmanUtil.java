package com.zoho.wms.asyncweb.server.http2.huffman;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;

public class HuffmanUtil
{
    static final ArrayList<HuffmanCode> HUFFMAN_LIST;
    static final HashMap<Integer,ArrayList<HuffmanCode>> LENGTH_MAP;
    static final HashSet<Integer> SPLIT_LENGTH;

    static
    {
        ArrayList<HuffmanCode> huffmanLst =new ArrayList<>();
        HashMap<Integer,ArrayList<HuffmanCode>> lgthMap=new HashMap<>();
        HashSet<Integer> splLength=new HashSet<>();
        HuffmanCode[] codes = new HuffmanCode[257];

        codes[0] = new HuffmanCode(0x1ff8, 13, 0);
        codes[1] = new HuffmanCode(0x7fffd8, 23, 1);
        codes[2] = new HuffmanCode(0xfffffe2, 28, 2);
        codes[3] = new HuffmanCode(0xfffffe3, 28, 3);
        codes[4] = new HuffmanCode(0xfffffe4, 28, 4);
        codes[5] = new HuffmanCode(0xfffffe5, 28, 5);
        codes[6] = new HuffmanCode(0xfffffe6, 28, 6);
        codes[7] = new HuffmanCode(0xfffffe7, 28, 7);
        codes[8] = new HuffmanCode(0xfffffe8, 28, 8);
        codes[9] = new HuffmanCode(0xffffea, 24, 9);
        codes[10] = new HuffmanCode(0x3ffffffc, 30, 10);
        codes[11] = new HuffmanCode(0xfffffe9, 28, 11);
        codes[12] = new HuffmanCode(0xfffffea, 28, 12);
        codes[13] = new HuffmanCode(0x3ffffffd, 30, 13);
        codes[14] = new HuffmanCode(0xfffffeb, 28, 14);
        codes[15] = new HuffmanCode(0xfffffec, 28, 15);
        codes[16] = new HuffmanCode(0xfffffed, 28, 16);
        codes[17] = new HuffmanCode(0xfffffee, 28, 17);
        codes[18] = new HuffmanCode(0xfffffef, 28, 18);
        codes[19] = new HuffmanCode(0xffffff0, 28, 19);
        codes[20] = new HuffmanCode(0xffffff1, 28, 20);
        codes[21] = new HuffmanCode(0xffffff2, 28, 21);
        codes[22] = new HuffmanCode(0x3ffffffe, 30, 22);
        codes[23] = new HuffmanCode(0xffffff3, 28, 23);
        codes[24] = new HuffmanCode(0xffffff4, 28, 24);
        codes[25] = new HuffmanCode(0xffffff5, 28, 25);
        codes[26] = new HuffmanCode(0xffffff6, 28, 26);
        codes[27] = new HuffmanCode(0xffffff7, 28, 27);
        codes[28] = new HuffmanCode(0xffffff8, 28, 28);
        codes[29] = new HuffmanCode(0xffffff9, 28, 29);
        codes[30] = new HuffmanCode(0xffffffa, 28, 30);
        codes[31] = new HuffmanCode(0xffffffb, 28, 31);
        codes[32] = new HuffmanCode(0x14, 6, 32);
        codes[33] = new HuffmanCode(0x3f8, 10, 33);
        codes[34] = new HuffmanCode(0x3f9, 10, 34);
        codes[35] = new HuffmanCode(0xffa, 12, 35);
        codes[36] = new HuffmanCode(0x1ff9, 13, 36);
        codes[37] = new HuffmanCode(0x15, 6, 37);
        codes[38] = new HuffmanCode(0xf8, 8, 38);
        codes[39] = new HuffmanCode(0x7fa, 11, 39);
        codes[40] = new HuffmanCode(0x3fa, 10, 40);
        codes[41] = new HuffmanCode(0x3fb, 10, 41);
        codes[42] = new HuffmanCode(0xf9, 8, 42);
        codes[43] = new HuffmanCode(0x7fb, 11, 43);
        codes[44] = new HuffmanCode(0xfa, 8, 44);
        codes[45] = new HuffmanCode(0x16, 6, 45);
        codes[46] = new HuffmanCode(0x17, 6, 46);
        codes[47] = new HuffmanCode(0x18, 6, 47);
        codes[48] = new HuffmanCode(0x0, 5, 48);
        codes[49] = new HuffmanCode(0x1, 5, 49);
        codes[50] = new HuffmanCode(0x2, 5, 50);
        codes[51] = new HuffmanCode(0x19, 6, 51);
        codes[52] = new HuffmanCode(0x1a, 6, 52);
        codes[53] = new HuffmanCode(0x1b, 6, 53);
        codes[54] = new HuffmanCode(0x1c, 6, 54);
        codes[55] = new HuffmanCode(0x1d, 6, 55);
        codes[56] = new HuffmanCode(0x1e, 6, 56);
        codes[57] = new HuffmanCode(0x1f, 6, 57);
        codes[58] = new HuffmanCode(0x5c, 7, 58);
        codes[59] = new HuffmanCode(0xfb, 8, 59);
        codes[60] = new HuffmanCode(0x7ffc, 15, 60);
        codes[61] = new HuffmanCode(0x20, 6, 61);
        codes[62] = new HuffmanCode(0xffb, 12, 62);
        codes[63] = new HuffmanCode(0x3fc, 10, 63);
        codes[64] = new HuffmanCode(0x1ffa, 13, 64);
        codes[65] = new HuffmanCode(0x21, 6, 65);
        codes[66] = new HuffmanCode(0x5d, 7, 66);
        codes[67] = new HuffmanCode(0x5e, 7, 67);
        codes[68] = new HuffmanCode(0x5f, 7, 68);
        codes[69] = new HuffmanCode(0x60, 7, 69);
        codes[70] = new HuffmanCode(0x61, 7, 70);
        codes[71] = new HuffmanCode(0x62, 7, 71);
        codes[72] = new HuffmanCode(0x63, 7, 72);
        codes[73] = new HuffmanCode(0x64, 7, 73);
        codes[74] = new HuffmanCode(0x65, 7, 74);
        codes[75] = new HuffmanCode(0x66, 7, 75);
        codes[76] = new HuffmanCode(0x67, 7, 76);
        codes[77] = new HuffmanCode(0x68, 7, 77);
        codes[78] = new HuffmanCode(0x69, 7, 78);
        codes[79] = new HuffmanCode(0x6a, 7, 79);
        codes[80] = new HuffmanCode(0x6b, 7, 80);
        codes[81] = new HuffmanCode(0x6c, 7, 81);
        codes[82] = new HuffmanCode(0x6d, 7, 82);
        codes[83] = new HuffmanCode(0x6e, 7, 83);
        codes[84] = new HuffmanCode(0x6f, 7, 84);
        codes[85] = new HuffmanCode(0x70, 7, 85);
        codes[86] = new HuffmanCode(0x71, 7, 86);
        codes[87] = new HuffmanCode(0x72, 7, 87);
        codes[88] = new HuffmanCode(0xfc, 8, 88);
        codes[89] = new HuffmanCode(0x73, 7, 89);
        codes[90] = new HuffmanCode(0xfd, 8, 90);
        codes[91] = new HuffmanCode(0x1ffb, 13, 91);
        codes[92] = new HuffmanCode(0x7fff0, 19, 92);
        codes[93] = new HuffmanCode(0x1ffc, 13, 93);
        codes[94] = new HuffmanCode(0x3ffc, 14, 94);
        codes[95] = new HuffmanCode(0x22, 6, 95);
        codes[96] = new HuffmanCode(0x7ffd, 15, 96);
        codes[97] = new HuffmanCode(0x3, 5, 97);
        codes[98] = new HuffmanCode(0x23, 6, 98);
        codes[99] = new HuffmanCode(0x4, 5, 99);
        codes[100] = new HuffmanCode(0x24, 6, 100);
        codes[101] = new HuffmanCode(0x5, 5, 101);
        codes[102] = new HuffmanCode(0x25, 6, 102);
        codes[103] = new HuffmanCode(0x26, 6, 103);
        codes[104] = new HuffmanCode(0x27, 6, 104);
        codes[105] = new HuffmanCode(0x6, 5, 105);
        codes[106] = new HuffmanCode(0x74, 7, 106);
        codes[107] = new HuffmanCode(0x75, 7, 107);
        codes[108] = new HuffmanCode(0x28, 6, 108);
        codes[109] = new HuffmanCode(0x29, 6, 109);
        codes[110] = new HuffmanCode(0x2a, 6, 110);
        codes[111] = new HuffmanCode(0x7, 5, 111);
        codes[112] = new HuffmanCode(0x2b, 6, 112);
        codes[113] = new HuffmanCode(0x76, 7, 113);
        codes[114] = new HuffmanCode(0x2c, 6, 114);
        codes[115] = new HuffmanCode(0x8, 5, 115);
        codes[116] = new HuffmanCode(0x9, 5, 116);
        codes[117] = new HuffmanCode(0x2d, 6, 117);
        codes[118] = new HuffmanCode(0x77, 7, 118);
        codes[119] = new HuffmanCode(0x78, 7, 119);
        codes[120] = new HuffmanCode(0x79, 7, 120);
        codes[121] = new HuffmanCode(0x7a, 7, 121);
        codes[122] = new HuffmanCode(0x7b, 7, 122);
        codes[123] = new HuffmanCode(0x7ffe, 15, 123);
        codes[124] = new HuffmanCode(0x7fc, 11, 124);
        codes[125] = new HuffmanCode(0x3ffd, 14, 125);
        codes[126] = new HuffmanCode(0x1ffd, 13, 126);
        codes[127] = new HuffmanCode(0xffffffc, 28, 127);
        codes[128] = new HuffmanCode(0xfffe6, 20, 128);
        codes[129] = new HuffmanCode(0x3fffd2, 22, 129);
        codes[130] = new HuffmanCode(0xfffe7, 20, 130);
        codes[131] = new HuffmanCode(0xfffe8, 20, 131);
        codes[132] = new HuffmanCode(0x3fffd3, 22, 132);
        codes[133] = new HuffmanCode(0x3fffd4, 22, 133);
        codes[134] = new HuffmanCode(0x3fffd5, 22, 134);
        codes[135] = new HuffmanCode(0x7fffd9, 23, 135);
        codes[136] = new HuffmanCode(0x3fffd6, 22, 136);
        codes[137] = new HuffmanCode(0x7fffda, 23, 137);
        codes[138] = new HuffmanCode(0x7fffdb, 23, 138);
        codes[139] = new HuffmanCode(0x7fffdc, 23, 139);
        codes[140] = new HuffmanCode(0x7fffdd, 23, 140);
        codes[141] = new HuffmanCode(0x7fffde, 23, 141);
        codes[142] = new HuffmanCode(0xffffeb, 24, 142);
        codes[143] = new HuffmanCode(0x7fffdf, 23, 143);
        codes[144] = new HuffmanCode(0xffffec, 24, 144);
        codes[145] = new HuffmanCode(0xffffed, 24, 145);
        codes[146] = new HuffmanCode(0x3fffd7, 22, 146);
        codes[147] = new HuffmanCode(0x7fffe0, 23, 147);
        codes[148] = new HuffmanCode(0xffffee, 24, 148);
        codes[149] = new HuffmanCode(0x7fffe1, 23, 149);
        codes[150] = new HuffmanCode(0x7fffe2, 23, 150);
        codes[151] = new HuffmanCode(0x7fffe3, 23, 151);
        codes[152] = new HuffmanCode(0x7fffe4, 23, 152);
        codes[153] = new HuffmanCode(0x1fffdc, 21, 153);
        codes[154] = new HuffmanCode(0x3fffd8, 22, 154);
        codes[155] = new HuffmanCode(0x7fffe5, 23, 155);
        codes[156] = new HuffmanCode(0x3fffd9, 22, 156);
        codes[157] = new HuffmanCode(0x7fffe6, 23, 157);
        codes[158] = new HuffmanCode(0x7fffe7, 23, 158);
        codes[159] = new HuffmanCode(0xffffef, 24, 159);
        codes[160] = new HuffmanCode(0x3fffda, 22, 160);
        codes[161] = new HuffmanCode(0x1fffdd, 21, 161);
        codes[162] = new HuffmanCode(0xfffe9, 20, 162);
        codes[163] = new HuffmanCode(0x3fffdb, 22, 163);
        codes[164] = new HuffmanCode(0x3fffdc, 22, 164);
        codes[165] = new HuffmanCode(0x7fffe8, 23, 165);
        codes[166] = new HuffmanCode(0x7fffe9, 23, 166);
        codes[167] = new HuffmanCode(0x1fffde, 21, 167);
        codes[168] = new HuffmanCode(0x7fffea, 23, 168);
        codes[169] = new HuffmanCode(0x3fffdd, 22, 169);
        codes[170] = new HuffmanCode(0x3fffde, 22, 170);
        codes[171] = new HuffmanCode(0xfffff0, 24, 171);
        codes[172] = new HuffmanCode(0x1fffdf, 21, 172);
        codes[173] = new HuffmanCode(0x3fffdf, 22, 173);
        codes[174] = new HuffmanCode(0x7fffeb, 23, 174);
        codes[175] = new HuffmanCode(0x7fffec, 23, 175);
        codes[176] = new HuffmanCode(0x1fffe0, 21, 176);
        codes[177] = new HuffmanCode(0x1fffe1, 21, 177);
        codes[178] = new HuffmanCode(0x3fffe0, 22, 178);
        codes[179] = new HuffmanCode(0x1fffe2, 21, 179);
        codes[180] = new HuffmanCode(0x7fffed, 23, 180);
        codes[181] = new HuffmanCode(0x3fffe1, 22, 181);
        codes[182] = new HuffmanCode(0x7fffee, 23, 182);
        codes[183] = new HuffmanCode(0x7fffef, 23, 183);
        codes[184] = new HuffmanCode(0xfffea, 20, 184);
        codes[185] = new HuffmanCode(0x3fffe2, 22, 185);
        codes[186] = new HuffmanCode(0x3fffe3, 22, 186);
        codes[187] = new HuffmanCode(0x3fffe4, 22, 187);
        codes[188] = new HuffmanCode(0x7ffff0, 23, 188);
        codes[189] = new HuffmanCode(0x3fffe5, 22, 189);
        codes[190] = new HuffmanCode(0x3fffe6, 22, 190);
        codes[191] = new HuffmanCode(0x7ffff1, 23, 191);
        codes[192] = new HuffmanCode(0x3ffffe0, 26, 192);
        codes[193] = new HuffmanCode(0x3ffffe1, 26, 193);
        codes[194] = new HuffmanCode(0xfffeb, 20, 194);
        codes[195] = new HuffmanCode(0x7fff1, 19, 195);
        codes[196] = new HuffmanCode(0x3fffe7, 22, 196);
        codes[197] = new HuffmanCode(0x7ffff2, 23, 197);
        codes[198] = new HuffmanCode(0x3fffe8, 22, 198);
        codes[199] = new HuffmanCode(0x1ffffec, 25, 199);
        codes[200] = new HuffmanCode(0x3ffffe2, 26, 200);
        codes[201] = new HuffmanCode(0x3ffffe3, 26, 201);
        codes[202] = new HuffmanCode(0x3ffffe4, 26, 202);
        codes[203] = new HuffmanCode(0x7ffffde, 27, 203);
        codes[204] = new HuffmanCode(0x7ffffdf, 27, 204);
        codes[205] = new HuffmanCode(0x3ffffe5, 26, 205);
        codes[206] = new HuffmanCode(0xfffff1, 24, 206);
        codes[207] = new HuffmanCode(0x1ffffed, 25, 207);
        codes[208] = new HuffmanCode(0x7fff2, 19, 208);
        codes[209] = new HuffmanCode(0x1fffe3, 21, 209);
        codes[210] = new HuffmanCode(0x3ffffe6, 26, 210);
        codes[211] = new HuffmanCode(0x7ffffe0, 27, 211);
        codes[212] = new HuffmanCode(0x7ffffe1, 27, 212);
        codes[213] = new HuffmanCode(0x3ffffe7, 26, 213);
        codes[214] = new HuffmanCode(0x7ffffe2, 27, 214);
        codes[215] = new HuffmanCode(0xfffff2, 24, 215);
        codes[216] = new HuffmanCode(0x1fffe4, 21, 216);
        codes[217] = new HuffmanCode(0x1fffe5, 21, 217);
        codes[218] = new HuffmanCode(0x3ffffe8, 26, 218);
        codes[219] = new HuffmanCode(0x3ffffe9, 26, 219);
        codes[220] = new HuffmanCode(0xffffffd, 28, 220);
        codes[221] = new HuffmanCode(0x7ffffe3, 27, 221);
        codes[222] = new HuffmanCode(0x7ffffe4, 27, 222);
        codes[223] = new HuffmanCode(0x7ffffe5, 27, 223);
        codes[224] = new HuffmanCode(0xfffec, 20, 224);
        codes[225] = new HuffmanCode(0xfffff3, 24, 225);
        codes[226] = new HuffmanCode(0xfffed, 20, 226);
        codes[227] = new HuffmanCode(0x1fffe6, 21, 227);
        codes[228] = new HuffmanCode(0x3fffe9, 22, 228);
        codes[229] = new HuffmanCode(0x1fffe7, 21, 229);
        codes[230] = new HuffmanCode(0x1fffe8, 21, 230);
        codes[231] = new HuffmanCode(0x7ffff3, 23, 231);
        codes[232] = new HuffmanCode(0x3fffea, 22, 232);
        codes[233] = new HuffmanCode(0x3fffeb, 22, 233);
        codes[234] = new HuffmanCode(0x1ffffee, 25, 234);
        codes[235] = new HuffmanCode(0x1ffffef, 25, 235);
        codes[236] = new HuffmanCode(0xfffff4, 24, 236);
        codes[237] = new HuffmanCode(0xfffff5, 24, 237);
        codes[238] = new HuffmanCode(0x3ffffea, 26, 238);
        codes[239] = new HuffmanCode(0x7ffff4, 23, 239);
        codes[240] = new HuffmanCode(0x3ffffeb, 26, 240);
        codes[241] = new HuffmanCode(0x7ffffe6, 27, 241);
        codes[242] = new HuffmanCode(0x3ffffec, 26, 242);
        codes[243] = new HuffmanCode(0x3ffffed, 26, 243);
        codes[244] = new HuffmanCode(0x7ffffe7, 27, 244);
        codes[245] = new HuffmanCode(0x7ffffe8, 27, 245);
        codes[246] = new HuffmanCode(0x7ffffe9, 27, 246);
        codes[247] = new HuffmanCode(0x7ffffea, 27, 247);
        codes[248] = new HuffmanCode(0x7ffffeb, 27, 248);
        codes[249] = new HuffmanCode(0xffffffe, 28, 249);
        codes[250] = new HuffmanCode(0x7ffffec, 27, 250);
        codes[251] = new HuffmanCode(0x7ffffed, 27, 251);
        codes[252] = new HuffmanCode(0x7ffffee, 27, 252);
        codes[253] = new HuffmanCode(0x7ffffef, 27, 253);
        codes[254] = new HuffmanCode(0x7fffff0, 27, 254);
        codes[255] = new HuffmanCode(0x3ffffee, 26, 255);
        codes[256] = new HuffmanCode(0x3fffffff, 30, 256);

        for(int i=0;i<codes.length;i++)
        {
            huffmanLst.add(codes[i]);
            splLength.add(codes[i].getLength());
            if(!lgthMap.containsKey(codes[i].getLength()))
            {
                lgthMap.put(codes[i].getLength(),new ArrayList<HuffmanCode>());
            }
            lgthMap.get(codes[i].getLength()).add(codes[i]);

        }

        HUFFMAN_LIST = huffmanLst;
        SPLIT_LENGTH = splLength;
        LENGTH_MAP = lgthMap;

    }

    public static boolean isValidSplitLength(int length)
    {
        return SPLIT_LENGTH.contains(length);
    }

    public static HuffmanCode getCode(int index)
    {
        return HUFFMAN_LIST.get(index);
    }

    public static int getIndex(long value,int length)
    {
        if(LENGTH_MAP.get(length) != null)
        {
            ArrayList<HuffmanCode> list= LENGTH_MAP.get(length);
            int index=-1;
            for(int i=0;i<list.size();i++)
            {
                if((value-list.get(i).getValue())==0)
                {
                    index=list.get(i).getIndex();
                }
            }
            return index;
        }
        return -1;
    }
}

