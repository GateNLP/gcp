/*
 *  SnappyURLStreamHandler.java
 *  Copyright (c) 2016, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 */
package gate.cloud.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import org.xerial.snappy.SnappyInputStream;

/**
 * A URL stream handler that can be used to read data compressed in Snappy format.
 * This implementation is not feature complete - it only has the functionality 
 * required to open GATE documents from snappy-compressed files! 
 */
public class SnappyURLStreamHandler extends URLStreamHandler {

  /**
   * A URL connection that has the minimal implementation for uncompressing 
   * snappy content. 
   */
  public class SnappyURLConnection extends URLConnection{
    public SnappyURLConnection() throws IOException {
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
      return new SnappyInputStream(originalConnection.getInputStream());
    }
    
  }
  
  
  /* (non-Javadoc)
   * @see java.net.URLStreamHandler#openConnection(java.net.URL)
   */
  @Override
  protected URLConnection openConnection(URL u) throws IOException {
    return new SnappyURLConnection();
  }

  
  /**
   * The URL we are wrapping.
   */
  protected URL originalURL;
  
  
  public SnappyURLStreamHandler(URL wrappedUrl) {
    super();
    this.originalURL = wrappedUrl;
  }
  
  
}
