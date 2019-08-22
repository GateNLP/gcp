/*
 *  FileDocumentEnumerator.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: FileDocumentEnumerator.java 13582 2011-03-29 16:23:58Z ian_roberts $ 
 */
package gate.cloud.io.file;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_DOCUMENT_ROOT;
import gate.cloud.io.AntBasedDocumentEnumerator;
import gate.util.GateException;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.tools.ant.types.FileSet;

public class FileDocumentEnumerator extends AntBasedDocumentEnumerator {

  /**
   * The top-level directory containing the document files.
   */
  protected File documentRoot;

  /**
   * The directory containing the batch specification file, or
   * <code>null</code> if the batch specification did not come from a
   * file.
   */
  protected File batchDir;

  public void config(Map<String, String> configData) throws GateException,
          IOException {
    super.config(configData);

    // doc root
    String docRootStr = configData.get(PARAM_DOCUMENT_ROOT);
    if(docRootStr == null || docRootStr.trim().length() == 0) {
      throw new IllegalArgumentException(
              "No value was provided for the required parameter \""
                      + PARAM_DOCUMENT_ROOT + "\"!");
    }
    String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
    if(batchFileStr != null) {
      batchDir = new File(batchFileStr).getParentFile();
    }
    documentRoot = new File(docRootStr);
    if(!documentRoot.isAbsolute()) {
      documentRoot = new File(batchDir, docRootStr);
    }
    if(!documentRoot.exists()) {
      throw new IllegalArgumentException("Directory \"" + documentRoot
              + "\", provided as value for required parameter \""
              + PARAM_DOCUMENT_ROOT + "\", does not exist!");
    }
    if(!documentRoot.isDirectory()) {
      throw new IllegalArgumentException("File \"" + documentRoot
              + "\", provided as value for required parameter \""
              + PARAM_DOCUMENT_ROOT + "\", is not a directory!");
    }
  }

  protected FileSet createFileSet() {
    FileSet fs = new FileSet();
    fs.setDir(documentRoot);
    return fs;
  }

}
