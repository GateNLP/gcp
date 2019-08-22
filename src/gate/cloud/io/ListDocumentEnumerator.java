/*
 *  ListDocumentEnumerator.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ListDocumentEnumerator.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.io;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_ENCODING;
import static gate.cloud.io.IOConstants.PARAM_FILE_NAME_PREFIX;
import gate.cloud.batch.DocumentID;
import gate.util.GateException;
import gate.util.GateRuntimeException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A document enumerator that reads a list of document IDs from a
 * text file, one ID per line.  Leading and trailing whitespace
 * are stripped from the lines of the file before the IDs are returned.
 */
public class ListDocumentEnumerator implements DocumentEnumerator {

  public static final String PARAM_FILE_NAME = "file";

  /**
   * The file containing the IDs.
   */
  protected File file;

  /**
   * The character encoding of the file (null means the platform
   * default encoding).
   */
  protected String encoding;

  /**
   * The reader used to read lines from the file.
   */
  protected BufferedReader reader;

  /**
   * The next ID to return.  The BufferedReader is conceptually
   * kept one step ahead of the iterator, i.e. we read the next
   * line from the file just before we return the previous one
   * from the next() method.
   */
  protected String nextId = null;

  /**
   * Optional prefix to add to the document IDs returned.
   */
  protected String prefix;

  /**
   * The directory containing the batch specification file, or
   * <code>null</code> if the batch specification did not come from a
   * file.
   */
  protected File batchDir;

  public void config(Map<String, String> configData) throws GateException,
          IOException {
    // list file
    String listFileStr = configData.get(PARAM_FILE_NAME);
    if(listFileStr == null || listFileStr.trim().length() == 0) {
      throw new IllegalArgumentException(
              "No value was provided for the required parameter \""
                      + PARAM_FILE_NAME + "\"!");
    }
    String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
    if(batchFileStr != null) {
      batchDir = new File(batchFileStr).getParentFile();
    }
    file = new File(listFileStr);
    if(!file.isAbsolute()) {
      file = new File(batchDir, listFileStr);
    }
    if(!file.canRead()) {
      throw new IllegalArgumentException("File \"" + file
              + "\", provided as value for required parameter \""
              + PARAM_FILE_NAME + "\", does not exist or is not readable!");
    }

    encoding = configData.get(PARAM_ENCODING);
    prefix = configData.get(PARAM_FILE_NAME_PREFIX);
    if(prefix == null) {
      prefix = "";
    }
  }

  public void init() throws IOException, GateException {
    // open the file and read the first line (if any).
    reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding));
    advance();
  }

  public boolean hasNext() {
    return nextId != null;
  }

  public DocumentID next() {
    if(nextId == null) {
      throw new NoSuchElementException("No more entries in list file "
              + file.getAbsolutePath());
    }
    String toReturn = nextId;
    try {
      advance();
    }
    catch(IOException e) {
      throw new GateRuntimeException("Exception reading list file "
              + file.getAbsolutePath(), e);
    }
    return new DocumentID(prefix + toReturn);
  }

  /**
   * Read the next line from the reader, closing it if we reach
   * EOF.
   */
  protected void advance() throws IOException {
    nextId = reader.readLine();
    if(nextId == null) {
      reader.close();
    } else {
      nextId = nextId.trim();
    }
  }

  public void remove() {
    throw new UnsupportedOperationException("remove not supported");
  }

}
