/*
 *  PooledDocumentProcessor.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: PooledDocumentProcessor.java 20272 2017-10-31 18:56:39Z ian_roberts $ 
 */
package gate.cloud.batch;

import gate.Corpus;
import gate.CorpusController;
import gate.Factory;
import gate.cloud.io.DocumentData;
import gate.cloud.io.InputHandler;
import gate.cloud.io.OutputHandler;
import gate.cloud.io.StreamingInputHandler;
import gate.cloud.util.GateResourcePool;
import gate.creole.AbstractController;
import gate.creole.ExecutionException;
import gate.creole.ExecutionInterruptedException;
import gate.creole.ResourceInstantiationException;
import gate.util.Benchmark;
import gate.util.GateException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.stream.XMLOutputFactory;

import org.apache.log4j.Logger;

/**
 * Multi-threaded implementation of a processor for documents.
 */
public class PooledDocumentProcessor implements DocumentProcessor {
  static final String PROCESSING_TIME_FEATURE =
          PooledDocumentProcessor.class.getName() + ".processingTime";

  public static final String FILE_SIZE_FEATURE =
          PooledDocumentProcessor.class.getName() + ".fileSize";
  
  private static final XMLOutputFactory staxFactory =
          XMLOutputFactory.newInstance();


  private static final Logger log =
          Logger.getLogger(PooledDocumentProcessor.class);

  private static int uniqueNumber = 1;

  /**
   * ID used as the basis for benchmark IDs.
   */
  private final String id = "PooledGCPProcessor_" + uniqueNumber++;

  /**
   * The number of threads used to process documents.
   */
  private int poolSize = 1;

  /**
   * Output definitions.
   */
  private List<OutputHandler> outputHandlers = new ArrayList<OutputHandler>();

  /**
   * Template controller that will be used to fill the pool at
   * init()-time.
   */
  private CorpusController templateController;

  /**
   * Base dir for input files.
   */
  private InputHandler inputHandler;
  
  /**
   * Executor used to run processing jobs.
   */
  private Executor executor;

  /**
   * Queue to which processing results are sent.
   */
  private BlockingQueue<ProcessResult> resultQueue;

  /**
   * Application pool initialised with copies of the template
   * controller.
   */
  private GateResourcePool<CorpusController> appPool;

  private AtomicBoolean interrupted = new AtomicBoolean(false);

  /**
   * Pool holding corpora.
   */
  //private GateResourcePool<Corpus> corpusPool;

  /**
   * Create a processor using the given number of threads to process
   * documents.
   * @param poolSize the size of the pool to create.
   */
  public PooledDocumentProcessor(int poolSize) {
    this.poolSize = poolSize;
  }

  @Override
  public void setOutputHandlers(List<OutputHandler> outputHandlers) {
    this.outputHandlers = outputHandlers;
  }

  @Override
  public void setController(CorpusController c) {
    templateController = c;
  }

  
  /* (non-Javadoc)
   * @see gate.sam.batch.DocumentProcessor#setInputHandler(gate.cloud.InputHandler)
   */
  @Override
  public void setInputHandler(InputHandler handler) {
    this.inputHandler = handler;
  }

  @Override
  public void setExecutor(Executor executor) {
    this.executor = executor;
  }

  @Override
  public void setResultQueue(BlockingQueue<ProcessResult> resultQueue) {
    this.resultQueue = resultQueue;
  }

  @Override
  public void init() throws ResourceInstantiationException {
    // create the application pool
    appPool = new GateResourcePool<CorpusController>();
    appPool.fillPool(templateController, poolSize);

    // JP(20150210) we do not use a separate pool for the corpora any more. Instead we go through
    // the controllers we just created and for each, we add a new corpus instance to it.
    int i = 0;
    //while(ctIt.hasNext()) {
    for(CorpusController ct : appPool) {
      ct.setCorpus(Factory.newCorpus("GCPProcessorCorpus_"+i));
      i++;
    }
    
    // Now that all controllers have got their own corpus each, we go through the controllers
    // again and invoke the controllerExecutionStarted() callback method for each.
    // We also disable the automatic callbacks and thus prevent the callbacks to get run for 
    // every individual document.
    for(CorpusController ct : appPool) {
        // disable the callbacks
        ((AbstractController)ct).setControllerCallbacksEnabled(false);
        try {
          ((AbstractController)ct).invokeControllerExecutionStarted();
        } catch (ExecutionException ex) {
          log.error(id+": Exception when executing the controllerExecutionStarted method for controller "+ct.getName(), ex);
        }
    }
  }

