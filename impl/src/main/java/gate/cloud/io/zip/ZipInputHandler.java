/*
 *  ZipInputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ZipInputHandler.java 17349 2014-02-19 18:02:24Z ian_roberts $ 
 */
package gate.cloud.io.zip;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_ENCODING;
import static gate.cloud.io.IOConstants.PARAM_FILE_NAME_ENCODING;
import static gate.cloud.io.IOConstants.PARAM_MIME_TYPE;
import static gate.cloud.io.IOConstants.PARAM_REPOSITIONING_INFO;
import static gate.cloud.io.IOConstants.PARAM_SOURCE_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_ZIP_FILE_LOCATION;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.DocumentData;
import gate.cloud.io.InputHandler;
import gate.util.GateException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.log4j.Logger;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;

/**
 * Input handler that reads from a zip file. Document IDs are assumed to
 * be entry paths within the zip file.
 */
public class ZipInputHandler implements InputHandler {

  private static Logger logger = Logger.getLogger(ZipInputHandler.class);

  /**
   * The mime type used when loading documents.
   */
  protected String mimeType;

  /**
   * The encoding used when loading documents.
   */
  protected String encoding;

  /**
   * The directory containing the batch specification file, or
   * <code>null</code> if the batch specification did not come from a
   * file.
   */
  protected File batchDir;

  /**
   * The zip file containing the document files.
   */
  protected File zipFileLocation;

  /**
   * The location of the zip file as a URI.
   */
  protected String zipFileUri;
  
  /**
   * The default encoding for file names in the zip file.  We read
   * zip entries using the Ant ZipFile implementation rather than
   * the java.util.zip one, so it will look for the flags that
   * specify that a certain entry name is UTF-8, but if these flags
   * are not set, the default encoding here will be used.  If
   * unspecified, the platform default is used.
   */
  protected String fileNameEncoding;
  
  /**
   * Should we collect repositioning info when parsing?
   */
  protected boolean repositioningInfo = false;

  /**
   * Pool of ZipFile instances used to load documents.
   */
  protected BlockingQueue<ZipFile> zipFiles;

  @SuppressWarnings("deprecation")
  public void config(Map<String, String> configData) throws IOException,
          GateException {
    // zip file
    // Backwards compatibility
    if(configData.containsKey(PARAM_ZIP_FILE_LOCATION)
            && !configData.containsKey(PARAM_SOURCE_FILE_LOCATION)) {
      configData.put(PARAM_SOURCE_FILE_LOCATION,
              configData.get(PARAM_ZIP_FILE_LOCATION));
    }
    String zipFileStr = configData.get(PARAM_SOURCE_FILE_LOCATION);
    if(zipFileStr == null || zipFileStr.trim().length() == 0) {
      throw new IllegalArgumentException(
              "No value was provided for the required parameter \""
                      + PARAM_SOURCE_FILE_LOCATION + "\"!");
    }
    String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
    if(batchFileStr != null) {
      batchDir = new File(batchFileStr).getParentFile();
    }
    zipFileLocation = new File(zipFileStr);
    if(!zipFileLocation.isAbsolute()) {
      zipFileLocation = new File(batchDir, zipFileStr);
    }
    if(!zipFileLocation.exists()) {
      throw new IllegalArgumentException("File \"" + zipFileLocation
              + "\", provided as value for required parameter \""
              + PARAM_SOURCE_FILE_LOCATION + "\", does not exist!");
    }
    if(!zipFileLocation.isFile()) {
      throw new IllegalArgumentException("File \"" + zipFileLocation
              + "\", provided as value for required parameter \""
              + PARAM_SOURCE_FILE_LOCATION + "\", is not a file!");
    }

    zipFileUri = zipFileLocation.toURI().toString();

    // encoding
    encoding = configData.get(PARAM_ENCODING);
    // mime type
    mimeType = configData.get(PARAM_MIME_TYPE);
    // file name encoding
    fileNameEncoding = configData.get(PARAM_FILE_NAME_ENCODING);
    
    if("true".equals(configData.get(PARAM_REPOSITIONING_INFO))) {
      repositioningInfo = true;
    }
  }

  public DocumentData getInputDocument(DocumentID id) throws IOException, GateException {
    ZipFile zipFile = borrowZip();
    InputStream is = null;
    ZipEntryStreamHandler handler = null;
    try {
      ZipEntry ze = zipFile.getEntry(id.getIdText());
      if(ze == null) {
        throw new GateException("Unknown entry " + id
                + " requested from zip file "
                + zipFileLocation.getAbsolutePath());
      }
      // construct a jar: URL for the given file
      handler = new ZipEntryStreamHandler(zipFile, ze);
      URL docUrl =
              new URL(null, "jar:" + zipFileUri + "!/" + id,
                      handler);
      FeatureMap params = Factory.newFeatureMap();
      params.put(Document.DOCUMENT_URL_PARAMETER_NAME, docUrl);
      if(mimeType != null && mimeType.length() > 0) {
        params.put(Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME, mimeType);
      }
      if(encoding != null && encoding.length() > 0) {
        params.put(Document.DOCUMENT_ENCODING_PARAMETER_NAME, encoding);
      }

      params.put(Document.DOCUMENT_MARKUP_AWARE_PARAMETER_NAME, Boolean.TRUE);
      
      if(repositioningInfo) {
        params.put(Document.DOCUMENT_REPOSITIONING_PARAMETER_NAME, Boolean.TRUE);
      }

      logger.debug("Loading document from URL " + docUrl);

      DocumentData docData = new DocumentData(
              (Document)Factory.createResource("gate.corpora.DocumentImpl",
                  params, Factory.newFeatureMap(), id.getIdText()), id);
      docData.fileSize = ze.getSize();

      return docData;
    }
    finally {
      if(handler != null) {
        handler.clear();
      }
      releaseZip(zipFile);
      if(is != null) {
        is.close();
      }
    }

  }

  protected ZipFile borrowZip() throws IOException {
    ZipFile f = zipFiles.poll();
    if(f == null) {
      synchronized(this) {
        f = new ZipFile(zipFileLocation, fileNameEncoding);
      }
    }
    return f;
  }

  protected void releaseZip(ZipFile f) {
    zipFiles.add(f);
  }

  public void init() throws IOException, GateException {
    zipFiles = new LinkedBlockingQueue<ZipFile>();
  }

  /**
   * Close all ZipFiles in the pool.
   */
  public void close() throws IOException, GateException {
    for(ZipFile f : zipFiles) {
      try {
        f.close();
      }
      catch(IOException e) {
        logger.warn("Exception while closing zip file", e);
      }
    }
  }

  /**
   * UrlStreamHandler that reads from an underlying zip file entry.
   */
  protected class ZipEntryStreamHandler extends URLStreamHandler {

    protected volatile ZipFile file;

    protected volatile ZipEntry entry;

    public ZipEntryStreamHandler(ZipFile file, ZipEntry entry) {
      this.file = file;
      this.entry = entry;
    }
    
    public void clear() {
      this.file = null;
      this.entry = null;
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
      if(file != null) {
        return new URLConnection(u) {

          @Override
          public void connect() throws IOException {
            // do nothing
          }

          @Override
          public int getContentLength() {
            return (int)entry.getSize();
          }

          @Override
          public InputStream getInputStream() throws IOException {
            return file.getInputStream(entry);
          }
        };
      }
      else {
        logger.warn("ZipEntryStreamHandler.openConnection called with "
                + "no safe zip file, falling back to default jar: "
                + "URL behaviour.");
        return new URL(u, "").openConnection();
      }
    }

  }

}
