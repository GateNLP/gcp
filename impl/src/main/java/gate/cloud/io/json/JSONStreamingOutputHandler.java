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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.apache.log4j.Logger;

import gate.Document;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.file.JSONOutputHandler;
import gate.cloud.io.file.StreamingFileOutputHelper;
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

  private StreamingFileOutputHelper<byte[], OutputStream> helper;

  public JSONStreamingOutputHandler() {
    helper = new StreamingFileOutputHelper<byte[], OutputStream>(
        new byte[0],
        // we use the output stream as-is
        x -> x,
        (OutputStream os, byte[] bytes) -> {
          try {
            os.write(bytes);
            os.flush();
          } catch(IOException e) {
            logger.warn("Error writing to file", e);
          }
        },
        (byte[] bytes) -> bytes.length);
  }
  
  @Override
  protected void configImpl(Map<String, String> configData) throws IOException,
          GateException {
    // TODO Auto-generated method stub
    super.configImpl(configData);
    helper.config(configData);
  }

  protected ThreadLocal<ByteArrayOutputStream> baos =
          new ThreadLocal<ByteArrayOutputStream>() {
            protected ByteArrayOutputStream initialValue() {
              return new ByteArrayOutputStream();
            }
          };

  @Override
  protected void outputDocumentImpl(Document document, DocumentID documentId)
          throws IOException, GateException {
    super.outputDocumentImpl(document, documentId);
    baos.get().write('\n');
    byte[] result = baos.get().toByteArray();
    baos.get().reset();
    helper.sendItem(result);
  }

  @Override
  protected OutputStream getFileOutputStream(DocumentID docId)
          throws IOException {
    return baos.get();
  }

  @Override
  public void init() throws IOException, GateException {
    helper.init();
  }

  @Override
  public void close() throws IOException, GateException {
    helper.close();
  }
}
