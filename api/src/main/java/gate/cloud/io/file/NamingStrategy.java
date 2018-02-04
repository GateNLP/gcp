/*
 *  NamingStrategy.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: NamingStrategy.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io.file;

import gate.cloud.batch.DocumentID;
import gate.util.GateException;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Interface encapsulating the mapping from document IDs to file names on disk.
 */
public interface NamingStrategy {
  /**
   * Configures this naming strategy by providing a {@link Map} containing 
   * configuration options.
   * @param isOutput is this strategy being used for input or output file names?
   * @param configData the configuration map.
   * @throws IOException if an I/O error occurs during configuration.
   * @throws GateException if any other error occurs during configuration.
   */
  public void config(boolean isOutput, Map<String, String> configData) throws IOException, GateException;
  
  /**
   * Calculates the file corresponding to a given ID. 
   * @param id
   * @return the File that corresponds to the given ID in this strategy.
   * @throws IOException if an exception occurs in this mapping process.
   */
  public File toFile(DocumentID id) throws IOException;
}
