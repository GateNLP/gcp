/*
 *  OutputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: OutputHandler.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io;

import gate.Document;
import gate.cloud.batch.AnnotationSetDefinition;
import gate.cloud.batch.DocumentID;
import gate.util.GateException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * An output handler manages the output process after a document has been 
 * processed. A batch definition can include multiple output handler
 * definitions (all serving different roles).
 * 
 * The client code will:
 * <ul>
 *   <li>create an instance of the handler;</li>
 *   <li>call <code>config</code>, providing the configuration data;</li>
 *    <li>call <code>setAnnSetDefinitions</code>, providing the definitions for 
 *    the annotations to be saved;</li>
 *    <li>call <code>init</code>.</li>
 * </ul>
 * in this order.
 * 
 * After that, multiple calls (possibly from multiple threads) will be made to 
 * the <code>ouputDocument</code> method.
 * 
 * Finally, at he end of the process, when this handler is no longer required, 
 * the <code>close</code> method is called. 
 */
public interface OutputHandler {
  
  /**
   * Configures this input handler by providing a {@link Map} containing 
   * configuration options. 
   * @param configData the {@link Map} containing all the configuration 
   * parameters.
   */
  public void config(Map<String, String> configData) throws IOException, GateException;
  
  
  /**
   * Sets the list of annotation set definitions for the annotations that need 
   * to be saved by this handler.
   * @param annotationsToSave the annotation set definitions from the batch descriptor.
   */
  public void setAnnSetDefinitions(List<AnnotationSetDefinition> annotationsToSave);
  

  /**
   * Gets the list of annotation set definitions for the annotations that need 
   * to be saved by this handler.
   */
  public List<AnnotationSetDefinition> getAnnSetDefinitions();
  
  /**
   * Initialises this output handler. This method will always be called once by 
   * the client code, after the call to {@link #config(Map)} and before the 
   * first call to {@link #outputDocument}. 
   * @throws IOException if an I/O error occurs during init.
   * @throws GateException if any other error occurs during init.
   */
  public void init() throws IOException, GateException;
  
  /**
   * Outputs the data from a processed GATE document. This method may be called
   * from multiple threads, so implementations must be thread-safe!
   * @param document the document to be sent for output.
   * @param documentId the identifier for the document to be output 
   * @throws IOException if an I/O error occurs during outputting.
   * @throws GateException if any other error occurs during outputting.
   */
  public void outputDocument(Document document, DocumentID documentId) throws IOException, GateException;
  
  /**
   * Notifies this handler that all required documents have now been output, and
   * the handler is thus no longer required.
   * @throws IOException if an I/O error occurs during close.
   * @throws GateException if any other error occurs during close.
   */
  public void close()  throws IOException, GateException;
}
