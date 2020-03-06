/*
 *  SerializedObjectOutputHandler.java
 *  Copyright (c) 2007-2012, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: SerializedObjectOutputHandler.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io.file;

import static gate.cloud.io.IOConstants.PARAM_ENCODING;
import static gate.cloud.io.IOConstants.PARAM_FILE_EXTENSION;
import gate.Document;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.OutputHandler;
import gate.util.Benchmark;
import gate.util.GateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;

/**
 * An {@link OutputHandler} that writes GATE Documents to files using
 * Java serialization. The files may be optionally gzip compressed. Note
 * that this always writes the complete document, any annotation type
 * filters specified in the batch definition are ignored.
 */
public class SerializedObjectOutputHandler extends AbstractFileOutputHandler {

  private static final Logger logger = LoggerFactory.getLogger(SerializedObjectOutputHandler.class);
  
  @Override
  protected void configImpl(Map<String, String> configData) throws IOException,
          GateException {
    // make sure we default to .ser as the extension
    if(!configData.containsKey(PARAM_FILE_EXTENSION)) {
      configData.put(PARAM_FILE_EXTENSION, ".ser");
    }
    if(configData.containsKey(PARAM_ENCODING)) {
      logger.warn("{} does not support the {} parameter - ignored",
              this.getClass().getName(), PARAM_ENCODING);
    }
    super.configImpl(configData);
  }

  @Override
  protected void outputDocumentImpl(Document document, DocumentID documentId)
          throws IOException, GateException {
    ObjectOutputStream outputStream =
            new ObjectOutputStream(getFileOutputStream(documentId));
    try {
      String saveBID =
              Benchmark.createBenchmarkId("saveSerialized", document.getName());
      long startTime = Benchmark.startPoint();
      outputStream.writeObject(document);
      Benchmark.checkPoint(startTime, saveBID, this, null);
    } finally {
      outputStream.close();
    }
  }

}
