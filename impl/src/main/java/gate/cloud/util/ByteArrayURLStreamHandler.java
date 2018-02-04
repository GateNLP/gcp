/*
 *  ByteArrayURLStreamHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ByteArrayURLStreamHandler.java 17349 2014-02-19 18:02:24Z ian_roberts $ 
 */
package gate.cloud.util;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Header;

/**
 * This oddity is just a wrapper around a byte array and a URL, to
 * allow creation of GATE documents from a byte array with 
 * application/pdf type.  Donated by Ian. 
 */
public class ByteArrayURLStreamHandler
    extends URLStreamHandler   {
  
  private byte[] data;
  private Header[] headers;
  
  public ByteArrayURLStreamHandler(byte[] data) {
    this(data, null);
  }
  
  public ByteArrayURLStreamHandler(byte[] data, Header[] headers) {
    this.data = data;
    this.headers = headers;
  }
  
  public URLConnection openConnection(URL u) {
    return new URLConnection(u) {
      public void connect() {
        // do nothing, but superclass method is abstract
      }

      public InputStream getInputStream() {
        return new ByteArrayInputStream(data);
      }

      @Override
      public String getHeaderField(String name) {
        if(headers == null) return null;
        
        // scan headers in reverse order as spec for this method says it
        // should return the _last_ instance of the specified header name
        for(int i = headers.length - 1; i >= 0; i--) {
          if(headers[i].getName().equalsIgnoreCase(name)) {
            return headers[i].getValue();
          }
        }
        // run out of headers without finding anything
        return null;
      }

      @Override
      public Map<String, List<String>> getHeaderFields() {
        if(headers == null) return super.getHeaderFields();
        
        Map<String, List<String>> fields = new HashMap<String, List<String>>();
        for(Header h : headers) {
          List<String> values = fields.get(h.getName());
          if(values == null) {
            fields.put(h.getName(), Collections.singletonList(h.getValue()));
          } else if(values.size() == 1) {
            values = new ArrayList<String>(values);
            values.add(h.getValue());
            fields.put(h.getName(), values);            
          } else {
            values.add(h.getValue());
          }
        }
        return fields;
      }

      @Override
      public String getHeaderFieldKey(int n) {
        if(headers == null || headers.length <= n) {
          return null;
        }
        
        return headers[n].getName();
      }

      @Override
      public String getHeaderField(int n) {
        if(headers == null || headers.length <= n) {
          return null;
        }
        
        return headers[n].getValue();
      }
    };
  }
   

}
