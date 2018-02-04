/*
 *  InputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: InputHandler.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io;

import java.io.IOException;
import java.util.Map;

import gate.cloud.batch.DocumentID;
import gate.util.GateException;

/**
 * An InputHandler provides an implementation capable of supplying a GATE 
 * document for a givenID.
 */
public interface InputHandler {
  
  /**
   * Obtains a GATE document for a given ID. This method may be called
   * from multiple threads, so implementations must be thread-safe! 
   * @param id the ID for the requested document.
   * @return the GATE document object.
   * @throws IOException if the GATE document data cannot be accessed.
   * @throws GateException if the GATE document cannot be created.
   */
  public DocumentData getInputDocument(DocumentID id) throws IOException, GateException;
  
  /**
   * Configures this input handler by providing a {@link Map} containing 
   * configuration options. 
   * @param configData the configuration data from the batch descriptor.
   * @throws IOException if an I/O error occurs during configuration.
   * @throws GateException if any other error occurs during configuration.
   */
  public void config(Map<String, String> configData) throws IOException, GateException;
  
  /**
   * Initialises this input handler. This method will always be called once by 
   * the client code, before the first call to 
   * {@link #getInputDocument(DocumentID)}. 
   * @throws IOException if an I/O error occurs during init.
   * @throws GateException if any other error occurs during init.
   */
  public void init() throws IOException, GateException;
  
  /**
   * Called once processing is complete to allow the handler to release
   * any resources it is using.  After this method is called there will
   * be no more calls to {@link #getInputDocument(DocumentID)}.
   * @throws IOException if an I/O error occurs during the close process.
   * @throws GateException if any other error occurs during the close process.
   */
  public void close() throws IOException, GateException;
  
}
