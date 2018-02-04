/*
 *  StreamingInputHandler.java
 *  Copyright (c) 2007-2014, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: StreamingInputHandler.java 18202 2014-07-20 18:55:23Z ian_roberts $ 
 */
package gate.cloud.io;

import gate.cloud.batch.Batch;
import gate.util.GateException;

import java.io.IOException;

/**
 * Input handler that operates in "streaming mode" where the list of
 * document IDs is not known up-front. Instead of fetching a specific
 * document by ID the handler simply returns the next available document
 * each time {@link #nextDocument()} is called.
 * 
 * @author ian
 * 
 */
public interface StreamingInputHandler extends InputHandler {

  /**
   * Called just before GCP starts requesting documents from the
   * handler.
   * 
   * @param batch the batch that is about to start.
   */
  public void startBatch(Batch batch);

  /**
   * Load and return the next available document for this handler, or
   * <code>null</code> if there are no more documents to process. This
   * method does not need to be thread safe - it will only be called
   * from one thread at a time.
   * 
   * @return the loaded document, which must include a suitable ID
   */
  public DocumentData nextDocument() throws IOException, GateException;
}
