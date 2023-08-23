/*
 *  MimirOutputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: MimirOutputHandler.java 17427 2014-02-26 13:38:20Z valyt $ 
 */
package gate.cloud.io.mimir;

import gate.Document;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.AbstractOutputHandler;
import gate.cloud.io.OutputHandler;
import gate.mimir.index.MimirConnector;
import gate.mimir.tool.WebUtils;
import gate.util.GateException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * An {@link OutputHandler} that sends documents for indexing to a M&iacute;mir
 * server.
 */
public class MimirOutputHandler extends AbstractOutputHandler {

  /**
   * The URL to the M&iacute;mir indexing server.
   */
  private URL indexUrl;
  
  /**
   * The namespace used to generate document URIs (by concatenating the 
   * namespace and the document name). 
   */
  private String namespace;
  
  /**
   * The name of the document feature that contains the Mimir URI for the
   * document.  If this parameter is set, the URI will be taken directly
   * from this feature rather than being constructed from the namespace and
   * the document's name.
   */
  private String uriFeature;

  /**
   * The {@link MimirConnector} used to send documents to M&iacute;mir.
   */
  private MimirConnector mimirConnector;

  /**
   * Parameter name for the index URL parameter (for which the value should be
   * the URL of the M&iacute;mir index). 
   */
  public static final String PARAM_INDEX_URL = "indexUrl";
  
  
  /**
   * Parameter name for the connection interval parameter of the M&iacute;mir
   * connector. If non-negative, then connections to the remote server are only
   * made at intervals of the number of milliseconds specified by the value.
   */
  public static final String PARAM_CONNECTION_INTERVAL = "connectionInterval";
  /**
   * Namespace parameter name. The namespace value is a string that is 
   * prepended to the document name to obtain a document URI.
   */
  public static final String PARAM_NAMESPACE = "namespace";
  
  /**
   * URI feature parameter name.  If this parameter is set, the document
   * URI passed to Mimir will be taken from the specified document
   * feature rather than being constructed from the namespace and the
   * document's name.
   */
  public static final String PARAM_URI_FEATURE = "uriFeature";

  /**
   * Username parameter name.  If this parameter is set it is used as the HTTP
   * basic authentication username when connecting to M&iacute;mir.  If not
   * set, no authentication header will be sent (unless you have installed a
   * system-wide <code>java.net.Authenticator</code>).  Note that this
   * connector supports only a single username/password pair, so when posting
   * documents to a federated index <i>all</i> indexes in the federation must
   * accept the same credentials.
   */
  public static final String PARAM_USERNAME = "username";

  /**
   * Password parameter name.  If this parameter is set it is used as the HTTP
   * basic authentication password when connecting to M&iacute;mir.  This
   * parameter is only used if {@link #PARAM_USERNAME} is also set.
   */
  public static final String PARAM_PASSWORD = "password";

  @Override
  protected void configImpl(Map<String, String> configData) throws IOException,
          GateException {
    String indexUrlStr = configData.get(PARAM_INDEX_URL);
    if(indexUrlStr == null || indexUrlStr.length() == 0) {
      throw new GateException("No value provided the required parameter " + 
              PARAM_INDEX_URL + "!");
    }
    try {
      indexUrl = new URL(indexUrlStr);
    } catch(MalformedURLException e) {
      throw new GateException("Invalid value provided the required parameter " + 
              PARAM_INDEX_URL + "!", e);      
    }
    
    namespace = configData.get(PARAM_NAMESPACE);
    if(namespace == null) namespace = "";
    uriFeature = configData.get(PARAM_URI_FEATURE);

    WebUtils webUtils = null;
    if(configData.get(PARAM_USERNAME) == null) {
      // no authentication
      webUtils = new WebUtils();
    } else {
      // use username and password
      webUtils = new WebUtils(configData.get(PARAM_USERNAME),
          configData.get(PARAM_PASSWORD));
    }

    mimirConnector = new MimirConnector(indexUrl, webUtils);
    String connIntervalStr = configData.get(PARAM_CONNECTION_INTERVAL);
    if(connIntervalStr != null && connIntervalStr.length() > 0) {
      try {
        mimirConnector.setConnectionInterval(Integer.parseInt(connIntervalStr));
      } catch(NumberFormatException e) {
        throw new GateException("Invalid " + PARAM_CONNECTION_INTERVAL + 
            " value (not an integer number).", e);
      }
    }
  }

  protected void outputDocumentImpl(Document document, DocumentID documentId)
          throws IOException, GateException {
    // send to mimir, using the ID as the document URI.
    String uri = null;
    if(uriFeature != null) {
      Object uriObj = document.getFeatures().get(uriFeature);
      if(uriObj != null) {
        uri = uriObj.toString();
      }
    }
    else {
      uri = namespace + document.getName();
    }
    try {
      mimirConnector.sendToMimir(document, uri);
    } catch(InterruptedException e) {
      throw new GateException("Interrupted while witing to submit document", e);
    }
  }

  @Override
  public void close() throws IOException, GateException {
    super.close();
    try {
      mimirConnector.close();
    } catch(InterruptedException e) {
      throw new GateException(
          "Interrupted while waiting for the Mímir conenctor to close.", e);
    }
  }
  
  
}
