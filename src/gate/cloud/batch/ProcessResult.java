/*
 *  ProcessResult.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ProcessResult.java 18202 2014-07-20 18:55:23Z ian_roberts $ 
 */
package gate.cloud.batch;


import java.util.Map;

/**
 * A process result contains information about the processing of a document. 
 * This includes the success/fail state plus a set of useful statistics.
 */
public interface ProcessResult {
  /**
   * An enumeration of possible return codes.
   */
  public enum ReturnCode {SUCCESS, FAIL, END_OF_BATCH};
  
  /**
   * Gets the return code of a process.
   * @return a {@link ReturnCode} value.
   */
  public ReturnCode getReturnCode();
  
  /**
   * Returns the execution time for this process.
   * @return a long value representing the execution time in milliseconds.
   */
  public long getExecutionTime();
  
  /**
   * Gets the size of the input file that was processed, represented in bytes.
   * @return a long value.
   */
  public long getOriginalFileSize();
  
  /**
   * Gets the size of the GATE document (number of characters in the content).
   * @return a long value
   */
  public long getDocumentLength();
  
  /**
   * Returns a map with statistics regarding the number of annotations of each 
   * type that were created.  
   * @return a {@link Map} object linking each annotation type with the number
   * of created annotations.
   */
  public Map<String, Integer> getAnnotationCounts();
  
  /**
   * Gets the ID for the document that was processed.
   * @return a {@link String} value.
   */
  public DocumentID getDocumentId();
  
  /**
   * Gets a textual representation of the error that caused a failure. This 
   * field will be be set in the case of process failure (which is marked by the
   * value of {@link #getReturnCode()}.
   *  
   * @return a String object.
   */
  public String getErrorDescription();
  
}
