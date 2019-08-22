/*
 *  GZIPURLStreamHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: GZIPURLStreamHandler.java 13582 2011-03-29 16:23:58Z ian_roberts $ 
 */
package gate.cloud.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.zip.GZIPInputStream;

/**
 * A URL stream handler that can be used to read data compressed in GZip format.
 * This implementation is not feature complete - it only has the functionality 
 * required to open GATE documents from gzipped files! 
 */
public class GZIPURLStreamHandler extends URLStreamHandler {

  /**
   * A URL connection that has the minimal implementation for uncompressing 
   * GZip content. 
   */
  public class GZIPURLConnection extends URLConnection{
    public GZIPURLConnection() throws IOException {
      super(originalURL);
    }
    
    /**
     * A URLConnection from the original URL.
     */
    protected URLConnection originalConnection;
    
    
    /* (non-Javadoc)
     * @see java.net.URLConnection#connect()
     */
    @Override
    public void connect() throws IOException {
      if(!connected){
        this.originalConnection = originalURL.openConnection();
        connected = true;
      }
    }


    /* (non-Javadoc)
     * @see java.net.URLConnection#getInputStream()
     */
    @Override
    public InputStream getInputStream() throws IOException {
      if(!connected) connect();
      return new GZIPInputStream(originalConnection.getInputStream());
    }
    
  }
  
  
  /* (non-Javadoc)
   * @see java.net.URLStreamHandler#openConnection(java.net.URL)
   */
  @Override
  protected URLConnection openConnection(URL u) throws IOException {
    return new GZIPURLConnection();
  }

  
  /**
   * The URL we are wrapping.
   */
  protected URL originalURL;
  
  
  public GZIPURLStreamHandler(URL wrappedUrl) {
    super();
    this.originalURL = wrappedUrl;
  }
  
  
}
