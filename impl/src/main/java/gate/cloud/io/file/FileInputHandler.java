/*
 *  FileInputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: FileInputHandler.java 19679 2016-10-14 16:40:39Z johann_p $ 
 */
package gate.cloud.io.file;

import static gate.cloud.io.IOConstants.*;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.DocumentData;
import gate.cloud.io.IOConstants;
import gate.cloud.io.InputHandler;
import gate.cloud.util.GZIPURLStreamHandler;
import gate.cloud.util.SnappyURLStreamHandler;
import gate.util.GateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;


/**
 * An input handler that creates GATE documents by reading files on disk. The 
 * document IDs are interpreted as /-separated paths, starting from the document
 * root.   
 */
public class FileInputHandler implements InputHandler {
  
  private static Logger logger = LoggerFactory.getLogger(FileInputHandler.class);
  
  /**
   * The file naming strategy used by this input handler.
   */
  protected NamingStrategy namingStrategy;
  
  /**
   * The mime type used when loading documents.
   */
  protected String mimeType;
  
  /**
   * The encoding used when loading documents.
   */
  protected String encoding;

  /**
   * The type of compression used (one of {@link IOConstants#VALUE_COMPRESSION_NONE}, or
   * {@link IOConstants#VALUE_COMPRESSION_GZIP}).
   */
  protected String compression; 
  
  /**
   * Should we collect repositioning info when parsing?
   */
  protected boolean repositioningInfo = false;
  
  /* (non-Javadoc)
   * @see gate.cloud.InputHandler#config(java.util.Map)
   */
  public void config(Map<String, String> configData) throws IOException, GateException {
    // naming strategy
    String namingStrategyClassName = configData.get(PARAM_NAMING_STRATEGY);
    if(namingStrategyClassName == null || namingStrategyClassName.length() == 0) {
      namingStrategyClassName = SimpleNamingStrategy.class.getName();
    }
    try {
      Class<? extends NamingStrategy> namingStrategyClass =
        Class.forName(namingStrategyClassName, true, Gate.getClassLoader())
               .asSubclass(NamingStrategy.class);
      namingStrategy = namingStrategyClass.newInstance();
      namingStrategy.config(false, configData);
    } catch(Exception e) {
      throw new GateException(
              "Could not instantiate specified naming strategy", e);
    }
    //compression
    compression = configData.get(PARAM_COMPRESSION);
    if(compression == null){
      compression = VALUE_COMPRESSION_NONE;
    }
    //encoding
    encoding = configData.get(PARAM_ENCODING);
    //mime type
    mimeType = configData.get(PARAM_MIME_TYPE);
    
    if("true".equals(configData.get(PARAM_REPOSITIONING_INFO))) {
      repositioningInfo = true;
    }
  }
  
  /* (non-Javadoc)
   * @see gate.cloud.InputHandler#getInputDocument(java.lang.String)
   */
  public DocumentData getInputDocument(DocumentID id) throws IOException, GateException {
    File docFile = namingStrategy.toFile(id);
    FeatureMap params = Factory.newFeatureMap();
    URL docUrl = docFile.toURI().toURL();
    if(compression.equals(VALUE_COMPRESSION_GZIP)){
      docUrl = new URL(docUrl, "", new GZIPURLStreamHandler(docUrl));
    } else if(compression.equals(VALUE_COMPRESSION_SNAPPY)) {
      docUrl = new URL(docUrl, "", new SnappyURLStreamHandler(docUrl));      
    }
    
    params.put(Document.DOCUMENT_URL_PARAMETER_NAME, docUrl);
    if(mimeType != null && mimeType.length() > 0) {
      params.put(Document.DOCUMENT_MIME_TYPE_PARAMETER_NAME, mimeType);
    }
    if(encoding!= null && encoding.length() > 0){
      params.put(Document.DOCUMENT_ENCODING_PARAMETER_NAME, encoding);
    }
    
    params.put(Document.DOCUMENT_MARKUP_AWARE_PARAMETER_NAME, Boolean.TRUE);
    
    if(repositioningInfo) {
      params.put(Document.DOCUMENT_REPOSITIONING_PARAMETER_NAME, Boolean.TRUE);
    }
    
    logger.debug("Loading document from file {}", docFile);

    DocumentData docData = new DocumentData(
            (Document)Factory.createResource("gate.corpora.DocumentImpl",
                params, Factory.newFeatureMap(), id.toString()), id);
    docData.fileSize = docFile.length();
    return docData;
  }

  public void init() {
  }
  
  public void close() {
  }


  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder text = new StringBuilder();
    text.append("\n\tClass:           " + this.getClass().getName() + "\n");
    text.append(  "\tNaming strategy: " + namingStrategy + "\n");
    text.append(  "\tCompression:     " + compression + "\n");
    text.append(  "\tEncoding:        " + encoding + "\n");
    text.append(  "\tMime type:       " + mimeType + "\n");
    return text.toString();
  }
  
  
}