  /**
   * Process a single document (specified by ID), reporting
   * success or failure to the output queue.
   */
  public void processDocument(final DocumentID documentId) {
    log.debug("processDocument called for ID " + documentId);
    try {
      final CorpusController controller = appPool.take();
      if(controller != null) {
        Runnable r = new Runnable() {
          public void run() {
            DocumentData docData = null;
            try {
              try {
                log.debug("Loading document " + documentId);
                docData =  inputHandler.getInputDocument(documentId);
                log.debug("processing document " + documentId);
                processDocumentWithGATE(docData, controller);
                log.debug("exporting results for document " + documentId);
                exportResults(docData);
                reportSuccess(docData);
                log.debug("document " + documentId + " processed successfully");
              }
              finally {
                if(docData != null && docData.document != null) {
                  Factory.deleteResource(docData.document);
                  docData.document = null;
                }
              }
            }
            catch(Exception e) {
              log.error("Error processing document " + documentId, e);
              reportFailure(documentId, docData, e);
            }
            finally {
              appPool.release(controller);
            }
          }
        };

        try {
          executor.execute(r);
        }
        catch(RejectedExecutionException ree) {
          log.error("Processing job for document " + documentId
                  + " could not be executed", ree);
          // if the executor refused the task, release the controller
          // here, otherwise let the task release it
          appPool.release(controller);
        }
      }
    }
    catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
  
  /**
   * Process a stream of documents from a StreamingInputHandler, reporting
   * success or failure of each document to the result queue.
   */
  public void processStreaming() {
    StreamingInputHandler stream = (StreamingInputHandler)inputHandler;
    log.info("Processing in streaming mode");
    DocumentData dd = null;
    try {
      while((dd = stream.nextDocument()) != null && !isInterrupted()) {
        final DocumentData docData = dd;
        log.debug("Loaded document " + dd.id);
        final CorpusController controller = appPool.take();
        if(controller != null) {
          Runnable r = new Runnable() {
            public void run() {
              try {
                try {
                  log.debug("processing document " + docData.id);
                  processDocumentWithGATE(docData, controller);
                  log.debug("exporting results for document " + docData.id);
                  exportResults(docData);
                  reportSuccess(docData);
                  log.debug("document " + docData.id + " processed successfully");
                }
                finally {
                  if(docData != null && docData.document != null) {
                    Factory.deleteResource(docData.document);
                    docData.document = null;
                  }
                }
              }
              catch(Exception e) {
                log.error("Error processing document " + docData.id, e);
                reportFailure(docData.id, docData, e);
              }
              finally {
                appPool.release(controller);
              }
            }
          };
  
          try {
            executor.execute(r);
          }
          catch(RejectedExecutionException ree) {
            log.error("Processing job for document " + docData.id
                    + " could not be executed", ree);
            // if the executor refused the task, release the controller
            // here, otherwise let the task release it
            appPool.release(controller);
          }
        }
      }
    }
    catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch(Exception e) {
      log.error("Error getting documents from streaming input handler", e);
    }
  }

  /**
   * Process the given document with a GATE application from the pool.
   * 
   * @param doc the document to process
   * @throws GateException if an error occurs during processing.
   */
  private void processDocumentWithGATE(DocumentData docData, CorpusController controller)
          throws GateException, InterruptedException {
    Corpus myCorpus = controller.getCorpus();
    if (myCorpus != null) {
      try {
        myCorpus.clear();
        myCorpus.add(docData.document);

        // do benchmark logging if it has been enabled externally
        String bid = Benchmark.createBenchmarkId(id, docData.document.getName());
        bid = Benchmark.createBenchmarkId("runApplication", bid);

        // store the running time in a document feature
        long startTime = System.currentTimeMillis();
        Benchmark.executeWithBenchmarking(controller, bid, this, null);
        long timeTaken = System.currentTimeMillis() - startTime;
        docData.processingTime = timeTaken;
      } finally {
        myCorpus.clear();
      }
    } else {
      throw new ExecutionException("Internal error: no corpus available");
    }
  }

  /**
   * Export the results of processing for the given document as
   * specified by the configured output definitions.
   * 
   * @param doc the processed document
   * @param documentId the ID of the document, used to generate the output file
   * names.
   * @throws GateException if an error occurs during export.
   */
  private void exportResults(DocumentData docData) throws IOException, GateException,
          InterruptedException {
    for(OutputHandler output : outputHandlers) {
      output.outputDocument(docData.document, docData.id);
    }
  }

  /**
   * Report successful processing of the given document, including
   * statistics calculated from the annotations on the document.
   * 
   * @param doc the processed document
   * @throws GateException if an error occurs generating statistics.
   */
  private void reportSuccess(DocumentData docData) throws GateException {
    resultQueue.offer(new SuccessResult(docData, outputHandlers));
  }

  /**
   * Report that processing for the given document failed.
   * 
   * @param docId the document ID
   * @param e the exception (if any) that caused processing to fail.
   */
  private void reportFailure(DocumentID docId, DocumentData docData, Throwable e) {
    resultQueue.offer(new FailureResult(docData, docId, e));
  }

  public void dispose() {
    log.info("Cleaning up PooledGCPProcessor");    
    // Run the controller callback method controllerExecutionFinished for all controllers. 
    if(isInterrupted()) {
      ExecutionException interruptException = new ExecutionInterruptedException("Batch was interrupted");
      for(CorpusController ct : appPool) {
        try {
          ((AbstractController)ct).invokeControllerExecutionAborted(interruptException);
        } catch (ExecutionException ex) {
          log.error(id+": Exception when executing the controllerExecutionAborted method for controller "+ct.getName(), ex);
        }
      }    
    } else {
      for(CorpusController ct : appPool) {
        try {
          ((AbstractController)ct).invokeControllerExecutionFinished();
        } catch (ExecutionException ex) {
          log.error(id+": Exception when executing the controllerExecutionFinished method for controller "+ct.getName(), ex);
        }
      }    
    }
    // Now dispose of all the corpora 
    for(CorpusController ct : appPool) {
      Corpus co = ct.getCorpus();
      ct.setCorpus(null);
      Factory.deleteResource(co);
    }    
    
    try {
      inputHandler.close();
    }
    catch(Exception e) {
      log.warn("Exception while closing input handler " + inputHandler, e);
    }
    for(OutputHandler oh : outputHandlers) {
      try {
        oh.close();
      }
      catch(Exception e) {
        log.warn("Exception while closing output handler " + oh, e);
      }
    }    
    appPool.dispose();
  }

  public boolean isInterrupted() {
    return interrupted.get();
  }

  public void interruptBatch() {
    interrupted.set(true);
  }
}
