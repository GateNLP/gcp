/*
 *  FailureResult.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: FailureResult.java 16610 2013-03-21 16:05:54Z valyt $ 
 */
package gate.cloud.batch;

import gate.cloud.io.DocumentData;

import java.util.Map;


class FailureResult implements ProcessResult {

  private DocumentID documentId;
  
  private String errorDescription;
  
  private long fileSize = -1;
  
  private long docLength = -1;

  FailureResult(DocumentData docData, DocumentID docId, Throwable e) {
    documentId = docId;
    if(docData != null) {
      fileSize = docData.fileSize;
      docLength = docData.documentLength;
    }
    errorDescription = (e == null) ? "Internal error" : e.getLocalizedMessage();
  }
  
  public Map<String, Integer> getAnnotationCounts() {
    return null;
  }

  public DocumentID getDocumentId() {
    return documentId;
  }

  public long getExecutionTime() {
    return 0;
  }

  public long getOriginalFileSize() {
    return fileSize;
  }

  public long getDocumentLength() {
    return docLength;
  }

  public ReturnCode getReturnCode() {
    return ReturnCode.FAIL;
  }

  public String getErrorDescription() {
    return errorDescription;
  }

}
