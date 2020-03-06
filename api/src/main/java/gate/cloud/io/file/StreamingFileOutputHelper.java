/*
 *  SteamingFileOutputHelper.java
 *  Copyright (c) 2007-2018, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 */
package gate.cloud.io.file;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_CHUNK_SIZE;
import static gate.cloud.io.IOConstants.PARAM_COMPRESSION;
import static gate.cloud.io.IOConstants.PARAM_NAMING_STRATEGY;
import static gate.cloud.io.IOConstants.PARAM_PATTERN;
import static gate.cloud.io.IOConstants.VALUE_COMPRESSION_GZIP;
import static gate.cloud.io.IOConstants.VALUE_COMPRESSION_NONE;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.ToIntFunction;
import java.util.zip.GZIPOutputStream;

import gate.Gate;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.IOConstants;
import gate.util.GateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for streaming output handlers that want to write to a series of chunk files.
 *
 * @param <TItem> the type of the items that will be streamed to this helper.
 * @param <TWriter> the type of the object that will be used to write items to the files.
 */
public class StreamingFileOutputHelper<TItem, TWriter extends AutoCloseable> {
  
  @FunctionalInterface
  public static interface WriteOperation<W, I> {
    public void writeItem(W writer, I item) throws Exception;
  }
  
  @FunctionalInterface
  public static interface WriterCreator<W> {
    public W create(OutputStream stream) throws Exception;
  }
  
  private static final Logger logger =
      LoggerFactory.getLogger(StreamingFileOutputHelper.class);

  protected String pattern;

  protected long chunkSize = -1L;

  protected String compression;

  protected File batchDir;

  protected NamingStrategy namingStrategy;

  protected BlockingQueue<TItem> results = new ArrayBlockingQueue<>(100);

  protected ExecutorService processWaiter = Executors.newCachedThreadPool();

  protected TItem endOfData;

  protected WriterCreator<TWriter> openWriter;

  protected WriteOperation<TWriter, TItem> writeItem;

  protected ToIntFunction<TItem> itemSize;

  /**
   * Construct a streaming output helper.
   * 
   * @param endOfData
   *          flag object that will be used to signal the end of the data
   *          stream. This will be compared by reference and should be an object
   *          that will not otherwise be used in normal processing.
   * @param openWriter
   *          operation that takes an output stream and creates an appropriate
   *          writer object for the item type.
   * @param writeItem operation that writes the given item to the given writer.
   * @param itemSize function that computes an approcimate size in bytes of the
   *          given item, used to determine when to check for chunk roll-over.
   */
  public StreamingFileOutputHelper(TItem endOfData,
      WriterCreator<TWriter> openWriter,
      WriteOperation<TWriter, TItem> writeItem, ToIntFunction<TItem> itemSize) {
    super();
    this.endOfData = endOfData;
    this.openWriter = openWriter;
    this.writeItem = writeItem;
    this.itemSize = itemSize;
  }

  public void config(Map<String, String> configData)
      throws IOException, GateException {
    String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
    if(batchFileStr != null) {
      batchDir = new File(batchFileStr).getParentFile();
    }
    // naming strategy
    String namingStrategyClassName = configData.get(PARAM_NAMING_STRATEGY);
    if(namingStrategyClassName == null
        || namingStrategyClassName.length() == 0) {
      namingStrategyClassName = SimpleNamingStrategy.class.getName();
    }
    try {
      Class<? extends NamingStrategy> namingStrategyClass =
          Class.forName(namingStrategyClassName, true, Gate.getClassLoader())
              .asSubclass(NamingStrategy.class);
      namingStrategy = namingStrategyClass.newInstance();
      namingStrategy.config(true, configData);
    } catch(Exception e) {
      throw new GateException("Could not instantiate specified naming strategy",
          e);
    }
    pattern = configData.get(PARAM_PATTERN);
    if(pattern == null) {
      pattern = "part-%03d";
    }
    String chunkSizeStr = configData.get(PARAM_CHUNK_SIZE);
    try {
      chunkSize = Long.parseLong(chunkSizeStr);
    } catch(Exception e) {
      logger.info("Using default chunk size");
      chunkSize = 99000000;
    }
    // get the compression value
    compression = configData.get(PARAM_COMPRESSION);
    if(compression == null) {
      // default
      compression = IOConstants.VALUE_COMPRESSION_NONE;
    }
  }

  public void init() throws IOException, GateException {
    // TODO Auto-generated method stub
    new Thread(new StreamOutputter()).start();
  }

  public void sendItem(TItem item) {
    try {
      results.put(item);
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void close() throws IOException, GateException {
    try {
      results.put(endOfData);
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  protected class StreamOutputter implements Runnable {
    private File currentFile;

    private int currentChunk = -1;

    private TWriter currentOutput;

    private Process currentProcess;

    public void run() {
      TItem item = null;
      try {
        try {
          int bytesSinceLastCheck = 0;
          while((item = results.take()) != endOfData) {
            if(currentOutput == null) {
              try {
                openNextChunk();
              } catch(Exception e) {
                logger.error("Failed to open output file " + currentFile, e);
              }
            }
            try {
              writeItem.writeItem(currentOutput, item);
            } catch(Exception e) {
              logger.warn("Error writing to file " + currentFile, e);
            }
            bytesSinceLastCheck += itemSize.applyAsInt(item);
            if(bytesSinceLastCheck > 1024 * 1024) {
              if(currentFile.length() > chunkSize) {
                closeChunk();
              }
              bytesSinceLastCheck = 0;
            }
          }
        } finally {
          closeChunk();
          processWaiter.shutdown();
        }
      } catch(InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    private void closeChunk() {
      if(currentOutput != null) {
        try {
          currentOutput.close();
        } catch(Exception e) {
          logger.warn("Error closing file " + currentFile.getAbsolutePath(), e);
        }
        if(currentProcess != null) {
          final Process p = currentProcess;
          processWaiter.execute(new Runnable() {
            public void run() {
              try {
                p.waitFor();
              } catch(InterruptedException e) {
                logger.warn("Interrupted while waiting for process", e);
                Thread.currentThread().interrupt();
              }
            }
          });
          currentProcess = null;
        }
        currentOutput = null;
        currentFile = null;
      }
    }

    private void openNextChunk() throws Exception {
      // if we're restarting we might have to skip some batches
      do {
        String newFileName = String.format(pattern, ++currentChunk);
        currentFile = namingStrategy.toFile(new DocumentID(newFileName));
      } while(currentFile.exists());
      OutputStream newStream = null;
      if(VALUE_COMPRESSION_GZIP.equals(compression)) {
        newStream = new GZIPOutputStream(new FileOutputStream(currentFile));
      } else if(compression == null
          || VALUE_COMPRESSION_NONE.equals(compression)) {
        newStream = new FileOutputStream(currentFile);
      } else {
        // treat compression value as a command line
        ProcessBuilder pb =
            new ProcessBuilder(compression.trim().split("\\s+"));
        pb.directory();
        pb.redirectInput(Redirect.PIPE);
        pb.redirectOutput(currentFile);
        pb.redirectError(Redirect.INHERIT);
        currentProcess = pb.start();
        newStream = currentProcess.getOutputStream();
      }
      currentOutput = openWriter.create(newStream);
    }
  }
}
