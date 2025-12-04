//$Id$
package com.zoho.wms.asyncweb.server.http2;

import java.util.ArrayList;
import com.zoho.wms.asyncweb.server.ConfManager;
import static com.zoho.wms.asyncweb.server.http2.Http2Constants.HEADER_SEPARATOR;

public class IndexTable
{
	private ArrayList<HeaderField> dynamicTable = new ArrayList<>();
	private int currentTableSize = 0;
	private int maxTableSize = ConfManager.getDynamicTableSize();

	public boolean isIndexPresent(int index)
	{
		return (index <= IndexUtil.STATIC_TABLE_SIZE+dynamicTable.size());
	}

	public String getIndexValue(int index)
	{
		index = index - 1;

		if(index>=0 && index<IndexUtil.STATIC_TABLE_SIZE)
		{
			return IndexUtil.getStaticTableValue(index);
		}
		else if(index>=IndexUtil.STATIC_TABLE_SIZE && index<IndexUtil.STATIC_TABLE_SIZE+dynamicTable.size())
		{
			HeaderField headerField = dynamicTable.get(index-IndexUtil.STATIC_TABLE_SIZE);
			return headerField.getName() + HEADER_SEPARATOR+ headerField.getValue();
		}

		return ""+(index+1);
	}

	public String getIndexName(int index)
	{
		index = index - 1;

		if(index>=0 && index<IndexUtil.STATIC_TABLE_SIZE)
		{
			return IndexUtil.getStaticTableValue(index).split(HEADER_SEPARATOR)[0];
		}
		else if(index>=IndexUtil.STATIC_TABLE_SIZE && index<IndexUtil.STATIC_TABLE_SIZE+dynamicTable.size())
		{
			return dynamicTable.get(index-IndexUtil.STATIC_TABLE_SIZE).getName();
		}

		return ""+(index+1);
	}

	public int getIndex(String value)
	{
		if(IndexUtil.getStaticTableIndex(value) != -1)
		{
			return IndexUtil.getStaticTableIndex(value)+1;
		}
		else if(dynamicTable.indexOf(value) != -1)
		{
			return dynamicTable.indexOf(value)+1;
		}

		return -1;
	}

	public void addToDynamicTable(HeaderField headerField)
	{
		while(headerField.getSize() + currentTableSize > maxTableSize && !dynamicTable.isEmpty())
		{
			currentTableSize -= dynamicTable.remove(dynamicTable.size() - 1).getSize();
		}
		dynamicTable.add(0, headerField);
		currentTableSize += headerField.getSize();
	}

	public void setMaxTableSize(int size)
	{
		maxTableSize = size;
	}

	public int getMaxTableSize()
	{
		return maxTableSize;
	}

	public void close()
	{
		dynamicTable = null;
	}

	static class HeaderField
	{
		private String name;
		private String value;
		private int size;

		public HeaderField(String name, String value)
		{
			this.name = name;
			this.value = value;
			this.size = name.length() + value.length() + 32; //32 is added as per rfc : refer rfc 7541 - sec 4.1
		}

		public String getName()
		{
			return name;
		}

		public String getValue()
		{
			return value;
		}

		public int getSize()
		{
			return size;
		}
	}
}