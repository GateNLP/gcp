/*
 *  JSONStreamingOutputHandler.java
 *  Copyright (c) 2007-2014, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: JSONStreamingOutputHandler.java 18208 2014-07-23 12:50:37Z ian_roberts $ 
 */
package gate.cloud.io.json;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_CHUNK_SIZE;
import static gate.cloud.io.IOConstants.PARAM_PATTERN;
import static gate.cloud.io.IOConstants.VALUE_COMPRESSION_GZIP;
import static gate.cloud.io.IOConstants.VALUE_COMPRESSION_NONE;

import java.io.ByteArrayOutputStream;
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
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

import gate.Document;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.file.JSONOutputHandler;
import gate.util.GateException;

/**
 * JSON output handler that concatenates JSON objects into a single
 * large file rather than putting each one in its own individual file
 * named for the document ID.
 * 
 * @author Ian Roberts
 * 
 */
public class JSONStreamingOutputHandler extends JSONOutputHandler {

  private static final Logger logger = Logger
          .getLogger(JSONStreamingOutputHandler.class);

  protected String pattern;

  protected long chunkSize = -1L;
  
  protected File batchDir;

  private static final byte[] END_OF_DATA = new byte[0];

  @Override
  protected void configImpl(Map<String, String> configData) throws IOException,
          GateException {
    // TODO Auto-generated method stub
    super.configImpl(configData);
    String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
    if(batchFileStr != null) {
      batchDir = new File(batchFileStr).getParentFile();
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
  }

  protected ExecutorService processWaiter = Executors.newCachedThreadPool();
  
  protected ThreadLocal<ByteArrayOutputStream> baos =
          new ThreadLocal<ByteArrayOutputStream>() {
            protected ByteArrayOutputStream initialValue() {
              return new ByteArrayOutputStream();
            }
          };

  protected BlockingQueue<byte[]> results = new ArrayBlockingQueue<byte[]>(100);

  @Override
  protected void outputDocumentImpl(Document document, DocumentID documentId)
          throws IOException, GateException {
    super.outputDocumentImpl(document, documentId);
    baos.get().write('\n');
    byte[] result = baos.get().toByteArray();
    baos.get().reset();
    try {
      results.put(result);
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  protected OutputStream getFileOutputStream(DocumentID docId)
          throws IOException {
    return baos.get();
  }

  @Override
  public void init() throws IOException, GateException {
    // TODO Auto-generated method stub
    new Thread(new StreamOutputter()).start();
  }

  @Override
  public void close() throws IOException, GateException {
    try {
      results.put(END_OF_DATA);
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  protected class StreamOutputter implements Runnable {
    private File currentFile;

    private int currentChunk = -1;

    private OutputStream currentOutput;

    private Process currentProcess;

    public void run() {
      byte[] item = null;
      try {
        try {
          int bytesSinceLastCheck = 0;
          while((item = results.take()) != END_OF_DATA) {
            if(currentOutput == null) {
              try {
                openNextChunk();
              } catch(IOException e) {
                logger.error("Failed to open output file", e);
                
              }
            }
            try {
              currentOutput.write(item);
              currentOutput.flush();
            } catch(IOException e) {
              logger.warn("Error writing to " + currentFile.getAbsolutePath(), e);
            }
            bytesSinceLastCheck += item.length;
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
        } catch(IOException e) {
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
    
    private void openNextChunk() throws IOException {
      // if we're restarting we might have to skip some batches
      do {
        String newFileName = String.format(pattern, ++currentChunk);
        currentFile = namingStrategy.toFile(new DocumentID(newFileName));
      } while(currentFile.exists());
      if(VALUE_COMPRESSION_GZIP.equals(compression)) {
        currentOutput = new GZIPOutputStream(new FileOutputStream(currentFile));
      } else if(compression == null || VALUE_COMPRESSION_NONE.equals(compression)) {
        currentOutput = new FileOutputStream(currentFile);
      } else {
        // treat compression value as a command line
        ProcessBuilder pb = new ProcessBuilder(compression.trim().split("\\s+"));
        pb.directory();
        pb.redirectInput(Redirect.PIPE);
        pb.redirectOutput(currentFile);
        pb.redirectError(Redirect.INHERIT);
        currentProcess = pb.start();
        currentOutput = currentProcess.getOutputStream();
      }
    }
  }

}
