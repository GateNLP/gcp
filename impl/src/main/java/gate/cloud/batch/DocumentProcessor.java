/*
 *  DocumentProcessor.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: DocumentProcessor.java 18202 2014-07-20 18:55:23Z ian_roberts $ 
 */
package gate.cloud.batch;

import gate.CorpusController;
import gate.cloud.io.InputHandler;
import gate.cloud.io.OutputHandler;
import gate.creole.ResourceInstantiationException;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

/**
 * Interface for a component that processes documents.
 */
public interface DocumentProcessor {
  /**
   * Set the controller which will be used to process the documents.
   */
  public void setController(CorpusController c);

  /**
   * Sets the {@link InputHandler} that should be used to load input
   * documents.
   * 
   * @param handler the handler that will provide the GATE documents to
   *          be processed.
   */
  public void setInputHandler(InputHandler handler);

  /**
   * Add the given definition to the set of standoff files this
   * processor will output for each document.
   */
  public void setOutputHandlers(List<OutputHandler> outputDefs);

  /**
   * Set the executor that is to be used for running processing tasks.
   */
  public void setExecutor(Executor executor);

  /**
   * Set the queue to which results should be sent.
   */
  public void setResultQueue(BlockingQueue<ProcessResult> queue);

  /**
   * This method should be called after the processor has been
   * configured but before the first call to {@link #processDocument},
   * in order to initialise the processor and make it ready to serve
   * requests.
   */
  public void init() throws ResourceInstantiationException;

  /**
   * Releases all resources used by this processor. Do not call
   * {@link #processDocument} again after calling this method.
   */
  public void dispose();

  /**
   * Process the document with the given ID. The document will be
   * processed, the resulting content and annotations will be written
   * out to their respective files (according to the configured output
   * definitions), and the result status and statistics will be
   * returned.
   * 
   * @param docId the ID of the document to process
   */
  public void processDocument(DocumentID docId);

  /**
   * Process the stream of documents from this processor's
   * StreamingInputHandler. This method will only be called if the input
   * handler implements that interface.
   */
  public void processStreaming();

  /**
   * Interrupt the execution of this processor, requesting it to stop
   * processing any new documents and end any streaming processing loop.
   */
  public void interruptBatch();

  /**
   * Has this processor been interrupted?
   */
  public boolean isInterrupted();
}
