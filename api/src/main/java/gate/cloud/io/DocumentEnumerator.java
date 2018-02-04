/*
 *  DocumentEnumerator.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: DocumentEnumerator.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io;

import gate.cloud.batch.DocumentID;
import gate.util.GateException;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 *  An enumerator is used to obtain a list of document IDs that are then used 
 *  to populate the &lt;documents7gt; element of a batch descriptor. If 
 *  possible, implementations should return the document IDs in the same order
 *  for consecutive executions given the same configuration parameters.
 *    
 *  This interface extends the {@link Iterator} interface and produces an 
 *  iteration {@link String} values that represent document IDs. The remove()
 *  method does not apply in this context and is not implemented.  
 */
public interface DocumentEnumerator extends Iterator<DocumentID> {

  /**
   * Configures this document enumerator by providing a {@link Map} containing 
   * configuration options. 
   * @param configData
   */
  public void config(Map<String, String> configData) throws IOException, GateException;
  
  /**
   * Called by the framework after {@link #config(Map)} and before the first 
   * call to {@link #hasNext()} and {@link #next()}. 
   */
  public void init() throws IOException, GateException;
}
