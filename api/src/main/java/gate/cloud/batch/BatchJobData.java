/*
 *  BatchJobData.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: BatchJobData.java 13582 2011-03-29 16:23:58Z ian_roberts $ 
 */
package gate.cloud.batch;

/**
 * An interface for objects that contain information about a running batch job.
 */
public interface BatchJobData {
  
  public enum JobState { RUNNING, FINISHED, PAUSED, ERROR }
  
  /**
   * Has the batch job execution completed?
   * @return a boolean value.
   */
  public JobState getState();
  
  /**
   * Gets the number of document that were processed so far. The result of this
   * call is the sum of {@link #getSuccessDocumentCount()} and
   * {@link #getErrorDocumentCount()}.
   * @return an int value.
   */
  public int getProcessedDocumentCount();
  
  /**
   * Gets the total number of documents included in this batch job.
   * @return an int value
   */
  public int getTotalDocumentCount();
  
  /**
   * Gets the number of documents still remaining in the rpocessing queue.
   * @return an int value.
   */
  public int getRemainingDocumentCount();
  
  /**
   * Gets the number of documents from this batch that have already been 
   * processed successfully.
   * @return an int value.
   */
  public int getSuccessDocumentCount();
  
  /**
   * Gets the number of documents from this batch for which the processing 
   * resulted in error.
   * @return an int value.
   */
  public int getErrorDocumentCount();
  
  /**
   * Gets the moment in time when the execution of this batch was started.
   * @return a long value, obtained from {@link System#currentTimeMillis()}.
   */
  public long getStartTime();
  
  /**
   * Gets the ID of this batch.
   * @return a {@link String} value.
   */
  public String getBatchId();
  
  /**
   * Get the total number of characters that were processed (the sum of 
   * {@link ProcessResult#getDocumentLength()} values for all processed 
   * documents). 
   * @return a long value
   */
  public long getTotalDocumentLength();
  
  
  /**
   * Get the total number of bytes for the original files of all the documents
   * that were processed (the sum of {@link ProcessResult#getOriginalFileSize()}
   * values for all processed documents). 
   * @return a long value
   */  
  public long getTotalFileSize();
}
